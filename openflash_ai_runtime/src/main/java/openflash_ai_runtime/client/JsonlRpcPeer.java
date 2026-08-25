package openflash_ai_runtime.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * 在一对进程流上收发无 {@code jsonrpc} 字段的 JSONL request/response/notification.
 * 此类只负责 wire 多路复用和连接级故障传播, 不解释业务 turn 生命周期.
 */
public final class JsonlRpcPeer implements AutoCloseable {

    public static final int MAX_LINE_BYTES = 1024 * 1024;

    private static final int READ_BUFFER_BYTES = 8192;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_ERROR_CODE = -32603;
    private static final int MAX_SAFE_ERROR_MESSAGE_CHARS = 512;
    private static final String GENERIC_RPC_ERROR_MESSAGE = "RPC request failed";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final InputStream stdout;
    private final OutputStream stdin;
    private final Object lifecycleLock = new Object();
    private final Object writerLock = new Object();
    private final AtomicLong nextRequestId = new AtomicLong();
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pending =
            new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<BiConsumer<String, JsonNode>> notificationHandlers =
            new CopyOnWriteArrayList<>();
    private final CompletableFuture<Void> terminal = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile PeerClosedException terminalFailure =
            new PeerClosedException("RPC peer closed");

    /** 启动唯一 stdout reader; stdout 来自 child stdout, stdin 写入 child stdin. */
    public JsonlRpcPeer(InputStream stdout, OutputStream stdin) {
        this.stdout = Objects.requireNonNull(stdout, "stdout");
        this.stdin = Objects.requireNonNull(stdin, "stdin");
        Thread reader = new Thread(this::readLoop, "codex-jsonl-rpc-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** 发送 request, 返回只由同 id response 完成的 stage. */
    public CompletionStage<JsonNode> request(String method, Object params) {
        Objects.requireNonNull(method, "method");
        if (closed.get()) return CompletableFuture.failedFuture(terminalFailure);

        long id = nextRequestId.incrementAndGet();
        ObjectNode message = JSON.createObjectNode();
        message.put("id", id);
        message.put("method", method);
        message.set("params", JSON.valueToTree(params));
        byte[] line = serializeLine(message);

        CompletableFuture<JsonNode> result = new CompletableFuture<>();
        result.whenComplete((value, failure) -> pending.remove(id, result));
        pending.put(id, result);
        if (closed.get()) {
            failPending(id, result, terminalFailure);
            return result;
        }
        try {
            writeLine(line);
        } catch (PeerClosedException failure) {
            failPending(id, result, failure);
        }
        return result;
    }

    /** 发送不带 id 的 notification. */
    public void notify(String method, Object params) {
        Objects.requireNonNull(method, "method");
        ObjectNode message = JSON.createObjectNode();
        message.put("method", method);
        message.set("params", JSON.valueToTree(params));
        writeLine(message);
    }

    /** 注册 notification listener; 关闭返回值可幂等取消该 listener. */
    public AutoCloseable onNotification(BiConsumer<String, JsonNode> handler) {
        Objects.requireNonNull(handler, "handler");
        synchronized (lifecycleLock) {
            if (closed.get()) throw terminalFailure;
            notificationHandlers.add(handler);
        }
        AtomicBoolean subscribed = new AtomicBoolean(true);
        return () -> {
            if (subscribed.compareAndSet(true, false)) notificationHandlers.remove(handler);
        };
    }

    /** 连接因 close/EOF/非法 wire/超大 wire/写失败进入唯一 terminal 时完成. */
    public CompletionStage<Void> terminal() {
        return terminal;
    }

    /** 幂等关闭双向流并失败所有 pending request. */
    @Override
    public void close() {
        terminate(new PeerClosedException("RPC peer closed"));
    }

    private void readLoop() {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        byte[] chunk = new byte[READ_BUFFER_BYTES];
        try {
            while (!closed.get()) {
                int count = stdout.read(chunk);
                if (count == -1) {
                    terminate(new PeerClosedException("RPC peer closed: stdout EOF"));
                    return;
                }
                for (int i = 0; i < count; i++) {
                    byte value = chunk[i];
                    if (value == '\n') {
                        readLine(line.toByteArray());
                        line.reset();
                    } else {
                        if (line.size() == MAX_LINE_BYTES) {
                            terminate(new PeerClosedException("RPC peer closed: oversized line"));
                            return;
                        }
                        line.write(value);
                    }
                }
            }
        } catch (IOException failure) {
            if (!closed.get()) {
                terminate(new PeerClosedException("RPC peer closed: stdout read failed"));
            }
        } catch (MalformedWireException failure) {
            terminate(new PeerClosedException("RPC peer closed: malformed JSON"));
        } catch (PeerClosedException failure) {
            terminate(failure);
        } catch (RuntimeException failure) {
            terminate(new PeerClosedException("RPC peer closed: invalid message"));
        }
    }

    private void readLine(byte[] bytes) {
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') length--;
        try {
            JsonNode message = JSON.readTree(length == bytes.length
                    ? bytes
                    : Arrays.copyOf(bytes, length));
            if (message == null || !message.isObject()) throw new MalformedWireException();
            dispatch(message);
        } catch (JsonProcessingException failure) {
            throw new MalformedWireException();
        } catch (IOException failure) {
            throw new MalformedWireException();
        }
    }

    private void dispatch(JsonNode message) {
        JsonNode method = message.get("method");
        if (method != null) {
            if (!method.isTextual()) throw new MalformedWireException();
            if (message.has("id")) rejectServerRequest(message.get("id"));
            else dispatchNotification(method.textValue(), message.get("params"));
            return;
        }
        if (message.has("id")) {
            dispatchResponse(message);
            return;
        }
        throw new MalformedWireException();
    }

    private void dispatchResponse(JsonNode message) {
        JsonNode idNode = message.get("id");
        if (idNode == null || !idNode.isIntegralNumber() || !idNode.canConvertToLong()) return;
        long id = idNode.longValue();
        CompletableFuture<JsonNode> result = pending.get(id);
        if (result == null) return;

        JsonNode error = message.get("error");
        if (error != null && error.isObject()) {
            int code = error.path("code").canConvertToInt()
                    ? error.path("code").intValue()
                    : INVALID_ERROR_CODE;
            if (pending.remove(id, result)) {
                result.completeExceptionally(
                        new RpcException(code, safeRpcErrorMessage(error.get("message"))));
            }
            return;
        }
        if (message.has("result")) {
            if (pending.remove(id, result)) result.complete(message.get("result"));
            return;
        }
        throw new MalformedWireException();
    }

    private void dispatchNotification(String method, JsonNode params) {
        JsonNode safeParams = params == null ? JSON.nullNode() : params;
        for (BiConsumer<String, JsonNode> handler : notificationHandlers) {
            try {
                handler.accept(method, safeParams);
            } catch (RuntimeException ignored) {
                // Listener failure is local; it must not corrupt wire reader or expose payload in logs.
            }
        }
    }

    private void rejectServerRequest(JsonNode id) {
        ObjectNode response = JSON.createObjectNode();
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", METHOD_NOT_FOUND);
        error.put("message", "Server-initiated requests are not supported");
        writeLine(response);
    }

    private byte[] serializeLine(JsonNode message) {
        byte[] json;
        try {
            json = JSON.writeValueAsBytes(message);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("RPC message serialization failed");
        }
        byte[] line = Arrays.copyOf(json, json.length + 1);
        line[line.length - 1] = '\n';
        return line;
    }

    private void writeLine(JsonNode message) {
        writeLine(serializeLine(message));
    }

    private void writeLine(byte[] line) {
        synchronized (writerLock) {
            if (closed.get()) throw terminalFailure;
            try {
                stdin.write(line);
                stdin.flush();
            } catch (IOException failure) {
                PeerClosedException safeFailure =
                        new PeerClosedException("RPC peer closed: stdin write failed");
                terminate(safeFailure);
                throw safeFailure;
            }
        }
    }

    private void terminate(PeerClosedException failure) {
        synchronized (lifecycleLock) {
            if (closed.get()) return;
            terminalFailure = failure;
            closed.set(true);
            notificationHandlers.clear();
        }
        pending.forEach((id, result) -> failPending(id, result, failure));
        closeQuietly(stdout);
        closeQuietly(stdin);
        terminal.completeExceptionally(failure);
    }

    private void failPending(
            long id,
            CompletableFuture<JsonNode> result,
            PeerClosedException failure) {
        if (pending.remove(id, result)) result.completeExceptionally(failure);
    }

    private static String safeRpcErrorMessage(JsonNode message) {
        if (message == null || !message.isTextual()) return GENERIC_RPC_ERROR_MESSAGE;
        String value = message.textValue();
        if (value.isBlank() || value.length() > MAX_SAFE_ERROR_MESSAGE_CHARS) {
            return GENERIC_RPC_ERROR_MESSAGE;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) return GENERIC_RPC_ERROR_MESSAGE;
        }
        return value;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // First terminal failure owns the safe error surfaced to callers.
        }
    }

    /** JSON-RPC error response; excludes error data and raw wire payload. */
    public static final class RpcException extends RuntimeException {
        private final int code;

        private RpcException(int code, String message) {
            super(message);
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    /** Connection-level terminal failure with a bounded, non-wire-derived message. */
    public static final class PeerClosedException extends RuntimeException {
        private PeerClosedException(String message) {
            super(message);
        }
    }

    private static final class MalformedWireException extends RuntimeException {}
}
