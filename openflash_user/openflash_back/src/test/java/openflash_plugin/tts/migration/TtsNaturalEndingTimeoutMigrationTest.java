package openflash_plugin.tts.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TtsNaturalEndingTimeoutMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V74__extend_tts_request_timeout.sql");
    private static final Path PLUGIN_CONFIG = Path.of(
            "src/main/resources/plugins/tts/application.yaml");

    @Test
    void multiCandidateSynthesisHasEnoughTimeToFinish() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V74 TTS timeout migration is missing");

        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("('tts', 'tts.request-timeout-millis', '30000', 'INT'"));
        assertTrue(sql.contains("`value` = VALUES(`value`)"));

        String yaml = Files.readString(PLUGIN_CONFIG);
        assertTrue(yaml.contains("request-timeout-millis: 30000"));
    }
}
