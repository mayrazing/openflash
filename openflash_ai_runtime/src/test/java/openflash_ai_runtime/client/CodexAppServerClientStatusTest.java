package openflash_ai_runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import openflash_ai_runtime.common.CodexLogCapture;
import openflash_ai_runtime.service.RuntimeSystemConfigService;
import openflash_ai_runtime.support.CodexProcessManager;
import org.junit.jupiter.api.Test;

class CodexAppServerClientStatusTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INJECTED_SECRET =
            "secret@example.com /private/account/path raw-stderr-token";

    @Test
    void mapsAccountAndAuthRequirementWithoutReturningEmail() {
        assertStatus("{\"account\":null,\"requiresOpenaiAuth\":true}",
                CodexAppServerClient.StatusCode.NOT_LOGGED_IN);
        assertStatus("{\"account\":null,\"requiresOpenaiAuth\":false}",
                CodexAppServerClient.StatusCode.AVAILABLE);

        CodexAppServerClient.StatusResponse response = assertStatus(
                "{\"account\":{\"type\":\"chatgpt\",\"email\":\"secret@example.com\"},"
                        + "\"requiresOpenaiAuth\":true,\"unknownFutureField\":1}",
                CodexAppServerClient.StatusCode.AVAILABLE);
        assertFalse(response.toString().contains("secret@example.com"));
    }

    @Test
    void mapsSpawnIOExceptionToNotInstalledAndOtherFailuresToError() {
        try (CodexLogCapture logs = CodexLogCapture.capture(CodexAppServerClient.class)) {
            assertEquals(CodexAppServerClient.StatusCode.NOT_INSTALLED,
                    client(failed(new CodexProcessManager.CodexNotInstalledException()))
                            .status().status());
            assertEquals(CodexAppServerClient.StatusCode.ERROR,
                    client(failed(new IllegalStateException(INJECTED_SECRET)))
                            .status().status());
            assertEquals(CodexAppServerClient.StatusCode.ERROR,
                    client(completed((method, params) -> failed(new IllegalStateException("account failed"))))
                            .status().status());

            assertStatusWarnings(logs, 2);
        }
    }

    @Test
    void accountReadDisablesRefreshAndUsesConfiguredDeadline() {
        RecordingRpc rpc = new RecordingRpc(json(
                "{\"account\":null,\"requiresOpenaiAuth\":false}"));
        RuntimeSystemConfigService config = mock(RuntimeSystemConfigService.class);
        when(config.getLong("ai.codex-status-timeout-millis", 5000L)).thenReturn(100L);
        CodexAppServerClient client = new CodexAppServerClient(
                () -> CompletableFuture.completedFuture(rpc), new CodexModelCatalog(), config);

        assertEquals(CodexAppServerClient.StatusCode.AVAILABLE, client.status().status());
        assertEquals("account/read", rpc.method);
        assertEquals(Map.of("refreshToken", false), rpc.params);
        verify(config).getLong("ai.codex-status-timeout-millis", 5000L);
    }

    @Test
    void accountLogoutUsesNativeRpcWithNullParams() {
        RecordingRpc rpc = new RecordingRpc(json("{}"));

        client(completed(rpc)).logoutAccount().toCompletableFuture().join();

        assertEquals("account/logout", rpc.method);
        assertNull(rpc.params);
    }

    @Test
    void timeoutAndMalformedAccountResponseBecomeSafeError() {
        RuntimeSystemConfigService config = mock(RuntimeSystemConfigService.class);
        when(config.getLong("ai.codex-status-timeout-millis", 5000L)).thenReturn(20L);
        CodexAppServerClient timeoutClient = new CodexAppServerClient(
                () -> new CompletableFuture<>(), new CodexModelCatalog(), config);

        try (CodexLogCapture logs = CodexLogCapture.capture(CodexAppServerClient.class)) {
            assertEquals(CodexAppServerClient.StatusCode.ERROR, timeoutClient.status().status());
            assertEquals(CodexAppServerClient.StatusCode.ERROR,
                    client(completed((method, params) -> CompletableFuture.completedFuture(
                            json("{\"account\":null}")))).status().status());

            assertStatusWarnings(logs, 2);
        }
    }

    @Test
    void timeoutCancelsPendingConnectionAndInnerAccountRead() {
        RuntimeSystemConfigService config = mock(RuntimeSystemConfigService.class);
        when(config.getLong("ai.codex-status-timeout-millis", 5000L)).thenReturn(20L);
        CompletableFuture<CodexModelCatalog.Rpc> pendingConnection = new CompletableFuture<>();

        CodexAppServerClient waitingForConnection = new CodexAppServerClient(
                () -> pendingConnection, new CodexModelCatalog(), config);
        assertEquals(CodexAppServerClient.StatusCode.ERROR, waitingForConnection.status().status());
        assertTrue(pendingConnection.isCancelled());

        CompletableFuture<JsonNode> accountRead = new CompletableFuture<>();
        CodexAppServerClient waitingForRpc = new CodexAppServerClient(
                () -> CompletableFuture.completedFuture((method, params) -> accountRead),
                new CodexModelCatalog(), config);
        assertEquals(CodexAppServerClient.StatusCode.ERROR, waitingForRpc.status().status());
        assertTrue(accountRead.isCancelled());
    }

    @Test
    void callerInterruptionCancelsInnerAccountReadAndRestoresInterruptFlag() throws Exception {
        RuntimeSystemConfigService config = mock(RuntimeSystemConfigService.class);
        when(config.getLong("ai.codex-status-timeout-millis", 5000L)).thenReturn(5000L);
        CompletableFuture<JsonNode> accountRead = new CompletableFuture<>();
        CountDownLatch requestStarted = new CountDownLatch(1);
        CodexAppServerClient client = new CodexAppServerClient(
                () -> CompletableFuture.completedFuture((method, params) -> {
                    requestStarted.countDown();
                    return accountRead;
                }), new CodexModelCatalog(), config);
        AtomicReference<CodexAppServerClient.StatusResponse> response = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            response.set(client.status());
            interruptRestored.set(Thread.currentThread().isInterrupted());
        });

        caller.start();
        assertTrue(requestStarted.await(1, TimeUnit.SECONDS));
        caller.interrupt();
        caller.join(1000L);

        assertFalse(caller.isAlive());
        assertEquals(CodexAppServerClient.StatusCode.ERROR, response.get().status());
        assertTrue(accountRead.isCancelled());
        assertTrue(interruptRestored.get());
    }

    @Test
    void configReadFailureAlsoBecomesSafeError() {
        RuntimeSystemConfigService config = mock(RuntimeSystemConfigService.class);
        when(config.getLong("ai.codex-status-timeout-millis", 5000L))
                .thenThrow(new IllegalStateException("jdbc://private/path"));
        CodexAppServerClient client = new CodexAppServerClient(
                () -> {
                    throw new AssertionError("connection must not start when config read fails");
                },
                new CodexModelCatalog(),
                config);

        try (CodexLogCapture logs = CodexLogCapture.capture(CodexAppServerClient.class)) {
            assertEquals(CodexAppServerClient.StatusCode.ERROR, client.status().status());
            assertStatusWarnings(logs, 1);
        }
    }

    private static void assertStatusWarnings(CodexLogCapture logs, int expectedCount) {
        assertEquals(expectedCount, logs.events().size());
        logs.events().forEach(warning -> {
            assertEquals(Level.WARN, warning.getLevel());
            assertEquals("Codex app-server status check failed", warning.getFormattedMessage());
            assertTrue(warning.getKeyValuePairs().stream()
                    .anyMatch(value -> "event".equals(value.key)
                            && "codex_status_failure".equals(value.value)));
            assertTrue(warning.getKeyValuePairs().stream()
                    .anyMatch(value -> "failure_type".equals(value.key)
                            && Set.of(
                                            "InterruptedException",
                                            "TimeoutException",
                                            "ExecutionException",
                                            "RuntimeException",
                                            "Throwable")
                                    .contains(value.value)));
            assertNull(warning.getThrowableProxy());
            assertFalse(render(warning).contains(INJECTED_SECRET));
        });
    }

    private static String render(ILoggingEvent event) {
        PatternLayout layout = new PatternLayout();
        layout.setContext(((ch.qos.logback.classic.Logger)
                        org.slf4j.LoggerFactory.getLogger(CodexAppServerClient.class))
                .getLoggerContext());
        layout.setPattern("%msg %kvp %ex");
        layout.start();
        try {
            return layout.doLayout(event);
        } finally {
            layout.stop();
        }
    }

    private static CodexAppServerClient.StatusResponse assertStatus(
            String responseJson, CodexAppServerClient.StatusCode expected) {
        RecordingRpc rpc = new RecordingRpc(json(responseJson));
        CodexAppServerClient.StatusResponse response = client(completed(rpc)).status();
        assertEquals(expected, response.status());
        return response;
    }

    private static CodexAppServerClient client(
            CompletionStage<CodexModelCatalog.Rpc> connection) {
        RuntimeSystemConfigService config = mock(RuntimeSystemConfigService.class);
        when(config.getLong("ai.codex-status-timeout-millis", 5000L)).thenReturn(100L);
        return new CodexAppServerClient(() -> connection, new CodexModelCatalog(), config);
    }

    private static CompletionStage<CodexModelCatalog.Rpc> completed(CodexModelCatalog.Rpc rpc) {
        return CompletableFuture.completedFuture(rpc);
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private static JsonNode json(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class RecordingRpc implements CodexModelCatalog.Rpc {
        private final JsonNode response;
        private String method;
        private Map<String, Object> params;

        private RecordingRpc(JsonNode response) {
            this.response = response;
        }

        @Override
        public CompletionStage<JsonNode> request(String method, Map<String, Object> params) {
            this.method = method;
            this.params = params == null ? null : Map.copyOf(params);
            return CompletableFuture.completedFuture(response);
        }
    }
}
