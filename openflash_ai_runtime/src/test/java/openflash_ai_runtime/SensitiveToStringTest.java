package openflash_ai_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import openflash_ai_runtime.service.PlatformAiCatalogService;
import openflash_ai_runtime.entity.PlatformAiSecret;
import openflash_ai_runtime.dto.GenerationProfile;
import openflash_ai_runtime.controller.PlatformAiAdminController;
import openflash_ai_runtime.controller.PlatformAiCoreController;
import openflash_ai_runtime.transport.PlatformAiTransport;
import org.junit.jupiter.api.Test;

class SensitiveToStringTest {

    private static final String API_KEY = "api-key-value-that-must-never-leak";
    private static final String CIPHERTEXT = "ciphertext-that-must-never-leak";
    private static final String PROMPT = "private-user-prompt-that-must-never-leak";
    private static final String SYSTEM = "private-system-prompt-that-must-never-leak";
    private static final UUID REQUEST_ID = UUID.fromString(
            "12345678-1234-4234-9234-123456789abc");

    @Test
    void everyRuntimeSensitiveCarrierRedactsSecretsAndPromptsFromToString() {
        assertRedacted(new PlatformAiSecret(9L, CIPHERTEXT), CIPHERTEXT);
        assertRedacted(new PlatformAiTransport.ConnectionTarget(
                "https://api.example.test", API_KEY), API_KEY);
        assertRedacted(new PlatformAiTransport.GenerateCommand(
                REQUEST_ID, "https://api.example.test", API_KEY, "gpt-5.4",
                PROMPT, SYSTEM, 0.2), API_KEY, PROMPT, SYSTEM);
        assertRedacted(new GenerationProfile("gpt-5.4", SYSTEM, 0.2, "low"), SYSTEM);
        assertRedacted(new PlatformAiCatalogService.GenerationCommand(
                REQUEST_ID, 7L, "platform-api-model", "gpt-5.4", "low",
                PROMPT, SYSTEM, 0.2), PROMPT, SYSTEM);
        assertRedacted(new PlatformAiCoreController.GenerateRequest(
                REQUEST_ID, 7L, "platform-api-model", "gpt-5.4", "low",
                PROMPT, SYSTEM, 0.2), PROMPT, SYSTEM);
        assertRedacted(new PlatformAiAdminController.ReplaceCredentialsRequest(API_KEY), API_KEY);
    }

    @Test
    void redactedGenerationStringsRetainUsefulRequestAndModelIdentifiers() {
        String value = new PlatformAiCatalogService.GenerationCommand(
                REQUEST_ID, 7L, "platform-api-model", "gpt-5.4", "low",
                PROMPT, SYSTEM, 0.2).toString();

        assertThat(value).contains(REQUEST_ID.toString(), "gpt-5.4", "platform-api-model");
    }

    private static void assertRedacted(Object value, String... sensitiveValues) {
        assertThat(value.toString()).doesNotContain(sensitiveValues);
    }
}
