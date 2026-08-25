package openflash_plugin.tts.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TtsNaturalEndingCacheMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V73__invalidate_abrupt_tts_cache.sql");

    @Test
    void migrationInvalidatesAudioWithAbruptEndings() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V73 natural-ending cache migration is missing");

        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("('tts', 'tts.engine-version', 'tts-synthesis-v5', 'STRING'"));
        assertTrue(sql.contains("`value` = VALUES(`value`)"));
    }
}
