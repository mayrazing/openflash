package openflash_ai_runtime.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import openflash_ai_runtime.common.AiErrorCode;
import openflash_ai_runtime.common.CodexAppException;
import openflash_ai_runtime.dto.GenerationProfile;
import openflash_ai_runtime.service.RuntimeSystemConfigService;
import openflash_ai_runtime.support.CodexProcessManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 编排 Codex account/model RPC 并只向调用方返回安全状态. */
@Component
@ConditionalOnProperty(prefix = "app.codex", name = "enabled", havingValue = "true")
public class CodexAppServerClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodexAppServerClient.class);
    private static final String STATUS_TIMEOUT_KEY = "ai.codex-status-timeout-millis";
    private static final long DEFAULT_STATUS_TIMEOUT_MILLIS = 5000L;
    private static final String GENERATION_TIMEOUT_KEY = "ai.codex-timeout-millis";
    private static final long DEFAULT_GENERATION_TIMEOUT_MILLIS = 90000L;
    private static final long DEFAULT_ASYNC_TASK_LEASE_MILLIS = 120000L;
    private static final long LEASE_SAFETY_MARGIN_MILLIS = 5000L;
    private static final long DEFAULT_INTERRUPT_GRACE_MILLIS = 250L;
    private static final long DEFAULT_TOMBSTONE_RETENTION_MILLIS = 5000L;
    private static final String TRANSPORT_GUARD =
            "Return only the requested text. Do not call tools, commands, web, apps, plugins, skills, or subagents.";
    private static final String BASE_INSTRUCTIONS =
            "You are a plain text generation assistant. "
                    + "Follow the developer instructions and user input.";
    private static final Map<String, Object> CLEAN_THREAD_CONFIG = Map.ofEntries(
            Map.entry("include_permissions_instructions", false),
            Map.entry("include_apps_instructions", false),
            Map.entry("include_collaboration_mode_instructions", false),
            Map.entry("include_environment_context", false),
            Map.entry("project_doc_max_bytes", 0),
            Map.entry("skills.include_instructions", false),
            Map.entry("features.apps", false),
            Map.entry("features.plugins", false),
            Map.entry("features.tool_suggest", false),
            Map.entry("features.multi_agent", false),
            Map.entry("features.multi_agent_v2.root_agent_usage_hint_text", ""),
            Map.entry("features.multi_agent_v2.multi_agent_mode_hint_text", ""),
            Map.entry("web_search", "disabled"));

    private final ConnectionProvider connectionProvider;
    private final GenerationConnectionProvider generationConnectionProvider;
    private final CodexModelCatalog modelCatalog;
    private final RuntimeSystemConfigService systemConfigService;
    private final OwnedDirectoryFactory ownedDirectoryFactory;
    private final long interruptGraceMillis;
    private final long tombstoneRetentionMillis;
    private final ConcurrentHashMap<Long, GenerationWatch> generationWatches =
            new ConcurrentHashMap<>();

    /** 使用 lazy process manager 构造生产 app-server client. */
    @Autowired
    public CodexAppServerClient(
            CodexProcessManager processManager,
            CodexModelCatalog modelCatalog,
            RuntimeSystemConfigService systemConfigService) {
        this(
                () -> processManager.connection().thenApply(connection ->
                        (CodexModelCatalog.Rpc) (method, params) ->
                                connection.peer().request(method, params)),
                () -> processManager.connection().thenApply(ManagedGenerationConnection::new),
                modelCatalog,
                systemConfigService,
                () -> Files.createTempDirectory("openflash-codex-request-"),
                DEFAULT_INTERRUPT_GRACE_MILLIS,
                DEFAULT_TOMBSTONE_RETENTION_MILLIS);
    }

    CodexAppServerClient(
            ConnectionProvider connectionProvider,
            CodexModelCatalog modelCatalog,
            RuntimeSystemConfigService systemConfigService) {
        this(
                connectionProvider,
                null,
                modelCatalog,
                systemConfigService,
                null,
                DEFAULT_INTERRUPT_GRACE_MILLIS,
                DEFAULT_TOMBSTONE_RETENTION_MILLIS);
    }

    CodexAppServerClient(
            GenerationConnectionProvider generationConnectionProvider,
            CodexModelCatalog modelCatalog,
            RuntimeSystemConfigService systemConfigService,
            OwnedDirectoryFactory ownedDirectoryFactory,
            long interruptGraceMillis,
            long tombstoneRetentionMillis) {
        this(
                null,
                generationConnectionProvider,
                modelCatalog,
                systemConfigService,
                ownedDirectoryFactory,
                interruptGraceMillis,
                tombstoneRetentionMillis);
    }

    private CodexAppServerClient(
            ConnectionProvider connectionProvider,
            GenerationConnectionProvider generationConnectionProvider,
            CodexModelCatalog modelCatalog,
            RuntimeSystemConfigService systemConfigService,
            OwnedDirectoryFactory ownedDirectoryFactory,
            long interruptGraceMillis,
            long tombstoneRetentionMillis) {
        this.connectionProvider = connectionProvider;
        this.generationConnectionProvider = generationConnectionProvider;
        this.modelCatalog = modelCatalog;
        this.systemConfigService = systemConfigService;
        this.ownedDirectoryFactory = ownedDirectoryFactory;
        this.interruptGraceMillis = Math.max(1L, interruptGraceMillis);
        this.tombstoneRetentionMillis = Math.max(1L, tombstoneRetentionMillis);
    }

    /** 查询账号可用性; 失败只映射 enum, 不返回账号或原始错误. */
    public StatusResponse status() {
        CompletableFuture<JsonNode> accountRead = null;
        try {
            long configuredTimeout = systemConfigService.getLong(
                    STATUS_TIMEOUT_KEY, DEFAULT_STATUS_TIMEOUT_MILLIS);
            long timeoutMillis = configuredTimeout > 0
                    ? configuredTimeout
                    : DEFAULT_STATUS_TIMEOUT_MILLIS;
            accountRead = accountRead();
            JsonNode response = accountRead.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return mapStatus(response);
        } catch (InterruptedException interrupted) {
            cancel(accountRead);
            Thread.currentThread().interrupt();
            logFailure(interrupted);
            return new StatusResponse(StatusCode.ERROR);
        } catch (TimeoutException timeout) {
            cancel(accountRead);
            logFailure(timeout);
            return new StatusResponse(StatusCode.ERROR);
        } catch (ExecutionException | RuntimeException failure) {
            if (containsNotInstalled(failure)) {
                return new StatusResponse(StatusCode.NOT_INSTALLED);
            }
            logFailure(failure);
            return new StatusResponse(StatusCode.ERROR);
        }
    }

    /** 创建可取消的 account/read 链; outer cancel 会清理 acquire 和 inner RPC pending. */
    private CompletableFuture<JsonNode> accountRead() {
        CompletableFuture<JsonNode> result = new CompletableFuture<>();
        CompletableFuture<CodexModelCatalog.Rpc> connection;
        try {
            connection = connectionProvider.connection().toCompletableFuture();
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        AtomicReference<CompletableFuture<JsonNode>> activeRequest = new AtomicReference<>();
        result.whenComplete((value, failure) -> {
            if (!result.isCancelled()) return;
            connection.cancel(true);
            cancel(activeRequest.get());
        });
        connection.whenComplete((rpc, connectionFailure) -> {
            if (connectionFailure != null) {
                result.completeExceptionally(connectionFailure);
                return;
            }
            if (result.isCancelled()) return;
            CompletableFuture<JsonNode> request;
            try {
                request = rpc.request(
                        "account/read", Map.of("refreshToken", false)).toCompletableFuture();
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
                return;
            }
            activeRequest.set(request);
            if (result.isCancelled()) {
                request.cancel(true);
                return;
            }
            request.whenComplete((response, requestFailure) -> {
                if (requestFailure == null) result.complete(response);
                else result.completeExceptionally(requestFailure);
            });
        });
        return result;
    }

    private static void cancel(CompletableFuture<?> future) {
        if (future != null) future.cancel(true);
    }

    /** 读取 app-server runtime model truth. */
    public CompletionStage<CodexModelCatalog.Catalog> models() {
        CompletableFuture<CodexModelCatalog.Catalog> result = new CompletableFuture<>();
        CompletableFuture<CodexModelCatalog.Rpc> connection =
                connectionProvider.connection().toCompletableFuture();
        AtomicReference<CompletableFuture<CodexModelCatalog.Catalog>> activeCatalog =
                new AtomicReference<>();
        result.whenComplete((catalog, failure) -> {
            if (!result.isCancelled()) return;
            connection.cancel(true);
            CompletableFuture<CodexModelCatalog.Catalog> active = activeCatalog.get();
            if (active != null) active.cancel(true);
        });
        connection.whenComplete((rpc, connectionFailure) -> {
            if (connectionFailure != null) {
                result.completeExceptionally(connectionFailure);
                return;
            }
            if (result.isCancelled()) return;
            CompletableFuture<CodexModelCatalog.Catalog> catalog;
            try {
                catalog = modelCatalog.load(rpc).toCompletableFuture();
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
                return;
            }
            activeCatalog.set(catalog);
            if (result.isCancelled()) {
                catalog.cancel(true);
                return;
            }
            catalog.whenComplete((value, failure) -> {
                if (failure == null) result.complete(value);
                else result.completeExceptionally(failure);
            });
        });
        return result;
    }

    /** 退出 app-server 当前共享账号; 请求和响应都不暴露账号数据. */
    public CompletionStage<Void> logoutAccount() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture<CodexModelCatalog.Rpc> connection;
        try {
            connection = connectionProvider.connection().toCompletableFuture();
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        AtomicReference<CompletableFuture<JsonNode>> activeRequest = new AtomicReference<>();
        result.whenComplete((ignored, failure) -> {
            if (!result.isCancelled()) return;
            connection.cancel(true);
            cancel(activeRequest.get());
        });
        connection.whenComplete((rpc, connectionFailure) -> {
            if (connectionFailure != null) {
                result.completeExceptionally(connectionFailure);
                return;
            }
            if (result.isCancelled()) return;
            CompletableFuture<JsonNode> request;
            try {
                request = rpc.request("account/logout", null).toCompletableFuture();
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
                return;
            }
            activeRequest.set(request);
            if (result.isCancelled()) {
                request.cancel(true);
                return;
            }
            request.whenComplete((response, requestFailure) -> {
                if (requestFailure != null) {
                    result.completeExceptionally(requestFailure);
                } else if (response == null || !response.isObject()) {
                    result.completeExceptionally(new ProtocolException());
                } else {
                    result.complete(null);
                }
            });
        });
        return result;
    }

    /** 通过独立 ephemeral thread 执行一次纯文本生成. */
    public String generate(String prompt, GenerationProfile profile) {
        String effort = profile == null || profile.reasoningEffort() == null
                ? "low"
                : profile.reasoningEffort();
        return generate(prompt, profile, effort);
    }

    /** 通过独立 ephemeral thread 按用户已保存的 reasoning effort 执行一次纯文本生成. */
    public String generate(String prompt, GenerationProfile profile, String reasoningEffort) {
        if (profile == null || profile.model() == null || profile.model().isBlank()
                || reasoningEffort == null || reasoningEffort.isBlank()) {
            throw new CodexAppException(AiErrorCode.AI_CODEX_SELECTION_INVALID);
        }

        RequestLifecycle lifecycle = new RequestLifecycle();
        try {
            long timeoutMillis = resolveGenerationTimeoutMillis();
            long deadlineNanos = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            Path ownedDirectory = createOwnedDirectory();
            lifecycle.ownedDirectory = ownedDirectory;
            ensureBeforeDeadline(deadlineNanos);
            GenerationConnection connection = await(
                    generationConnectionProvider.connection(), deadlineNanos);
            lifecycle.connection = connection;

            ensureBeforeDeadline(deadlineNanos);
            CompletionStage<JsonNode> threadStart = connection.request(
                    "thread/start", threadStartParams(profile, ownedDirectory));
            lifecycle.threadStart = threadStart;
            JsonNode threadResponse = await(threadStart, deadlineNanos);
            String threadId = requiredText(threadResponse.path("thread"), "id");
            lifecycle.threadId = threadId;

            CodexTurnCollector collector = new CodexTurnCollector(
                    connection.generation(),
                    threadId,
                    lifecycle::interruptOnce);
            lifecycle.collector = collector;
            lifecycle.subscription = connection.onNotification(collector::accept);
            lifecycle.watchRegistration = watch(connection).register(collector);

            ensureBeforeDeadline(deadlineNanos);
            CompletionStage<JsonNode> turnStart = connection.request(
                    "turn/start", turnStartParams(threadId, prompt, reasoningEffort));
            lifecycle.turnStart = turnStart;
            JsonNode turnResponse = await(turnStart, deadlineNanos);
            String turnId = requiredText(turnResponse.path("turn"), "id");
            lifecycle.turnId = turnId;
            collector.bindTurn(threadId, turnId);
            return await(collector.result(), deadlineNanos);
        } catch (InterruptedException interrupted) {
            lifecycle.abandon();
            Thread.currentThread().interrupt();
            throw new CodexAppException(AiErrorCode.AI_INTERRUPTED);
        } catch (TimeoutException timeout) {
            lifecycle.abandon();
            throw new CodexAppException(AiErrorCode.AI_INTERRUPTED);
        } catch (CodexAppException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new CodexAppException(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
        } catch (ExecutionException failure) {
            throw mapGenerationFailure(failure.getCause());
        } catch (RuntimeException failure) {
            throw mapGenerationFailure(failure);
        } finally {
            if (!lifecycle.deferred.get()) lifecycle.cleanup();
        }
    }

    long resolveGenerationTimeoutMillis() {
        long configured = systemConfigService.getLong(
                GENERATION_TIMEOUT_KEY, DEFAULT_GENERATION_TIMEOUT_MILLIS);
        long timeout = configured > 0 ? configured : DEFAULT_GENERATION_TIMEOUT_MILLIS;
        long lease = systemConfigService.getLong(
                "async-task.lease-millis", DEFAULT_ASYNC_TASK_LEASE_MILLIS);
        if (lease <= 1L) lease = DEFAULT_ASYNC_TASK_LEASE_MILLIS;
        long margin = Math.min(LEASE_SAFETY_MARGIN_MILLIS, Math.max(1L, lease / 10L));
        long effective = Math.min(timeout, lease - margin);
        if (effective <= 0L || effective >= lease) {
            throw new CodexAppException(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
        }
        return effective;
    }

    private static Map<String, Object> threadStartParams(
            GenerationProfile profile, Path ownedDirectory) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", profile.model());
        params.put("cwd", ownedDirectory.toString());
        params.put("approvalPolicy", "never");
        params.put("sandbox", "read-only");
        params.put("ephemeral", true);
        params.put("personality", "none");
        params.put("baseInstructions", BASE_INSTRUCTIONS);
        params.put(
                "developerInstructions", joinNonBlank(TRANSPORT_GUARD, profile.systemPrompt()));
        params.put("config", CLEAN_THREAD_CONFIG);
        return Map.copyOf(params);
    }

    private static Map<String, Object> turnStartParams(
            String threadId, String prompt, String reasoningEffort) {
        return Map.of(
                "threadId", threadId,
                "input", List.of(Map.of("type", "text", "text", Objects.toString(prompt, ""))),
                "effort", reasoningEffort);
    }

    private Path createOwnedDirectory() throws IOException {
        Path path = ownedDirectoryFactory.create().toAbsolutePath().normalize();
        try {
            if (!Files.isDirectory(path)) {
                throw new IOException("Codex request working directory was not created");
            }
            try (var children = Files.list(path)) {
                if (children.findAny().isPresent()) {
                    throw new IOException("Codex request working directory is not empty");
                }
            }
            return path;
        } catch (IOException failure) {
            deleteOwnedDirectory(path);
            throw failure;
        }
    }

    private static CodexAppException mapGenerationFailure(Throwable failure) {
        if (failure instanceof CodexAppException codexFailure) return codexFailure;
        if (containsNotInstalled(failure)) {
            return new CodexAppException(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
        }
        if (failure instanceof ProtocolException) {
            return new CodexAppException(AiErrorCode.AI_CODEX_PROTOCOL_INCOMPATIBLE);
        }
        return new CodexAppException(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
    }

    private static <T> T await(CompletionStage<T> stage, long deadlineNanos)
            throws InterruptedException, ExecutionException, TimeoutException {
        long remaining = remainingNanos(deadlineNanos);
        return stage.toCompletableFuture().get(remaining, TimeUnit.NANOSECONDS);
    }

    private static void ensureBeforeDeadline(long deadlineNanos) throws TimeoutException {
        remainingNanos(deadlineNanos);
    }

    private static long remainingNanos(long deadlineNanos) throws TimeoutException {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0L) throw new TimeoutException("Codex generation deadline exceeded");
        return remaining;
    }

    private static String requiredText(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new ProtocolException();
        }
        return value.textValue();
    }

    private static String joinNonBlank(String first, String second) {
        return second == null || second.isBlank() ? first : first + "\n\n" + second;
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Listener cleanup is best effort.
        }
    }

    private static void deleteOwnedDirectory(Path path) {
        if (path == null || !Files.exists(path)) return;
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException ignored) {
                    // Cleanup failure must not expose owned local paths.
                }
            });
        } catch (IOException ignored) {
            LOGGER.warn("Codex request working directory cleanup failed");
        }
    }

    private GenerationWatch watch(GenerationConnection connection) {
        return generationWatches.computeIfAbsent(
                connection.generation(), ignored -> new GenerationWatch(connection));
    }

    /** 保存单 request 的 collector/tombstone 资源, cleanup 幂等且只删 owned cwd. */
    private final class RequestLifecycle {
        private final AtomicBoolean deferred = new AtomicBoolean();
        private final AtomicBoolean cleanupStarted = new AtomicBoolean();
        private final AtomicBoolean interruptRequested = new AtomicBoolean();
        private final AtomicBoolean unsubscribeRequested = new AtomicBoolean();
        private final Set<CompletableFuture<JsonNode>> bestEffortRequests =
                ConcurrentHashMap.newKeySet();
        private volatile Path ownedDirectory;
        private volatile GenerationConnection connection;
        private volatile String threadId;
        private volatile String turnId;
        private volatile CodexTurnCollector collector;
        private volatile CompletionStage<JsonNode> threadStart;
        private volatile CompletionStage<JsonNode> turnStart;
        private volatile AutoCloseable subscription;
        private volatile AutoCloseable watchRegistration;

        private void abandon() {
            if (connection == null) return;
            if (threadId == null) {
                CompletionStage<JsonNode> pendingThread = threadStart;
                if (pendingThread == null) return;
                deferCleanup();
                pendingThread.whenComplete(this::lateThreadStarted);
                return;
            }
            if (collector == null) return;
            deferCleanup();

            if (turnId != null) {
                interruptOnce(connection.generation(), threadId, turnId);
                waitInterruptGrace();
                return;
            }
            CompletionStage<JsonNode> pendingTurn = turnStart;
            if (pendingTurn != null) pendingTurn.whenComplete(this::lateTurnStarted);
        }

        private void deferCleanup() {
            if (!deferred.compareAndSet(false, true)) return;
            CodexTurnCollector activeCollector = collector;
            if (activeCollector != null) {
                activeCollector.result().whenComplete((ignored, failure) -> cleanup());
            }
            CompletableFuture.delayedExecutor(
                            tombstoneRetentionMillis, TimeUnit.MILLISECONDS)
                    .execute(this::expire);
        }

        private void lateThreadStarted(JsonNode response, Throwable failure) {
            if (cleanupStarted.get()) return;
            if (failure != null) {
                cleanup();
                return;
            }
            try {
                threadId = requiredText(response.path("thread"), "id");
                CompletableFuture<JsonNode> unsubscribe = unsubscribeOnce();
                if (unsubscribe == null) cleanup();
                else unsubscribe.whenComplete((ignored, unsubscribeFailure) -> cleanup());
            } catch (RuntimeException incompatible) {
                cleanup();
            }
        }

        private void lateTurnStarted(JsonNode response, Throwable failure) {
            if (cleanupStarted.get()) return;
            if (failure != null) {
                cleanup();
                return;
            }
            try {
                String lateTurnId = requiredText(response.path("turn"), "id");
                turnId = lateTurnId;
                collector.bindTurn(threadId, lateTurnId);
                interruptOnce(connection.generation(), threadId, lateTurnId);
            } catch (RuntimeException incompatible) {
                cleanup();
            }
        }

        private void interruptOnce(long generation, String targetThreadId, String targetTurnId) {
            GenerationConnection active = connection;
            if (active == null
                    || active.generation() != generation
                    || !Objects.equals(threadId, targetThreadId)
                    || targetTurnId == null
                    || targetTurnId.isBlank()
                    || !interruptRequested.compareAndSet(false, true)) {
                return;
            }
            CompletableFuture<JsonNode> interrupt = requestBestEffort(
                    "turn/interrupt",
                    Map.of("threadId", targetThreadId, "turnId", targetTurnId));
            if (interrupt == null) {
                LOGGER.atWarn()
                        .addKeyValue("event", "codex_turn_interrupt_failure")
                        .log("Codex turn interrupt request failed");
                return;
            }
            interrupt.whenComplete((ignored, failure) -> {
                if (failure != null && !interrupt.isCancelled()) {
                    LOGGER.atWarn()
                            .addKeyValue("event", "codex_turn_interrupt_failure")
                            .log("Codex turn interrupt request failed");
                }
            });
        }

        private void waitInterruptGrace() {
            try {
                collector.result().get(interruptGraceMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException ignored) {
                // Caller still receives original deadline failure after bounded grace.
            }
        }

        private void expire() {
            if (cleanupStarted.get()) return;
            LOGGER.atWarn()
                    .addKeyValue("event", "codex_late_event_sink_expired")
                    .addKeyValue("generation", connection == null ? -1L : connection.generation())
                    .log("Codex late-event sink retention expired");
            CompletionStage<JsonNode> pendingThread = threadStart;
            if (pendingThread != null) pendingThread.toCompletableFuture().cancel(false);
            CompletionStage<JsonNode> pendingTurn = turnStart;
            if (pendingTurn != null) pendingTurn.toCompletableFuture().cancel(false);
            bestEffortRequests.forEach(stage -> stage.cancel(false));
            cleanup();
        }

        private void cleanup() {
            if (!cleanupStarted.compareAndSet(false, true)) return;
            unsubscribeOnce();
            closeQuietly(subscription);
            closeQuietly(watchRegistration);
            deleteOwnedDirectory(ownedDirectory);
        }

        private CompletableFuture<JsonNode> unsubscribeOnce() {
            if (connection == null
                    || threadId == null
                    || !unsubscribeRequested.compareAndSet(false, true)) {
                return null;
            }
            return requestBestEffort("thread/unsubscribe", Map.of("threadId", threadId));
        }

        private CompletableFuture<JsonNode> requestBestEffort(
                String method, Map<String, Object> params) {
            CompletableFuture<JsonNode> request;
            try {
                request = connection.request(method, params).toCompletableFuture();
            } catch (RuntimeException failure) {
                return null;
            }
            bestEffortRequests.add(request);
            request.whenComplete((ignored, failure) -> bestEffortRequests.remove(request));
            CompletableFuture.delayedExecutor(
                            tombstoneRetentionMillis, TimeUnit.MILLISECONDS)
                    .execute(() -> request.cancel(false));
            return request;
        }
    }

    /** 每 generation 只监听一次 connection damage, 原子失败其 active collectors. */
    private final class GenerationWatch {
        private final long generation;
        private final Set<CodexTurnCollector> collectors = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean failed = new AtomicBoolean();

        private GenerationWatch(GenerationConnection connection) {
            generation = connection.generation();
            connection.damaged().whenComplete((ignored, failure) -> failAll());
        }

        private AutoCloseable register(CodexTurnCollector collector) {
            if (failed.get()) {
                collector.failConnection();
                return () -> {};
            }
            collectors.add(collector);
            if (failed.get() && collectors.remove(collector)) collector.failConnection();
            return () -> collectors.remove(collector);
        }

        private void failAll() {
            if (!failed.compareAndSet(false, true)) return;
            collectors.forEach(CodexTurnCollector::failConnection);
            collectors.clear();
            generationWatches.remove(generation, this);
        }
    }

    private static final class ProtocolException extends RuntimeException {}

    private static StatusResponse mapStatus(JsonNode response) {
        if (response == null || !response.isObject()) {
            throw new IllegalStateException("Invalid account/read response");
        }
        JsonNode requiresAuth = response.get("requiresOpenaiAuth");
        if (requiresAuth == null || !requiresAuth.isBoolean()) {
            throw new IllegalStateException("Invalid account/read response");
        }
        JsonNode account = response.get("account");
        boolean noAccount = account == null || account.isNull();
        StatusCode status = noAccount && requiresAuth.booleanValue()
                ? StatusCode.NOT_LOGGED_IN
                : StatusCode.AVAILABLE;
        return new StatusResponse(status);
    }

    private static boolean containsNotInstalled(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof CodexProcessManager.CodexNotInstalledException) return true;
            current = current.getCause();
        }
        return false;
    }

    private static void logFailure(Throwable failure) {
        LOGGER.atWarn()
                .addKeyValue("event", "codex_status_failure")
                .addKeyValue("failure_type", safeFailureType(failure))
                .log("Codex app-server status check failed");
    }

    private static String safeFailureType(Throwable failure) {
        if (failure instanceof InterruptedException) return "InterruptedException";
        if (failure instanceof TimeoutException) return "TimeoutException";
        if (failure instanceof ExecutionException) return "ExecutionException";
        if (failure instanceof RuntimeException) return "RuntimeException";
        return "Throwable";
    }

    @FunctionalInterface
    interface ConnectionProvider {
        CompletionStage<CodexModelCatalog.Rpc> connection();
    }

    @FunctionalInterface
    interface GenerationConnectionProvider {
        CompletionStage<GenerationConnection> connection();
    }

    interface GenerationConnection {
        long generation();

        CompletionStage<JsonNode> request(String method, Map<String, Object> params);

        AutoCloseable onNotification(BiConsumer<String, JsonNode> listener);

        CompletionStage<Void> damaged();
    }

    @FunctionalInterface
    interface OwnedDirectoryFactory {
        Path create() throws IOException;
    }

    private record ManagedGenerationConnection(CodexProcessManager.CodexConnection delegate)
            implements GenerationConnection {

        @Override
        public long generation() {
            return delegate.generation();
        }

        @Override
        public CompletionStage<JsonNode> request(String method, Map<String, Object> params) {
            return delegate.peer().request(method, params);
        }

        @Override
        public AutoCloseable onNotification(BiConsumer<String, JsonNode> listener) {
            return delegate.peer().onNotification(listener);
        }

        @Override
        public CompletionStage<Void> damaged() {
            return delegate.peer().terminal();
        }
    }

    /** 设置页可安全展示的 app-server 可用性. */
    public enum StatusCode {
        AVAILABLE,
        NOT_LOGGED_IN,
        NOT_INSTALLED,
        ERROR
    }

    /** 状态 DTO 刻意不含 account/email/path/error 字段. */
    public record StatusResponse(StatusCode status) {}
}
