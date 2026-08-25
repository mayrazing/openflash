package openflash_ai_runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeStartupDocumentationTest {

    @Test
    void readmeDocumentsExactPrivateBindAndHealthEndpoint() throws IOException {
        String readme = Files.readString(Path.of("README.md"));

        assertContains(readme, "openflash_ai_runtime listens on 127.0.0.1:8082 by default.");
        assertContains(readme, "Its safe startup probe is GET http://127.0.0.1:8082/health.");
        assertContains(readme, "Browsers must not connect to openflash_ai_runtime directly.");
    }

    @Test
    void readmeDocumentsRequiredSecretsAndIndependentStartCommand() throws IOException {
        String readme = Files.readString(Path.of("README.md"));

        for (String name : List.of(
                "OPENFLASH_AI_RUNTIME_ADMIN_TOKEN",
                "OPENFLASH_AI_RUNTIME_CORE_TOKEN",
                "OPENFLASH_PLATFORM_AI_ENCRYPTOR_PASSWORD",
                "OPENFLASH_PLATFORM_AI_ENCRYPTOR_SALT")) {
            assertContains(readme, name);
        }
        assertContains(
            readme,
            "OPENFLASH_AI_RUNTIME_ADMIN_TOKEN and OPENFLASH_AI_RUNTIME_CORE_TOKEN must use "
                + "different non-empty values."
        );
        assertContains(readme, "Never print any token or encryption value.");
        assertOrdered(readme, List.of("cd openflash_ai_runtime", "./mvnw spring-boot:run"));
    }

    @Test
    void readmeDocumentsFlywayOwnershipAndOfflineBehavior() throws IOException {
        String readme = Files.readString(Path.of("README.md"));

        assertContains(
            readme,
            "openflash_back is the only Flyway owner. A fresh database needs one successful "
                + "openflash_back startup before admin_back or openflash_ai_runtime can use it."
        );
        assertContains(
            readme,
            "After that initialization, admin_back and openflash_ai_runtime can run while "
                + "openflash_back is offline."
        );
        assertContains(
            readme,
            "Personal AI remains available when openflash_ai_runtime is offline because it stays "
                + "in pw_user_ai_config and is still handled by openflash_back."
        );
    }

    @Test
    void applicationDefaultsKeepRuntimePrivateAndFlywayDisabled() throws IOException {
        String application = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertContains(application, "address: ${OPENFLASH_AI_RUNTIME_ADDRESS:127.0.0.1}");
        assertContains(application, "port: ${OPENFLASH_AI_RUNTIME_PORT:8082}");
        assertContains(application, "flyway:\n    enabled: false");
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Missing runtime startup contract: " + expected);
    }

    private static void assertOrdered(String source, List<String> values) {
        int previous = -1;
        for (String value : values) {
            int current = source.indexOf(value);
            assertTrue(current >= 0, () -> "Missing documentation value: " + value);
            assertTrue(current > previous, () -> "Documentation value is out of order: " + value);
            previous = current;
        }
    }
}
