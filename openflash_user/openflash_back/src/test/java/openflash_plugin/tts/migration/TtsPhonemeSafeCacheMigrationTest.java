package openflash_plugin.tts.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TtsPhonemeSafeCacheMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V72__invalidate_instruct_tts_cache.sql");

    @Test
    void migrationInvalidatesAudioGeneratedByInstructMode() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V72 TTS cache invalidation migration is missing");

        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("('tts', 'tts.engine-version', 'tts-synthesis-v4', 'STRING'"));
        assertTrue(sql.contains("`value` = VALUES(`value`)"));
    }
}
