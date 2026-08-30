package openflash_ai_runtime.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import openflash_ai_runtime.client.CodexAppServerClient;
import openflash_ai_runtime.client.JsonlRpcPeer;
import openflash_ai_runtime.service.CodexRuntimeService;
import openflash_ai_runtime.service.RuntimeSystemConfigService;
import openflash_ai_runtime.service.impl.CodexRuntimeServiceImpl;
import openflash_ai_runtime.support.CodexLoginCoordinator.LoginSnapshot;
import openflash_ai_runtime.support.CodexLoginCoordinator.LoginState;

class CodexLoginCoordinatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VERIFICATION_URL = "https://auth.openai.example/device";
    private static final String USER_CODE = "ABCD-EFGH";
    private final List<CodexLoginCoordinator> coordinators = new ArrayList<>();
    private final List<FakeRpc> rpcFixtures = new ArrayList<>();

    @AfterEach
    void shutDownCoordinators() {
        coordinators.forEach(CodexLoginCoordinator::shutdown);
        rpcFixtures.forEach(FakeRpc::close);
    }

    @Test
    void registersListenerBeforeSendingExactStartRequest() {
        FakeRpc rpc = new FakeRpc();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()), scheduler);

        CompletionStage<LoginSnapshot> started = coordinator.start();

        assertEquals(1, rpc.listenerCountAtStartRequest);
        assertEquals("account/login/start", rpc.onlyRequest().method());
        assertEquals(Map.of("type", "chatgptDeviceCode"), rpc.onlyRequest().params());

        rpc.completeStart(validStart("login-1"));
        assertEquals(
                new LoginSnapshot(LoginState.PENDING, VERIFICATION_URL, USER_CODE),
                started.toCompletableFuture().join());
    }

    @Test
    void concurrentStartsShareConnectionRequestAndResult() throws Exception {
        FakeRpc rpc = new FakeRpc();
        CompletableFuture<CodexProcessManager.CodexConnection> connection = new CompletableFuture<>();
        AtomicInteger acquisitions = new AtomicInteger();
        CodexLoginCoordinator coordinator = coordinator(() -> {
            acquisitions.incrementAndGet();
            return connection;
        }, new TestScheduler());
        CyclicBarrier barrier = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CompletionStage<LoginSnapshot>> firstCall = executor.submit(() -> {
                barrier.await(2, TimeUnit.SECONDS);
                return coordinator.start();
            });
            Future<CompletionStage<LoginSnapshot>> secondCall = executor.submit(() -> {
                barrier.await(2, TimeUnit.SECONDS);
                return coordinator.start();
            });
            barrier.await(2, TimeUnit.SECONDS);

            CompletionStage<LoginSnapshot> first = firstCall.get(2, TimeUnit.SECONDS);
            CompletionStage<LoginSnapshot> second = secondCall.get(2, TimeUnit.SECONDS);

            assertSame(first, second);
            assertEquals(1, acquisitions.get());
            connection.complete(rpc.connection());
            rpc.completeStart(validStart("shared-login"));
            assertEquals(
                    first.toCompletableFuture().get(2, TimeUnit.SECONDS),
                    second.toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals(1, rpc.startRequestCount());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void matchingSuccessfulCompletionBecomesSucceeded() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = pendingCoordinator(rpc, new TestScheduler(), "login-1");

        rpc.emitCompletion("login-1", true, null);

        awaitState(coordinator, LoginState.SUCCEEDED);
        assertEquals(LoginState.SUCCEEDED, coordinator.snapshot().state());
        awaitSubscriptionClosed(rpc);
    }

    @Test
    void matchingFailedCompletionHidesRawError() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = pendingCoordinator(rpc, new TestScheduler(), "login-1");
        String raw = "/home/alice/.codex/auth.json stderr secret-account@example.com";

        rpc.emitCompletion("login-1", false, raw);

        awaitState(coordinator, LoginState.FAILED);
        LoginSnapshot snapshot = coordinator.snapshot();
        assertEquals(LoginState.FAILED, snapshot.state());
        assertSafe(snapshot.toString(), raw);
        awaitSubscriptionClosed(rpc);
    }

    @Test
    void unknownStaleAndDuplicateCompletionsDoNotChangeCurrentAttempt() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = pendingCoordinator(rpc, new TestScheduler(), "current");

        rpc.emitCompletion("unknown", true, null);
        rpc.emitCompletion("stale", false, "raw");
        assertEquals(LoginState.PENDING, coordinator.snapshot().state());

        rpc.emitCompletion("current", true, null);
        awaitState(coordinator, LoginState.SUCCEEDED);
        assertEquals(LoginState.SUCCEEDED, coordinator.snapshot().state());
        rpc.emitCompletion("current", false, "must be ignored");
        assertEquals(LoginState.SUCCEEDED, coordinator.snapshot().state());
    }

    @Test
    void completionBeforeStartResponseIsBufferedAndApplied() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()), new TestScheduler());
        CompletionStage<LoginSnapshot> started = coordinator.start();

        rpc.emitCompletion("early", true, null);
        rpc.completeStart(validStart("early"));

        assertEquals(LoginState.PENDING, started.toCompletableFuture().join().state());
        assertEquals(LoginState.SUCCEEDED, coordinator.snapshot().state());
    }

    @Test
    void matchingEarlyCompletionSurvivesStaleNotificationsAfterIt() {
        assertEarlyNotificationOrder(List.of("match", "stale-1", "stale-2"));
    }

    @Test
    void matchingEarlyCompletionSurvivesStaleNotificationsBeforeIt() {
        assertEarlyNotificationOrder(List.of("stale-1", "stale-2", "match"));
    }

    @Test
    void sixtyFifthDistinctEarlyCompletionFailsSafely() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()), new TestScheduler());
        CompletionStage<LoginSnapshot> started = coordinator.start();

        for (int index = 0; index < 65; index++) {
            rpc.emitCompletion("early-" + index, true, null);
        }

        awaitState(coordinator, LoginState.FAILED);
        assertEquals(LoginState.FAILED, coordinator.snapshot().state());
        assertEquals(LoginState.FAILED, started.toCompletableFuture().join().state());
        awaitSubscriptionClosed(rpc);
    }

    @Test
    void connectionTerminalWhileStartingOrPendingFailsAttempt() {
        FakeRpc startingRpc = new FakeRpc();
        CodexLoginCoordinator starting = coordinator(
                () -> CompletableFuture.completedFuture(startingRpc.connection()),
                new TestScheduler());
        CompletionStage<LoginSnapshot> startingResult = starting.start();

        startingRpc.terminate(new IllegalStateException("raw /home/user/.codex/auth.json"));
        awaitState(starting, LoginState.FAILED);
        assertEquals(LoginState.FAILED, starting.snapshot().state());
        assertEquals(LoginState.FAILED, startingResult.toCompletableFuture().join().state());

        FakeRpc pendingRpc = new FakeRpc();
        CodexLoginCoordinator pending = pendingCoordinator(
                pendingRpc, new TestScheduler(), "pending-login");
        pendingRpc.terminate(new IllegalStateException("raw stderr"));
        awaitState(pending, LoginState.FAILED);
        assertEquals(LoginState.FAILED, pending.snapshot().state());
        awaitSubscriptionClosed(pendingRpc);
    }

    @Test
    void acquisitionRpcAndValidationFailuresReturnSafeFailedSnapshots() {
        String raw = "/home/user/.codex/auth.json raw stderr account@example.com";
        CodexLoginCoordinator acquisitionFailure = coordinator(
                () -> CompletableFuture.failedFuture(new IllegalStateException(raw)),
                new TestScheduler());
        assertSafeFailure(acquisitionFailure.start(), raw);

        FakeRpc rpcFailure = new FakeRpc();
        CodexLoginCoordinator requestFailure = coordinator(
                () -> CompletableFuture.completedFuture(rpcFailure.connection()),
                new TestScheduler());
        CompletionStage<LoginSnapshot> failedRequest = requestFailure.start();
        rpcFailure.failStart(raw);
        assertSafeFailure(failedRequest, raw);

        FakeRpc invalidRpc = new FakeRpc();
        CodexLoginCoordinator validationFailure = coordinator(
                () -> CompletableFuture.completedFuture(invalidRpc.connection()),
                new TestScheduler());
        CompletionStage<LoginSnapshot> invalid = validationFailure.start();
        invalidRpc.completeStart(validStart(" "));
        assertSafeFailure(invalid, raw);
    }

    @Test
    void timeoutExpiresClosesResourcesAndSendsOneBestEffortCancel() {
        FakeRpc rpc = new FakeRpc();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = pendingCoordinator(rpc, scheduler, "timeout-login");
        ScheduledFuture<?> timeout = scheduler.onlyTask();

        scheduler.runOnlyTask();

        assertEquals(LoginState.EXPIRED, coordinator.snapshot().state());
        awaitSubscriptionClosed(rpc);
        assertTrue(timeout.isDone());
        assertEquals(1, rpc.cancelRequestCount());
        assertEquals(Map.of("loginId", "timeout-login"), rpc.cancelRequest().params());
    }

    @Test
    void cancelWhileStartingCancelsLateValidLoginExactlyOnceWithoutRevivingAttempt() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()), new TestScheduler());
        CompletionStage<LoginSnapshot> started = coordinator.start();

        LoginSnapshot canceled = coordinator.cancel().toCompletableFuture().join();
        rpc.completeStart(validStart("late-canceled-login"));

        awaitCancelRequestCount(rpc, 1);
        assertEquals(LoginState.CANCELED, canceled.state());
        assertEquals(LoginState.CANCELED, started.toCompletableFuture().join().state());
        assertEquals(LoginState.CANCELED, coordinator.snapshot().state());
        assertEquals(1, rpc.cancelRequestCount());
        assertEquals(Map.of("loginId", "late-canceled-login"), rpc.cancelRequest().params());
        assertSafe(coordinator.snapshot().toString(),
                "late-canceled-login raw /home/user/.codex/auth.json stderr");
    }

    @Test
    void logoutWaitsForPendingCancelResponseBeforeCallingNativeLogout() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = pendingCoordinator(
                rpc, new TestScheduler(), "logout-pending");
        CodexAppServerClient client = mock(CodexAppServerClient.class);
        when(client.logoutAccount()).thenReturn(CompletableFuture.completedFuture(null));
        CodexRuntimeService service = runtimeService(coordinator, client);

        CompletableFuture<Boolean> logout = service.logoutAccount().toCompletableFuture();

        assertFalse(logout.isDone());
        verify(client, never()).logoutAccount();
        rpc.completeCancel();
        assertTrue(logout.join());
        verify(client).logoutAccount();
    }

    @Test
    void logoutWaitsForLateStartingResponseThenCancelResponse() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()), new TestScheduler());
        coordinator.start();
        CodexAppServerClient client = mock(CodexAppServerClient.class);
        when(client.logoutAccount()).thenReturn(CompletableFuture.completedFuture(null));
        CodexRuntimeService service = runtimeService(coordinator, client);

        CompletableFuture<Boolean> logout = service.logoutAccount().toCompletableFuture();

        assertFalse(logout.isDone());
        verify(client, never()).logoutAccount();
        rpc.completeStart(validStart("logout-late-start"));
        awaitCancelRequestCount(rpc, 1);
        assertFalse(logout.isDone());
        verify(client, never()).logoutAccount();
        rpc.completeCancel();
        assertTrue(logout.join());
        verify(client).logoutAccount();
    }

    @Test
    void logoutDoesNotCallNativeLogoutAfterCancelRpcFailure() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = pendingCoordinator(
                rpc, new TestScheduler(), "logout-cancel-failure");
        CodexAppServerClient client = mock(CodexAppServerClient.class);
        CodexRuntimeService service = runtimeService(coordinator, client);

        CompletableFuture<Boolean> logout = service.logoutAccount().toCompletableFuture();
        rpc.failCancel("raw secret cancel failure");

        assertThrowsCompletion(logout);
        verify(client, never()).logoutAccount();
    }

    @Test
    void terminalBeforeCancelFailureWaitsAndNeverCallsNativeLogout() throws Exception {
        FakeRpc rpc = new FakeRpc();
        CompletableFuture<JsonNode> cancellation = new CompletableFuture<>();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()),
                new TestScheduler(),
                (peer, loginId) -> cancellation);
        coordinator.start();
        rpc.completeStart(validStart("terminal-first"));
        // completeStart 仅写管道, start 响应由 rpc 读线程异步处理; 等 PENDING
        // 落定后再驱动 logout, 否则 drain 会把"未开始"当成"立即完成".
        awaitState(coordinator, LoginState.PENDING);
        CodexAppServerClient client = mock(CodexAppServerClient.class);
        CodexRuntimeService service = runtimeService(coordinator, client);
        CompletableFuture<Boolean> logout = service.logoutAccount().toCompletableFuture();
        CountDownLatch terminalDone = new CountDownLatch(1);
        CountDownLatch allowFailure = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> terminal = executor.submit(() -> {
                invokeConnectionTerminal(coordinator, rpc.connection());
                terminalDone.countDown();
            });
            Future<?> failure = executor.submit(() -> {
                try {
                    assertTrue(allowFailure.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                cancellation.completeExceptionally(new IllegalStateException("raw cancel"));
            });

            assertTrue(terminalDone.await(2, TimeUnit.SECONDS));
            terminal.get(2, TimeUnit.SECONDS);
            assertFalse(logout.isDone());
            verify(client, never()).logoutAccount();

            allowFailure.countDown();
            failure.get(2, TimeUnit.SECONDS);
            assertThrowsCompletion(logout);
            verify(client, never()).logoutAccount();
        } finally {
            allowFailure.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void cancelFailureBeforeTerminalRemainsFailedAndNeverCallsNativeLogout() throws Exception {
        FakeRpc rpc = new FakeRpc();
        CompletableFuture<JsonNode> cancellation = new CompletableFuture<>();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()),
                new TestScheduler(),
                (peer, loginId) -> cancellation);
        coordinator.start();
        rpc.completeStart(validStart("failure-first"));
        // 同上: 等 rpc 读线程把 start 响应处理成 PENDING 再驱动 logout.
        awaitState(coordinator, LoginState.PENDING);
        CodexAppServerClient client = mock(CodexAppServerClient.class);
        CodexRuntimeService service = runtimeService(coordinator, client);
        CompletableFuture<Boolean> logout = service.logoutAccount().toCompletableFuture();
        CountDownLatch failureDone = new CountDownLatch(1);
        CountDownLatch allowTerminal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> failure = executor.submit(() -> {
                cancellation.completeExceptionally(new IllegalStateException("raw cancel"));
                failureDone.countDown();
            });
            Future<?> terminal = executor.submit(() -> {
                try {
                    assertTrue(allowTerminal.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                invokeConnectionTerminal(coordinator, rpc.connection());
            });

            assertTrue(failureDone.await(2, TimeUnit.SECONDS));
            failure.get(2, TimeUnit.SECONDS);
            assertThrowsCompletion(logout);
            allowTerminal.countDown();
            terminal.get(2, TimeUnit.SECONDS);
            assertThrowsCompletion(logout);
            verify(client, never()).logoutAccount();
        } finally {
            allowTerminal.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void terminalBeforeSuccessfulCancelCallsNativeLogoutExactlyOnce() throws Exception {
        FakeRpc rpc = new FakeRpc();
        CompletableFuture<JsonNode> cancellation = new CompletableFuture<>();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()),
                new TestScheduler(),
                (peer, loginId) -> cancellation);
        coordinator.start();
        rpc.completeStart(validStart("terminal-success"));
        // 同上: 等 rpc 读线程把 start 响应处理成 PENDING 再驱动 logout.
        awaitState(coordinator, LoginState.PENDING);
        CodexAppServerClient client = mock(CodexAppServerClient.class);
        when(client.logoutAccount()).thenReturn(CompletableFuture.completedFuture(null));
        CodexRuntimeService service = runtimeService(coordinator, client);
        CompletableFuture<Boolean> logout = service.logoutAccount().toCompletableFuture();

        invokeConnectionTerminal(coordinator, rpc.connection());
        assertFalse(logout.isDone());
        verify(client, never()).logoutAccount();

        cancellation.complete(JSON.createObjectNode());
        assertTrue(logout.get(2, TimeUnit.SECONDS));
        invokeConnectionTerminal(coordinator, rpc.connection());
        verify(client).logoutAccount();
    }

    @Test
    void logoutDoesNotCallNativeLogoutAfterNullCancelStage() {
        FakeRpc rpc = new FakeRpc();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()),
                scheduler,
                (peer, loginId) -> null);
        CompletionStage<LoginSnapshot> start = coordinator.start();
        rpc.completeStart(validStart("logout-null-cancel"));
        assertEquals(LoginState.PENDING, start.toCompletableFuture().join().state());
        CodexAppServerClient client = mock(CodexAppServerClient.class);
        CodexRuntimeService service = runtimeService(coordinator, client);

        CompletableFuture<Boolean> logout = service.logoutAccount().toCompletableFuture();

        assertThrowsCompletion(logout);
        verify(client, never()).logoutAccount();
    }

    @Test
    void logoutDoesNotCallNativeLogoutAfterNullCancelResult() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()),
                new TestScheduler(),
                (peer, loginId) -> CompletableFuture.completedFuture(null));
        coordinator.start();
        rpc.completeStart(validStart("logout-null-result"));
        CodexAppServerClient client = mock(CodexAppServerClient.class);
        CodexRuntimeService service = runtimeService(coordinator, client);

        assertThrowsCompletion(service.logoutAccount().toCompletableFuture());

        verify(client, never()).logoutAccount();
    }

    @Test
    void logoutDoesNotCallNativeLogoutWhenCancelRequesterThrows() {
        FakeRpc rpc = new FakeRpc();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()),
                scheduler,
                (peer, loginId) -> {
                    throw new IllegalStateException("raw secret cancel failure");
                });
        CompletionStage<LoginSnapshot> start = coordinator.start();
        rpc.completeStart(validStart("logout-thrown-cancel"));
        assertEquals(LoginState.PENDING, start.toCompletableFuture().join().state());
        CodexAppServerClient client = mock(CodexAppServerClient.class);
        CodexRuntimeService service = runtimeService(coordinator, client);

        assertThrowsCompletion(service.logoutAccount().toCompletableFuture());

        verify(client, never()).logoutAccount();
    }

    @Test
    void logoutDoesNotCallNativeLogoutAfterDrainTimeout() {
        FakeRpc rpc = new FakeRpc();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = pendingCoordinator(
                rpc, scheduler, "logout-timeout");
        CodexAppServerClient client = mock(CodexAppServerClient.class);
        CodexRuntimeService service = runtimeService(coordinator, client);

        CompletableFuture<Boolean> logout = service.logoutAccount().toCompletableFuture();
        scheduler.runNextPendingTask();

        assertThrowsCompletion(logout);
        verify(client, never()).logoutAccount();
    }

    @Test
    void logoutDoesNotCallNativeLogoutWhenShutdownForcesStartingDrainClosed() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()), new TestScheduler());
        coordinator.start();
        CodexAppServerClient client = mock(CodexAppServerClient.class);
        CodexRuntimeService service = runtimeService(coordinator, client);
        CompletableFuture<Boolean> logout = service.logoutAccount().toCompletableFuture();

        coordinator.shutdown();

        assertThrowsCompletion(logout);
        verify(client, never()).logoutAccount();
    }

    @Test
    void timeoutWhileStartingCancelsLateValidLoginExactlyOnceWithoutRevivingAttempt() {
        FakeRpc rpc = new FakeRpc();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()), scheduler);
        CompletionStage<LoginSnapshot> started = coordinator.start();

        scheduler.runOnlyTask();
        rpc.completeStart(validStart("late-expired-login"));

        awaitCancelRequestCount(rpc, 1);
        assertEquals(LoginState.EXPIRED, started.toCompletableFuture().join().state());
        assertEquals(LoginState.EXPIRED, coordinator.snapshot().state());
        assertEquals(1, rpc.cancelRequestCount());
        assertEquals(Map.of("loginId", "late-expired-login"), rpc.cancelRequest().params());
        assertSafe(coordinator.snapshot().toString(),
                "late-expired-login raw /home/user/.codex/auth.json stderr");
    }

    @Test
    void startingCancelDrainsNeverResponseBeforeAdmittingAnotherUpstreamFlow() {
        FakeRpc firstRpc = new FakeRpc();
        FakeRpc secondRpc = new FakeRpc();
        AtomicInteger acquisitions = new AtomicInteger();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = coordinator(() -> CompletableFuture.completedFuture(
                acquisitions.getAndIncrement() == 0
                        ? firstRpc.connection()
                        : secondRpc.connection()),
                scheduler);
        CompletionStage<LoginSnapshot> firstStart = coordinator.start();

        LoginSnapshot canceled = coordinator.cancel().toCompletableFuture().join();
        CompletionStage<LoginSnapshot> blockedRetry = coordinator.start();

        assertEquals(LoginState.CANCELED, canceled.state());
        assertEquals(LoginState.CANCELED, firstStart.toCompletableFuture().join().state());
        assertSame(firstStart, blockedRetry);
        assertEquals(1, acquisitions.get());
        assertEquals(1, firstRpc.startRequestCount());
        assertEquals(1, pendingRequestCount(firstRpc.peer));

        scheduler.runNextPendingTask();

        assertEquals(0, pendingRequestCount(firstRpc.peer));
        CompletionStage<LoginSnapshot> retried = coordinator.start();
        assertEquals(2, acquisitions.get());
        assertEquals(1, secondRpc.startRequestCount());
        secondRpc.completeStart(validStart("fresh-login"));
        assertEquals(LoginState.PENDING, retried.toCompletableFuture().join().state());
    }

    @Test
    void startingCancelRetainsDrainAfterLateInvalidResponseWithoutLoginIdAndIsolatesRetry()
            throws Exception {
        FakeRpc firstRpc = new FakeRpc();
        FakeRpc secondRpc = new FakeRpc();
        FakeRpc thirdRpc = new FakeRpc();
        AtomicInteger acquisitions = new AtomicInteger();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = coordinator(() -> {
            int acquisition = acquisitions.getAndIncrement();
            return CompletableFuture.completedFuture(switch (acquisition) {
                case 0 -> firstRpc.connection();
                case 1 -> secondRpc.connection();
                default -> thirdRpc.connection();
            });
        }, scheduler);
        CompletionStage<LoginSnapshot> firstStart = coordinator.start();

        assertEquals(LoginState.CANCELED,
                coordinator.cancel().toCompletableFuture().join().state());
        CountDownLatch responseProcessed = new CountDownLatch(1);
        AutoCloseable marker = firstRpc.peer.onNotification((method, params) -> {
            if ("test/response-processed".equals(method))
                responseProcessed.countDown();
        });
        try {
            firstRpc.completeStart(JSON.createObjectNode()
                    .put("type", "chatgptDeviceCode")
                    .put("verificationUrl", VERIFICATION_URL)
                    .put("userCode", USER_CODE));
            firstRpc.writeMessage(JSON.createObjectNode()
                    .put("method", "test/response-processed")
                    .set("params", JSON.createObjectNode()));
            assertTrue(responseProcessed.await(2, TimeUnit.SECONDS));
        } finally {
            marker.close();
        }

        CompletionStage<LoginSnapshot> blockedRetry = coordinator.start();
        assertSame(firstStart, blockedRetry);
        assertEquals(LoginState.CANCELED, blockedRetry.toCompletableFuture().join().state());
        assertEquals(1, acquisitions.get());
        assertEquals(1, firstRpc.startRequestCount());
        assertEquals(0, firstRpc.cancelRequestCount());
        assertFalse(firstRpc.peer.terminal().toCompletableFuture().isDone());

        scheduler.runNextPendingTask();

        assertTrue(firstRpc.peer.terminal().toCompletableFuture().isDone());
        CompletionStage<LoginSnapshot> activeInvalid = coordinator.start();
        assertEquals(2, acquisitions.get());
        secondRpc.completeStart(JSON.createObjectNode()
                .put("type", "chatgptDeviceCode")
                .put("verificationUrl", VERIFICATION_URL)
                .put("userCode", USER_CODE));
        assertEquals(LoginState.FAILED, activeInvalid.toCompletableFuture().join().state());

        CompletionStage<LoginSnapshot> retried = coordinator.start();
        assertEquals(3, acquisitions.get());
        assertEquals(1, thirdRpc.startRequestCount());
        thirdRpc.completeStart(validStart("fresh-after-invalid"));
        assertEquals(LoginState.PENDING, retried.toCompletableFuture().join().state());
    }

    @Test
    void pendingCancelDrainsNeverCancelResponseBeforeAdmittingAnotherUpstreamFlow() {
        FakeRpc firstRpc = new FakeRpc();
        FakeRpc secondRpc = new FakeRpc();
        AtomicInteger acquisitions = new AtomicInteger();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = coordinator(() -> CompletableFuture.completedFuture(
                acquisitions.getAndIncrement() == 0
                        ? firstRpc.connection()
                        : secondRpc.connection()),
                scheduler);
        CompletionStage<LoginSnapshot> firstStart = coordinator.start();
        firstRpc.completeStart(validStart("pending-cancel"));
        assertEquals(LoginState.PENDING, firstStart.toCompletableFuture().join().state());

        LoginSnapshot canceled = coordinator.cancel().toCompletableFuture().join();
        CompletionStage<LoginSnapshot> blockedRetry = coordinator.start();

        assertEquals(LoginState.CANCELED, canceled.state());
        assertEquals(LoginState.CANCELED, coordinator.snapshot().state());
        assertEquals(LoginState.CANCELED, blockedRetry.toCompletableFuture().join().state());
        assertEquals(1, acquisitions.get());
        assertEquals(1, firstRpc.startRequestCount());
        assertEquals(1, firstRpc.cancelRequestCount());
        assertEquals(1, pendingRequestCount(firstRpc.peer));

        scheduler.runNextPendingTask();

        assertEquals(0, pendingRequestCount(firstRpc.peer));
        CompletionStage<LoginSnapshot> retried = coordinator.start();
        assertEquals(2, acquisitions.get());
        assertEquals(1, secondRpc.startRequestCount());
        secondRpc.completeStart(validStart("fresh-after-cancel"));
        assertEquals(LoginState.PENDING, retried.toCompletableFuture().join().state());
    }

    @Test
    void pendingExpiryDrainsFailedCancelBeforeAdmittingAnotherUpstreamFlow() {
        FakeRpc firstRpc = new FakeRpc();
        FakeRpc secondRpc = new FakeRpc();
        AtomicInteger acquisitions = new AtomicInteger();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = coordinator(() -> CompletableFuture.completedFuture(
                acquisitions.getAndIncrement() == 0
                        ? firstRpc.connection()
                        : secondRpc.connection()),
                scheduler);
        CompletionStage<LoginSnapshot> firstStart = coordinator.start();
        firstRpc.completeStart(validStart("pending-expiry"));
        assertEquals(LoginState.PENDING, firstStart.toCompletableFuture().join().state());

        scheduler.runOnlyTask();
        firstRpc.failCancel("raw cancel failure");
        CompletionStage<LoginSnapshot> blockedRetry = coordinator.start();

        assertEquals(LoginState.EXPIRED, coordinator.snapshot().state());
        assertEquals(LoginState.EXPIRED, blockedRetry.toCompletableFuture().join().state());
        assertEquals(1, acquisitions.get());
        assertEquals(1, firstRpc.startRequestCount());
        assertEquals(1, firstRpc.cancelRequestCount());

        scheduler.runNextPendingTask();

        assertEquals(0, pendingRequestCount(firstRpc.peer));
        CompletionStage<LoginSnapshot> retried = coordinator.start();
        assertEquals(2, acquisitions.get());
        assertEquals(1, secondRpc.startRequestCount());
        secondRpc.completeStart(validStart("fresh-after-expiry"));
        assertEquals(LoginState.PENDING, retried.toCompletableFuture().join().state());
    }

    @Test
    void thrownNullAndExceptionalCancelRequestsRetainDrainUntilDeadline() {
        List<CodexLoginCoordinator.CancelRequester> failures = List.of(
                (peer, loginId) -> {
                    throw new IllegalStateException("raw thrown cancel failure");
                },
                (peer, loginId) -> null,
                (peer, loginId) -> CompletableFuture.failedFuture(
                        new IllegalStateException("raw async cancel failure")));

        for (int index = 0; index < failures.size(); index++) {
            int failureIndex = index;
            FakeRpc firstRpc = new FakeRpc();
            FakeRpc secondRpc = new FakeRpc();
            AtomicInteger acquisitions = new AtomicInteger();
            AtomicInteger cancelCalls = new AtomicInteger();
            TestScheduler scheduler = new TestScheduler();
            CodexLoginCoordinator.CancelRequester failure = failures.get(index);
            CodexLoginCoordinator coordinator = coordinator(
                    () -> CompletableFuture.completedFuture(
                            acquisitions.getAndIncrement() == 0
                                    ? firstRpc.connection()
                                    : secondRpc.connection()),
                    scheduler,
                    (peer, loginId) -> {
                        cancelCalls.incrementAndGet();
                        assertEquals("failure-mode-" + failureIndex, loginId);
                        return failure.cancel(peer, loginId);
                    });
            CompletionStage<LoginSnapshot> firstStart = coordinator.start();
            firstRpc.completeStart(validStart("failure-mode-" + failureIndex));
            assertEquals(LoginState.PENDING, firstStart.toCompletableFuture().join().state());

            assertEquals(LoginState.CANCELED,
                    coordinator.cancel().toCompletableFuture().join().state());
            assertEquals(LoginState.CANCELED,
                    coordinator.start().toCompletableFuture().join().state());
            assertEquals(1, acquisitions.get());
            assertEquals(1, cancelCalls.get());
            assertFalse(firstRpc.peer.terminal().toCompletableFuture().isDone());

            scheduler.runNextPendingTask();

            assertTrue(firstRpc.peer.terminal().toCompletableFuture().isDone());
            CompletionStage<LoginSnapshot> retried = coordinator.start();
            assertEquals(2, acquisitions.get());
            assertEquals(1, secondRpc.startRequestCount());
            secondRpc.completeStart(validStart("retry-after-failure-" + failureIndex));
            assertEquals(LoginState.PENDING, retried.toCompletableFuture().join().state());
        }
    }

    @Test
    void repeatedCancelWhileStartingDrainIsIdempotent() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()), new TestScheduler());
        coordinator.start();

        LoginSnapshot first = coordinator.cancel().toCompletableFuture().join();
        LoginSnapshot second = coordinator.cancel().toCompletableFuture().join();

        assertEquals(LoginState.CANCELED, first.state());
        assertEquals(first, second);
        assertEquals(0, rpc.cancelRequestCount());
        assertEquals(1, rpc.startRequestCount());
    }

    @Test
    void invalidStartResponseWithSafeLoginIdIsCanceled() {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()), new TestScheduler());
        CompletionStage<LoginSnapshot> started = coordinator.start();

        rpc.completeStart(validStart("recoverable-login")
                .put("verificationUrl", "http://unsafe.example/device"));

        assertEquals(LoginState.FAILED, started.toCompletableFuture().join().state());
        awaitCancelRequestCount(rpc, 1);
        assertEquals(Map.of("loginId", "recoverable-login"), rpc.cancelRequest().params());
    }

    @Test
    void repeatedReloginRegistersOneTerminalObserverPerConnectionGeneration() {
        FakeRpc rpc = new FakeRpc();
        CodexProcessManager.CodexConnection connection = rpc.connection();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(connection), new TestScheduler());

        for (int index = 0; index < 3; index++) {
            CompletionStage<LoginSnapshot> started = coordinator.start();
            String loginId = "login-" + index;
            rpc.completeStart(validStart(loginId));
            assertEquals(LoginState.PENDING, started.toCompletableFuture().join().state());
            rpc.emitCompletion(loginId, true, null);
            awaitState(coordinator, LoginState.SUCCEEDED);
            awaitSubscriptionClosed(rpc);
        }

        assertEquals(1, terminalObserverCount(rpc.peer));
    }

    @Test
    void cancelUsesInternalLoginIdOnceAndNoAttemptCancelIsIdle() {
        FakeRpc rpc = new FakeRpc();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = pendingCoordinator(rpc, scheduler, "internal-login");
        ScheduledFuture<?> timeout = scheduler.onlyTask();

        LoginSnapshot canceled = coordinator.cancel().toCompletableFuture().join();

        assertEquals(LoginState.CANCELED, canceled.state());
        assertEquals(1, rpc.cancelRequestCount());
        assertEquals(Map.of("loginId", "internal-login"), rpc.cancelRequest().params());
        assertTrue(rpc.subscriptionClosed());
        assertTrue(timeout.isCancelled());

        LoginSnapshot stillDraining = coordinator.cancel().toCompletableFuture().join();
        assertEquals(LoginState.CANCELED, stillDraining.state());
        assertEquals(1, rpc.cancelRequestCount());

        rpc.completeCancel();
        LoginSnapshot idle = awaitIdleCancel(coordinator);
        assertEquals(LoginState.IDLE, idle.state());
        assertEquals(1, rpc.cancelRequestCount());
    }

    @Test
    void cancelWithoutAttemptIsIdempotentAndDoesNotAcquireConnection() {
        AtomicInteger acquisitions = new AtomicInteger();
        CodexLoginCoordinator coordinator = coordinator(() -> {
            acquisitions.incrementAndGet();
            return CompletableFuture.failedFuture(new AssertionError("must not connect"));
        }, new TestScheduler());

        assertEquals(LoginState.IDLE, coordinator.cancel().toCompletableFuture().join().state());
        assertEquals(LoginState.IDLE, coordinator.cancel().toCompletableFuture().join().state());
        assertEquals(0, acquisitions.get());
    }

    @Test
    void invalidOrOversizedResponseFieldsFailAttempt() {
        List<ObjectNode> invalidResponses = List.of(
                validStart(" "),
                validStart("x".repeat(513)),
                validStart("login").put("userCode", " "),
                validStart("login").put("userCode", "x".repeat(129)),
                validStart("login").put("verificationUrl", "http://auth.openai.example/device"),
                validStart("login").put("verificationUrl", "https:///missing-host"),
                validStart("login").put("type", "other"));

        for (ObjectNode response : invalidResponses) {
            FakeRpc rpc = new FakeRpc();
            CodexLoginCoordinator coordinator = coordinator(
                    () -> CompletableFuture.completedFuture(rpc.connection()),
                    new TestScheduler());
            CompletionStage<LoginSnapshot> result = coordinator.start();
            rpc.completeStart(response);
            assertEquals(LoginState.FAILED, result.toCompletableFuture().join().state());
            assertTrue(rpc.subscriptionClosed());
        }
    }

    @Test
    void everyTerminalOutcomeClosesListenerAndCancelsTimeout() {
        assertTerminalCleanup(true, LoginState.SUCCEEDED);
        assertTerminalCleanup(false, LoginState.FAILED);

        FakeRpc expiredRpc = new FakeRpc();
        TestScheduler expiredScheduler = new TestScheduler();
        CodexLoginCoordinator expired = pendingCoordinator(
                expiredRpc, expiredScheduler, "expired");
        expiredScheduler.runOnlyTask();
        assertEquals(LoginState.EXPIRED, expired.snapshot().state());
        assertTrue(expiredRpc.subscriptionClosed());

        FakeRpc canceledRpc = new FakeRpc();
        TestScheduler canceledScheduler = new TestScheduler();
        CodexLoginCoordinator canceled = pendingCoordinator(
                canceledRpc, canceledScheduler, "canceled");
        ScheduledFuture<?> timeout = canceledScheduler.onlyTask();
        canceled.cancel().toCompletableFuture().join();
        assertTrue(canceledRpc.subscriptionClosed());
        assertTrue(timeout.isCancelled());
    }

    @Test
    void shutdownIsIdempotentAndDoesNotExposeCredentialsOrPaths() {
        FakeRpc rpc = new FakeRpc();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = pendingCoordinator(
                rpc, scheduler, "private-login-id");

        coordinator.shutdown();
        coordinator.shutdown();

        assertTrue(rpc.subscriptionClosed());
        assertTrue(scheduler.isShutdown());
        assertSafe(coordinator.snapshot().toString(),
                "private-login-id /home/user/.codex/auth.json");
    }

    private void assertEarlyNotificationOrder(List<String> ids) {
        FakeRpc rpc = new FakeRpc();
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()), new TestScheduler());
        CompletionStage<LoginSnapshot> started = coordinator.start();
        ids.forEach(id -> rpc.emitCompletion(id, "match".equals(id), "raw stale error"));

        rpc.completeStart(validStart("match"));

        assertEquals(LoginState.PENDING, started.toCompletableFuture().join().state());
        assertEquals(LoginState.SUCCEEDED, coordinator.snapshot().state());
        rpc.emitCompletion("match", false, "duplicate raw error");
        assertEquals(LoginState.SUCCEEDED, coordinator.snapshot().state());
    }

    private void assertTerminalCleanup(boolean success, LoginState expected) {
        FakeRpc rpc = new FakeRpc();
        TestScheduler scheduler = new TestScheduler();
        CodexLoginCoordinator coordinator = pendingCoordinator(rpc, scheduler, "terminal");
        ScheduledFuture<?> timeout = scheduler.onlyTask();

        rpc.emitCompletion("terminal", success, success ? null : "raw");

        awaitState(coordinator, expected);
        assertEquals(expected, coordinator.snapshot().state());
        awaitSubscriptionClosed(rpc);
        assertTrue(timeout.isCancelled());
    }

    private CodexLoginCoordinator pendingCoordinator(
            FakeRpc rpc, TestScheduler scheduler, String loginId) {
        CodexLoginCoordinator coordinator = coordinator(
                () -> CompletableFuture.completedFuture(rpc.connection()), scheduler);
        CompletionStage<LoginSnapshot> result = coordinator.start();
        rpc.completeStart(validStart(loginId));
        assertEquals(LoginState.PENDING, result.toCompletableFuture().join().state());
        return coordinator;
    }

    private CodexLoginCoordinator coordinator(
            CodexLoginCoordinator.ConnectionProvider connectionProvider,
            TestScheduler scheduler) {
        RuntimeSystemConfigService config = mock(RuntimeSystemConfigService.class);
        when(config.getLong(
                CodexLoginCoordinator.LOGIN_TIMEOUT_KEY,
                CodexLoginCoordinator.DEFAULT_LOGIN_TIMEOUT_MILLIS))
                .thenReturn(1_000L);
        CodexLoginCoordinator coordinator = new CodexLoginCoordinator(connectionProvider, config, scheduler);
        coordinators.add(coordinator);
        return coordinator;
    }

    private CodexLoginCoordinator coordinator(
            CodexLoginCoordinator.ConnectionProvider connectionProvider,
            TestScheduler scheduler,
            CodexLoginCoordinator.CancelRequester cancelRequester) {
        RuntimeSystemConfigService config = mock(RuntimeSystemConfigService.class);
        when(config.getLong(
                CodexLoginCoordinator.LOGIN_TIMEOUT_KEY,
                CodexLoginCoordinator.DEFAULT_LOGIN_TIMEOUT_MILLIS))
                .thenReturn(1_000L);
        CodexLoginCoordinator coordinator = new CodexLoginCoordinator(connectionProvider, config, scheduler,
                cancelRequester);
        coordinators.add(coordinator);
        return coordinator;
    }

    private static CodexRuntimeService runtimeService(
            CodexLoginCoordinator coordinator, CodexAppServerClient client) {
        return new CodexRuntimeServiceImpl(
                client,
                coordinator,
                mock(RuntimeSystemConfigService.class),
                mock(ExecutorService.class),
                mock(ScheduledExecutorService.class));
    }

    private static void assertThrowsCompletion(CompletableFuture<?> future) {
        try {
            future.join();
        } catch (CompletionException expected) {
            return;
        }
        throw new AssertionError("expected exceptional completion");
    }

    private static void awaitState(CodexLoginCoordinator coordinator, LoginState expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (coordinator.snapshot().state() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(expected, coordinator.snapshot().state());
    }

    private static void awaitCancelRequestCount(FakeRpc rpc, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (rpc.cancelRequestCount() != expected && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(expected, rpc.cancelRequestCount());
    }

    private static void awaitSubscriptionClosed(FakeRpc rpc) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!rpc.subscriptionClosed() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(rpc.subscriptionClosed());
    }

    private static LoginSnapshot awaitIdleCancel(CodexLoginCoordinator coordinator) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        LoginSnapshot snapshot;
        do {
            snapshot = coordinator.cancel().toCompletableFuture().join();
            if (snapshot.state() == LoginState.IDLE)
                return snapshot;
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        return snapshot;
    }

    private static void assertSafeFailure(CompletionStage<LoginSnapshot> result, String raw) {
        LoginSnapshot snapshot = result.toCompletableFuture().join();
        assertEquals(LoginState.FAILED, snapshot.state());
        assertSafe(snapshot.toString(), raw);
    }

    private static void assertSafe(String value, String raw) {
        assertFalse(value.contains(raw));
        assertFalse(value.contains("auth.json"));
        assertFalse(value.contains("/home/"));
        assertFalse(value.contains("stderr"));
        assertFalse(value.contains("@example.com"));
    }

    private static ObjectNode validStart(String loginId) {
        return JSON.createObjectNode()
                .put("type", "chatgptDeviceCode")
                .put("loginId", loginId)
                .put("verificationUrl", VERIFICATION_URL)
                .put("userCode", USER_CODE);
    }

    private record RpcRequest(String method, Object params) {
    }

    private final class FakeRpc {
        private final PipedInputStream peerStdout;
        private final PipedOutputStream serverOutput;
        private final RequestSink peerStdin = new RequestSink();
        private final JsonlRpcPeer peer;
        private final List<RpcRequest> requests = new CopyOnWriteArrayList<>();
        private volatile Long startRequestId;
        private volatile Long cancelRequestId;
        private volatile int listenerCountAtStartRequest;

        private FakeRpc() {
            try {
                peerStdout = new PipedInputStream();
                serverOutput = new PipedOutputStream(peerStdout);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
            peer = new JsonlRpcPeer(peerStdout, peerStdin);
            rpcFixtures.add(this);
        }

        private CodexProcessManager.CodexConnection connection() {
            return new CodexProcessManager.CodexConnection(1L, peer, mock(Process.class));
        }

        private RpcRequest onlyRequest() {
            assertEquals(1, requests.size());
            return requests.get(0);
        }

        private int startRequestCount() {
            return (int) requests.stream()
                    .filter(request -> "account/login/start".equals(request.method()))
                    .count();
        }

        private int cancelRequestCount() {
            return (int) requests.stream()
                    .filter(request -> "account/login/cancel".equals(request.method()))
                    .count();
        }

        private RpcRequest cancelRequest() {
            return requests.stream()
                    .filter(request -> "account/login/cancel".equals(request.method()))
                    .findFirst()
                    .orElseThrow();
        }

        private void completeStart(JsonNode response) {
            writeMessage(JSON.createObjectNode()
                    .put("id", requireStartRequestId())
                    .set("result", response));
        }

        private void failStart(String raw) {
            ObjectNode message = JSON.createObjectNode().put("id", requireStartRequestId());
            message.putObject("error").put("code", -1).put("message", raw);
            writeMessage(message);
        }

        private void failCancel(String raw) {
            ObjectNode message = JSON.createObjectNode().put("id", requireCancelRequestId());
            message.putObject("error").put("code", -1).put("message", raw);
            writeMessage(message);
        }

        private void completeCancel() {
            writeMessage(JSON.createObjectNode()
                    .put("id", requireCancelRequestId())
                    .set("result", JSON.createObjectNode()));
        }

        private void emitCompletion(String loginId, boolean success, String error) {
            ObjectNode params = JSON.createObjectNode()
                    .put("loginId", loginId)
                    .put("success", success);
            if (error != null)
                params.put("error", error);
            writeMessage(JSON.createObjectNode()
                    .put("method", "account/login/completed")
                    .set("params", params));
        }

        private void terminate(Throwable failure) {
            try {
                serverOutput.close();
            } catch (IOException ignored) {
                // EOF is the intended terminal signal.
            }
        }

        private boolean subscriptionClosed() {
            return notificationHandlerCount(peer) == 0;
        }

        private long requireStartRequestId() {
            assertNotNull(startRequestId);
            return startRequestId;
        }

        private long requireCancelRequestId() {
            assertNotNull(cancelRequestId);
            return cancelRequestId;
        }

        private void writeMessage(JsonNode message) {
            try {
                serverOutput.write(JSON.writeValueAsBytes(message));
                serverOutput.write('\n');
                serverOutput.flush();
            } catch (IOException failure) {
                if (!subscriptionClosed())
                    throw new IllegalStateException(failure);
            }
        }

        private void close() {
            peer.close();
            try {
                serverOutput.close();
            } catch (IOException ignored) {
                // Test cleanup only.
            }
        }

        private final class RequestSink extends OutputStream {
            private final ByteArrayOutputStream line = new ByteArrayOutputStream();

            @Override
            public synchronized void write(int value) {
                if (value == '\n') {
                    captureLine();
                    line.reset();
                } else {
                    line.write(value);
                }
            }

            @Override
            public synchronized void write(byte[] bytes, int offset, int length) {
                for (int index = offset; index < offset + length; index++)
                    write(bytes[index]);
            }

            private void captureLine() {
                try {
                    JsonNode request = JSON.readTree(line.toByteArray());
                    String method = request.path("method").textValue();
                    Object params = JSON.convertValue(request.get("params"), Map.class);
                    requests.add(new RpcRequest(method, params));
                    if ("account/login/start".equals(method)) {
                        listenerCountAtStartRequest = notificationHandlerCount(peer);
                        startRequestId = request.path("id").longValue();
                    } else if ("account/login/cancel".equals(method)) {
                        cancelRequestId = request.path("id").longValue();
                    }
                } catch (IOException failure) {
                    throw new IllegalStateException(failure);
                }
            }
        }
    }

    private static int notificationHandlerCount(JsonlRpcPeer peer) {
        try {
            Field field = JsonlRpcPeer.class.getDeclaredField("notificationHandlers");
            field.setAccessible(true);
            return ((List<?>) field.get(peer)).size();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static int pendingRequestCount(JsonlRpcPeer peer) {
        try {
            Field field = JsonlRpcPeer.class.getDeclaredField("pending");
            field.setAccessible(true);
            return ((Map<?, ?>) field.get(peer)).size();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static int terminalObserverCount(JsonlRpcPeer peer) {
        return peer.terminal().toCompletableFuture().getNumberOfDependents();
    }

    private static void invokeConnectionTerminal(
            CodexLoginCoordinator coordinator,
            CodexProcessManager.CodexConnection connection) {
        try {
            Method method = CodexLoginCoordinator.class.getDeclaredMethod(
                    "onConnectionTerminal", long.class, JsonlRpcPeer.class);
            method.setAccessible(true);
            method.invoke(coordinator, connection.generation(), connection.peer());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class TestScheduler extends AbstractExecutorService
            implements ScheduledExecutorService {
        private final List<TestScheduledFuture> tasks = new ArrayList<>();
        private boolean shutdown;

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            TestScheduledFuture task = new TestScheduledFuture(command, unit.toNanos(delay));
            tasks.add(task);
            return task;
        }

        private ScheduledFuture<?> onlyTask() {
            assertEquals(1, tasks.size());
            return tasks.get(0);
        }

        private void runOnlyTask() {
            ((TestScheduledFuture) onlyTask()).run();
        }

        private void runNextPendingTask() {
            tasks.stream()
                    .filter(task -> !task.isDone())
                    .findFirst()
                    .orElseThrow()
                    .run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            tasks.forEach(task -> task.cancel(false));
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(
                java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestScheduledFuture implements ScheduledFuture<Object>, Runnable {
        private final Runnable command;
        private final long delayNanos;
        private boolean canceled;
        private boolean done;

        private TestScheduledFuture(Runnable command, long delayNanos) {
            this.command = command;
            this.delayNanos = delayNanos;
        }

        @Override
        public void run() {
            if (canceled || done)
                return;
            done = true;
            command.run();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(delayNanos, TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Comparator.comparingLong((Delayed delayed) -> delayed.getDelay(TimeUnit.NANOSECONDS))
                    .compare(this, other);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (done)
                return false;
            canceled = true;
            done = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return canceled;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            if (!done)
                throw new IllegalStateException("not completed");
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return get();
        }
    }
}
