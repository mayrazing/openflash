package openflash_core.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminApprovalMigrationTest {

    @Test
    void migrationRequiresExplicitApprovalWithoutTrustingRootUsername() throws IOException {
        String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V65__require_explicit_admin_approval.sql"));
        String normalized = sql.toLowerCase();

        assertTrue(normalized.contains("admin_approved"));
        assertTrue(normalized.contains("default 0"));
        assertTrue(normalized.contains("admin_approved_at"));
        assertTrue(normalized.contains("admin_approval_source"));
        assertFalse(normalized.contains("where `username` = 'root'"));
        assertFalse(normalized.contains("set `admin_approved` = 1"));
    }
}
