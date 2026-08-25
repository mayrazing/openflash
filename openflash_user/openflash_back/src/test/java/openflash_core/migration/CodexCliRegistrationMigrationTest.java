package openflash_core.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.filesystem.FileSystemResource;
import org.junit.jupiter.api.Test;

class CodexCliRegistrationMigrationTest {

    private static final Path V50 = Path.of(
            "src/main/resources/db/migration/V50__register_codex_cli_ai_provider.sql");
    private static final Path V51 = Path.of(
            "src/main/resources/db/migration/V51__migrate_legacy_codex_cli_provider_key.sql");

    @Test
    void v50RegistersCodexFeatureTimeoutsAndProviderKind() throws Exception {
        String sql = Files.readString(V50);

        assertTrue(sql.contains("feature.ai.codex-cli"));
        assertTrue(sql.contains("ai.codex-timeout-millis"));
        assertTrue(sql.contains("ai.codex-status-timeout-millis"));
        assertTrue(sql.contains("'ai_provider_kind', 'codex-cli'"));
        assertTrue(sql.contains("CODEX_APP_SERVER"));
        assertTrue(sql.contains("VALUES ('feature.ai.codex-cli', 1,"));
        assertTrue(sql.contains("('ai', 'ai.codex-timeout-millis', '90000', 'INT'"));
        assertTrue(sql.contains("('ai', 'ai.codex-status-timeout-millis', '5000', 'INT'"));
        assertTrue(sql.contains("\"builtIn\":true"));
        assertTrue(sql.contains(", 1, 1)"));
    }

    @Test
    void v50RetainsChecksumAlreadyRecordedByFlyway() {
        FileSystemResource resource = new FileSystemResource(
                null, V50.toString(), StandardCharsets.UTF_8, false);

        assertEquals(-1392288079, ChecksumCalculator.calculate(resource));
    }

    @Test
    void v51MovesOnlyLegacyApiRowOffReservedKeyWithoutChangingPayloadOrActiveState()
            throws Exception {
        assertTrue(Files.exists(V51), "V51 must migrate legacy codex-cli provider keys");
        String rename = Files.readString(V51);

        assertTrue(rename.contains("UPDATE `pw_user_ai_config`"));
        assertTrue(rename.contains(
                "SET `provider` = CONCAT('_codex_', LOWER(CONV(`id`, 10, 36)))"));
        assertTrue(rename.contains("WHERE `provider` = 'codex-cli'"));
        assertTrue(rename.contains("JSON_VALID(`config`)"));
        assertTrue(rename.contains("<> 'CODEX_APP_SERVER'"));
        assertFalse(rename.contains("SET `config`"));
        assertFalse(rename.contains("`is_active` ="));

        String firstLegacyKey = migratedLegacyKey(35L);
        String secondLegacyKey = migratedLegacyKey(36L);
        assertEquals("_codex_z", firstLegacyKey);
        assertFalse(firstLegacyKey.equals(secondLegacyKey));
        assertTrue(firstLegacyKey.length() <= 20);
        assertTrue(firstLegacyKey.matches("_codex_[a-z0-9]{1,13}"));
        assertFalse(firstLegacyKey.equals("deepseek"));
    }

    @Test
    void migrationUsesPlannedCodexLocaleKeys() throws Exception {
        String sql = Files.readString(V50);

        assertTrue(sql.contains("\"nameKey\":\"settings.aiCodexCliName\""));
        assertTrue(sql.contains(
                "\"descriptionKey\":\"settings.aiCodexCliSharedLocalAccountDescription\""));
    }

    @Test
    void codexLocaleKeysHaveRealTranslationsInEverySupportedLocale() throws Exception {
        Map<String, String> names = Map.of(
                "zh", "OpenAI 模型",
                "en", "OpenAI model",
                "fi", "OpenAI-malli",
                "de", "OpenAI-Modell");
        Map<String, String> descriptions = Map.of(
                "zh", "由管理员统一提供模型服务.",
                "en", "The model service is provided by the administrator.",
                "fi", "Järjestelmänvalvoja tarjoaa mallipalvelun.",
                "de", "Der Modelldienst wird vom Administrator bereitgestellt.");

        for (Map.Entry<String, String> entry : descriptions.entrySet()) {
            String locale = Files.readString(Path.of(
                    "../openflash_front/src/locales", entry.getKey() + ".json"));
            assertTrue(locale.contains(
                            "\"aiCodexCliName\": \"" + names.get(entry.getKey()) + "\""),
                    entry.getKey() + " locale is missing the Codex display name");
            assertTrue(locale.contains(
                            "\"aiCodexCliSharedLocalAccountDescription\": \""
                                    + entry.getValue() + "\""),
                    entry.getKey() + " locale is missing the shared local account description");
        }
    }

    private static String migratedLegacyKey(long id) {
        return "_codex_" + Long.toUnsignedString(id, 36);
    }
}
