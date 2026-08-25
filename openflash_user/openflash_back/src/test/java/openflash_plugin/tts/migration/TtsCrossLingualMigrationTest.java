package openflash_plugin.tts.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TtsCrossLingualMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V70__invalidate_zero_shot_tts_cache.sql");

    @Test
    void migrationInvalidatesExistingZeroShotCacheVariants() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V70 TTS cache invalidation migration is missing");

        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("('tts', 'tts.engine-version', 'tts-synthesis-v2', 'STRING'"));
        assertTrue(sql.contains("`value` = VALUES(`value`)"));
    }
}
