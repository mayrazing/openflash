package openflash_plugin.tts.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TtsDeckSpeedMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V80__add_deck_tts_speed.sql");

    @Test
    void migrationAddsBoundedDeckSpeedWithTheExistingDefault() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V80 deck TTS speed migration is missing");

        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("ADD COLUMN `speed` DECIMAL(4,2) NOT NULL DEFAULT 1.00"));
        assertTrue(sql.contains("CHECK (`speed` BETWEEN 0.70 AND 1.20)"));
    }
}
