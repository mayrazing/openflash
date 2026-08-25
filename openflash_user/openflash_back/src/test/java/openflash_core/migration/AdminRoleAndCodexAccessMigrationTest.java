package openflash_core.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminRoleAndCodexAccessMigrationTest {

    @Test
    void migrationAddsRolesAndCodexUserAccessWithoutCreatingAnAdminPassword() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V53__add_admin_roles_and_codex_user_access.sql"));

        assertTrue(sql.contains("ADD COLUMN `role` varchar(16) NOT NULL DEFAULT 'USER'"));
        assertTrue(sql.contains("WHERE `username` = 'root'"));
        assertTrue(sql.contains("SET `role` = 'ADMIN'"));
        assertTrue(sql.contains("ADD COLUMN `rollout_type`"));
        assertTrue(sql.contains("CREATE TABLE `pw_user_feature_flag`"));
        assertTrue(sql.contains("feature.ai.codex-cli.user-access"));
        assertTrue(sql.contains("'USER_OVERRIDE'"));
        assertTrue(sql.contains("FROM `pw_user_ai_config`"));
        assertFalse(sql.toLowerCase().contains("insert into `pw_user`"));
    }
}
