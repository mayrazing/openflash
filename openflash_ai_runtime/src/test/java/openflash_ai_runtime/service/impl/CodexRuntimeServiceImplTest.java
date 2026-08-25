package openflash_ai_runtime.service.impl;

import openflash_ai_runtime.common.AiErrorCode;
import openflash_ai_runtime.common.CodexAppException;
import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.client.CodexAppServerClient;
import openflash_ai_runtime.dto.GenerationProfile;
import openflash_ai_runtime.client.CodexModelCatalog;
import openflash_ai_runtime.service.CodexRuntimeService;
import openflash_ai_runtime.service.RuntimeSystemConfigService;
import openflash_ai_runtime.support.CodexLoginCoordinator;
import openflash_ai_runtime.support.PlatformGenerationRequestRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodexRuntimeServiceImplTest {

    private CodexAppServerClient client;
    private CodexLoginCoordinator loginCoordinator;
    private RuntimeSystemConfigService config;
    private ExecutorService generationExecutor;
    private ScheduledExecutorService scheduler;
    private CodexRuntimeService service;

    @BeforeEach
    void setUp() {
        client = mock(CodexAppServerClient.class);
        loginCoordinator = mock(CodexLoginCoordinator.class);
        config = mock(RuntimeSystemConfigService.class);
        generationExecutor = Executors.newSingleThreadExecutor();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        when(config.getLong("ai.codex-status-timeout-millis", 5000L)).thenReturn(5000L);
        service = new CodexRuntimeServiceImpl(
                client, loginCoordinator, config, generationExecutor, scheduler);
    }

    @AfterEach
    void tearDown() {
        generationExecutor.shutdownNow();
        scheduler.shutdownNow();
    }

    @Test
    void lateCancelAfterCompletionDoesNotInterruptAReusedRuntimeWorker() {
        AtomicReference<Thread> firstWorker = new AtomicReference<>();
        AtomicReference<Thread> secondWorker = new AtomicReference<>();
        AtomicBoolean secondStartedInterrupted = new AtomicBoolean();
        when(client.generate(anyString(), any())).thenAnswer(invocation -> {
            if (firstWorker.get() == null) {
                firstWorker.set(Thread.currentThread());
                return "first";
            }
            secondWorker.set(Thread.currentThread());
            secondStartedInterrupted.set(Thread.currentThread().isInterrupted());
            return "second";
        });
        UUID firstId = UUID.randomUUID();

        assertThat(service.generate(firstId, "first", profile())).isEqualTo("first");
        assertThat(service.cancel(firstId)).isFalse();
        assertThat(service.generate(UUID.randomUUID(), "second", profile())).isEqualTo("second");

        assertThat(secondWorker.get()).isSameAs(firstWorker.get());
        assertThat(secondStartedInterrupted).isFalse();
    }

    @Test
    void runningCancelInterruptsOnlyTheRuntimeWorkerAndNotTheWaitingServletThread()
            throws Exception {
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch workerInterrupted = new CountDownLatch(1);
        AtomicBoolean servletInterrupted = new AtomicBoolean();
        when(client.generate(anyString(), any())).thenAnswer(invocation -> {
            workerStarted.countDown();
            try {
                new CountDownLatch(1).await();
                return "unreachable";
            } catch (InterruptedException interrupted) {
                workerInterrupted.countDown();
                throw new CodexAppException(AiErrorCode.AI_INTERRUPTED);
            }
        });
        UUID requestId = UUID.randomUUID();
        ExecutorService servletExecutor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> servlet = CompletableFuture.runAsync(() -> {
                assertThatThrownBy(() -> service.generate(requestId, "prompt", profile()))
                        .isInstanceOf(CodexAppException.class);
                servletInterrupted.set(Thread.currentThread().isInterrupted());
            }, servletExecutor);

            assertThat(workerStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(service.cancel(requestId)).isTrue();
            servlet.get(1, TimeUnit.SECONDS);

            assertThat(workerInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(servletInterrupted).isFalse();
            assertThat(service.cancel(requestId)).isFalse();
        } finally {
            servletExecutor.shutdownNow();
        }
    }

    @Test
    void unifiedCancelAfterFutureBindButBeforeExecutorRunsNeverCallsClient() throws Exception {
        GateExecutingExecutor gatedExecutor = new GateExecutingExecutor();
        PlatformGenerationRequestRegistry registry = new PlatformGenerationRequestRegistry();
        CodexRuntimeService injected = new CodexRuntimeServiceImpl(
                client, loginCoordinator, config, gatedExecutor, scheduler, registry);
        UUID requestId = UUID.randomUUID();
        var state = registry.reserve(requestId);
        CompletableFuture<String> pending = CompletableFuture.supplyAsync(() ->
                injected.generate(requestId, "private prompt", profile(), state));
        try {
            assertThat(gatedExecutor.executeEntered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.cancel(requestId)).isTrue();
            gatedExecutor.allowRun.countDown();

            assertThatThrownBy(pending::join)
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(CodexAppException.class);
            verify(client, never()).generate(anyString(), any());
        } finally {
            gatedExecutor.allowRun.countDown();
            registry.complete(state);
        }
    }

    @Test
    void sharedCodexGenerationRejectsAStateReservedForAnotherRequest() {
        PlatformGenerationRequestRegistry registry = new PlatformGenerationRequestRegistry();
        CodexRuntimeService injected = new CodexRuntimeServiceImpl(
                client, loginCoordinator, config, generationExecutor, scheduler, registry);
        var state = registry.reserve(UUID.randomUUID());
        try {
            assertThatThrownBy(() -> injected.generate(
                    UUID.randomUUID(), "prompt", profile(), state))
                    .isInstanceOf(CodexAppException.class)
                    .extracting(failure -> ((CodexAppException) failure).getErrorCode())
                    .isEqualTo(AiErrorCode.AI_CODEX_SELECTION_INVALID);
            verify(client, never()).generate(anyString(), any());
        } finally {
            registry.complete(state);
        }
    }

    @Test
    void duplicateRequestIdIsRejectedAndFinishedRequestIsRemoved() throws Exception {
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.generate(anyString(), any())).thenAnswer(invocation -> {
            workerStarted.countDown();
            release.await();
            return "done";
        });
        UUID requestId = UUID.randomUUID();
        ExecutorService servletExecutor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<String> first = CompletableFuture.supplyAsync(
                    () -> service.generate(requestId, "first", profile()), servletExecutor);
            assertThat(workerStarted.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.generate(requestId, "duplicate", profile()))
                    .isInstanceOf(CodexAppException.class)
                    .extracting(failure -> ((CodexAppException) failure).getErrorCode())
                    .isEqualTo(AiErrorCode.AI_CODEX_SELECTION_INVALID);

            release.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("done");
            assertThat(service.cancel(requestId)).isFalse();
        } finally {
            release.countDown();
            servletExecutor.shutdownNow();
        }
    }

    @Test
    void interruptedServletCancelsItsRuntimeFutureAndRestoresInterruptStatus() throws Exception {
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch workerInterrupted = new CountDownLatch(1);
        AtomicReference<CodexAppException> failure = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        when(client.generate(anyString(), any())).thenAnswer(invocation -> {
            workerStarted.countDown();
            try {
                new CountDownLatch(1).await();
                return "unreachable";
            } catch (InterruptedException interrupted) {
                workerInterrupted.countDown();
                throw new CodexAppException(AiErrorCode.AI_INTERRUPTED);
            }
        });
        UUID requestId = UUID.randomUUID();
        Thread servlet = new Thread(() -> {
            try {
                service.generate(requestId, "prompt", profile());
            } catch (CodexAppException expected) {
                failure.set(expected);
                interruptRestored.set(Thread.currentThread().isInterrupted());
            }
        }, "test-servlet");

        servlet.start();
        assertThat(workerStarted.await(1, TimeUnit.SECONDS)).isTrue();
        servlet.interrupt();
        servlet.join(1000L);

        assertThat(servlet.isAlive()).isFalse();
        assertThat(workerInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNotNull();
        assertThat(interruptRestored).isTrue();
        assertThat(service.cancel(requestId)).isFalse();
    }

    @Test
    void logoutWaitsForPendingLoginCancelBeforeCallingClient() {
        CompletableFuture<Void> canceled = new CompletableFuture<>();
        when(loginCoordinator.cancelAndDrain()).thenReturn(canceled);
        when(client.logoutAccount()).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<Boolean> result = service.logoutAccount().toCompletableFuture();

        assertThat(result).isNotDone();
        verify(client, never()).logoutAccount();
        canceled.complete(null);
        assertThat(result.join()).isTrue();
        verify(client).logoutAccount();
    }

    @Test
    void logoutDoesNotCallClientWhenStartingLoginCancelFails() {
        when(loginCoordinator.cancelAndDrain()).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("secret login failure")));

        assertThatThrownBy(() -> service.logoutAccount().toCompletableFuture().join())
                .isInstanceOf(CompletionException.class);
        verify(client, never()).logoutAccount();
    }

    @Test
    void productionGenerationExecutorHasHardThreadAndQueueBounds() {
        ThreadPoolExecutor executor = CodexRuntimeServiceImpl.newGenerationExecutor();
        try {
            assertThat(executor.getMaximumPoolSize()).isPositive().isLessThanOrEqualTo(8);
            assertThat(executor.getQueue().remainingCapacity())
                    .isPositive()
                    .isLessThanOrEqualTo(32);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shutdownRejectsLaterGenerationWithoutSubmittingToInjectedExecutor() {
        AtomicInteger submissions = new AtomicInteger();
        ExecutorService directExecutor = new AbstractExecutorService() {
            @Override
            public void shutdown() {}

            @Override
            public List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return false;
            }

            @Override
            public void execute(Runnable command) {
                submissions.incrementAndGet();
                command.run();
            }
        };
        CodexRuntimeService injected = new CodexRuntimeServiceImpl(
                client, loginCoordinator, config, directExecutor, scheduler);

        injected.shutdown();

        assertThatThrownBy(() -> injected.generate(UUID.randomUUID(), "prompt", profile()))
                .isInstanceOf(CodexAppException.class)
                .extracting(failure -> ((CodexAppException) failure).getErrorCode())
                .isEqualTo(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
        assertThat(submissions).hasValue(0);
        assertThat(directExecutor.isShutdown()).isFalse();
    }

    @Test
    void shutdownWaitsForSubmitGateThenCancelsRegisteredQueuedGeneration() throws Exception {
        BlockingSubmitExecutor blockingExecutor = new BlockingSubmitExecutor();
        CodexRuntimeService injected = new CodexRuntimeServiceImpl(
                client, loginCoordinator, config, blockingExecutor, scheduler);
        UUID requestId = UUID.randomUUID();
        ExecutorService callers = Executors.newSingleThreadExecutor();
        CountDownLatch shutdownStarted = new CountDownLatch(1);
        AtomicReference<Throwable> shutdownFailure = new AtomicReference<>();
        Thread shutdownThread = new Thread(() -> {
            shutdownStarted.countDown();
            try {
                injected.shutdown();
            } catch (Throwable failure) {
                shutdownFailure.set(failure);
            }
        }, "test-runtime-shutdown");
        try {
            CompletableFuture<String> generation = CompletableFuture.supplyAsync(
                    () -> injected.generate(requestId, "prompt", profile()), callers);
            assertThat(blockingExecutor.executeEntered.await(2, TimeUnit.SECONDS)).isTrue();

            shutdownThread.start();
            assertThat(shutdownStarted.await(2, TimeUnit.SECONDS)).isTrue();
            awaitThreadState(shutdownThread, Thread.State.BLOCKED);
            blockingExecutor.allowExecuteReturn.countDown();

            shutdownThread.join(2000L);
            assertThat(shutdownThread.isAlive()).isFalse();
            assertThat(shutdownFailure.get()).isNull();
            boolean lateCancel = injected.cancel(requestId);
            assertThatThrownBy(generation::join)
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(CodexAppException.class);
            assertThat(lateCancel).isFalse();
            assertThat(injected.cancel(requestId)).isFalse();
            assertThat(blockingExecutor.isShutdown()).isFalse();
        } finally {
            blockingExecutor.allowExecuteReturn.countDown();
            shutdownThread.interrupt();
            callers.shutdownNow();
        }
    }

    @Test
    void shutdownFailsActiveModelsAsUnavailableEvenWithInjectedExecutors() {
        CompletableFuture<CodexModelCatalog.Catalog> source = new CompletableFuture<>();
        when(client.models()).thenReturn(source);
        CompletableFuture<CodexModelCatalog.Catalog> result = service.models().toCompletableFuture();

        service.shutdown();

        assertThat(result.isCompletedExceptionally()).isTrue();
        assertUnavailable(result);
        assertThat(source.isCancelled()).isTrue();
    }

    @Test
    void rejectedGenerationFailsFastAndLeavesNoCancelableRequest() {
        ExecutorService rejectingExecutor = new AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                throw new RejectedExecutionException("full");
            }

            @Override
            public void shutdown() {}

            @Override
            public List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return false;
            }
        };
        CodexRuntimeService injected = new CodexRuntimeServiceImpl(
                client, loginCoordinator, config, rejectingExecutor, scheduler);
        UUID requestId = UUID.randomUUID();

        assertThatThrownBy(() -> injected.generate(requestId, "prompt", profile()))
                .isInstanceOf(CodexAppException.class)
                .extracting(failure -> ((CodexAppException) failure).getErrorCode())
                .isEqualTo(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
        assertThat(injected.cancel(requestId)).isFalse();
    }

    @Test
    void modelsUsesConfiguredTimeoutKeyAndReturnsCatalog() {
        CodexModelCatalog.Catalog catalog = catalog();
        when(config.getLong("ai.codex-status-timeout-millis", 5000L)).thenReturn(1234L);
        when(client.models()).thenReturn(CompletableFuture.completedFuture(catalog));

        assertThat(service.models().toCompletableFuture().join()).isSameAs(catalog);

        verify(config).getLong("ai.codex-status-timeout-millis", 5000L);
    }

    @Test
    void modelsTimeoutCancelsUnderlyingCatalogAndFailsAsUnavailable() {
        CompletableFuture<CodexModelCatalog.Catalog> source = new CompletableFuture<>();
        when(config.getLong("ai.codex-status-timeout-millis", 5000L)).thenReturn(20L);
        when(client.models()).thenReturn(source);

        assertUnavailable(service.models().toCompletableFuture());

        assertThat(source.isCancelled()).isTrue();
    }

    @Test
    void modelsExceptionalSourceIsCanceledAndFailsAsUnavailable() {
        CancelTrackingFuture<CodexModelCatalog.Catalog> source = new CancelTrackingFuture<>();
        when(client.models()).thenReturn(source);
        CompletableFuture<CodexModelCatalog.Catalog> result = service.models().toCompletableFuture();

        source.completeExceptionally(new IllegalStateException("/private secret stderr"));

        assertUnavailable(result);
        assertThat(source.cancelCalls.get()).isGreaterThan(0);
    }

    @Test
    void modelsCallerCancellationCancelsUnderlyingCatalogAndFailsAsUnavailable() {
        CompletableFuture<CodexModelCatalog.Catalog> source = new CompletableFuture<>();
        when(client.models()).thenReturn(source);
        CompletableFuture<CodexModelCatalog.Catalog> result = service.models().toCompletableFuture();

        assertThat(result.cancel(true)).isTrue();

        assertThat(source.isCancelled()).isTrue();
        assertUnavailable(result);
    }

    @Test
    void modelsNonPositiveTimeoutUsesFiveSecondFallback() {
        CompletableFuture<CodexModelCatalog.Catalog> source = new CompletableFuture<>();
        when(config.getLong("ai.codex-status-timeout-millis", 5000L)).thenReturn(0L);
        when(client.models()).thenReturn(source);

        CompletableFuture<CodexModelCatalog.Catalog> result = service.models().toCompletableFuture();
        scheduler.schedule(() -> source.complete(catalog()), 25L, TimeUnit.MILLISECONDS);

        assertThat(result.join()).isEqualTo(catalog());
        assertThat(source.isCancelled()).isFalse();
    }

    private static void assertUnavailable(CompletableFuture<?> result) {
        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .extracting(Throwable::getCause)
                .isInstanceOf(openflash_ai_runtime.common.RuntimeException.class)
                .extracting(failure -> ((openflash_ai_runtime.common.RuntimeException) failure)
                        .getErrorCode())
                .isEqualTo(RuntimeErrorCode.UNAVAILABLE);
    }

    private static void awaitThreadState(Thread thread, Thread.State expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (thread.getState() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(thread.getState()).isEqualTo(expected);
    }

    private static GenerationProfile profile() {
        return new GenerationProfile("gpt-5.4", null, null, "low");
    }

    private static CodexModelCatalog.Catalog catalog() {
        CodexModelCatalog.Model model = new CodexModelCatalog.Model(
                "gpt-5.4", "gpt-5.4", "GPT-5.4", "", true, "low",
                List.of(new CodexModelCatalog.ReasoningEffort("low", "Low")));
        return new CodexModelCatalog.Catalog(List.of(model), model);
    }

    private static final class CancelTrackingFuture<T> extends CompletableFuture<T> {
        private final AtomicInteger cancelCalls = new AtomicInteger();

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelCalls.incrementAndGet();
            return super.cancel(mayInterruptIfRunning);
        }
    }

    private static final class BlockingSubmitExecutor extends AbstractExecutorService {
        private final CountDownLatch executeEntered = new CountDownLatch(1);
        private final CountDownLatch allowExecuteReturn = new CountDownLatch(1);

        @Override
        public void execute(Runnable command) {
            executeEntered.countDown();
            try {
                if (!allowExecuteReturn.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("execute gate timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return false;
        }
    }

    private static final class GateExecutingExecutor extends AbstractExecutorService {
        private final CountDownLatch executeEntered = new CountDownLatch(1);
        private final CountDownLatch allowRun = new CountDownLatch(1);

        @Override
        public void execute(Runnable command) {
            executeEntered.countDown();
            try {
                if (!allowRun.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("run gate timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            command.run();
        }

        @Override public void shutdown() {}
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return false; }
    }
}
