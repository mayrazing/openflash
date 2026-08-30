package openflash_core.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PostgresqlDatabaseConfigurationTest {

    private static final Path CORE = Path.of(".");
    private static final Path ADMIN = Path.of("../../openflash_admin/admin_back");
    private static final Path RUNTIME = Path.of("../../openflash_ai_runtime");

    @Test
    void everyBackendUsesOnlyThePostgresqlDriver() throws IOException {
        for (Path module : new Path[] { CORE, ADMIN, RUNTIME }) {
            String build = Files.readString(module.resolve("build.gradle.kts"));
            assertTrue(build.contains("org.postgresql:postgresql"), module.toString());
            assertFalse(build.contains("mysql-connector-j"), module.toString());
        }
    }

    @Test
    void everyBackendTargetsTheOpenflashPostgresqlSchema() throws IOException {
        for (Path module : new Path[] { CORE, ADMIN, RUNTIME }) {
            String application = Files.readString(
                    module.resolve("src/main/resources/application.yaml"));
            assertTrue(application.contains(
                    "jdbc:postgresql://localhost:5432/openflash_db?currentSchema=openflash"),
                    module.toString());
            assertTrue(application.contains("username: ${OPENFLASH_DB_USERNAME:postgres}"),
                    module.toString());
            assertTrue(application.contains("driver-class-name: org.postgresql.Driver"),
                    module.toString());
        }
    }

    @Test
    void coreOwnsPostgresqlFlywayAndOtherBackendsKeepItDisabled() throws IOException {
        String coreBuild = Files.readString(CORE.resolve("build.gradle.kts"));
        assertTrue(coreBuild.contains("flyway-database-postgresql"));
        assertFalse(coreBuild.contains("flyway-mysql"));

        String coreApplication = Files.readString(
                CORE.resolve("src/main/resources/application.yaml"));
        assertTrue(coreApplication.contains("default-schema: openflash"));
        assertTrue(coreApplication.contains("schemas:\n      - openflash"));

        for (Path module : new Path[] { ADMIN, RUNTIME }) {
            String application = Files.readString(
                    module.resolve("src/main/resources/application.yaml"));
            assertTrue(application.contains("flyway:\n    enabled: false"), module.toString());
        }
    }

    @Test
    void freshPostgresqlInstallHasAVersion84Baseline() throws IOException {
        Path baseline = CORE.resolve(
                "src/main/resources/db/migration/B84__postgresql_schema.sql");
        assertTrue(Files.isRegularFile(baseline));

        String sql = Files.readString(baseline);
        assertTrue(sql.contains("CREATE TABLE pw_user"));
        assertTrue(sql.contains("CREATE TABLE pw_card"));
        assertTrue(sql.contains("CREATE TABLE spring_session"));
        assertTrue(sql.contains("CREATE TABLE pw_platform_ai_connection"));
        assertFalse(sql.contains("ENGINE=InnoDB"));
        assertFalse(sql.contains("AUTO_INCREMENT"));
    }
}
