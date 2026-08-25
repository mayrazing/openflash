package openflash_core.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PostgresqlUsernameUniquenessMigrationTest {

    @Test
    void usernameRemainsCaseInsensitiveAndUniqueAfterMysqlMigration() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V86__preserve_case_insensitive_username.sql"))
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("create unique index"));
        assertTrue(sql.contains("on pw_user (lower(username))"));
    }
}
