package openflash_core.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

class PlatformAiCatalogMigrationTest {

    private static final int APPLIED_V59_CHECKSUM = 1170531032;
    private static final Path V59 = Path.of(
            "src/main/resources/db/migration/V59__create_platform_ai_catalog.sql");

    @Test
    void migrationCreatesPlatformCatalogOwnershipAndIntegrityConstraints() throws Exception {
        assertTrue(Files.exists(V59), "V59 platform AI catalog migration must exist");
        String sql = Files.readString(V59);

        assertTrue(sql.contains("CREATE TABLE `pw_platform_ai_connection`"));
        assertTrue(sql.contains("CREATE TABLE `pw_platform_ai_secret`"));
        assertTrue(sql.contains("CREATE TABLE `pw_platform_ai_offering`"));
        assertTrue(sql.contains("CREATE TABLE `pw_platform_ai_user_access`"));
        assertTrue(sql.contains(
                "UNIQUE KEY `uk_platform_ai_connection_key` (`connection_key`)"));
        assertTrue(sql.contains("UNIQUE KEY `uk_platform_ai_cli_key` (`cli_key`)"));
        assertTrue(sql.contains(
                "`dynamic_connection_id` bigint GENERATED ALWAYS AS (\n"
                        + "    CASE WHEN `model_key` IS NULL THEN `connection_id` ELSE NULL END\n"
                        + "  ) VIRTUAL"));
        assertTrue(sql.contains(
                "UNIQUE KEY `uk_platform_ai_dynamic_connection` (`dynamic_connection_id`)"));
        assertTrue(sql.contains("CONSTRAINT `chk_platform_ai_connection_kind` CHECK"));
        assertTrue(sql.contains("CONSTRAINT `chk_platform_ai_connection_cli_key` CHECK"));
        assertTrue(sql.contains("REFERENCES `pw_platform_ai_connection` (`id`) ON DELETE CASCADE"));
        assertTrue(sql.contains("REFERENCES `pw_user` (`id`) ON DELETE CASCADE"));
        assertTrue(sql.contains("REFERENCES `pw_platform_ai_offering` (`id`) ON DELETE CASCADE"));
    }

    @Test
    void migrationSeedsCodexFromLegacyGlobalAndAccessFlags() throws Exception {
        assertTrue(Files.exists(V59), "V59 platform AI catalog migration must exist");
        String sql = Files.readString(V59);

        assertTrue(sql.contains("`default_access` tinyint NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("'platform-codex', 'CLI', 'CODEX_APP_SERVER'"));
        assertTrue(sql.contains("'platform-codex-cli'"));
        assertTrue(sql.contains("'codex'"));
        assertTrue(sql.contains("feature.ai.codex-cli"));
        assertTrue(sql.contains("feature.ai.codex-cli.user-access"));
        assertTrue(sql.contains("COALESCE"));
        assertTrue(sql.contains("FROM `pw_user_feature_flag`"));
        assertTrue(sql.contains("`uff`.`enabled`"));
    }

    @Test
    void migrationKeepsLegacyCodexFlagsAndOverrides() throws Exception {
        assertTrue(Files.exists(V59), "V59 platform AI catalog migration must exist");
        String sql = Files.readString(V59).toLowerCase();

        assertFalse(sql.contains("delete from `pw_feature_flag`"));
        assertFalse(sql.contains("delete from `pw_user_feature_flag`"));
        assertFalse(sql.contains("drop table `pw_user_feature_flag`"));
    }

    @Test
    void migrationKeepsChecksumAlreadyAppliedToDevelopmentDatabases() throws Exception {
        CRC32 checksum = new CRC32();
        for (String line : Files.readAllLines(V59)) {
            checksum.update(line.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(APPLIED_V59_CHECKSUM, (int) checksum.getValue(),
                "Applied Flyway migrations must remain immutable");
    }
}
