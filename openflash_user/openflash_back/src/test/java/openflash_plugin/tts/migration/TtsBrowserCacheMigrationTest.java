package openflash_plugin.tts.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TtsBrowserCacheMigrationTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V82__move_tts_cache_to_browser.sql");

    @Test
    void migrationRemovesServerCacheTableAndQuotaConfiguration() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V82 TTS browser cache migration is missing");

        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("DROP TABLE IF EXISTS `pw_tts_cache_meta`"));
        assertTrue(sql.contains("'tts.cache.max-entries'"));
        assertTrue(sql.contains("'tts.cache.max-bytes-per-user'"));
    }
}
