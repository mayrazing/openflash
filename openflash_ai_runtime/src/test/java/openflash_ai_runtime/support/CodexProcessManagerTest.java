package openflash_ai_runtime.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import openflash_ai_runtime.client.JsonlRpcPeer;
import openflash_ai_runtime.config.CodexHome;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodexProcessManagerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long TIMEOUT_SECONDS = 3;

    private final List<CodexProcessManager> managers = new ArrayList<>();

    @TempDir
    Path tempDirectory;

    @AfterEach
    void tearDown() {
        managers.forEach(CodexProcessManager::shutdown);
    }

    @Test
    void lazilyStartsExactCommandAndCompletesHandshakeInProtocolOrder() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(false);
        CodexProcessManager manager = manager(factory);

        assertEquals(0, factory.startCount());
        CompletableFuture<CodexProcessManager.CodexConnection> pending =
                manager.connection().toCompletableFuture();

        FakeProcess process = factory.process(0);
        assertEquals(
                List.of(
                        "codex", "app-server",
                        "-c", "cli_auth_credentials_store=\"file\"",
                        "--listen", "stdio://"),
                factory.lastCommand());
        assertEquals(
                dedicatedHome().toAbsolutePath().normalize().toString(),
                factory.lastEnvironment().get("CODEX_HOME"));
        assertEquals(Set.of("CODEX_HOME"), factory.lastEnvironment().keySet());
        assertEquals(1, factory.startCount());
        assertEquals("initialize", process.awaitReceived(1).get(0).path("method").asText());
        assertFalse(pending.isDone());

        process.respondToInitialize();
        CodexProcessManager.CodexConnection connection = pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        List<JsonNode> received = process.awaitReceived(2);
        assertEquals("initialized", received.get(1).path("method").asText());
        assertEquals(1L, connection.generation());
        assertSame(process, connection.process());
        assertEquals(1, countMethod(received, "initialize"));
    }

    @Test
    void invalidationAfterInitializeResponsePreventsInitializedNotification() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(false);
        CodexProcessManager manager = manager(factory);
        CompletableFuture<CodexProcessManager.CodexConnection> pending =
                manager.connection().toCompletableFuture();
        FakeProcess process = factory.process(0);

        process.respondToInitializeWhile(process::exit);

        assertThrows(
                ExecutionException.class,
                () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(0, countMethod(process.receivedSnapshot(), "initialized"));
    }

    @Test
    void shutdownAfterInitializeResponsePreventsInitializedNotification() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(false);
        CodexProcessManager manager = manager(factory);
        CompletableFuture<CodexProcessManager.CodexConnection> pending =
                manager.connection().toCompletableFuture();
        FakeProcess process = factory.process(0);

        process.respondToInitializeWhile(manager::shutdown);

        assertThrows(
                ExecutionException.class,
                () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(0, countMethod(process.receivedSnapshot(), "initialized"));
    }

    @Test
    void initializedSendWinnerDelaysInvalidationUntilNotificationCompletes() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(false);
        CodexProcessManager manager = manager(factory);
        CompletableFuture<CodexProcessManager.CodexConnection> pending =
                manager.connection().toCompletableFuture();
        FakeProcess process = factory.process(0);
        process.stdin.blockInitializedWrite = true;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> responder = pool.submit(() -> {
                process.respondToInitialize();
                return null;
            });
            assertTrue(process.stdin.initializedWriteEntered.await(
                    TIMEOUT_SECONDS, TimeUnit.SECONDS));

            Future<?> invalidation = pool.submit(process::exit);
            assertTrue(process.onExitCallback.callbackEntered.await(
                    TIMEOUT_SECONDS, TimeUnit.SECONDS));

            assertThrows(
                    TimeoutException.class,
                    () -> invalidation.get(100, TimeUnit.MILLISECONDS),
                    "terminal transition must wait for the winning initialized send");
            process.stdin.releaseInitializedWrite.countDown();
            responder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            invalidation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertEquals(1, countMethod(process.receivedSnapshot(), "initialized"));
            assertTrue(pending.isDone());
        } finally {
            process.stdin.releaseInitializedWrite.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentConnectionsShareOneSpawnAndOneHandshake() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(false);
        CodexProcessManager manager = manager(factory);
        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<CompletableFuture<CodexProcessManager.CodexConnection>>> calls = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    return manager.connection().toCompletableFuture();
                }));
            }
            start.countDown();
            List<CompletableFuture<CodexProcessManager.CodexConnection>> pending = new ArrayList<>();
            for (Future<CompletableFuture<CodexProcessManager.CodexConnection>> call : calls) {
                pending.add(call.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            assertEquals(1, factory.startCount());
            FakeProcess process = factory.process(0);
            assertEquals(1, countMethod(process.awaitReceived(1), "initialize"));
            process.respondToInitialize();

            CodexProcessManager.CodexConnection first =
                    pending.get(0).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (CompletableFuture<CodexProcessManager.CodexConnection> future : pending) {
                assertSame(first, future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            assertEquals(1, countMethod(process.awaitReceived(2), "initialized"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentConnectionsShareSingleInFlightSpawnFailureAndLaterCallRetries() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(true);
        factory.blockNextSpawn = true;
        factory.spawnFailure = new IOException("codex missing");
        CodexProcessManager manager = manager(factory);
        ExecutorService pool = Executors.newFixedThreadPool(9);
        try {
            Future<CompletableFuture<CodexProcessManager.CodexConnection>> starter =
                    pool.submit(() -> manager.connection().toCompletableFuture());
            assertTrue(factory.spawnEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            List<Future<CompletableFuture<CodexProcessManager.CodexConnection>>> followers =
                    new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                followers.add(pool.submit(() -> manager.connection().toCompletableFuture()));
            }
            List<CompletableFuture<CodexProcessManager.CodexConnection>> sharedAttempts =
                    new ArrayList<>();
            for (Future<CompletableFuture<CodexProcessManager.CodexConnection>> follower : followers) {
                sharedAttempts.add(assertDoesNotThrow(
                        () -> follower.get(1, TimeUnit.SECONDS),
                        "followers must join the in-flight spawn while process creation is blocked"));
            }
            assertEquals(1, factory.startCount());

            factory.releaseBlockedSpawn();
            CompletableFuture<CodexProcessManager.CodexConnection> firstAttempt =
                    starter.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (CompletableFuture<CodexProcessManager.CodexConnection> attempt : sharedAttempts) {
                assertSame(firstAttempt, attempt);
                ExecutionException failure = assertThrows(
                        ExecutionException.class,
                        () -> attempt.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                assertInstanceOf(
                        CodexProcessManager.CodexNotInstalledException.class, failure.getCause());
            }
            assertEquals(1, factory.startCount());

            factory.spawnFailure = null;
            CodexProcessManager.CodexConnection retried = awaitConnection(manager, factory, 0);
            assertEquals(2L, retried.generation());
            assertEquals(2, factory.startCount());
        } finally {
            factory.releaseBlockedSpawn();
            pool.shutdownNow();
        }
    }

    @Test
    void failedAttemptSynchronousContinuationStartsFreshRetry() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(true);
        factory.blockNextSpawn = true;
        factory.spawnFailure = new IOException("codex missing");
        factory.clearSpawnFailureAfterThrow = true;
        CodexProcessManager manager = manager(factory);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<CompletableFuture<CodexProcessManager.CodexConnection>> starter =
                    pool.submit(() -> manager.connection().toCompletableFuture());
            assertTrue(factory.spawnEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            CompletableFuture<CodexProcessManager.CodexConnection> firstAttempt =
                    manager.connection().toCompletableFuture();
            CompletableFuture<CodexProcessManager.CodexConnection> retry = firstAttempt
                    .handle((ignored, failure) -> manager.connection())
                    .thenCompose(stage -> stage)
                    .toCompletableFuture();

            factory.releaseBlockedSpawn();

            assertSame(firstAttempt, starter.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            CodexProcessManager.CodexConnection connection =
                    retry.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertEquals(2L, connection.generation());
            assertEquals(2, factory.startCount());
        } finally {
            factory.releaseBlockedSpawn();
            pool.shutdownNow();
        }
    }

    @Test
    void completionCallbackCanReenterConnectionWithoutLifecycleLock() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(false);
        CodexProcessManager manager = manager(factory);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<CodexProcessManager.CodexConnection> pending =
                    manager.connection().toCompletableFuture();
            FakeProcess process = factory.process(0);
            CompletableFuture<Boolean> reenteredWithoutLock = pending.thenApply(connection -> {
                Future<CodexProcessManager.CodexConnection> reentry = pool.submit(() ->
                        manager.connection().toCompletableFuture()
                                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                try {
                    return reentry.get(1, TimeUnit.SECONDS) == connection;
                } catch (Exception blocked) {
                    return false;
                }
            });

            process.respondToInitialize();

            assertTrue(reenteredWithoutLock.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, factory.startCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void continuouslyDrainsStderrWithoutBlockingStdoutHandshake() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(true);
        CodexProcessManager manager = manager(factory);

        CodexProcessManager.CodexConnection connection =
                manager.connection().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        FakeProcess process = factory.process(0);
        assertSame(process, connection.process());
        assertTrue(process.stderr.awaitDrained(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    void missingExecutableFailsOnlyLazyConnectionAsNotInstalled() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(false);
        factory.spawnFailure = new IOException("secret /local/path/codex missing");
        CodexProcessManager manager = manager(factory);

        assertEquals(0, factory.startCount());
        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> manager.connection().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertInstanceOf(CodexProcessManager.CodexNotInstalledException.class, failure.getCause());
        assertEquals(1, factory.startCount());
    }

    @Test
    void invalidDedicatedHomeFailsAsSafeConnectionErrorBeforeProcessSpawn() throws Exception {
        Path invalidHome = tempDirectory.resolve("regular-file");
        Files.writeString(invalidHome, "not a directory");
        CodexHome codexHome = new CodexHome(key -> invalidHome.toString(), tempDirectory);
        FakeProcessFactory factory = new FakeProcessFactory(false);
        CodexProcessManager manager = new CodexProcessManager(factory, 25L, codexHome);
        managers.add(manager);

        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> manager.connection().toCompletableFuture()
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertInstanceOf(CodexProcessManager.CodexConnectionException.class, failure.getCause());
        assertFalse(failure.getCause() instanceof CodexProcessManager.CodexNotInstalledException);
        assertFalse(failure.getCause().getMessage().contains(tempDirectory.toString()));
        assertEquals(0, factory.startCount());
    }

    @Test
    void runtimeHomeLoaderFailureReturnsSafeFailedStageAndLaterCallRetries() throws Exception {
        AtomicBoolean failFirst = new AtomicBoolean(true);
        String raw = "secret /home/user/.codex/auth.json";
        CodexHome codexHome = new CodexHome(key -> {
            if (failFirst.getAndSet(false)) throw new IllegalStateException(raw);
            return dedicatedHome().toString();
        }, tempDirectory);
        FakeProcessFactory factory = new FakeProcessFactory(false);
        CodexProcessManager manager = new CodexProcessManager(factory, 25L, codexHome);
        managers.add(manager);

        CompletableFuture<CodexProcessManager.CodexConnection> failed = assertDoesNotThrow(
                () -> manager.connection().toCompletableFuture());
        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> failed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertInstanceOf(CodexProcessManager.CodexConnectionException.class, failure.getCause());
        assertFalse(failure.getCause().getMessage().contains(raw));
        assertEquals(0, factory.startCount());
        CodexProcessManager.CodexConnection retried = awaitConnection(manager, factory, 0);
        assertEquals(2L, retried.generation());
    }

    @Test
    void invalidConfiguredPathReturnsSafeFailedStageAndLaterCallRetries() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CodexHome codexHome = new CodexHome(
                key -> loads.getAndIncrement() == 0
                        ? "invalid\0path"
                        : dedicatedHome().toString(),
                tempDirectory);
        FakeProcessFactory factory = new FakeProcessFactory(false);
        CodexProcessManager manager = new CodexProcessManager(factory, 25L, codexHome);
        managers.add(manager);

        CompletableFuture<CodexProcessManager.CodexConnection> failed = assertDoesNotThrow(
                () -> manager.connection().toCompletableFuture());
        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> failed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertInstanceOf(CodexProcessManager.CodexConnectionException.class, failure.getCause());
        assertFalse(failure.getCause().getMessage().contains("invalid"));
        assertEquals(0, factory.startCount());
        CodexProcessManager.CodexConnection retried = awaitConnection(manager, factory, 0);
        assertEquals(2L, retried.generation());
    }

    @Test
    void uncheckedSpawnFailureDeletesOwnedDirectoryAndLaterCallRetries() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(false);
        factory.spawnRuntimeFailure = new IllegalStateException("secret runtime spawn failure");
        CodexProcessManager manager = manager(factory);

        CompletableFuture<CodexProcessManager.CodexConnection> failed = assertDoesNotThrow(
                () -> manager.connection().toCompletableFuture());
        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> failed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        assertInstanceOf(CodexProcessManager.CodexConnectionException.class, failure.getCause());
        assertFalse(Files.exists(factory.workingDirectories.get(0)));
        factory.spawnRuntimeFailure = null;
        CodexProcessManager.CodexConnection retried = awaitConnection(manager, factory, 0);
        assertEquals(2L, retried.generation());
    }

    @Test
    void cleanupRuntimeFailureDoesNotMaskSafeSpawnFailureOrBlockRetry() throws Exception {
        List<RuntimeException> cleanupFailures = List.of(
                new UncheckedIOException(new IOException("raw cleanup IO failure")),
                new SecurityException("raw cleanup security failure"));

        for (RuntimeException cleanupFailure : cleanupFailures) {
            FakeProcessFactory factory = new FakeProcessFactory(false);
            IllegalStateException spawnFailure =
                    new IllegalStateException("secret runtime spawn failure");
            factory.spawnRuntimeFailure = spawnFailure;
            AtomicInteger cleanupCalls = new AtomicInteger();
            CodexHome codexHome = new CodexHome(
                    key -> dedicatedHome().toString(), tempDirectory);
            CodexProcessManager manager = new CodexProcessManager(
                    factory,
                    25L,
                    codexHome,
                    workingDirectory -> {
                        cleanupCalls.incrementAndGet();
                        try (var paths = Files.walk(workingDirectory)) {
                            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                                Files.deleteIfExists(path);
                            }
                        } catch (IOException failure) {
                            throw new AssertionError(failure);
                        }
                        throw cleanupFailure;
                    });
            managers.add(manager);

            CompletableFuture<CodexProcessManager.CodexConnection> failed = assertDoesNotThrow(
                    () -> manager.connection().toCompletableFuture());
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> failed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            assertInstanceOf(CodexProcessManager.CodexConnectionException.class, failure.getCause());
            assertSame(spawnFailure, failure.getCause().getCause());
            assertEquals(1, cleanupCalls.get());
            assertFalse(Files.exists(factory.workingDirectories.get(0)));

            factory.spawnRuntimeFailure = null;
            CodexProcessManager.CodexConnection retried = awaitConnection(manager, factory, 0);
            assertEquals(2L, retried.generation());
        }
    }

    @Test
    void exitEofAndStdoutErrorEachInvalidateCurrentGeneration() throws Exception {
        assertRespawnsAfterFailure(FakeProcess::exit);
        assertRespawnsAfterFailure(process -> process.stdout.end());
        assertRespawnsAfterFailure(process -> process.stdout.fail(new IOException("read failed")));
    }

    @Test
    void malformedAndOversizedWireEachInvalidateCurrentGeneration() throws Exception {
        assertRespawnsAfterFailure(process -> process.stdout.writeUtf8("{malformed}\n"));
        assertRespawnsAfterFailure(process -> process.stdout.writeUtf8(
                "x".repeat(JsonlRpcPeer.MAX_LINE_BYTES + 1) + "\n"));
    }

    @Test
    void stdinWriteErrorInvalidatesCurrentGeneration() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(true);
        CodexProcessManager manager = manager(factory);
        CodexProcessManager.CodexConnection first = awaitConnection(manager, factory, 0);
        factory.process(0).stdin.failWrites = true;

        assertThrows(
                ExecutionException.class,
                () -> first.peer().request("account/read", java.util.Map.of())
                        .toCompletableFuture()
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        awaitCondition(() -> factory.process(0).destroyCount.get() > 0);

        CodexProcessManager.CodexConnection second = awaitConnection(manager, factory, 1);
        assertEquals(first.generation() + 1, second.generation());
    }

    @Test
    void lateFailureFromOldGenerationCannotInvalidateNewConnection() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(true);
        factory.delayNextOnExitCallback = true;
        CodexProcessManager manager = manager(factory);
        CodexProcessManager.CodexConnection first = awaitConnection(manager, factory, 0);
        factory.process(0).markExitedBeforeCallbacks();
        assertFalse(first.process().isAlive());
        assertFalse(factory.process(0).onExitCallbackCompleted());

        CodexProcessManager.CodexConnection second = awaitConnection(manager, factory, 1);
        assertFalse(factory.process(0).onExitCallbackCompleted());
        factory.process(0).signalDelayedOnExit();

        assertTrue(factory.process(0).onExitCallbackCompleted());
        assertSame(second, manager.connection().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(2, factory.startCount());
    }

    @Test
    void shutdownTerminatesThenForcesAfterGraceDeletesOwnedDirAndIsIdempotent() throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(true);
        factory.processExitsOnDestroy = false;
        CodexProcessManager manager = manager(factory);
        awaitConnection(manager, factory, 0);
        Path ownedDirectory = factory.workingDirectories.get(0);
        Path codexHome = dedicatedHome();
        Path auth = codexHome.resolve("auth.json");
        Files.writeString(auth, "{\"token\":\"keep\"}");
        assertTrue(Files.exists(ownedDirectory));

        manager.shutdown();
        manager.shutdown();

        FakeProcess process = factory.process(0);
        assertEquals(1, process.destroyCount.get());
        assertEquals(1, process.destroyForciblyCount.get());
        assertFalse(Files.exists(ownedDirectory));
        assertTrue(Files.isDirectory(codexHome));
        assertEquals("{\"token\":\"keep\"}", Files.readString(auth));
    }

    private void assertRespawnsAfterFailure(ProcessFailure failure) throws Exception {
        FakeProcessFactory factory = new FakeProcessFactory(true);
        CodexProcessManager manager = manager(factory);
        CodexProcessManager.CodexConnection first = awaitConnection(manager, factory, 0);

        failure.fail(factory.process(0));
        awaitCondition(() -> factory.process(0).destroyCount.get() > 0 || !first.process().isAlive());

        CodexProcessManager.CodexConnection second = awaitConnection(manager, factory, 1);
        assertEquals(first.generation() + 1, second.generation());
        assertEquals(2, factory.startCount());
    }

    private CodexProcessManager manager(FakeProcessFactory factory) {
        CodexHome codexHome = new CodexHome(
                key -> dedicatedHome().toString(), tempDirectory);
        CodexProcessManager manager = new CodexProcessManager(factory, 25L, codexHome);
        managers.add(manager);
        return manager;
    }

    private Path dedicatedHome() {
        return tempDirectory.resolve("codex-home");
    }

    private static CodexProcessManager.CodexConnection awaitConnection(
            CodexProcessManager manager, FakeProcessFactory factory, int processIndex) throws Exception {
        CompletableFuture<CodexProcessManager.CodexConnection> pending =
                manager.connection().toCompletableFuture();
        FakeProcess process = factory.process(processIndex);
        if (!process.autoRespond) process.respondToInitialize();
        return pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static int countMethod(List<JsonNode> messages, String method) {
        return (int) messages.stream()
                .filter(message -> method.equals(message.path("method").asText()))
                .count();
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertTrue(condition.getAsBoolean());
    }

    private interface ProcessFailure {
        void fail(FakeProcess process) throws Exception;
    }

    private static final class FakeProcessFactory implements CodexProcessManager.ProcessFactory {
        private final boolean autoRespond;
        private final AtomicInteger starts = new AtomicInteger();
        private final List<List<String>> commands = Collections.synchronizedList(new ArrayList<>());
        private final List<Path> workingDirectories = Collections.synchronizedList(new ArrayList<>());
        private final List<Map<String, String>> environments =
                Collections.synchronizedList(new ArrayList<>());
        private final List<FakeProcess> processes = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch spawnEntered = new CountDownLatch(1);
        private final CountDownLatch releaseSpawn = new CountDownLatch(1);
        private volatile IOException spawnFailure;
        private volatile RuntimeException spawnRuntimeFailure;
        private volatile boolean blockNextSpawn;
        private volatile boolean clearSpawnFailureAfterThrow;
        private volatile boolean delayNextOnExitCallback;
        private volatile boolean processExitsOnDestroy = true;

        private FakeProcessFactory(boolean autoRespond) {
            this.autoRespond = autoRespond;
        }

        @Override
        public Process start(
                List<String> command,
                Path workingDirectory,
                Map<String, String> environment) throws IOException {
            starts.incrementAndGet();
            commands.add(List.copyOf(command));
            workingDirectories.add(workingDirectory);
            environments.add(Map.copyOf(environment));
            try (var entries = Files.list(workingDirectory)) {
                assertEquals(0L, entries.count());
            }
            if (blockNextSpawn) {
                spawnEntered.countDown();
                try {
                    releaseSpawn.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("spawn interrupted", interrupted);
                }
            }
            if (spawnFailure != null) {
                IOException failure = spawnFailure;
                if (clearSpawnFailureAfterThrow) spawnFailure = null;
                throw failure;
            }
            if (spawnRuntimeFailure != null) throw spawnRuntimeFailure;
            FakeProcess process =
                    new FakeProcess(autoRespond, processExitsOnDestroy, delayNextOnExitCallback);
            delayNextOnExitCallback = false;
            processes.add(process);
            return process;
        }

        void releaseBlockedSpawn() {
            blockNextSpawn = false;
            releaseSpawn.countDown();
        }

        int startCount() {
            return starts.get();
        }

        List<String> lastCommand() {
            return commands.get(commands.size() - 1);
        }

        Map<String, String> lastEnvironment() {
            return environments.get(environments.size() - 1);
        }

        FakeProcess process(int index) {
            try {
                awaitCondition(() -> processes.size() > index);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            return processes.get(index);
        }
    }

    private static final class FakeProcess extends Process {
        private final ControlledInputStream stdout = new ControlledInputStream();
        private final DrainingInputStream stderr;
        private final RecordingStdin stdin = new RecordingStdin(this);
        private final CompletableFuture<Process> processExit = new CompletableFuture<>();
        private final ObservedExitFuture onExitCallback = new ObservedExitFuture();
        private final AtomicInteger destroyCount = new AtomicInteger();
        private final AtomicInteger destroyForciblyCount = new AtomicInteger();
        private final boolean autoRespond;
        private final boolean exitsOnDestroy;
        private final boolean delayOnExitCallback;
        private volatile boolean alive = true;

        private FakeProcess(boolean autoRespond, boolean exitsOnDestroy, boolean delayOnExitCallback) {
            this.autoRespond = autoRespond;
            this.exitsOnDestroy = exitsOnDestroy;
            this.delayOnExitCallback = delayOnExitCallback;
            byte[] noise = new byte[2 * 1024 * 1024];
            this.stderr = new DrainingInputStream(noise);
        }

        List<JsonNode> awaitReceived(int count) throws InterruptedException {
            return stdin.awaitReceived(count);
        }

        void respondToInitialize() throws IOException, InterruptedException {
            JsonNode initialize = awaitReceived(1).get(0);
            stdout.writeUtf8("{\"id\":" + initialize.path("id").longValue()
                    + ",\"result\":{\"serverInfo\":{\"name\":\"codex\"}}}\n");
        }

        void respondToInitializeWhile(Runnable afterResponseWrite)
                throws IOException, InterruptedException {
            JsonNode initialize = awaitReceived(1).get(0);
            stdout.writeUtf8While(
                    "{\"id\":" + initialize.path("id").longValue()
                            + ",\"result\":{\"serverInfo\":{\"name\":\"codex\"}}}\n",
                    afterResponseWrite);
        }

        List<JsonNode> receivedSnapshot() {
            return stdin.receivedSnapshot();
        }

        void exit() {
            alive = false;
            stdout.end();
            processExit.complete(this);
            if (!delayOnExitCallback) onExitCallback.complete(this);
        }

        void markExitedBeforeCallbacks() {
            alive = false;
            processExit.complete(this);
        }

        void signalDelayedOnExit() {
            onExitCallback.complete(this);
        }

        boolean onExitCallbackCompleted() {
            return onExitCallback.isDone();
        }

        @Override
        public OutputStream getOutputStream() {
            return stdin;
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() throws InterruptedException {
            try {
                processExit.get();
                return 0;
            } catch (ExecutionException failure) {
                throw new AssertionError(failure);
            }
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            try {
                processExit.get(timeout, unit);
                return true;
            } catch (java.util.concurrent.TimeoutException failure) {
                return false;
            } catch (ExecutionException failure) {
                throw new AssertionError(failure);
            }
        }

        @Override
        public int exitValue() {
            if (alive) throw new IllegalThreadStateException();
            return 0;
        }

        @Override
        public void destroy() {
            if (destroyCount.incrementAndGet() == 1 && exitsOnDestroy) exit();
        }

        @Override
        public Process destroyForcibly() {
            if (destroyForciblyCount.incrementAndGet() == 1) exit();
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public CompletableFuture<Process> onExit() {
            return onExitCallback;
        }
    }

    private static final class ObservedExitFuture extends CompletableFuture<Process> {
        private final CountDownLatch callbackEntered = new CountDownLatch(1);

        @Override
        public CompletableFuture<Process> whenComplete(
                java.util.function.BiConsumer<? super Process, ? super Throwable> action) {
            return super.whenComplete((process, failure) -> {
                callbackEntered.countDown();
                action.accept(process, failure);
            });
        }
    }

    private static final class RecordingStdin extends OutputStream {
        private final FakeProcess owner;
        private final ByteArrayOutputStream current = new ByteArrayOutputStream();
        private final List<JsonNode> received = new ArrayList<>();
        private final CountDownLatch initializedWriteEntered = new CountDownLatch(1);
        private final CountDownLatch releaseInitializedWrite = new CountDownLatch(1);
        private volatile boolean failWrites;
        private volatile boolean blockInitializedWrite;

        private RecordingStdin(FakeProcess owner) {
            this.owner = owner;
        }

        @Override
        public synchronized void write(int value) throws IOException {
            if (failWrites) throw new IOException("stdin write failed");
            if (value != '\n') {
                current.write(value);
                return;
            }
            JsonNode message = JSON.readTree(current.toByteArray());
            current.reset();
            received.add(message);
            notifyAll();
            if (owner.autoRespond && "initialize".equals(message.path("method").asText())) {
                owner.stdout.writeUtf8("{\"id\":" + message.path("id").longValue()
                        + ",\"result\":{}}\n");
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            String line = new String(bytes, offset, length, StandardCharsets.UTF_8);
            if (blockInitializedWrite && line.contains("\"method\":\"initialized\"")) {
                initializedWriteEntered.countDown();
                try {
                    releaseInitializedWrite.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("initialized write interrupted", interrupted);
                }
            }
            super.write(bytes, offset, length);
        }

        synchronized List<JsonNode> awaitReceived(int count) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (received.size() < count && System.nanoTime() < deadline) wait(10L);
            assertTrue(received.size() >= count);
            return List.copyOf(received);
        }

        synchronized List<JsonNode> receivedSnapshot() {
            return List.copyOf(received);
        }
    }

    private static final class ControlledInputStream extends InputStream {
        private byte[] data = new byte[0];
        private int offset;
        private boolean ended;
        private IOException failure;

        synchronized void writeUtf8(String text) {
            appendUtf8(text);
        }

        synchronized void writeUtf8While(String text, Runnable afterWrite) {
            appendUtf8(text);
            afterWrite.run();
        }

        private void appendUtf8(String text) {
            byte[] appended = text.getBytes(StandardCharsets.UTF_8);
            byte[] unread = java.util.Arrays.copyOfRange(data, offset, data.length);
            data = new byte[unread.length + appended.length];
            System.arraycopy(unread, 0, data, 0, unread.length);
            System.arraycopy(appended, 0, data, unread.length, appended.length);
            offset = 0;
            notifyAll();
        }

        synchronized void end() {
            ended = true;
            notifyAll();
        }

        synchronized void fail(IOException value) {
            failure = value;
            notifyAll();
        }

        @Override
        public synchronized int read() throws IOException {
            byte[] one = new byte[1];
            int count = read(one, 0, 1);
            return count < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public synchronized int read(byte[] target, int targetOffset, int length) throws IOException {
            while (offset == data.length && !ended && failure == null) {
                try {
                    wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted");
                }
            }
            if (failure != null) throw failure;
            if (offset == data.length) return -1;
            int count = Math.min(length, data.length - offset);
            System.arraycopy(data, offset, target, targetOffset, count);
            offset += count;
            return count;
        }
    }

    private static final class DrainingInputStream extends ByteArrayInputStream {
        private final CountDownLatch drained = new CountDownLatch(1);

        private DrainingInputStream(byte[] data) {
            super(data);
        }

        @Override
        public synchronized int read(byte[] target, int offset, int length) {
            int count = super.read(target, offset, length);
            if (count == -1) drained.countDown();
            return count;
        }

        @Override
        public synchronized long transferTo(OutputStream target) throws IOException {
            long count = super.transferTo(target);
            drained.countDown();
            return count;
        }

        boolean awaitDrained(long timeout, TimeUnit unit) throws InterruptedException {
            return drained.await(timeout, unit);
        }
    }
}
