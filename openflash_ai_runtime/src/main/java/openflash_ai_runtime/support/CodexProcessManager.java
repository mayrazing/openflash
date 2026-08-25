package openflash_ai_runtime.support;

import jakarta.annotation.PreDestroy;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import openflash_ai_runtime.client.JsonlRpcPeer;
import openflash_ai_runtime.config.CodexHome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 为 Codex app-server 提供按需启动且按 generation 隔离的单例连接. */
@Component
@ConditionalOnProperty(prefix = "app.codex", name = "enabled", havingValue = "true")
public class CodexProcessManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodexProcessManager.class);
    private static final List<String> COMMAND = List.of(
            "codex", "app-server",
            "-c", "cli_auth_credentials_store=\"file\"",
            "--listen", "stdio://");
    private static final long DEFAULT_SHUTDOWN_GRACE_MILLIS = 250L;

    private final Object lifecycleLock = new Object();
    private final ProcessFactory processFactory;
    private final long shutdownGraceMillis;
    private final CodexHome codexHome;
    private final DirectoryCleaner failedLaunchDirectoryCleaner;
    private final Set<Generation> ownedGenerations = ConcurrentHashMap.newKeySet();

    private long nextGeneration;
    private Generation current;
    private CompletableFuture<CodexConnection> starting;
    private boolean shutdown;

    /** 创建不启动子进程的 Spring 单例. */
    @Autowired
    public CodexProcessManager(CodexHome codexHome) {
        this(CodexProcessManager::startProcess,
                DEFAULT_SHUTDOWN_GRACE_MILLIS, codexHome);
    }

    CodexProcessManager(
            ProcessFactory processFactory,
            long shutdownGraceMillis,
            CodexHome codexHome) {
        this(processFactory, shutdownGraceMillis, codexHome,
                CodexProcessManager::deleteOwnedDirectory);
    }

    CodexProcessManager(
            ProcessFactory processFactory,
            long shutdownGraceMillis,
            CodexHome codexHome,
            DirectoryCleaner failedLaunchDirectoryCleaner) {
        this.processFactory = processFactory;
        this.shutdownGraceMillis = Math.max(1L, shutdownGraceMillis);
        this.codexHome = codexHome;
        this.failedLaunchDirectoryCleaner = failedLaunchDirectoryCleaner;
    }

    /** 返回当前可用连接; 无可用 generation 时只启动一个新进程. */
    public CompletionStage<CodexConnection> connection() {
        CompletableFuture<CodexConnection> attempt;
        long generationId;
        synchronized (lifecycleLock) {
            if (shutdown) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Codex process manager is shut down"));
            }
            if (current != null && !current.failed.get() && current.process.isAlive()) {
                return current.ready;
            }
            if (starting != null) return starting;
            generationId = ++nextGeneration;
            attempt = new CompletableFuture<>();
            starting = attempt;
        }
        startGeneration(generationId, attempt);
        return attempt;
    }

    private void startGeneration(
            long generationId, CompletableFuture<CodexConnection> attempt) {
        Path workingDirectory;
        try {
            workingDirectory = Files.createTempDirectory("openflash-codex-");
        } catch (IOException | RuntimeException failure) {
            failStartingAttempt(
                    attempt,
                    new CodexConnectionException("Cannot create Codex working directory", failure));
            return;
        }

        Path isolatedHome;
        try {
            isolatedHome = codexHome.prepare();
        } catch (IOException | RuntimeException failure) {
            failStartingAttemptAfterCleanup(
                    workingDirectory,
                    attempt,
                    new CodexConnectionException("Cannot prepare Codex home", failure));
            return;
        }

        Process process;
        try {
            process = processFactory.start(
                    COMMAND,
                    workingDirectory,
                    Map.of("CODEX_HOME", isolatedHome.toString()));
        } catch (IOException failure) {
            failStartingAttemptAfterCleanup(
                    workingDirectory, attempt, new CodexNotInstalledException(failure));
            return;
        } catch (RuntimeException failure) {
            failStartingAttemptAfterCleanup(
                    workingDirectory,
                    attempt,
                    new CodexConnectionException("Cannot start Codex app-server", failure));
            return;
        }

        Generation generation = new Generation(generationId, workingDirectory, process, attempt);
        CompletableFuture<CodexConnection> rejectedAttempt = null;
        synchronized (lifecycleLock) {
            if (shutdown || starting != attempt) {
                rejectedAttempt = attempt;
            } else {
                current = generation;
                ownedGenerations.add(generation);
            }
        }
        if (rejectedAttempt != null) {
            rejectedAttempt.completeExceptionally(
                    new CodexConnectionException("Codex process manager is shut down"));
            terminate(generation);
            return;
        }
        startStderrDrainer(generation);
        process.onExit().whenComplete((ignored, failure) -> invalidate(
                generation,
                new CodexConnectionException("Codex app-server exited", failure)));

        try {
            InputStream observedStdout = new ObservedInputStream(
                    process.getInputStream(),
                    failure -> invalidate(generation, failure));
            OutputStream observedStdin = new ObservedOutputStream(
                    process.getOutputStream(),
                    failure -> invalidate(generation, failure));
            JsonlRpcPeer peer = new JsonlRpcPeer(observedStdout, observedStdin);
            generation.peer = peer;
            peer.terminal().whenComplete((ignored, failure) -> invalidate(
                    generation,
                    new CodexConnectionException("Codex app-server RPC peer failed", failure)));
            CodexConnection connection = new CodexConnection(generationId, peer, process);
            generation.connection = connection;
            peer.request("initialize", initializeParams()).whenComplete((response, failure) -> {
                if (failure != null) {
                    invalidate(
                            generation,
                            new CodexConnectionException("Codex app-server initialize failed", failure));
                    return;
                }
                finishHandshake(generation);
            });
        } catch (RuntimeException failure) {
            invalidate(
                    generation,
                    new CodexConnectionException("Codex app-server initialize failed", failure));
        }
    }

    private void failStartingAttemptAfterCleanup(
            Path workingDirectory,
            CompletableFuture<CodexConnection> attempt,
            RuntimeException failure) {
        try {
            failedLaunchDirectoryCleaner.delete(workingDirectory);
        } catch (RuntimeException cleanupFailure) {
            LOGGER.warn("Codex failed-launch working directory cleanup failed");
        }
        failStartingAttempt(attempt, failure);
    }

    private void failStartingAttempt(
            CompletableFuture<CodexConnection> attempt, RuntimeException failure) {
        CompletableFuture<CodexConnection> failedAttempt;
        synchronized (lifecycleLock) {
            if (starting == attempt) starting = null;
            failedAttempt = attempt;
        }
        failedAttempt.completeExceptionally(failure);
    }

    private void finishHandshake(Generation generation) {
        synchronized (lifecycleLock) {
            if (shutdown || current != generation || generation.failed.get()) return;
        }
        RuntimeException notificationFailure = null;
        try {
            synchronized (generation.terminalGate) {
                if (generation.failed.get()) return;
                generation.peer.notify("initialized", Map.of());
            }
        } catch (RuntimeException failure) {
            notificationFailure = failure;
        }
        if (notificationFailure != null) {
            invalidate(generation, new CodexConnectionException(
                    "Codex app-server initialized failed", notificationFailure));
            return;
        }

        CompletableFuture<CodexConnection> ready = null;
        synchronized (lifecycleLock) {
            if (shutdown || current != generation || generation.failed.get()) return;
            if (starting == generation.ready) starting = null;
            ready = generation.ready;
        }
        ready.complete(generation.connection);
    }

    private void invalidate(Generation generation, RuntimeException failure) {
        if (!transitionToTerminal(generation)) return;
        CompletableFuture<CodexConnection> failedReady;
        synchronized (lifecycleLock) {
            if (current == generation) current = null;
            if (starting == generation.ready) starting = null;
            failedReady = generation.ready;
        }
        failedReady.completeExceptionally(failure);
        terminate(generation);
    }

    private static boolean transitionToTerminal(Generation generation) {
        synchronized (generation.terminalGate) {
            return generation.failed.compareAndSet(false, true);
        }
    }

    private void startStderrDrainer(Generation generation) {
        Thread drainer = new Thread(() -> {
            try (InputStream stderr = generation.process.getErrorStream()) {
                stderr.transferTo(OutputStream.nullOutputStream());
            } catch (IOException ignored) {
                // stderr content is diagnostic only; stdout owns protocol connection validity.
            }
        }, "codex-stderr-drainer-" + generation.id);
        drainer.setDaemon(true);
        drainer.start();
    }

    /** 正常终止当前及仍在清理中的 owned generation, 超过 grace 后强制终止. */
    @PreDestroy
    public void shutdown() {
        List<Generation> generations;
        Generation currentGeneration = null;
        CompletableFuture<CodexConnection> startingToFail;
        CompletableFuture<CodexConnection> currentToFail = null;
        synchronized (lifecycleLock) {
            if (shutdown) return;
            shutdown = true;
            startingToFail = starting;
            starting = null;
            if (current != null) {
                currentGeneration = current;
                currentToFail = current.ready;
                current = null;
            }
            generations = List.copyOf(ownedGenerations);
        }
        if (currentGeneration != null) transitionToTerminal(currentGeneration);
        CodexConnectionException failure =
                new CodexConnectionException("Codex process manager is shut down");
        if (startingToFail != null) startingToFail.completeExceptionally(failure);
        if (currentToFail != null && currentToFail != startingToFail) {
            currentToFail.completeExceptionally(failure);
        }
        generations.forEach(this::terminate);
    }

    private void terminate(Generation generation) {
        if (!generation.cleanupStarted.compareAndSet(false, true)) return;
        try {
            if (generation.peer != null) generation.peer.close();
            generation.process.destroy();
            boolean exited = waitFor(generation.process, shutdownGraceMillis);
            if (!exited) {
                generation.process.destroyForcibly();
                waitFor(generation.process, shutdownGraceMillis);
            }
        } finally {
            deleteOwnedDirectory(generation.workingDirectory);
            ownedGenerations.remove(generation);
        }
    }

    private static boolean waitFor(Process process, long graceMillis) {
        try {
            return process.waitFor(graceMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Process startProcess(
            List<String> command,
            Path workingDirectory,
            Map<String, String> environment)
            throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile());
        builder.environment().putAll(environment);
        return builder.start();
    }

    private static Map<String, Object> initializeParams() {
        return Map.of(
                "clientInfo",
                Map.of(
                        "name", "openflash",
                        "title", "OpenFlash",
                        "version", "0.1.0"));
    }

    private static void deleteOwnedDirectory(Path workingDirectory) {
        try (var paths = Files.walk(workingDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Cleanup failure must not expose a local path or block application shutdown.
                }
            });
        } catch (IOException failure) {
            LOGGER.warn("Codex owned working directory cleanup failed");
        }
    }

    /** OS process creation boundary; package visibility keeps fake processes outside production API. */
    @FunctionalInterface
    interface ProcessFactory {
        Process start(
                List<String> command,
                Path workingDirectory,
                Map<String, String> environment) throws IOException;
    }

    /** Failed-launch owned-directory cleanup boundary used to verify recovery semantics. */
    @FunctionalInterface
    interface DirectoryCleaner {
        void delete(Path workingDirectory);
    }

    /** 当前 generation 的 wire peer 与实际 process identity. */
    public record CodexConnection(long generation, JsonlRpcPeer peer, Process process) {}

    /** codex executable 无法 spawn. */
    public static final class CodexNotInstalledException extends RuntimeException {
        public CodexNotInstalledException() {
            super("Codex CLI is not installed");
        }

        private CodexNotInstalledException(IOException failure) {
            super("Codex CLI is not installed", failure);
        }
    }

    /** app-server lifecycle 或 handshake 失败, 消息不包含 wire/local path. */
    public static final class CodexConnectionException extends RuntimeException {
        private CodexConnectionException(String message) {
            super(message);
        }

        private CodexConnectionException(String message, Throwable failure) {
            super(message, failure);
        }
    }

    private static final class Generation {
        private final long id;
        private final Path workingDirectory;
        private final Process process;
        private final CompletableFuture<CodexConnection> ready;
        private final Object terminalGate = new Object();
        private final AtomicBoolean failed = new AtomicBoolean();
        private final AtomicBoolean cleanupStarted = new AtomicBoolean();
        private volatile JsonlRpcPeer peer;
        private volatile CodexConnection connection;

        private Generation(
                long id,
                Path workingDirectory,
                Process process,
                CompletableFuture<CodexConnection> ready) {
            this.id = id;
            this.workingDirectory = workingDirectory;
            this.process = process;
            this.ready = ready;
        }
    }

    private static final class ObservedInputStream extends FilterInputStream {
        private final java.util.function.Consumer<RuntimeException> failureListener;
        private final AtomicBoolean reported = new AtomicBoolean();

        private ObservedInputStream(
                InputStream delegate,
                java.util.function.Consumer<RuntimeException> failureListener) {
            super(delegate);
            this.failureListener = failureListener;
        }

        @Override
        public int read() throws IOException {
            try {
                int value = super.read();
                if (value == -1) report("Codex app-server stdout closed", null);
                return value;
            } catch (IOException failure) {
                report("Codex app-server stdout failed", failure);
                throw failure;
            }
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            try {
                int count = super.read(target, offset, length);
                if (count == -1) report("Codex app-server stdout closed", null);
                return count;
            } catch (IOException failure) {
                report("Codex app-server stdout failed", failure);
                throw failure;
            }
        }

        private void report(String message, Throwable failure) {
            if (reported.compareAndSet(false, true)) {
                failureListener.accept(new CodexConnectionException(message, failure));
            }
        }
    }

    private static final class ObservedOutputStream extends FilterOutputStream {
        private final java.util.function.Consumer<RuntimeException> failureListener;
        private final AtomicBoolean reported = new AtomicBoolean();

        private ObservedOutputStream(
                OutputStream delegate,
                java.util.function.Consumer<RuntimeException> failureListener) {
            super(delegate);
            this.failureListener = failureListener;
        }

        @Override
        public void write(int value) throws IOException {
            try {
                out.write(value);
            } catch (IOException failure) {
                report(failure);
                throw failure;
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            try {
                out.write(bytes, offset, length);
            } catch (IOException failure) {
                report(failure);
                throw failure;
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                out.flush();
            } catch (IOException failure) {
                report(failure);
                throw failure;
            }
        }

        private void report(IOException failure) {
            if (reported.compareAndSet(false, true)) {
                failureListener.accept(
                        new CodexConnectionException("Codex app-server stdin failed", failure));
            }
        }
    }
}
