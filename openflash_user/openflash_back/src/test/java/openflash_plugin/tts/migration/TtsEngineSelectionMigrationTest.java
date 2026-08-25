package openflash_plugin.tts.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TtsEngineSelectionMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V78__add_deck_tts_engine_selection.sql");

    @Test
    void migrationAddsDeckEngineAndRegistersOnlySupportedChoices() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V78 TTS engine selection migration is missing");

        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("ADD COLUMN `engine` VARCHAR(32) NOT NULL DEFAULT 'cosyvoice3'"));
        assertTrue(sql.contains("'tts_engine', 'cosyvoice3'"));
        assertTrue(sql.contains("'tts_engine', 'piper'"));
        assertTrue(sql.contains("'tts.piper-engine-version'"));
    }
}
