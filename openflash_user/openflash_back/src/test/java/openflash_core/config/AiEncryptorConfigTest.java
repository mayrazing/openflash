package openflash_core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.TextEncryptor;

class AiEncryptorConfigTest {

    private final AiEncryptorConfig config = new AiEncryptorConfig();

    @Test
    void rejectsMissingEncryptionPassword() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> config.aiTextEncryptor(" ", "installation-specific-salt"));

        assertTrue(error.getMessage().contains("AI_ENCRYPTOR_PASSWORD"));
    }

    @Test
    void rejectsMissingEncryptionSalt() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> config.aiTextEncryptor("installation-specific-password", " "));

        assertTrue(error.getMessage().contains("AI_ENCRYPTOR_SALT"));
    }

    @Test
    void rejectsRepositoryDefaultEncryptionPassword() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> config.aiTextEncryptor("openflash-dev-password", "installation-specific-salt"));

        assertTrue(error.getMessage().contains("AI_ENCRYPTOR_PASSWORD"));
    }

    @Test
    void configuredEncryptorRoundTripsCredentials() {
        TextEncryptor encryptor = config.aiTextEncryptor(
            "installation-specific-password",
            "installation-specific-salt");

        assertEquals("secret-key", encryptor.decrypt(encryptor.encrypt("secret-key")));
    }

    @Test
    void applicationConfigDoesNotContainKnownEncryptionFallbacks() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));

        assertTrue(yaml.contains("encryptor-password: ${AI_ENCRYPTOR_PASSWORD:}"));
        assertTrue(yaml.contains("encryptor-salt: ${AI_ENCRYPTOR_SALT:}"));
    }
}
