package openflash_ai_runtime.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import openflash_ai_runtime.common.AiErrorCode;
import openflash_ai_runtime.common.CodexAppException;
import openflash_ai_runtime.common.CodexLogCapture;
import openflash_ai_runtime.dto.GenerationProfile;
import openflash_ai_runtime.service.RuntimeSystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexAppServerClientGenerationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TRANSPORT_GUARD = "Return only the requested text. Do not call tools, commands, web, apps, plugins, skills, or subagents.";
    private static final String BASE_INSTRUCTIONS = "You are a plain text generation assistant. "
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

    @TempDir
    Path tempDirectory;

    @Test
    void sendsExactThreadAndTurnWireAfterRegisteringCollector() throws Exception {
        FakeConnection connection = successfulConnection();
        CodexAppServerClient client = client(connection, ownedDirectories(tempDirectory));
        GenerationProfile profile = profile("gpt-5.4", "existing business system prompt");

        assertEquals("answer", client.generate("apple", profile));

        Map<String, Object> thread = connection.only("thread/start").params();
        assertEquals("gpt-5.4", thread.get("model"));
        Path cwd = Path.of((String) thread.get("cwd"));
        assertTrue(cwd.isAbsolute());
        assertTrue(connection.cwdWasInitiallyEmpty);
        assertEquals("never", thread.get("approvalPolicy"));
        assertEquals("read-only", thread.get("sandbox"));
        assertEquals(true, thread.get("ephemeral"));
        assertEquals("none", thread.get("personality"));
        assertEquals(BASE_INSTRUCTIONS, thread.get("baseInstructions"));
        assertEquals(
                TRANSPORT_GUARD + "\n\nexisting business system prompt",
                thread.get("developerInstructions"));
        assertEquals(CLEAN_THREAD_CONFIG, thread.get("config"));
        assertEquals(Set.of(
                "model",
                "cwd",
                "approvalPolicy",
                "sandbox",
                "ephemeral",
                "personality",
                "baseInstructions",
                "developerInstructions",
                "config"), thread.keySet());
        assertFalse(thread.containsKey("dynamicTools"));
        assertFalse(thread.containsKey("environments"));
        assertFalse(thread.containsKey("apiKey"));
        assertFalse(thread.containsKey("temperature"));
        assertFalse(thread.keySet().stream()
                .anyMatch(key -> key.toLowerCase().contains("account")));
        assertTrue(thread.values().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(value -> value.startsWith("/"))
                .allMatch(cwd.toString()::equals));
        assertFalse(thread.toString().contains("apple"));

        Request turnStart = connection.only("turn/start");
        assertEquals(Map.of(
                "threadId", "thread-1",
                "input", List.of(Map.of("type", "text", "text", "apple")),
                "effort", "low"), turnStart.params());
        assertTrue(connection.listenerPresentAtTurnStart);
        assertEquals(Map.of("threadId", "thread-1"), connection.only("thread/unsubscribe").params());
        assertFalse(Files.exists(cwd));
    }

    @Test
    void sendsSelectedReasoningEffortToTurnStart() {
        FakeConnection connection = successfulConnection();
        CodexAppServerClient client = client(connection, ownedDirectories(tempDirectory));

        assertEquals("answer", client.generate("apple", profile("gpt-5.4", "system"), "high"));

        assertEquals("high", connection.only("turn/start").params().get("effort"));
    }

    @Test
    void blankProfileSystemStillSendsGuardAndNeverStringNull() {
        FakeConnection connection = successfulConnection();
        CodexAppServerClient client = client(connection, ownedDirectories(tempDirectory));

        assertEquals("answer", client.generate("apple", profile("gpt-5.4", "  ")));

        Map<String, Object> params = connection.only("thread/start").params();
        assertEquals(TRANSPORT_GUARD, params.get("developerInstructions"));
        assertFalse(params.toString().contains("null"));
    }

    @Test
    void eachRequestGetsUniqueOwnedEmptyDirectoryAndDeletesOnlyIt() throws Exception {
        Path sentinel = Files.writeString(tempDirectory.resolve("keep.txt"), "keep");
        OwnedDirectoryFactory directories = ownedDirectories(tempDirectory);
        FakeConnection first = successfulConnection();
        FakeConnection second = successfulConnection();
        AtomicInteger call = new AtomicInteger();
        CodexAppServerClient client = client(
                () -> CompletableFuture.completedFuture(call.getAndIncrement() == 0 ? first : second),
                directories);

        client.generate("one", profile("gpt-5.4", null));
        client.generate("two", profile("gpt-5.4", null));

        Path firstCwd = Path.of((String) first.only("thread/start").params().get("cwd"));
        Path secondCwd = Path.of((String) second.only("thread/start").params().get("cwd"));
        assertNotEquals(firstCwd, secondCwd);
        assertFalse(Files.exists(firstCwd));
        assertFalse(Files.exists(secondCwd));
        assertTrue(Files.exists(sentinel));
    }

    @Test
    void totalDeadlineIncludesConnectionAcquire() {
        CodexAppServerClient client = client(
                () -> new CompletableFuture<>(), ownedDirectories(tempDirectory), 35L, 120000L, 10L, 80L);

        assertCode(AiErrorCode.AI_INTERRUPTED,
                () -> client.generate("apple", profile("gpt-5.4", null)));
    }

    @Test
    void exhaustedDeadlineDoesNotStartNextPhase() {
        AtomicInteger acquires = new AtomicInteger();
        OwnedDirectoryFactory slowDirectory = () -> {
            java.util.concurrent.locks.LockSupport.parkNanos(45_000_000L);
            return Files.createDirectory(tempDirectory.resolve("slow-request"));
        };
        CodexAppServerClient client = client(
                () -> {
                    acquires.incrementAndGet();
                    return new CompletableFuture<>();
                }, slowDirectory, 20L, 120000L, 10L, 80L);

        assertCode(AiErrorCode.AI_INTERRUPTED,
                () -> client.generate("apple", profile("gpt-5.4", null)));
        assertEquals(0, acquires.get());
    }

    @Test
    void generationTimeoutFallsBackAndClampsBelowCurrentLease() {
        RuntimeSystemConfigService config = mock(RuntimeSystemConfigService.class);
        when(config.getLong("ai.codex-timeout-millis", 90000L)).thenReturn(-1L);
        when(config.getLong("async-task.lease-millis", 120000L)).thenReturn(120000L);
        CodexAppServerClient fallback = new CodexAppServerClient(
                () -> new CompletableFuture<>(), new CodexModelCatalog(), config,
                ownedDirectories(tempDirectory), 10L, 80L);
        assertEquals(90000L, fallback.resolveGenerationTimeoutMillis());

        when(config.getLong("ai.codex-timeout-millis", 90000L)).thenReturn(90000L);
        when(config.getLong("async-task.lease-millis", 120000L)).thenReturn(10000L);
        CodexAppServerClient clamped = new CodexAppServerClient(
                () -> new CompletableFuture<>(), new CodexModelCatalog(), config,
                ownedDirectories(tempDirectory), 10L, 80L);
        assertEquals(9000L, clamped.resolveGenerationTimeoutMillis());
        assertTrue(clamped.resolveGenerationTimeoutMillis() < config.getLong("async-task.lease-millis", 120000L));
    }

    @Test
    void zeroAndOneMillisecondLeaseFallBackBeforeTimeoutClamp() {
        RuntimeSystemConfigService config = mock(RuntimeSystemConfigService.class);
        when(config.getLong("ai.codex-timeout-millis", 90000L)).thenReturn(500000L);
        CodexAppServerClient client = new CodexAppServerClient(
                () -> new CompletableFuture<>(), new CodexModelCatalog(), config,
                ownedDirectories(tempDirectory), 10L, 80L);

        when(config.getLong("async-task.lease-millis", 120000L)).thenReturn(0L, 1L);

        assertEquals(115000L, client.resolveGenerationTimeoutMillis());
        assertEquals(115000L, client.resolveGenerationTimeoutMillis());
    }

    @Test
    void resolvedTimeoutAlwaysLeavesPositiveLeaseSafetyMargin() {
        RuntimeSystemConfigService config = mock(RuntimeSystemConfigService.class);
        when(config.getLong("ai.codex-timeout-millis", 90000L)).thenReturn(Long.MAX_VALUE);
        CodexAppServerClient client = new CodexAppServerClient(
                () -> new CompletableFuture<>(), new CodexModelCatalog(), config,
                ownedDirectories(tempDirectory), 10L, 80L);

        for (long lease : List.of(2L, 10L, 10000L, 120000L)) {
            when(config.getLong("async-task.lease-millis", 120000L)).thenReturn(lease);
            long timeout = client.resolveGenerationTimeoutMillis();
            assertTrue(timeout > 0L);
            assertTrue(timeout < lease, () -> "timeout=" + timeout + ", lease=" + lease);
        }
    }

    @Test
    void missingProfileOrModelFailsBeforeAcquire() {
        AtomicInteger acquires = new AtomicInteger();
        CodexAppServerClient client = client(
                () -> {
                    acquires.incrementAndGet();
                    return new CompletableFuture<>();
                }, ownedDirectories(tempDirectory));

        assertCode(AiErrorCode.AI_CODEX_SELECTION_INVALID,
                () -> client.generate("apple", null));
        assertCode(AiErrorCode.AI_CODEX_SELECTION_INVALID,
                () -> client.generate("apple", profile("  ", null)));
        assertEquals(0, acquires.get());
    }

    @Test
    void totalDeadlineIncludesThreadStart() throws Exception {
        CompletableFuture<JsonNode> neverThread = new CompletableFuture<>();
        FakeConnection connection = new FakeConnection(1);
        connection.handler = (method, params) -> {
            if ("thread/start".equals(method))
                return neverThread;
            throw new AssertionError("unexpected " + method);
        };
        CodexAppServerClient client = client(
                connection, ownedDirectories(tempDirectory), 35L, 120000L, 10L, 80L);

        try (CodexLogCapture logs = CodexLogCapture.capture(CodexAppServerClient.class)) {
            assertCode(AiErrorCode.AI_INTERRUPTED,
                    () -> client.generate("apple", profile("gpt-5.4", null)));
            assertEquals(0, connection.count("turn/start"));
            assertEventually(neverThread::isCancelled);
            assertWarningEvents(logs, "codex_late_event_sink_expired");
        }
    }

    @Test
    void threadStartTimeoutRetainsCwdUntilLateThreadIsUnsubscribedOrExpires() throws Exception {
        CompletableFuture<JsonNode> lateThread = new CompletableFuture<>();
        CompletableFuture<JsonNode> unsubscribe = new CompletableFuture<>();
        AtomicReference<Path> cwd = new AtomicReference<>();
        FakeConnection connection = new FakeConnection(2);
        connection.handler = (method, params) -> switch (method) {
            case "thread/start" -> {
                cwd.set(Path.of((String) params.get("cwd")));
                yield lateThread;
            }
            case "thread/unsubscribe" -> unsubscribe;
            default -> throw new AssertionError("unexpected " + method);
        };
        CodexAppServerClient client = client(
                connection, ownedDirectories(tempDirectory), 35L, 120000L, 10L, 100L);

        try (CodexLogCapture logs = CodexLogCapture.capture(CodexAppServerClient.class)) {
            assertCode(AiErrorCode.AI_INTERRUPTED,
                    () -> client.generate("apple", profile("gpt-5.4", null)));
            assertTrue(Files.exists(cwd.get()));
            assertFalse(lateThread.isCancelled());

            lateThread.complete(json("{\"thread\":{\"id\":\"thread-late\"}}"));
            assertEventually(() -> connection.count("thread/unsubscribe") == 1);
            assertEquals(Map.of("threadId", "thread-late"),
                    connection.only("thread/unsubscribe").params());
            assertTrue(Files.exists(cwd.get()));
            assertEventually(unsubscribe::isCancelled);
            assertEventually(() -> !Files.exists(cwd.get()));
            assertEquals(0, connection.listenerCount());
            assertEquals(1, connection.count("thread/unsubscribe"));
            assertEquals(Map.of("threadId", "thread-late"),
                    connection.only("thread/unsubscribe").params());
            assertOnlyOptionalWarningEvent(logs, "codex_late_event_sink_expired");
        }
    }

    @Test
    void threadStartTombstoneCancelsNeverResponseBeforeDeletingCwd() throws Exception {
        CompletableFuture<JsonNode> neverThread = new CompletableFuture<>();
        AtomicReference<Path> cwd = new AtomicReference<>();
        FakeConnection connection = new FakeConnection(3);
        connection.handler = (method, params) -> {
            if (!"thread/start".equals(method))
                throw new AssertionError("unexpected " + method);
            cwd.set(Path.of((String) params.get("cwd")));
            return neverThread;
        };
        CodexAppServerClient client = client(
                connection, ownedDirectories(tempDirectory), 35L, 120000L, 10L, 80L);

        try (CodexLogCapture logs = CodexLogCapture.capture(CodexAppServerClient.class)) {
            assertCode(AiErrorCode.AI_INTERRUPTED,
                    () -> client.generate("apple", profile("gpt-5.4", null)));
            assertTrue(Files.exists(cwd.get()));
            assertEventually(neverThread::isCancelled);
            assertFalse(Files.exists(cwd.get()));
            assertWarningEvents(logs, "codex_late_event_sink_expired");
        }
    }

    @Test
    void neverRespondingInterruptAndUnsubscribeStagesAreBoundedlyCancelled() throws Exception {
        CompletableFuture<JsonNode> interrupt = new CompletableFuture<>();
        CompletableFuture<JsonNode> unsubscribe = new CompletableFuture<>();
        AtomicReference<Path> cwd = new AtomicReference<>();
        FakeConnection connection = new FakeConnection(4);
        connection.handler = (method, params) -> switch (method) {
            case "thread/start" -> {
                cwd.set(Path.of((String) params.get("cwd")));
                yield completed("{\"thread\":{\"id\":\"thread-control\"}}");
            }
            case "turn/start" -> completed("{\"turn\":{\"id\":\"turn-control\"}}");
            case "turn/interrupt" -> interrupt;
            case "thread/unsubscribe" -> unsubscribe;
            default -> throw new AssertionError("unexpected " + method);
        };
        CodexAppServerClient client = client(
                connection, ownedDirectories(tempDirectory), 35L, 120000L, 10L, 80L);

        try (CodexLogCapture logs = CodexLogCapture.capture(CodexAppServerClient.class)) {
            assertCode(AiErrorCode.AI_INTERRUPTED,
                    () -> client.generate("apple", profile("gpt-5.4", null)));
            assertEventually(interrupt::isCancelled);
            assertEventually(() -> connection.count("thread/unsubscribe") == 1);
            assertEventually(unsubscribe::isCancelled);
            assertFalse(Files.exists(cwd.get()));
            assertWarningEvents(logs, "codex_late_event_sink_expired");
        }
    }

    @Test
    void successfulRequestBoundedlyCancelsNeverRespondingUnsubscribe() throws Exception {
        CompletableFuture<JsonNode> unsubscribe = new CompletableFuture<>();
        FakeConnection connection = successfulConnection();
        connection.handler = wrapUnsubscribe(connection.handler, unsubscribe);
        CodexAppServerClient client = client(
                connection, ownedDirectories(tempDirectory), 2000L, 120000L, 10L, 60L);

        assertEquals("answer", client.generate("apple", profile("gpt-5.4", null)));

        assertEventually(unsubscribe::isCancelled);
    }

    @Test
    void turnStartTimeoutLeavesBoundedTombstoneAndInterruptsLateTurn() throws Exception {
        CompletableFuture<JsonNode> lateTurn = new CompletableFuture<>();
        AtomicReference<Path> cwd = new AtomicReference<>();
        FakeConnection connection = new FakeConnection(4);
        connection.handler = (method, params) -> switch (method) {
            case "thread/start" -> {
                cwd.set(Path.of((String) params.get("cwd")));
                yield completed("{\"thread\":{\"id\":\"thread-late\"}}");
            }
            case "turn/start" -> lateTurn;
            case "turn/interrupt", "thread/unsubscribe" -> completed("{}");
            default -> throw new AssertionError("unexpected " + method);
        };
        CodexAppServerClient client = client(
                connection, ownedDirectories(tempDirectory), 40L, 120000L, 10L, 140L);

        assertCode(AiErrorCode.AI_INTERRUPTED,
                () -> client.generate("apple", profile("gpt-5.4", null)));
        assertEquals(1, connection.listenerCount());
        assertTrue(Files.exists(cwd.get()));

        lateTurn.complete(json("{\"turn\":{\"id\":\"turn-late\"}}"));
        assertEventually(() -> connection.count("turn/interrupt") == 1);
        assertEquals(Map.of("threadId", "thread-late", "turnId", "turn-late"),
                connection.only("turn/interrupt").params());
        connection.emit("turn/completed", json(
                "{\"threadId\":\"thread-late\",\"turn\":{\"id\":\"turn-late\","
                        + "\"status\":\"interrupted\",\"items\":[]}}"));
        assertEventually(() -> connection.listenerCount() == 0);
        assertFalse(Files.exists(cwd.get()));
    }

    @Test
    void unresolvedLateTurnTombstoneExpiresAndCleansLocalState() throws Exception {
        AtomicReference<Path> cwd = new AtomicReference<>();
        FakeConnection connection = new FakeConnection(5);
        connection.handler = (method, params) -> switch (method) {
            case "thread/start" -> {
                cwd.set(Path.of((String) params.get("cwd")));
                yield completed("{\"thread\":{\"id\":\"thread-never\"}}");
            }
            case "turn/start" -> new CompletableFuture<>();
            case "thread/unsubscribe" -> completed("{}");
            default -> throw new AssertionError("unexpected " + method);
        };
        CodexAppServerClient client = client(
                connection, ownedDirectories(tempDirectory), 35L, 120000L, 10L, 90L);

        try (CodexLogCapture logs = CodexLogCapture.capture(CodexAppServerClient.class)) {
            assertCode(AiErrorCode.AI_INTERRUPTED,
                    () -> client.generate("apple", profile("gpt-5.4", null)));
            assertEquals(1, connection.listenerCount());
            assertTrue(Files.exists(cwd.get()));
            assertEventually(() -> connection.listenerCount() == 0);
            assertFalse(Files.exists(cwd.get()));
            assertEquals(0, connection.count("turn/interrupt"));
            assertWarningEvents(logs, "codex_late_event_sink_expired");
        }
    }

    @Test
    void knownTurnTimeoutInterruptsOnceWaitsGraceAndKeepsLateEventSink() throws Exception {
        AtomicReference<Path> cwd = new AtomicReference<>();
        FakeConnection connection = new FakeConnection(6);
        connection.handler = (method, params) -> switch (method) {
            case "thread/start" -> {
                cwd.set(Path.of((String) params.get("cwd")));
                yield completed("{\"thread\":{\"id\":\"thread-known\"}}");
            }
            case "turn/start" -> completed("{\"turn\":{\"id\":\"turn-known\"}}");
            case "turn/interrupt" -> CompletableFuture.failedFuture(new IllegalStateException("interrupt failed"));
            case "thread/unsubscribe" -> completed("{}");
            default -> throw new AssertionError("unexpected " + method);
        };
        CodexAppServerClient client = client(
                connection, ownedDirectories(tempDirectory), 45L, 120000L, 25L, 180L);

        try (CodexLogCapture logs = CodexLogCapture.capture(CodexAppServerClient.class)) {
            long started = System.nanoTime();
            assertCode(AiErrorCode.AI_INTERRUPTED,
                    () -> client.generate("apple", profile("gpt-5.4", null)));
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

            assertTrue(elapsedMillis >= 60L, "deadline plus interrupt grace");
            assertEquals(1, connection.count("turn/interrupt"));
            assertEquals(1, connection.listenerCount());
            assertTrue(Files.exists(cwd.get()));
            connection.emit("turn/completed", json(
                    "{\"threadId\":\"thread-known\",\"turn\":{\"id\":\"turn-known\","
                            + "\"status\":\"interrupted\",\"items\":[]}}"));
            assertEventually(() -> connection.listenerCount() == 0);
            assertFalse(Files.exists(cwd.get()));
            assertWarningEvents(logs, "codex_turn_interrupt_failure");
        }
    }

    @Test
    void callerInterruptionInterruptsKnownTurnOnceWithoutDamagingSharedConnection() throws Exception {
        AtomicInteger acquires = new AtomicInteger();
        FakeConnection connection = waitingConnection(21);
        BiFunction<String, Map<String, Object>, CompletionStage<JsonNode>> waitingHandler = connection.handler;
        connection.handler = (method, params) -> "turn/interrupt".equals(method)
                ? completed("{}")
                : waitingHandler.apply(method, params);
        CodexAppServerClient client = client(
                () -> {
                    acquires.incrementAndGet();
                    return CompletableFuture.completedFuture(connection);
                }, ownedDirectories(tempDirectory), 5000L, 120000L, 10L, 500L);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                client.generate("apple", profile("gpt-5.4", null));
            } catch (Throwable thrown) {
                failure.set(thrown);
                interruptRestored.set(Thread.currentThread().isInterrupted());
            }
        });

        caller.start();
        assertEventually(() -> connection.count("turn/start") == 1);
        caller.interrupt();
        caller.join(1000L);

        assertFalse(caller.isAlive());
        assertEquals(AiErrorCode.AI_INTERRUPTED,
                ((CodexAppException) failure.get()).getErrorCode());
        assertTrue(interruptRestored.get());
        assertEventually(() -> connection.count("turn/interrupt") == 1);
        Request interrupt = connection.only("turn/interrupt");
        assertEquals("thread-wait-1", interrupt.params().get("threadId"));
        assertEquals("turn-thread-wait-1", interrupt.params().get("turnId"));
        assertFalse(connection.damaged.isDone());
        assertEquals(1, acquires.get());
        connection.emit("turn/completed", json(
                "{\"threadId\":\"thread-wait-1\",\"turn\":{\"id\":\"turn-thread-wait-1\","
                        + "\"status\":\"interrupted\",\"items\":[]}}"));
        assertEventually(() -> connection.listenerCount() == 0);
    }

    @Test
    void callerInterruptionRetainsLateTurnSinkAndInterruptsLateTurnOnce() throws Exception {
        CompletableFuture<JsonNode> lateTurn = new CompletableFuture<>();
        FakeConnection connection = new FakeConnection(22);
        connection.handler = (method, params) -> switch (method) {
            case "thread/start" -> completed("{\"thread\":{\"id\":\"thread-late-interrupt\"}}");
            case "turn/start" -> lateTurn;
            case "turn/interrupt", "thread/unsubscribe" -> completed("{}");
            default -> throw new AssertionError("unexpected " + method);
        };
        CodexAppServerClient client = client(
                connection, ownedDirectories(tempDirectory), 5000L, 120000L, 10L, 500L);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                client.generate("apple", profile("gpt-5.4", null));
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });

        caller.start();
        assertEventually(() -> connection.count("turn/start") == 1);
        caller.interrupt();
        caller.join(1000L);
        assertEquals(AiErrorCode.AI_INTERRUPTED,
                ((CodexAppException) failure.get()).getErrorCode());
        assertEquals(0, connection.count("turn/interrupt"));

        lateTurn.complete(json("{\"turn\":{\"id\":\"turn-late-interrupt\"}}"));
        assertEventually(() -> connection.count("turn/interrupt") == 1);
        assertEquals(Map.of(
                "threadId", "thread-late-interrupt",
                "turnId", "turn-late-interrupt"), connection.only("turn/interrupt").params());
        assertFalse(connection.damaged.isDone());
        connection.emit("turn/completed", json(
                "{\"threadId\":\"thread-late-interrupt\",\"turn\":{"
                        + "\"id\":\"turn-late-interrupt\",\"status\":\"interrupted\","
                        + "\"items\":[]}}"));
        assertEventually(() -> connection.listenerCount() == 0);
    }

    @Test
    void generationDamageFailsAllCollectorsWithoutReplayAndNextCallUsesNewGeneration() throws Exception {
        assertGenerationDamageFanout("malformed wire");
        assertGenerationDamageFanout("oversized wire");
    }

    private void assertGenerationDamageFanout(String damage) throws Exception {
        FakeConnection crashed = waitingConnection(11);
        FakeConnection replacement = successfulConnection();
        AtomicInteger acquires = new AtomicInteger();
        CodexAppServerClient client = client(
                () -> CompletableFuture.completedFuture(
                        acquires.incrementAndGet() <= 2 ? crashed : replacement),
                ownedDirectories(tempDirectory), 2000L, 120000L, 10L, 80L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> client.generate("one", profile("gpt-5.4", null)));
            Future<String> second = executor.submit(() -> client.generate("two", profile("gpt-5.4", null)));
            assertEventually(() -> crashed.count("turn/start") == 2);

            crashed.damaged.completeExceptionally(new IllegalStateException(damage));
            assertFutureCode(first, AiErrorCode.AI_CODEX_RUNTIME_FAILED);
            assertFutureCode(second, AiErrorCode.AI_CODEX_RUNTIME_FAILED);
            assertEquals(2, crashed.count("thread/start"));

            assertEquals("answer", client.generate("three", profile("gpt-5.4", null)));
            assertEquals(3, acquires.get());
            assertEquals(1, replacement.count("thread/start"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void oneTurnTimeoutAndInterruptFailureDoNotDamageConcurrentTurn() throws Exception {
        AtomicInteger threadSequence = new AtomicInteger();
        FakeConnection connection = new FakeConnection(12);
        connection.handler = (method, params) -> switch (method) {
            case "thread/start" -> completed("{\"thread\":{\"id\":\"thread-"
                    + threadSequence.incrementAndGet() + "\"}}");
            case "turn/start" -> {
                String threadId = (String) params.get("threadId");
                String prompt = (String) ((Map<?, ?>) ((List<?>) params.get("input")).get(0)).get("text");
                String turnId = "turn-" + threadId;
                if ("fast".equals(prompt)) {
                    connection.emit("item/completed", json(
                            "{\"threadId\":\"" + threadId + "\",\"turnId\":\"" + turnId
                                    + "\",\"item\":{\"id\":\"a\",\"type\":\"agentMessage\",\"text\":\"fast answer\"}}"));
                    connection.emit("turn/completed", json(
                            "{\"threadId\":\"" + threadId + "\",\"turn\":{\"id\":\"" + turnId
                                    + "\",\"status\":\"completed\",\"items\":[]}}"));
                }
                yield completed("{\"turn\":{\"id\":\"" + turnId + "\"}}");
            }
            case "turn/interrupt" -> CompletableFuture.failedFuture(new IllegalStateException("interrupt failed"));
            case "thread/unsubscribe" -> completed("{}");
            default -> throw new AssertionError("unexpected " + method);
        };
        CodexAppServerClient client = client(
                connection, ownedDirectories(tempDirectory), 100L, 120000L, 15L, 100L);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (CodexLogCapture logs = CodexLogCapture.capture(CodexAppServerClient.class)) {
            Future<String> slow = executor.submit(() -> client.generate("slow", profile("gpt-5.4", null)));
            assertEventually(() -> connection.count("turn/start") == 1);
            assertEquals("fast answer", client.generate("fast", profile("gpt-5.4", null)));
            assertFutureCode(slow, AiErrorCode.AI_INTERRUPTED);
            assertEquals(1, connection.count("turn/interrupt"));
            assertFalse(connection.damaged.isDone());
            assertEquals(2, connection.count("thread/start"));
            assertEventually(() -> connection.listenerCount() == 0);
            assertWarningEvents(
                    logs, "codex_turn_interrupt_failure", "codex_late_event_sink_expired");
        } finally {
            executor.shutdownNow();
        }
    }

    private CodexAppServerClient client(
            FakeConnection connection, OwnedDirectoryFactory directoryFactory) {
        return client(() -> CompletableFuture.completedFuture(connection), directoryFactory);
    }

    private CodexAppServerClient client(
            CodexAppServerClient.GenerationConnectionProvider connectionProvider,
            OwnedDirectoryFactory directoryFactory) {
        return client(connectionProvider, directoryFactory, 2000L, 120000L, 20L, 100L);
    }

    private CodexAppServerClient client(
            FakeConnection connection,
            OwnedDirectoryFactory directoryFactory,
            long timeoutMillis,
            long leaseMillis,
            long interruptGraceMillis,
            long tombstoneRetentionMillis) {
        return client(
                () -> CompletableFuture.completedFuture(connection),
                directoryFactory,
                timeoutMillis,
                leaseMillis,
                interruptGraceMillis,
                tombstoneRetentionMillis);
    }

    private CodexAppServerClient client(
            CodexAppServerClient.GenerationConnectionProvider connectionProvider,
            OwnedDirectoryFactory directoryFactory,
            long timeoutMillis,
            long leaseMillis,
            long interruptGraceMillis,
            long tombstoneRetentionMillis) {
        RuntimeSystemConfigService config = mock(RuntimeSystemConfigService.class);
        when(config.getLong("ai.codex-timeout-millis", 90000L)).thenReturn(timeoutMillis);
        when(config.getLong("async-task.lease-millis", 120000L)).thenReturn(leaseMillis);
        return new CodexAppServerClient(
                connectionProvider,
                new CodexModelCatalog(),
                config,
                directoryFactory,
                interruptGraceMillis,
                tombstoneRetentionMillis);
    }

    private static FakeConnection successfulConnection() {
        FakeConnection connection = new FakeConnection(1);
        connection.handler = (method, params) -> switch (method) {
            case "thread/start" -> {
                try {
                    Path cwd = Path.of((String) params.get("cwd"));
                    try (var children = Files.list(cwd)) {
                        connection.cwdWasInitiallyEmpty = Files.isDirectory(cwd)
                                && children.findAny().isEmpty();
                    }
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                yield completed("{\"thread\":{\"id\":\"thread-1\"}}");
            }
            case "turn/start" -> {
                connection.listenerPresentAtTurnStart = !connection.listeners.isEmpty();
                connection.emit("item/completed", json(
                        "{\"threadId\":\"thread-1\",\"turnId\":\"turn-1\","
                                + "\"item\":{\"id\":\"a1\",\"type\":\"agentMessage\",\"text\":\"answer\"}}"));
                connection.emit("turn/completed", json(
                        "{\"threadId\":\"thread-1\",\"turn\":{\"id\":\"turn-1\","
                                + "\"status\":\"completed\",\"items\":[]}}"));
                yield completed("{\"turn\":{\"id\":\"turn-1\"}}");
            }
            case "thread/unsubscribe" -> completed("{}");
            default -> CompletableFuture.failedFuture(new AssertionError("unexpected " + method));
        };
        return connection;
    }

    private static FakeConnection waitingConnection(long generation) {
        AtomicInteger sequence = new AtomicInteger();
        FakeConnection connection = new FakeConnection(generation);
        connection.handler = (method, params) -> switch (method) {
            case "thread/start" -> completed("{\"thread\":{\"id\":\"thread-wait-"
                    + sequence.incrementAndGet() + "\"}}");
            case "turn/start" -> completed("{\"turn\":{\"id\":\"turn-"
                    + params.get("threadId") + "\"}}");
            case "thread/unsubscribe" -> completed("{}");
            default -> throw new AssertionError("unexpected " + method);
        };
        return connection;
    }

    private static OwnedDirectoryFactory ownedDirectories(Path parent) {
        AtomicInteger sequence = new AtomicInteger();
        return () -> Files.createDirectory(parent.resolve("request-" + sequence.incrementAndGet()));
    }

    private static GenerationProfile profile(String model, String system) {
        return new GenerationProfile(model, system, 0.77, null);
    }

    private static CompletionStage<JsonNode> completed(String value) {
        return CompletableFuture.completedFuture(json(value));
    }

    private static BiFunction<String, Map<String, Object>, CompletionStage<JsonNode>> wrapUnsubscribe(
            BiFunction<String, Map<String, Object>, CompletionStage<JsonNode>> delegate,
            CompletableFuture<JsonNode> unsubscribe) {
        return (method, params) -> "thread/unsubscribe".equals(method)
                ? unsubscribe
                : delegate.apply(method, params);
    }

    private static JsonNode json(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static void assertCode(AiErrorCode code, Runnable invocation) {
        CodexAppException failure = assertThrows(CodexAppException.class, invocation::run);
        assertEquals(code, failure.getErrorCode());
    }

    private static void assertFutureCode(Future<String> future, AiErrorCode code) throws Exception {
        java.util.concurrent.ExecutionException wrapped = assertThrows(
                java.util.concurrent.ExecutionException.class, future::get);
        CodexAppException failure = (CodexAppException) wrapped.getCause();
        assertEquals(code, failure.getErrorCode());
    }

    private static void assertEventually(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 1_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertTrue(condition.getAsBoolean());
    }

    private static void assertWarningEvents(CodexLogCapture logs, String... expectedEvents) {
        assertEquals(expectedEvents.length, logs.events().size(), () -> logs.events().stream()
                .map(event -> event.getKeyValuePairs().toString())
                .toList().toString());
        for (int index = 0; index < expectedEvents.length; index++) {
            String expected = expectedEvents[index];
            assertTrue(logs.events().get(index).getKeyValuePairs().stream()
                    .anyMatch(value -> "event".equals(value.key) && expected.equals(value.value)));
        }
    }

    private static void assertOnlyOptionalWarningEvent(
            CodexLogCapture logs, String expectedEvent) {
        assertTrue(logs.events().size() <= 1, () -> logs.events().stream()
                .map(event -> event.getKeyValuePairs().toString())
                .toList().toString());
        logs.events().forEach(event -> assertTrue(event.getKeyValuePairs().stream()
                .anyMatch(value -> "event".equals(value.key)
                        && expectedEvent.equals(value.value))));
    }

    @FunctionalInterface
    interface OwnedDirectoryFactory extends CodexAppServerClient.OwnedDirectoryFactory {
    }

    private record Request(String method, Map<String, Object> params) {
    }

    private static final class FakeConnection implements CodexAppServerClient.GenerationConnection {
        private final long generation;
        private final List<Request> requests = new CopyOnWriteArrayList<>();
        private final List<BiConsumer<String, JsonNode>> listeners = new CopyOnWriteArrayList<>();
        private final CompletableFuture<Void> damaged = new CompletableFuture<>();
        private BiFunction<String, Map<String, Object>, CompletionStage<JsonNode>> handler;
        private boolean listenerPresentAtTurnStart;
        private boolean cwdWasInitiallyEmpty;

        private FakeConnection(long generation) {
            this.generation = generation;
        }

        @Override
        public long generation() {
            return generation;
        }

        @Override
        public CompletionStage<JsonNode> request(String method, Map<String, Object> params) {
            Map<String, Object> copied = Map.copyOf(params);
            requests.add(new Request(method, copied));
            return handler.apply(method, copied);
        }

        @Override
        public AutoCloseable onNotification(BiConsumer<String, JsonNode> listener) {
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }

        @Override
        public CompletionStage<Void> damaged() {
            return damaged;
        }

        private void emit(String method, JsonNode params) {
            listeners.forEach(listener -> listener.accept(method, params));
        }

        private Request only(String method) {
            List<Request> matching = requests.stream()
                    .filter(request -> method.equals(request.method()))
                    .toList();
            assertEquals(1, matching.size(), method + " request count");
            return matching.get(0);
        }

        private int count(String method) {
            return (int) requests.stream().filter(request -> method.equals(request.method())).count();
        }

        private int listenerCount() {
            return listeners.size();
        }
    }
}
