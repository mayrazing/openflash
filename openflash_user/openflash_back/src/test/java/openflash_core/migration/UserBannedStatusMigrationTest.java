package openflash_core.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserBannedStatusMigrationTest {
    @Test
    void migrationAddsNonBannedDefaultAndActiveAdminLookupIndex() throws Exception {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V54__add_user_banned_status.sql"));

        assertTrue(sql.contains("ADD COLUMN `banned` tinyint NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("ADD INDEX `idx_pw_user_active_admin` (`deleted`, `banned`, `role`)"));
    }
}
