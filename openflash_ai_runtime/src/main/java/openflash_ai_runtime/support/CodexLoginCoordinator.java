package openflash_ai_runtime.support;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import openflash_ai_runtime.client.JsonlRpcPeer;
import openflash_ai_runtime.service.RuntimeSystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 协调进程级唯一 Codex device-code 登录 attempt, 只暴露安全状态. */
@Component
@ConditionalOnProperty(prefix = "app.codex", name = "enabled", havingValue = "true")
public final class CodexLoginCoordinator {

    static final String LOGIN_TIMEOUT_KEY = "ai.codex-login-timeout-millis";
    static final long DEFAULT_LOGIN_TIMEOUT_MILLIS = 600_000L;
    static final long START_DRAIN_TIMEOUT_MILLIS = 5_000L;

    private static final int MAX_EARLY_COMPLETIONS = 64;
    private static final String START_METHOD = "account/login/start";
    private static final String CANCEL_METHOD = "account/login/cancel";
    private static final String COMPLETED_METHOD = "account/login/completed";
    private static final String LOGIN_TYPE = "chatgptDeviceCode";

    private final Object lock = new Object();
    private final ConnectionProvider connectionProvider;
    private final RuntimeSystemConfigService systemConfigService;
    private final ScheduledExecutorService scheduler;
    private final CancelRequester cancelRequester;

    private Attempt currentAttempt;
    private CompletableFuture<LoginSnapshot> currentStart;
    private LoginSnapshot currentSnapshot = LoginSnapshot.idle();
    private long observedTerminalGeneration = Long.MIN_VALUE;
    private JsonlRpcPeer observedTerminalPeer;
    private boolean shutdown;

    @FunctionalInterface
    interface ConnectionProvider {
        CompletionStage<CodexProcessManager.CodexConnection> connection();
    }

    @FunctionalInterface
    interface CancelRequester {
        CompletionStage<JsonNode> cancel(JsonlRpcPeer peer, String loginId);
    }

    @Autowired
    public CodexLoginCoordinator(
            CodexProcessManager processManager,
            RuntimeSystemConfigService systemConfigService) {
        this(
                processManager::connection,
                systemConfigService,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "codex-login-timeout");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    CodexLoginCoordinator(
            ConnectionProvider connectionProvider,
            RuntimeSystemConfigService systemConfigService,
            ScheduledExecutorService scheduler) {
        this(
                connectionProvider,
                systemConfigService,
                scheduler,
                (peer, loginId) -> peer.request(
                        CANCEL_METHOD, Map.of("loginId", loginId)));
    }

    CodexLoginCoordinator(
            ConnectionProvider connectionProvider,
            RuntimeSystemConfigService systemConfigService,
            ScheduledExecutorService scheduler,
            CancelRequester cancelRequester) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.systemConfigService = Objects.requireNonNull(systemConfigService, "systemConfigService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.cancelRequester = Objects.requireNonNull(cancelRequester, "cancelRequester");
    }

    /** 启动或复用当前登录 attempt; 所有故障折叠为安全 FAILED 快照. */
    public CompletionStage<LoginSnapshot> start() {
        Attempt created;
        CompletableFuture<LoginSnapshot> result;
        Finish failed = null;
        synchronized (lock) {
            if (shutdown) {
                return CompletableFuture.completedFuture(
                        new LoginSnapshot(LoginState.FAILED, null, null));
            }
            if (currentAttempt != null)
                return currentStart;

            created = new Attempt();
            result = new CompletableFuture<>();
            currentAttempt = created;
            currentStart = result;
            currentSnapshot = new LoginSnapshot(LoginState.STARTING, null, null);
            try {
                long configured = systemConfigService.getLong(
                        LOGIN_TIMEOUT_KEY, DEFAULT_LOGIN_TIMEOUT_MILLIS);
                long timeoutMillis = configured > 0L
                        ? configured
                        : DEFAULT_LOGIN_TIMEOUT_MILLIS;
                created.timeout = scheduler.schedule(
                        () -> expire(created), timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (RuntimeException failure) {
                failed = prepareFinishLocked(created, LoginState.FAILED);
            }
        }
        runFinish(failed);
        if (failed != null)
            return result;

        CompletionStage<CodexProcessManager.CodexConnection> connection;
        try {
            connection = connectionProvider.connection();
        } catch (RuntimeException failure) {
            finishAttempt(created, LoginState.FAILED);
            return result;
        }
        if (connection == null) {
            finishAttempt(created, LoginState.FAILED);
            return result;
        }
        connection.whenComplete((value, failure) -> {
            if (failure != null || value == null) {
                finishAttempt(created, LoginState.FAILED);
                return;
            }
            attachConnection(created, value);
        });
        return result;
    }

    /** 返回当前安全状态; 不读取 account 或本地 credential 文件. */
    public LoginSnapshot snapshot() {
        synchronized (lock) {
            return currentSnapshot;
        }
    }

    /** 取消当前 attempt; 无 attempt 时幂等返回 IDLE. */
    public CompletionStage<LoginSnapshot> cancel() {
        Finish finish;
        Attempt attempt;
        CodexProcessManager.CodexConnection connection;
        String loginId;
        boolean draining;
        synchronized (lock) {
            if (currentAttempt == null) {
                currentSnapshot = LoginSnapshot.idle();
                return CompletableFuture.completedFuture(currentSnapshot);
            }
            if (currentAttempt.draining) {
                return CompletableFuture.completedFuture(currentSnapshot);
            }
            attempt = currentAttempt;
            connection = attempt.connection;
            loginId = attempt.loginId;
            finish = preparePublicTerminalLocked(attempt, LoginState.CANCELED);
            draining = currentAttempt == attempt && attempt.draining;
        }
        runFinish(finish);
        if (draining && loginId != null) {
            cancelDrainingAttempt(attempt, connection, loginId);
        } else {
            bestEffortCancel(connection, loginId);
        }
        return CompletableFuture.completedFuture(finish.snapshot());
    }

    /** 取消当前 attempt, 并等待该 attempt 已无法迟到完成登录. */
    public CompletionStage<Void> cancelAndDrain() {
        Finish finish;
        Attempt attempt;
        CodexProcessManager.CodexConnection connection;
        String loginId;
        CompletableFuture<Void> drained;
        boolean draining;
        synchronized (lock) {
            if (currentAttempt == null) {
                currentSnapshot = LoginSnapshot.idle();
                return CompletableFuture.completedFuture(null);
            }
            if (currentAttempt.draining)
                return currentAttempt.drainResult;
            attempt = currentAttempt;
            connection = attempt.connection;
            loginId = attempt.loginId;
            finish = preparePublicTerminalLocked(attempt, LoginState.CANCELED);
            draining = currentAttempt == attempt && attempt.draining;
            drained = draining
                    ? attempt.drainResult
                    : CompletableFuture.completedFuture(null);
        }
        runFinish(finish);
        if (draining && loginId != null) {
            cancelDrainingAttempt(attempt, connection, loginId);
        }
        return drained;
    }

    /** 关闭当前 attempt 与唯一 timeout scheduler; 可重复调用. */
    @PreDestroy
    public void shutdown() {
        Finish finish = null;
        CodexProcessManager.CodexConnection connection = null;
        String loginId = null;
        JsonlRpcPeer peerToClose = null;
        synchronized (lock) {
            if (shutdown)
                return;
            shutdown = true;
            if (currentAttempt != null) {
                connection = currentAttempt.connection;
                loginId = currentAttempt.loginId;
                if (currentAttempt.startRequestPending || currentAttempt.draining) {
                    peerToClose = connection == null ? null : connection.peer();
                }
                finish = prepareFinishLocked(currentAttempt, LoginState.CANCELED);
            }
        }
        runFinish(finish);
        bestEffortCancel(connection, loginId);
        closePeer(peerToClose);
        scheduler.shutdownNow();
    }

    private void attachConnection(
            Attempt attempt, CodexProcessManager.CodexConnection connection) {
        JsonlRpcPeer peer = connection.peer();
        if (peer == null) {
            finishAttempt(attempt, LoginState.FAILED);
            return;
        }
        synchronized (lock) {
            if (!isCurrentLocked(attempt, LoginState.STARTING))
                return;
            attempt.connection = connection;
        }

        AutoCloseable subscription;
        try {
            subscription = peer.onNotification(
                    (method, params) -> onNotification(attempt, method, params));
        } catch (RuntimeException failure) {
            finishAttempt(attempt, LoginState.FAILED);
            return;
        }

        boolean accepted;
        synchronized (lock) {
            accepted = isCurrentLocked(attempt, LoginState.STARTING);
            if (accepted)
                attempt.notificationSubscription = subscription;
        }
        if (!accepted) {
            closeQuietly(subscription);
            return;
        }

        CompletionStage<Void> terminal;
        try {
            terminal = peer.terminal();
        } catch (RuntimeException failure) {
            finishAttempt(attempt, LoginState.FAILED);
            return;
        }
        if (terminal == null) {
            finishAttempt(attempt, LoginState.FAILED);
            return;
        }
        observeConnectionTerminal(connection, peer, terminal);

        synchronized (lock) {
            if (!isCurrentLocked(attempt, LoginState.STARTING))
                return;
            attempt.startRequestPending = true;
        }
        CompletionStage<JsonNode> startResponse;
        try {
            startResponse = peer.request(START_METHOD, Map.of("type", LOGIN_TYPE));
        } catch (RuntimeException failure) {
            settleFailedStartRequest(attempt);
            return;
        }
        if (startResponse == null) {
            settleFailedStartRequest(attempt);
            return;
        }
        startResponse.whenComplete((response, failure) -> {
            if (failure != null) {
                settleFailedStartRequest(attempt);
                return;
            }
            acceptStartResponse(attempt, connection, response);
        });
    }

    private void acceptStartResponse(
            Attempt attempt,
            CodexProcessManager.CodexConnection connection,
            JsonNode response) {
        StartResponse validated = validateStartResponse(response);
        String recoverableLoginId = response != null && response.isObject()
                ? optionalSafeLoginId(response.get("loginId"))
                : null;

        JsonNode matchingCompletion;
        CompletableFuture<LoginSnapshot> startResult;
        LoginSnapshot pending;
        boolean accepted;
        boolean draining;
        boolean completeDrainingWithoutLoginId;
        Finish invalidFinish = null;
        synchronized (lock) {
            if (currentAttempt != attempt)
                return;
            attempt.startRequestPending = false;
            draining = attempt.draining;
            accepted = !draining
                    && validated != null
                    && isCurrentLocked(attempt, LoginState.STARTING);
            if (accepted) {
                attempt.loginId = validated.loginId();
                matchingCompletion = attempt.earlyCompletions.remove(attempt.loginId);
                attempt.earlyCompletions.clear();
                pending = new LoginSnapshot(
                        LoginState.PENDING,
                        validated.verificationUrl(),
                        validated.userCode());
                currentSnapshot = pending;
                startResult = currentStart;
            } else if (!draining && validated == null && recoverableLoginId != null) {
                invalidFinish = prepareDrainLocked(attempt, LoginState.FAILED);
                matchingCompletion = null;
                pending = null;
                startResult = null;
                draining = true;
            } else {
                matchingCompletion = null;
                pending = null;
                startResult = null;
            }
            completeDrainingWithoutLoginId = draining && recoverableLoginId == null;
        }
        runFinish(invalidFinish);
        if (!accepted) {
            if (recoverableLoginId != null) {
                cancelDrainingAttempt(attempt, connection, recoverableLoginId);
            } else if (completeDrainingWithoutLoginId) {
                completeDrainResult(attempt);
            } else if (!draining) {
                finishAttempt(attempt, LoginState.FAILED);
            }
            return;
        }
        startResult.complete(pending);
        if (matchingCompletion != null)
            applyCompletion(attempt, matchingCompletion);
    }

    private void onNotification(Attempt attempt, String method, JsonNode params) {
        if (!COMPLETED_METHOD.equals(method) || params == null || !params.isObject())
            return;

        Finish overflow = null;
        synchronized (lock) {
            if (!isCurrentLocked(attempt, LoginState.STARTING, LoginState.PENDING))
                return;
            if (attempt.loginId == null) {
                String completedLoginId = optionalSafeLoginId(params.get("loginId"));
                if (completedLoginId == null)
                    return;
                if (!attempt.earlyCompletions.containsKey(completedLoginId)
                        && attempt.earlyCompletions.size() == MAX_EARLY_COMPLETIONS) {
                    overflow = preparePublicTerminalLocked(attempt, LoginState.FAILED);
                } else {
                    attempt.earlyCompletions.putIfAbsent(completedLoginId, params.deepCopy());
                }
            }
        }
        if (overflow != null) {
            runFinish(overflow);
            return;
        }
        applyCompletion(attempt, params);
    }

    private void applyCompletion(Attempt attempt, JsonNode params) {
        Finish finish = null;
        synchronized (lock) {
            if (!isCurrentLocked(attempt, LoginState.PENDING))
                return;
            String completedLoginId = optionalSafeLoginId(params.get("loginId"));
            if (!Objects.equals(attempt.loginId, completedLoginId))
                return;
            JsonNode success = params.get("success");
            if (success == null || !success.isBoolean())
                return;
            finish = prepareFinishLocked(
                    attempt, success.booleanValue() ? LoginState.SUCCEEDED : LoginState.FAILED);
        }
        runFinish(finish);
    }

    private void expire(Attempt attempt) {
        Finish finish;
        CodexProcessManager.CodexConnection connection;
        String loginId;
        boolean draining;
        synchronized (lock) {
            if (!isCurrentLocked(attempt, LoginState.STARTING, LoginState.PENDING))
                return;
            connection = attempt.connection;
            loginId = attempt.loginId;
            finish = preparePublicTerminalLocked(attempt, LoginState.EXPIRED);
            draining = currentAttempt == attempt && attempt.draining;
        }
        runFinish(finish);
        if (draining && loginId != null) {
            cancelDrainingAttempt(attempt, connection, loginId);
        } else {
            bestEffortCancel(connection, loginId);
        }
    }

    private void finishAttempt(Attempt attempt, LoginState state) {
        Finish finish;
        synchronized (lock) {
            finish = preparePublicTerminalLocked(attempt, state);
        }
        runFinish(finish);
    }

    private void settleFailedStartRequest(Attempt attempt) {
        Finish finish = null;
        DrainCompletion drain = null;
        synchronized (lock) {
            if (currentAttempt != attempt)
                return;
            attempt.startRequestPending = false;
            if (attempt.draining) {
                drain = prepareDrainCompletionLocked(attempt);
            } else {
                finish = prepareFinishLocked(attempt, LoginState.FAILED);
            }
        }
        runDrainCompletion(drain);
        runFinish(finish);
    }

    private Finish preparePublicTerminalLocked(Attempt attempt, LoginState state) {
        if (currentAttempt != attempt)
            return null;
        if (attempt.draining)
            return null;
        if (attempt.startRequestPending
                || (currentSnapshot.state() == LoginState.PENDING && attempt.loginId != null)) {
            return prepareDrainLocked(attempt, state);
        }
        return prepareFinishLocked(attempt, state);
    }

    private Finish prepareDrainLocked(Attempt attempt, LoginState state) {
        if (currentAttempt != attempt || attempt.draining)
            return null;
        LoginSnapshot terminalSnapshot = new LoginSnapshot(
                state, currentSnapshot.verificationUrl(), currentSnapshot.userCode());
        CompletableFuture<LoginSnapshot> incompleteStart = currentStart;
        AutoCloseable subscription = attempt.notificationSubscription;
        ScheduledFuture<?> timeout = attempt.timeout;
        attempt.earlyCompletions.clear();
        attempt.notificationSubscription = null;
        attempt.timeout = null;
        attempt.draining = true;
        attempt.drainResult = new CompletableFuture<>();
        attempt.drainTimeout = scheduler.schedule(
                () -> expireDrain(attempt), START_DRAIN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        currentSnapshot = terminalSnapshot;
        if (incompleteStart.isDone()) {
            currentStart = CompletableFuture.completedFuture(terminalSnapshot);
        }
        return new Finish(subscription, timeout, incompleteStart, terminalSnapshot, null);
    }

    private void expireDrain(Attempt attempt) {
        JsonlRpcPeer peer;
        DrainCompletion failure;
        synchronized (lock) {
            if (currentAttempt != attempt || !attempt.draining)
                return;
            peer = attempt.connection == null ? null : attempt.connection.peer();
            attempt.drainFailed = true;
            attempt.drainInvalidating = true;
            failure = new DrainCompletion(null, attempt.drainResult, false);
        }
        runDrainCompletion(failure);
        closePeer(peer);
        DrainCompletion cleanup;
        synchronized (lock) {
            if (currentAttempt == attempt)
                attempt.drainInvalidating = false;
            cleanup = prepareDrainCompletionLocked(attempt);
        }
        runDrainCompletion(cleanup);
    }

    private void completeDrainResult(Attempt attempt) {
        DrainCompletion completion;
        synchronized (lock) {
            if (currentAttempt != attempt || !attempt.draining)
                return;
            completion = new DrainCompletion(null, attempt.drainResult, !attempt.drainFailed);
        }
        runDrainCompletion(completion);
    }

    private DrainCompletion prepareDrainCompletionLocked(Attempt attempt) {
        if (currentAttempt != attempt || !attempt.draining || attempt.drainInvalidating) {
            return null;
        }
        if (attempt.cancelRequestStarted
                && !attempt.cancelRequestSettled
                && !attempt.drainFailed) {
            return null;
        }
        ScheduledFuture<?> drainTimeout = attempt.drainTimeout;
        CompletableFuture<Void> drainResult = attempt.drainResult;
        boolean succeeded = !attempt.drainFailed
                && (!attempt.cancelRequestStarted || attempt.cancelRequestSucceeded);
        attempt.drainTimeout = null;
        attempt.drainResult = null;
        attempt.connection = null;
        attempt.loginId = null;
        attempt.cancelRequestStarted = false;
        attempt.cancelRequestSettled = false;
        attempt.cancelRequestSucceeded = false;
        attempt.drainFailed = false;
        attempt.connectionTerminated = false;
        attempt.draining = false;
        attempt.drainInvalidating = false;
        currentAttempt = null;
        currentStart = null;
        return new DrainCompletion(drainTimeout, drainResult, succeeded);
    }

    private Finish prepareFinishLocked(Attempt attempt, LoginState state) {
        if (currentAttempt != attempt)
            return null;
        LoginState previousState = currentSnapshot.state();
        LoginSnapshot terminalSnapshot = new LoginSnapshot(
                state, currentSnapshot.verificationUrl(), currentSnapshot.userCode());
        CompletableFuture<LoginSnapshot> incompleteStart = previousState == LoginState.STARTING ? currentStart : null;
        AutoCloseable subscription = attempt.notificationSubscription;
        ScheduledFuture<?> timeout = attempt.timeout;
        ScheduledFuture<?> drainTimeout = attempt.drainTimeout;
        CompletableFuture<Void> failedDrain = attempt.draining ? attempt.drainResult : null;

        if (attempt.draining)
            attempt.drainFailed = true;

        attempt.earlyCompletions.clear();
        attempt.notificationSubscription = null;
        attempt.timeout = null;
        attempt.drainTimeout = null;
        attempt.drainResult = null;
        attempt.startRequestPending = false;
        attempt.cancelRequestStarted = false;
        attempt.cancelRequestSettled = false;
        attempt.cancelRequestSucceeded = false;
        attempt.drainFailed = false;
        attempt.connectionTerminated = false;
        attempt.draining = false;
        attempt.drainInvalidating = false;
        attempt.connection = null;
        attempt.loginId = null;
        currentSnapshot = terminalSnapshot;
        currentAttempt = null;
        currentStart = null;
        cancelScheduled(drainTimeout);
        return new Finish(subscription, timeout, incompleteStart, terminalSnapshot, failedDrain);
    }

    private static void runFinish(Finish finish) {
        if (finish == null)
            return;
        closeQuietly(finish.subscription());
        if (finish.timeout() != null)
            finish.timeout().cancel(false);
        if (finish.incompleteStart() != null) {
            finish.incompleteStart().complete(finish.snapshot());
        }
        if (finish.failedDrain() != null) {
            finish.failedDrain().completeExceptionally(drainFailure());
        }
    }

    private static void runDrainCompletion(DrainCompletion drain) {
        if (drain == null)
            return;
        cancelScheduled(drain.timeout());
        if (drain.succeeded()) {
            drain.result().complete(null);
        } else {
            drain.result().completeExceptionally(drainFailure());
        }
    }

    private static IllegalStateException drainFailure() {
        return new IllegalStateException("Codex login cancellation did not converge");
    }

    private static void bestEffortCancel(
            CodexProcessManager.CodexConnection connection, String loginId) {
        if (connection == null || loginId == null)
            return;
        try {
            CompletionStage<JsonNode> cancellation = connection.peer().request(
                    CANCEL_METHOD, Map.of("loginId", loginId));
            if (cancellation != null)
                cancellation.exceptionally(ignored -> null);
        } catch (RuntimeException ignored) {
            // Cancellation is cleanup only; RPC details must not escape this boundary.
        }
    }

    private void cancelDrainingAttempt(
            Attempt attempt,
            CodexProcessManager.CodexConnection connection,
            String loginId) {
        synchronized (lock) {
            if (currentAttempt != attempt
                    || !attempt.draining
                    || attempt.cancelRequestStarted) {
                return;
            }
            attempt.cancelRequestStarted = true;
            attempt.cancelRequestSettled = false;
            attempt.cancelRequestSucceeded = false;
        }
        CompletionStage<JsonNode> cancellation;
        try {
            cancellation = connection == null || connection.peer() == null
                    ? null
                    : cancelRequester.cancel(connection.peer(), loginId);
        } catch (RuntimeException failure) {
            failDrain(attempt);
            return;
        }
        if (cancellation == null) {
            failDrain(attempt);
            return;
        }
        cancellation.whenComplete((result, failure) -> {
            settleCancelRequest(attempt, failure == null && result != null);
        });
    }

    private void failDrain(Attempt attempt) {
        settleCancelRequest(attempt, false);
    }

    private void settleCancelRequest(Attempt attempt, boolean succeeded) {
        DrainCompletion drain;
        synchronized (lock) {
            if (currentAttempt != attempt || !attempt.draining)
                return;
            attempt.cancelRequestSettled = true;
            attempt.cancelRequestSucceeded = succeeded;
            if (!succeeded)
                attempt.drainFailed = true;
            if (succeeded || attempt.connectionTerminated) {
                drain = prepareDrainCompletionLocked(attempt);
            } else {
                drain = new DrainCompletion(null, attempt.drainResult, false);
            }
        }
        runDrainCompletion(drain);
    }

    private void observeConnectionTerminal(
            CodexProcessManager.CodexConnection connection,
            JsonlRpcPeer peer,
            CompletionStage<Void> terminal) {
        boolean register;
        synchronized (lock) {
            register = observedTerminalGeneration != connection.generation()
                    || observedTerminalPeer != peer;
            if (register) {
                observedTerminalGeneration = connection.generation();
                observedTerminalPeer = peer;
            }
        }
        if (register) {
            terminal.whenComplete((ignored, failure) -> onConnectionTerminal(connection.generation(), peer));
        }
    }

    private void onConnectionTerminal(long generation, JsonlRpcPeer peer) {
        Finish finish = null;
        DrainCompletion drain = null;
        synchronized (lock) {
            Attempt attempt = currentAttempt;
            if (attempt == null
                    || attempt.connection == null
                    || attempt.connection.generation() != generation
                    || attempt.connection.peer() != peer) {
                return;
            }
            attempt.startRequestPending = false;
            attempt.connectionTerminated = true;
            if (attempt.draining) {
                drain = prepareDrainCompletionLocked(attempt);
            } else {
                finish = prepareFinishLocked(attempt, LoginState.FAILED);
            }
        }
        runDrainCompletion(drain);
        runFinish(finish);
    }

    private static void closePeer(JsonlRpcPeer peer) {
        if (peer == null)
            return;
        try {
            peer.close();
        } catch (RuntimeException ignored) {
            // Closing a draining connection is cleanup only.
        }
    }

    private static void cancelScheduled(ScheduledFuture<?> task) {
        if (task != null)
            task.cancel(false);
    }

    private boolean isCurrentLocked(Attempt attempt, LoginState... states) {
        if (currentAttempt != attempt)
            return false;
        LoginState current = currentSnapshot.state();
        for (LoginState state : states) {
            if (current == state)
                return true;
        }
        return false;
    }

    private static StartResponse validateStartResponse(JsonNode response) {
        if (response == null || !response.isObject())
            return null;
        String type = optionalText(response.get("type"));
        String loginId = optionalSafeLoginId(response.get("loginId"));
        String userCode = optionalText(response.get("userCode"));
        String verificationUrl = optionalText(response.get("verificationUrl"));
        if (!LOGIN_TYPE.equals(type)
                || loginId == null
                || userCode == null
                || userCode.isBlank()
                || userCode.length() > 128
                || !isSafeVerificationUrl(verificationUrl)) {
            return null;
        }
        return new StartResponse(loginId, verificationUrl, userCode);
    }

    private static String optionalSafeLoginId(JsonNode value) {
        String loginId = optionalText(value);
        return loginId == null || loginId.isBlank() || loginId.length() > 512
                ? null
                : loginId;
    }

    private static String optionalText(JsonNode value) {
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static boolean isSafeVerificationUrl(String value) {
        if (value == null)
            return false;
        try {
            URI uri = new URI(value);
            return uri.isAbsolute()
                    && "https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (URISyntaxException failure) {
            return false;
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null)
            return;
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Listener cleanup is local and contains no useful user-facing detail.
        }
    }

    public enum LoginState {
        IDLE, STARTING, PENDING, SUCCEEDED, FAILED, EXPIRED, CANCELED
    }

    public record LoginSnapshot(
            LoginState state,
            String verificationUrl,
            String userCode) {
        static LoginSnapshot idle() {
            return new LoginSnapshot(LoginState.IDLE, null, null);
        }
    }

    private record StartResponse(String loginId, String verificationUrl, String userCode) {
    }

    private record Finish(
            AutoCloseable subscription,
            ScheduledFuture<?> timeout,
            CompletableFuture<LoginSnapshot> incompleteStart,
            LoginSnapshot snapshot,
            CompletableFuture<Void> failedDrain) {
    }

    private record DrainCompletion(
            ScheduledFuture<?> timeout,
            CompletableFuture<Void> result,
            boolean succeeded) {
    }

    private static final class Attempt {
        private String loginId;
        private final Map<String, JsonNode> earlyCompletions = new LinkedHashMap<>();
        private AutoCloseable notificationSubscription;
        private ScheduledFuture<?> timeout;
        private ScheduledFuture<?> drainTimeout;
        private CompletableFuture<Void> drainResult;
        private CodexProcessManager.CodexConnection connection;
        private boolean startRequestPending;
        private boolean cancelRequestStarted;
        private boolean cancelRequestSettled;
        private boolean cancelRequestSucceeded;
        private boolean drainFailed;
        private boolean connectionTerminated;
        private boolean draining;
        private boolean drainInvalidating;

    }
}
