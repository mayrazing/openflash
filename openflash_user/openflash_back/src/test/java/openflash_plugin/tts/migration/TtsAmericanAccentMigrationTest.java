package openflash_plugin.tts.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TtsAmericanAccentMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V71__configure_american_tts_accent.sql");

    @Test
    void migrationConfiguresAmericanAccentAndInvalidatesOldAudio() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V71 American accent migration is missing");

        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("('tts', 'tts.accent', 'american', 'STRING'"));
        assertTrue(sql.contains("('tts', 'tts.engine-version', 'tts-synthesis-v3', 'STRING'"));
        assertTrue(sql.contains("`value` = VALUES(`value`)"));
    }

    @Test
    void fallbackUsesAmericanAccentAndLatestCacheVersion() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/plugins/tts/application.yaml"));

        assertTrue(yaml.contains("accent: american"));
        assertTrue(yaml.contains("engine-version: tts-synthesis-v5"));
    }
}
