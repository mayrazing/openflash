package openflash_core.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CoreAiOwnershipBoundaryTest {

    private static final Path CORE_ROOT = Path.of("src/main/java/openflash_core");
    private static final String PLUGIN_ROOT = "openflash_" + "plugin";
    private static final Path AI_CARD_ROOT = Path.of("src/main/java", PLUGIN_ROOT, "ai_card");

    @Test
    void coreOwnsReusableAiRuntimeAndAiCardOwnsOnlyCardBusiness() throws IOException {
        assertTrue(Files.exists(Path.of("src/main/java/openflash_core/service/AiGateway.java")));
        String pluginSources = readTree(AI_CARD_ROOT);
        assertTrue(pluginSources.contains("import openflash_core.service.AiGateway;"));
        assertFalse(pluginSources.contains("import openflash_core.ai.impl."));
        assertFalse(pluginSources.contains("import openflash_core.ai.codex."));
        assertFalse(pluginSources.contains("import openflash_core.ai.entity."));
        assertFalse(pluginSources.contains("import openflash_core.ai.mapper."));
        assertFalse(pluginSources.contains("import openflash_core.ai.provider."));
        assertFalse(pluginSources.contains("org.springframework.ai."));
        assertFalse(pluginSources.contains("okhttp3."));
        assertFalse(pluginSources.contains("TextEncryptor"));
        assertFalse(pluginSources.contains("UserAiConfigService"));
        assertFalse(pluginSources.contains("/api/settings/ai-config"));
        assertFalse(pluginSources.contains("package " + PLUGIN_ROOT + ".ai_card.codex"));
    }

    @Test
    void coreDoesNotImportConcretePluginCode() throws IOException {
        assertFalse(readTree(CORE_ROOT).contains("import " + PLUGIN_ROOT + "."));
    }

    private static String readTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .map(CoreAiOwnershipBoundaryTest::read)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
