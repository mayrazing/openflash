package openflash_ai_runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JsonlRpcPeerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long TIMEOUT_SECONDS = 3;

    private final List<Fixture> fixtures = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Fixture fixture : fixtures)
            fixture.close();
    }

    @Test
    void assignsMonotonicIdsAndMatchesOutOfOrderResponses() throws Exception {
        Fixture fixture = fixture();

        CompletionStage<JsonNode> first = fixture.peer.request("first", Map.of("value", 1));
        CompletionStage<JsonNode> second = fixture.peer.request("second", Map.of("value", 2));

        List<byte[]> writes = fixture.childStdin.awaitWrites(2);
        assertEquals(1L, parseWrite(writes.get(0)).path("id").longValue());
        assertEquals(2L, parseWrite(writes.get(1)).path("id").longValue());
        fixture.serverStdout.writeUtf8(
                "{\"id\":2,\"result\":{\"requestId\":2}}\n"
                        + "{\"id\":1,\"result\":{\"requestId\":1}}\n");

        Set<Long> completedRequestIds = Set.of(
                await(first).path("requestId").longValue(),
                await(second).path("requestId").longValue());
        assertEquals(Set.of(1L, 2L), completedRequestIds);
    }

    @Test
    void writesEachConcurrentNotificationAsOneCompleteUtf8JsonLine() throws Exception {
        Fixture fixture = fixture();
        int notificationCount = 40;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> sends = new ArrayList<>();
        try {
            for (int i = 0; i < notificationCount; i++) {
                int index = i;
                sends.add(pool.submit(() -> {
                    start.await();
                    fixture.peer.notify("通知-" + index, Map.of("text", "你好🙂-" + index));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> send : sends)
                send.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        List<byte[]> writes = fixture.childStdin.awaitWrites(notificationCount);
        assertEquals(notificationCount, writes.size());
        Set<String> methods = new HashSet<>();
        for (byte[] write : writes) {
            JsonNode line = parseWrite(write);
            assertFalse(line.has("jsonrpc"));
            methods.add(line.path("method").textValue());
            assertTrue(line.path("params").path("text").textValue().startsWith("你好🙂-"));
        }
        assertEquals(notificationCount, methods.size());
    }

    @Test
    void parsesPartialChunksMultipleLinesAndCrlfAndDispatchesMethods() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch received = new CountDownLatch(2);
        List<String> notifications = Collections.synchronizedList(new ArrayList<>());
        AutoCloseable subscription = fixture.peer.onNotification((method, params) -> {
            notifications.add(method + ":" + params.path("n").intValue());
            received.countDown();
        });

        fixture.serverStdout.writeUtf8("{\"method\":\"alpha\",\"params\":{");
        assertFalse(received.await(100, TimeUnit.MILLISECONDS));
        fixture.serverStdout.writeUtf8("\"n\":1}}\n{\"method\":\"beta\",\"params\":{\"n\":2}}\r\n");

        assertTrue(received.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(Set.of("alpha:1", "beta:2"), new HashSet<>(notifications));
        subscription.close();
    }

    @Test
    void silentlyIgnoresNotificationWhenNoHandlerKnowsIt() throws Exception {
        Fixture fixture = fixture();
        CompletionStage<JsonNode> pending = fixture.peer.request("still-alive", Map.of());
        fixture.childStdin.awaitWrites(1);

        fixture.serverStdout.writeUtf8(
                "{\"method\":\"unknown/event\",\"params\":{\"ignored\":true}}\n"
                        + "{\"id\":1,\"result\":{\"ok\":true}}\n");

        assertTrue(await(pending).path("ok").booleanValue());
    }

    @Test
    void immediatelyRejectsServerInitiatedRequestWithoutJsonrpcField() throws Exception {
        Fixture fixture = fixture();

        fixture.serverStdout.writeUtf8(
                "{\"id\":91,\"method\":\"account/read\",\"params\":{\"secret\":\"do-not-echo\"}}\n");

        JsonNode serverRequestReply = parseWrite(fixture.childStdin.awaitWrites(1).get(0));
        assertEquals(91L, serverRequestReply.path("id").longValue());
        assertTrue(serverRequestReply.path("error").isObject());
        assertEquals(-32601, serverRequestReply.path("error").path("code").intValue());
        assertFalse(serverRequestReply.has("jsonrpc"));
        assertFalse(serverRequestReply.toString().contains("do-not-echo"));
    }

    @Test
    void preservesRpcErrorCodeAndSafeMessageWithoutErrorData() throws Exception {
        Fixture fixture = fixture();
        CompletionStage<JsonNode> pending = fixture.peer.request("fail", Map.of());
        fixture.childStdin.awaitWrites(1);

        fixture.serverStdout.writeUtf8(
                "{\"id\":1,\"error\":{\"code\":-32007,\"message\":\"model unavailable\","
                        + "\"data\":{\"prompt\":\"do-not-leak\"}}}\n");

        ExecutionException thrown = assertThrows(
                ExecutionException.class,
                () -> pending.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        JsonlRpcPeer.RpcException rpcError = assertInstanceOf(JsonlRpcPeer.RpcException.class, thrown.getCause());
        assertEquals(-32007, rpcError.code());
        assertEquals("model unavailable", rpcError.getMessage());
        assertFalse(rpcError.getMessage().contains("do-not-leak"));
    }

    @Test
    void malformedJsonClosesPeerAndFailsEveryPendingRequestWithSafeError() throws Exception {
        Fixture fixture = fixture();
        CompletionStage<JsonNode> first = fixture.peer.request("first", Map.of());
        CompletionStage<JsonNode> second = fixture.peer.request("second", Map.of());
        fixture.childStdin.awaitWrites(2);

        fixture.serverStdout.writeUtf8("{malformed-prompt-secret}\n");

        JsonlRpcPeer.PeerClosedException firstFailure = assertPeerFailure(first);
        assertPeerFailure(second);
        assertTerminalFailure(fixture.peer);
        assertFalse(firstFailure.getMessage().contains("malformed-prompt-secret"));
        assertPeerFailure(fixture.peer.request("after-close", Map.of()));
    }

    @Test
    void oversizedLineClosesPeerAndFailsEveryPendingRequest() throws Exception {
        Fixture fixture = fixture();
        CompletionStage<JsonNode> pending = fixture.peer.request("waiting", Map.of());
        fixture.childStdin.awaitWrites(1);
        byte[] oversized = new byte[JsonlRpcPeer.MAX_LINE_BYTES + 2];
        Arrays.fill(oversized, (byte) 'x');
        oversized[oversized.length - 1] = (byte) '\n';

        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
            try {
                fixture.serverStdout.write(oversized);
                fixture.serverStdout.flush();
            } catch (IOException ignored) {
                // Peer closes the read side as soon as the byte limit is crossed.
            }
        });

        assertPeerFailure(pending);
        assertTerminalFailure(fixture.peer);
        writer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void stdoutEofClosesPeerAndFailsEveryPendingRequest() throws Exception {
        Fixture fixture = fixture();
        CompletionStage<JsonNode> first = fixture.peer.request("first", Map.of());
        CompletionStage<JsonNode> second = fixture.peer.request("second", Map.of());
        fixture.childStdin.awaitWrites(2);

        fixture.serverStdout.close();

        assertPeerFailure(first);
        assertPeerFailure(second);
        assertTerminalFailure(fixture.peer);
    }

    @Test
    void stdoutEofDoesNotDispatchUnterminatedResponse() throws Exception {
        Fixture fixture = fixture();
        CompletionStage<JsonNode> pending = fixture.peer.request("waiting", Map.of());
        fixture.childStdin.awaitWrites(1);

        fixture.serverStdout.writeUtf8("{\"id\":1,\"result\":{\"mustNotDispatch\":true}}");
        fixture.serverStdout.close();

        assertPeerFailure(pending);
    }

    @Test
    void writeFailureClosesPeerAndFailsEveryPendingRequest() throws Exception {
        PipedInputStream childStdout = new PipedInputStream();
        PipedOutputStream serverStdout = new PipedOutputStream(childStdout);
        FailAfterWritesOutputStream childStdin = new FailAfterWritesOutputStream(1);
        Fixture fixture = fixture(childStdout, serverStdout, childStdin);
        CompletionStage<JsonNode> first = fixture.peer.request("first", Map.of());

        CompletionStage<JsonNode> second = fixture.peer.request("second", Map.of());

        assertPeerFailure(first);
        assertPeerFailure(second);
        assertTerminalFailure(fixture.peer);
    }

    @Test
    void closeIsIdempotentAndFailsPendingRequest() throws Exception {
        Fixture fixture = fixture();
        CompletionStage<JsonNode> pending = fixture.peer.request("waiting", Map.of());
        fixture.childStdin.awaitWrites(1);

        fixture.peer.close();
        fixture.peer.close();

        assertPeerFailure(pending);
    }

    @Test
    void cancellingRequestRemovesPendingWithoutServerResponse() throws Exception {
        Fixture fixture = fixture();
        CompletableFuture<JsonNode> pending = fixture.peer.request("never-answered", Map.of()).toCompletableFuture();
        fixture.childStdin.awaitWrites(1);
        assertEquals(1, pendingRequestCount(fixture.peer));

        assertTrue(pending.cancel(false));

        assertEquals(0, pendingRequestCount(fixture.peer));
    }

    @Test
    void cancellingModelCatalogRemovesUnderlyingPeerRequest() throws Exception {
        Fixture fixture = fixture();
        CodexModelCatalog.Rpc rpc = fixture.peer::request;
        CodexAppServerClient client = new CodexAppServerClient(
                () -> CompletableFuture.completedFuture(rpc),
                new CodexModelCatalog(),
                null);
        CompletableFuture<CodexModelCatalog.Catalog> catalog = client.models().toCompletableFuture();
        fixture.childStdin.awaitWrites(1);
        assertEquals(1, pendingRequestCount(fixture.peer));

        assertTrue(catalog.cancel(false));

        assertEquals(0, pendingRequestCount(fixture.peer));
    }

    @Test
    void paramsSerializationFailureDoesNotLeavePendingRequest() throws Exception {
        Fixture fixture = fixture();

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.peer.request("cannot-serialize", new ThrowingParams()));

        assertEquals(0, pendingRequestCount(fixture.peer));
        assertEquals(0, fixture.childStdin.awaitWrites(0).size());
    }

    @Test
    void requestRacingCloseCannotLeavePendingRequest() throws Exception {
        Fixture fixture = fixture();
        BlockingParams params = new BlockingParams();
        CompletableFuture<CompletionStage<JsonNode>> requestCall = CompletableFuture
                .supplyAsync(() -> fixture.peer.request("racing-close", params));
        assertTrue(params.serializationStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        fixture.peer.close();
        params.allowSerialization.countDown();

        assertPeerFailure(requestCall.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(0, pendingRequestCount(fixture.peer));
    }

    @Test
    void responseCompletionRacingCloseKeepsResponseResult() throws Exception {
        Fixture fixture = fixture();
        CompletableFuture<JsonNode> result = fixture.peer.request("racing-response", Map.of()).toCompletableFuture();
        fixture.childStdin.awaitWrites(1);
        CountDownLatch completionStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        CompletableFuture<JsonNode> observed = result.whenComplete((value, failure) -> {
            completionStarted.countDown();
            try {
                if (!allowCompletion.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("response completion was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("response completion interrupted", interrupted);
            }
        });

        try {
            fixture.serverStdout.writeUtf8("{\"id\":1,\"result\":{\"ok\":true}}\n");
            assertTrue(completionStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            fixture.peer.close();
        } finally {
            allowCompletion.countDown();
        }

        assertTrue(observed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).path("ok").booleanValue());
        assertEquals(0, pendingRequestCount(fixture.peer));
    }

    @Test
    void notificationHandlerExceptionDoesNotStopOtherHandlersOrResponses() throws Exception {
        Fixture fixture = fixture();
        fixture.peer.onNotification((method, params) -> {
            throw new IllegalStateException("listener failed");
        });
        CountDownLatch healthyHandlerCalled = new CountDownLatch(1);
        fixture.peer.onNotification((method, params) -> healthyHandlerCalled.countDown());
        CompletionStage<JsonNode> pending = fixture.peer.request("still-alive", Map.of());
        fixture.childStdin.awaitWrites(1);

        fixture.serverStdout.writeUtf8(
                "{\"method\":\"event\",\"params\":{}}\n"
                        + "{\"id\":1,\"result\":{\"ok\":true}}\n");

        assertTrue(healthyHandlerCalled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(await(pending).path("ok").booleanValue());
    }

    @Test
    void lateDuplicateAndUnknownResponseIdsCannotCompleteAnotherRequest() throws Exception {
        Fixture fixture = fixture();
        CompletableFuture<JsonNode> cancelled = fixture.peer.request("cancelled", Map.of()).toCompletableFuture();
        CompletableFuture<JsonNode> active = fixture.peer.request("active", Map.of()).toCompletableFuture();
        fixture.childStdin.awaitWrites(2);
        assertTrue(cancelled.cancel(false));
        CountDownLatch barrier = new CountDownLatch(1);
        fixture.peer.onNotification((method, params) -> {
            if (method.equals("barrier"))
                barrier.countDown();
        });

        fixture.serverStdout.writeUtf8(
                "{\"id\":1,\"result\":{\"wrong\":1}}\n"
                        + "{\"id\":1,\"result\":{\"wrong\":2}}\n"
                        + "{\"id\":999,\"result\":{\"wrong\":3}}\n"
                        + "{\"method\":\"barrier\",\"params\":{}}\n");

        assertTrue(barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertFalse(active.isDone());
        fixture.serverStdout.writeUtf8("{\"id\":2,\"result\":{\"right\":true}}\n");
        assertTrue(active.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).path("right").booleanValue());
    }

    private Fixture fixture() throws IOException {
        PipedInputStream childStdout = new PipedInputStream(64 * 1024);
        PipedOutputStream serverStdout = new PipedOutputStream(childStdout);
        return fixture(childStdout, serverStdout, new RecordingOutputStream());
    }

    private Fixture fixture(
            InputStream childStdout,
            PipedOutputStream serverStdout,
            RecordingOutputStream childStdin) {
        Fixture fixture = new Fixture(
                new JsonlRpcPeer(childStdout, childStdin), serverStdout, childStdin);
        fixtures.add(fixture);
        return fixture;
    }

    private static JsonNode await(CompletionStage<JsonNode> stage) throws Exception {
        return stage.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static JsonlRpcPeer.PeerClosedException assertPeerFailure(
            CompletionStage<JsonNode> stage) {
        ExecutionException thrown = assertThrows(
                ExecutionException.class,
                () -> stage.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        return assertInstanceOf(JsonlRpcPeer.PeerClosedException.class, thrown.getCause());
    }

    private static void assertTerminalFailure(JsonlRpcPeer peer) {
        ExecutionException thrown = assertThrows(
                ExecutionException.class,
                () -> peer.terminal().toCompletableFuture()
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertInstanceOf(JsonlRpcPeer.PeerClosedException.class, thrown.getCause());
    }

    private static JsonNode parseWrite(byte[] write) throws IOException {
        assertTrue(write.length > 1);
        assertEquals((byte) '\n', write[write.length - 1]);
        assertEquals(1L, count(write, (byte) '\n'));
        String utf8Line = new String(write, 0, write.length - 1, StandardCharsets.UTF_8);
        return JSON.readTree(utf8Line);
    }

    private static int pendingRequestCount(JsonlRpcPeer peer) throws ReflectiveOperationException {
        Field pendingField = JsonlRpcPeer.class.getDeclaredField("pending");
        pendingField.setAccessible(true);
        return ((Map<?, ?>) pendingField.get(peer)).size();
    }

    private static long count(byte[] bytes, byte target) {
        long count = 0;
        for (byte value : bytes) {
            if (value == target)
                count++;
        }
        return count;
    }

    private static final class Fixture implements AutoCloseable {
        private final JsonlRpcPeer peer;
        private final Utf8Pipe serverStdout;
        private final RecordingOutputStream childStdin;

        private Fixture(
                JsonlRpcPeer peer,
                PipedOutputStream serverStdout,
                RecordingOutputStream childStdin) {
            this.peer = peer;
            this.serverStdout = new Utf8Pipe(serverStdout);
            this.childStdin = childStdin;
        }

        @Override
        public void close() {
            peer.close();
            try {
                serverStdout.close();
            } catch (IOException ignored) {
                // Test cleanup only.
            }
        }
    }

    private static final class Utf8Pipe extends OutputStream {
        private final OutputStream delegate;

        private Utf8Pipe(OutputStream delegate) {
            this.delegate = delegate;
        }

        private void writeUtf8(String value) throws IOException {
            write(value.getBytes(StandardCharsets.UTF_8));
            flush();
        }

        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            delegate.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static class RecordingOutputStream extends OutputStream {
        private final List<byte[]> writes = new ArrayList<>();

        @Override
        public synchronized void write(int value) {
            writes.add(new byte[] { (byte) value });
            notifyAll();
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            writes.add(Arrays.copyOfRange(bytes, offset, offset + length));
            notifyAll();
        }

        private synchronized List<byte[]> awaitWrites(int expected) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (writes.size() < expected) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0)
                    break;
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
            assertEquals(expected, writes.size());
            return List.copyOf(writes);
        }
    }

    private static final class FailAfterWritesOutputStream extends RecordingOutputStream {
        private int successfulWritesRemaining;

        private FailAfterWritesOutputStream(int successfulWrites) {
            this.successfulWritesRemaining = successfulWrites;
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            if (successfulWritesRemaining-- == 0)
                throw new IOException("simulated broken pipe");
            super.write(bytes, offset, length);
        }
    }

    private static final class ThrowingParamsSerializer extends JsonSerializer<ThrowingParams> {
        @Override
        public void serialize(
                ThrowingParams value, JsonGenerator generator, SerializerProvider serializers)
                throws IOException {
            throw new IllegalStateException("cannot serialize params");
        }
    }

    private static final class BlockingParamsSerializer extends JsonSerializer<BlockingParams> {
        @Override
        public void serialize(
                BlockingParams value, JsonGenerator generator, SerializerProvider serializers)
                throws IOException {
            value.serializationStarted.countDown();
            try {
                if (!value.allowSerialization.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("params serialization was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("params serialization interrupted", interrupted);
            }
            generator.writeStartObject();
            generator.writeStringField("value", "serialized");
            generator.writeEndObject();
        }
    }

    @JsonSerialize(using = ThrowingParamsSerializer.class)
    private static final class ThrowingParams {
    }

    @JsonSerialize(using = BlockingParamsSerializer.class)
    private static final class BlockingParams {
        private final CountDownLatch serializationStarted = new CountDownLatch(1);
        private final CountDownLatch allowSerialization = new CountDownLatch(1);
    }
}
