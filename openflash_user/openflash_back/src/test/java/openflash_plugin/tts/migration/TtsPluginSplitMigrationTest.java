package openflash_plugin.tts.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TtsPluginSplitMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V83__split_tts_plugin_by_engine.sql");

    @Test
    void migrationRegistersTwoPluginsAndMigratesByEngine() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V83 TTS plugin split migration is missing");

        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("'feature.tts-cosyvoice3'"));
        assertTrue(sql.contains("'feature.tts-piper'"));
        assertTrue(sql.contains("'plugin', 'tts-cosyvoice3'"));
        assertTrue(sql.contains("'plugin', 'tts-piper'"));
        assertTrue(sql.contains("CASE WHEN tds.`engine` = 'piper' THEN 'tts-piper' ELSE 'tts-cosyvoice3' END"));
        assertTrue(sql.contains("ADD COLUMN `plugin_id` VARCHAR(32) NOT NULL DEFAULT 'tts-cosyvoice3'"));
        assertTrue(sql.contains("ADD PRIMARY KEY (`deck_id`, `plugin_id`)"));
        assertTrue(sql.contains("DROP COLUMN `engine`"));
    }
}
