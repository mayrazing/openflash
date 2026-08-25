package openflash_core.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CardMediaPathIndexMigrationTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/V58__index_card_media_upload_paths.sql");

    @Test
    void addsPrefixIndexCoveringMaximumDirectUploadPathLength() throws Exception {
        assertTrue(Files.exists(MIGRATION), "V58 migration must exist");
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("ALTER TABLE `pw_card_media`"));
        assertTrue(sql.contains("KEY `idx_pw_card_media_media_url` (`media_url`(255))"));
    }
}
