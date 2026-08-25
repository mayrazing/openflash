package openflash_plugin.tts.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TtsEngineSwitchRollbackMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V77__remove_tts_engine_switch.sql");

    @Test
    void migrationRemovesPreviouslyAppliedDeckEngineColumn() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V77 TTS engine switch rollback migration is missing");

        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("ALTER TABLE pw_tts_deck_settings"));
        assertTrue(sql.contains("DROP COLUMN cosyvoice_enabled"));
    }
}
