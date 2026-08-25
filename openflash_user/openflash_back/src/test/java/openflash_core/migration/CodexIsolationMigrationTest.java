package openflash_core.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CodexIsolationMigrationTest {

    @Test
    void registersIsolatedCodexHomeAndLoginTimeout() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V52__isolate_codex_home_and_login_timeout.sql"));

        assertTrue(sql.contains("ai.codex-home"));
        assertTrue(sql.contains("~/.local/share/openflash/codex-home"));
        assertTrue(sql.contains("ai.codex-login-timeout-millis"));
        assertTrue(sql.contains("600000"));
        String duplicateUpdate = sql.substring(sql.indexOf("ON DUPLICATE KEY UPDATE"));
        assertFalse(duplicateUpdate.matches("(?is).*\\n\\s*`?value`?\\s*=.*"));
    }
}
