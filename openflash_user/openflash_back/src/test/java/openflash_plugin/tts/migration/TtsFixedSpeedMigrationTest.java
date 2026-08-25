package openflash_plugin.tts.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TtsFixedSpeedMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V81__fix_tts_speed_by_engine.sql");

    @Test
    void migrationRemovesDeckSpeedAndRegistersFixedEngineSpeeds() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V81 fixed TTS speed migration is missing");

        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("DROP COLUMN `speed`"));
        assertTrue(sql.contains("SET `value` = '0.95'"));
        assertTrue(sql.contains("'tts.piper-speed', '0.70'"));
    }
}
