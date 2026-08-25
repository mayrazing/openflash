package openflash_ai_runtime.service.impl;

import jakarta.annotation.PreDestroy;
import openflash_ai_runtime.client.CodexAppServerClient;
import openflash_ai_runtime.common.AiErrorCode;
import openflash_ai_runtime.common.CodexAppException;
import openflash_ai_runtime.client.CodexModelCatalog;
import openflash_ai_runtime.dto.GenerationProfile;
import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.support.CodexLoginCoordinator;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry.RequestState;
import openflash_ai_runtime.service.CodexRuntimeService;
import openflash_ai_runtime.service.RuntimeSystemConfigService;
import openflash_ai_runtime.validation.GenerationRequestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 提供 runtime 内唯一 Codex 进程上的安全业务操作. */
@Service
@ConditionalOnProperty(prefix = "app.codex", name = "enabled", havingValue = "true")
public class CodexRuntimeServiceImpl implements CodexRuntimeService {

    private static final String CATALOG_TIMEOUT_KEY = "ai.codex-status-timeout-millis";
    private static final long DEFAULT_CATALOG_TIMEOUT_MILLIS = 5000L;
    private static final int MAX_GENERATION_THREADS = 8;
    private static final int GENERATION_QUEUE_CAPACITY = 32;
    private static final AtomicInteger GENERATION_THREAD_SEQUENCE = new AtomicInteger();

    private final CodexAppServerClient client;
    private final CodexLoginCoordinator loginCoordinator;
    private final RuntimeSystemConfigService systemConfigService;
    private final ExecutorService generationExecutor;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsExecutors;
    private final PlatformGenerationRequestRegistry requestRegistry;
    private final Object lifecycleLock = new Object();
    private final ConcurrentHashMap<UUID, GenerationHandle> generations =
            new ConcurrentHashMap<>();
    private final Map<CompletableFuture<CodexModelCatalog.Catalog>,
            CompletableFuture<CodexModelCatalog.Catalog>> activeModels = new HashMap<>();
    private boolean shutdown;

    @Autowired
    public CodexRuntimeServiceImpl(
            CodexAppServerClient client,
            CodexLoginCoordinator loginCoordinator,
            RuntimeSystemConfigService systemConfigService,
            PlatformGenerationRequestRegistry requestRegistry) {
        this(
                client,
                loginCoordinator,
                systemConfigService,
                newGenerationExecutor(),
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "codex-runtime-catalog-timeout");
                    thread.setDaemon(true);
                    return thread;
                }),
                true,
                requestRegistry);
    }

    public CodexRuntimeServiceImpl(
            CodexAppServerClient client,
            CodexLoginCoordinator loginCoordinator,
            RuntimeSystemConfigService systemConfigService) {
        this(client, loginCoordinator, systemConfigService,
                new PlatformGenerationRequestRegistry());
    }

    public CodexRuntimeServiceImpl(
            CodexAppServerClient client,
            CodexLoginCoordinator loginCoordinator,
            RuntimeSystemConfigService systemConfigService,
            ExecutorService generationExecutor,
            ScheduledExecutorService scheduler) {
        this(client, loginCoordinator, systemConfigService, generationExecutor, scheduler,
                new PlatformGenerationRequestRegistry());
    }

    CodexRuntimeServiceImpl(
            CodexAppServerClient client,
            CodexLoginCoordinator loginCoordinator,
            RuntimeSystemConfigService systemConfigService,
            ExecutorService generationExecutor,
            ScheduledExecutorService scheduler,
            PlatformGenerationRequestRegistry requestRegistry) {
        this(
                client,
                loginCoordinator,
                systemConfigService,
                generationExecutor,
                scheduler,
                false,
                requestRegistry);
    }

    private CodexRuntimeServiceImpl(
            CodexAppServerClient client,
            CodexLoginCoordinator loginCoordinator,
            RuntimeSystemConfigService systemConfigService,
            ExecutorService generationExecutor,
            ScheduledExecutorService scheduler,
            boolean ownsExecutors,
            PlatformGenerationRequestRegistry requestRegistry) {
        this.client = client;
        this.loginCoordinator = loginCoordinator;
        this.systemConfigService = systemConfigService;
        this.generationExecutor = Objects.requireNonNull(generationExecutor, "generationExecutor");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ownsExecutors = ownsExecutors;
        this.requestRegistry = Objects.requireNonNull(requestRegistry, "requestRegistry");
    }

    public CodexAppServerClient.StatusResponse status() {
        return client.status();
    }

    public CompletionStage<CodexModelCatalog.Catalog> models() {
        synchronized (lifecycleLock) {
            if (shutdown) return CompletableFuture.failedFuture(unavailable());
        }
        long configured;
        try {
            configured = systemConfigService.getLong(
                    CATALOG_TIMEOUT_KEY, DEFAULT_CATALOG_TIMEOUT_MILLIS);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(unavailable());
        }
        long timeoutMillis = configured > 0L
                ? configured
                : DEFAULT_CATALOG_TIMEOUT_MILLIS;
        CompletableFuture<CodexModelCatalog.Catalog> source;
        try {
            CompletionStage<CodexModelCatalog.Catalog> stage = client.models();
            if (stage == null) return CompletableFuture.failedFuture(unavailable());
            source = stage.toCompletableFuture();
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(unavailable());
        }

        CompletableFuture<CodexModelCatalog.Catalog> result =
                new UnavailableOnCancelFuture<>();
        AtomicReference<ScheduledFuture<?>> timeout = new AtomicReference<>();
        boolean rejected = false;
        synchronized (lifecycleLock) {
            if (shutdown) {
                rejected = true;
            } else {
                activeModels.put(result, source);
                try {
                    timeout.set(scheduler.schedule(() -> {
                        source.cancel(true);
                        result.completeExceptionally(unavailable());
                    }, timeoutMillis, TimeUnit.MILLISECONDS));
                } catch (RuntimeException failure) {
                    activeModels.remove(result);
                    rejected = true;
                }
            }
        }
        result.whenComplete((value, failure) -> {
            synchronized (lifecycleLock) {
                activeModels.remove(result);
            }
            ScheduledFuture<?> scheduled = timeout.get();
            if (scheduled != null) scheduled.cancel(false);
            if (failure != null) source.cancel(true);
        });
        source.whenComplete((catalog, failure) -> {
            if (failure != null || catalog == null) {
                source.cancel(true);
                result.completeExceptionally(unavailable());
            } else {
                result.complete(catalog);
            }
        });
        if (rejected) {
            source.cancel(true);
            result.completeExceptionally(unavailable());
        }
        if (result.isDone()) {
            ScheduledFuture<?> scheduled = timeout.get();
            if (scheduled != null) scheduled.cancel(false);
        }
        return result;
    }

    /** 在 runtime 专用 worker 执行同步生成, servlet 线程只等待该 request future. */
    public String generate(UUID requestId, String prompt, GenerationProfile profile) {
        Objects.requireNonNull(requestId, "requestId");
        if (profile == null) throw runtimeFailure();
        GenerationRequestValidator.validateCodex(
                requestId, profile.model(), profile.reasoningEffort(), prompt,
                profile.systemPrompt(), profile.temperature());
        RequestState requestState;
        try {
            requestState = requestRegistry.reserve(requestId);
        } catch (openflash_ai_runtime.common.RuntimeException duplicate) {
            throw new CodexAppException(AiErrorCode.AI_CODEX_SELECTION_INVALID);
        }
        try {
            return generate(requestId, prompt, profile, requestState);
        } finally {
            requestRegistry.complete(requestState);
        }
    }

    /** 使用上层已预留的统一请求状态绑定 FutureTask, 避免取消注册竞态. */
    public String generate(
            UUID requestId,
            String prompt,
            GenerationProfile profile,
            RequestState requestState) {
        Objects.requireNonNull(requestId, "requestId");
        if (profile == null) throw runtimeFailure();
        if (requestState == null || !requestId.equals(requestState.requestId())) {
            throw new CodexAppException(AiErrorCode.AI_CODEX_SELECTION_INVALID);
        }
        GenerationRequestValidator.validateCodex(
                requestId, profile.model(), profile.reasoningEffort(), prompt,
                profile.systemPrompt(), profile.temperature());
        FutureTask<String> task = new FutureTask<>(() -> client.generate(prompt, profile));
        GenerationHandle generation = new GenerationHandle(task, requestState);
        synchronized (lifecycleLock) {
            if (shutdown) throw runtimeFailure();
            if (generations.putIfAbsent(requestId, generation) != null) {
                throw new CodexAppException(AiErrorCode.AI_CODEX_SELECTION_INVALID);
            }
            if (!requestRegistry.bind(requestState, () -> task.cancel(true))) {
                generations.remove(requestId, generation);
                throw new CodexAppException(AiErrorCode.AI_INTERRUPTED);
            }
            try {
                generationExecutor.execute(task);
            } catch (RejectedExecutionException rejected) {
                generations.remove(requestId, generation);
                task.cancel(false);
                throw runtimeFailure();
            }
        }
        try {
            return task.get();
        } catch (InterruptedException interrupted) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new CodexAppException(AiErrorCode.AI_INTERRUPTED);
        } catch (CancellationException canceled) {
            throw new CodexAppException(AiErrorCode.AI_INTERRUPTED);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof CodexAppException codexFailure) throw codexFailure;
            throw new CodexAppException(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
        } finally {
            generations.remove(requestId, generation);
        }
    }

    /** 只取消该 request 的 runtime future; 已完成或未知请求返回 false. */
    public boolean cancel(UUID requestId) {
        if (requestRegistry.cancel(requestId)) return true;
        GenerationHandle generation = generations.get(requestId);
        return generation != null && generation.task().cancel(true);
    }

    public CompletionStage<CodexLoginCoordinator.LoginSnapshot> startLogin() {
        return loginCoordinator.start();
    }

    public CompletionStage<CodexLoginCoordinator.LoginSnapshot> cancelLogin() {
        return loginCoordinator.cancel();
    }

    public CodexLoginCoordinator.LoginSnapshot loginSnapshot() {
        return loginCoordinator.snapshot();
    }

    public CompletionStage<Boolean> logoutAccount() {
        return loginCoordinator.cancelAndDrain()
                .thenCompose(ignored -> client.logoutAccount())
                .thenApply(ignored -> true);
    }

    static ThreadPoolExecutor newGenerationExecutor() {
        int threads = Math.max(
                1, Math.min(Runtime.getRuntime().availableProcessors(), MAX_GENERATION_THREADS));
        return new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(GENERATION_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "codex-runtime-generation-"
                                    + GENERATION_THREAD_SEQUENCE.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** 取消运行中 generation, 再关闭本服务拥有的 executor. */
    @PreDestroy
    public void shutdown() {
        List<GenerationHandle> generationTasks;
        List<Runnable> neverStarted = List.of();
        List<CompletableFuture<CodexModelCatalog.Catalog>> modelResults;
        synchronized (lifecycleLock) {
            if (shutdown) return;
            shutdown = true;
            if (ownsExecutors) {
                neverStarted = generationExecutor.shutdownNow();
                scheduler.shutdownNow();
            }
            generationTasks = new ArrayList<>(generations.values());
            generations.clear();
            modelResults = new ArrayList<>(activeModels.keySet());
            activeModels.clear();
        }
        generationTasks.forEach(generation -> {
            requestRegistry.cancel(generation.state().requestId());
            generation.task().cancel(true);
            requestRegistry.complete(generation.state());
        });
        neverStarted.forEach(task -> {
            if (task instanceof FutureTask<?> future) future.cancel(true);
        });
        modelResults.forEach(result -> result.completeExceptionally(unavailable()));
    }

    private static CodexAppException runtimeFailure() {
        return new CodexAppException(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
    }

    private static openflash_ai_runtime.common.RuntimeException unavailable() {
        return new openflash_ai_runtime.common.RuntimeException(RuntimeErrorCode.UNAVAILABLE);
    }

    private record GenerationHandle(FutureTask<String> task, RequestState state) {
    }

    private static final class UnavailableOnCancelFuture<T> extends CompletableFuture<T> {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return completeExceptionally(unavailable());
        }
    }
}
