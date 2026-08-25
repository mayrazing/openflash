package openflash_core.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class CodexAdminBoundaryTest {

    private static final Path USER_CONTROLLER =
            Path.of("src/main/java/openflash_core/controller/UserAiConfigController.java");
    private static final Path OLD_INTERNAL_CONTROLLER =
            Path.of("src/main/java/openflash_core/internal_admin/InternalCodexAdminController.java");
    private static final Path INTERNAL_CLI_CONTROLLER =
            Path.of("src/main/java/openflash_core/internal_admin/InternalCliAdminController.java");
    private static final Path INTERNAL_CLI_SERVICE =
            Path.of("src/main/java/openflash_core/internal_admin/InternalCliAdminService.java");
    private static final Path ADMIN_BACKEND =
            Path.of("../../openflash_admin/admin_back/src/main/java");
    private static final Path USER_FRONTEND = Path.of("../openflash_front/src");
    private static final Path ADMIN_FRONTEND = Path.of("../../openflash_admin/admin_front/src");

    @Test
    void userControllerDoesNotExposeDeviceLogin() throws IOException {
        assertFalse(Files.readString(USER_CONTROLLER).contains("/ai-config/codex/login"));
    }

    @Test
    void oldCoreCliAccessEndpointsAreRemoved() {
        assertFalse(Files.exists(OLD_INTERNAL_CONTROLLER));
        assertFalse(Files.exists(INTERNAL_CLI_CONTROLLER));
        assertFalse(Files.exists(INTERNAL_CLI_SERVICE));
    }

    @Test
    void adminBackendDoesNotOwnCodexRuntimeImplementation() throws IOException {
        String source = readTree(ADMIN_BACKEND, path -> path.toString().endsWith(".java"));

        assertFalse(source.contains("CodexProcessManager"));
        assertFalse(source.contains("CodexAppServerClient"));
        assertFalse(source.contains("JsonlRpcPeer"));
        assertFalse(source.contains("account/login/start"));
        assertFalse(source.contains("turn/start"));
    }

    @Test
    void deviceLoginCallsExistOnlyInAdminFrontend() throws IOException {
        Predicate<Path> productionScript = path -> {
            String name = path.getFileName().toString();
            return (name.endsWith(".js") || name.endsWith(".jsx"))
                    && !name.contains(".test.");
        };
        String adminSource = readTree(ADMIN_FRONTEND, productionScript);
        String userSource = readTree(USER_FRONTEND, productionScript);

        assertTrue(adminSource.contains("startCodexLogin"));
        assertTrue(adminSource.contains("cancelCodexLogin"));
        assertFalse(userSource.contains("startCodexLogin"));
        assertFalse(userSource.contains("cancelCodexLogin"));
        assertFalse(userSource.contains("/ai-config/codex/login"));
    }

    private static String readTree(Path root, Predicate<Path> include) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(include)
                    .map(CodexAdminBoundaryTest::read)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read source: " + path, ex);
        }
    }
}
