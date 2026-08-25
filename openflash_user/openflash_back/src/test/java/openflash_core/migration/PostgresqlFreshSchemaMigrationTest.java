package openflash_core.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

class PostgresqlFreshSchemaMigrationTest {

    private static final String SCHEMA_PREFIX = "openflash_sdd_migration_";
    private static final String DATABASE_URL = System.getProperty(
            "openflash.migration.postgresql.url",
            "jdbc:postgresql://127.0.0.1:5432/openflash_db");
    private static final String USERNAME = System.getProperty(
            "openflash.migration.postgresql.username", "postgres");
    private static final String PASSWORD = System.getProperty(
            "openflash.migration.postgresql.password", "root");
    private static final String EXISTING_SCHEMA = System.getProperty(
            "openflash.migration.postgresql.existing-schema", "openflash");

    @Test
    void migratedOpenflashSchemaHistoryRemainsValidWithoutWritingToIt() throws Exception {
        assumeTrue(
                Boolean.getBoolean("openflash.migration.postgresql.existing-enabled"),
                "Set -Dopenflash.migration.postgresql.existing-enabled=true to validate the migrated schema");
        int historyRowsBefore = scalarInt(EXISTING_SCHEMA,
                "SELECT COUNT(*) FROM flyway_schema_history");

        flywayForReadOnlyValidation(EXISTING_SCHEMA).validate();

        assertEquals(historyRowsBefore, scalarInt(EXISTING_SCHEMA,
                "SELECT COUNT(*) FROM flyway_schema_history"));
    }

    @Test
    void emptySchemaUsesVersion84BaselineAndVersion85Defaults() throws Exception {
        requireRealPostgresql();
        withSchema(schema -> {
            Flyway flyway = flyway(schema);

            MigrateResult firstStart = flyway.migrate();

            assertEquals(4, firstStart.migrationsExecuted);
            assertEquals(4, scalarInt(schema,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success"));
            assertEquals(1, scalarInt(schema,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'SQL_BASELINE'"));
            assertEquals(87, scalarInt(schema,
                    "SELECT MAX(version::integer) FROM flyway_schema_history"));
            assertEquals(29, scalarInt(schema, """
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = current_schema()
                    """));
            assertEquals(8, scalarInt(schema, "SELECT COUNT(*) FROM pw_feature_flag"));
            assertEquals(37, scalarInt(schema, "SELECT COUNT(*) FROM pw_system_config"));
            assertEquals(23, scalarInt(schema, "SELECT COUNT(*) FROM pw_type_registry"));
            assertEquals(1, scalarInt(schema,
                    "SELECT COUNT(*) FROM pw_platform_ai_connection"
                            + " WHERE connection_key = 'platform-codex'"));
            assertEquals(0, scalarInt(schema, "SELECT COUNT(*) FROM pw_user"));
            assertEquals("ALWAYS", scalarString(schema, """
                    SELECT is_generated FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'pw_platform_ai_offering'
                      AND column_name = 'dynamic_connection_id'
                    """));
            assertEquals(18, scalarInt(schema, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND column_name = 'updated_at'
                      AND is_nullable = 'NO'
                      AND column_default IS NOT NULL
                      AND table_name IN (
                        'pw_async_task', 'pw_card', 'pw_card_ai_cache', 'pw_card_progress',
                        'pw_deck', 'pw_deck_ai_settings', 'pw_feature_flag',
                        'pw_mask_mode_deck_settings', 'pw_platform_ai_connection',
                        'pw_platform_ai_secret', 'pw_practice_session_store',
                        'pw_system_config', 'pw_type_registry', 'pw_user',
                        'pw_user_ai_config', 'pw_user_feature_flag',
                        'pw_user_platform_ai_preference', 'pw_user_settings'
                      )
                    """));
            assertEquals(37, scalarInt(schema, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND data_type = 'timestamp without time zone'
                    """));
            assertEquals(0, scalarInt(schema, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND data_type = 'timestamp with time zone'
                      AND table_name <> 'flyway_schema_history'
                    """));
            assertThrows(SQLException.class, () -> execute(schema, """
                    INSERT INTO pw_platform_ai_offering
                        (id, connection_id, offering_key, model_key, enabled, default_access, sort_order)
                    VALUES (2, 1, 'duplicate-dynamic-connection', NULL, 1, 0, 1)
                    """));

            Flyway restarted = flyway(schema);
            assertTrue(restarted.validateWithResult().validationSuccessful);
            assertEquals(0, restarted.migrate().migrationsExecuted);
        });
    }

    @Test
    void version85PreservesAdministratorChoicesAndRestoresMissingDefaults() throws Exception {
        requireRealPostgresql();
        withSchema(schema -> {
            assertEquals(1, flywayAtVersion84(schema).migrate().migrationsExecuted);
            execute(schema,
                    "DROP INDEX idx_16504_uk_platform_ai_dynamic_connection",
                    "ALTER TABLE pw_platform_ai_offering DROP COLUMN dynamic_connection_id",
                    "ALTER TABLE pw_platform_ai_offering ADD COLUMN dynamic_connection_id bigint",
                    "CREATE UNIQUE INDEX idx_16504_uk_platform_ai_dynamic_connection"
                            + " ON pw_platform_ai_offering (dynamic_connection_id)",
                    "ALTER TABLE pw_feature_flag ALTER COLUMN updated_at DROP DEFAULT",
                    "ALTER TABLE pw_feature_flag ALTER COLUMN updated_at DROP NOT NULL",
                    """
                    INSERT INTO pw_feature_flag
                        (id, feature_key, enabled, rollout_type, description)
                    VALUES (14, 'feature.tts', 0, 'GLOBAL', 'administrator choice')
                    """,
                    """
                    INSERT INTO pw_system_config
                        (id, group_name, config_key, value, value_type, description)
                    VALUES (1, 'tts', 'tts.voice', 'review-custom-voice', 'STRING',
                            'administrator choice')
                    """,
                    """
                    INSERT INTO pw_type_registry
                        (id, registry_type, item_key, item_name, config, sort_order, enabled)
                    VALUES (28, 'plugin', 'tts', 'tts', '{}', 1, 0)
                    """);

            assertEquals(3, flyway(schema).migrate().migrationsExecuted);

            assertEquals(0, scalarInt(schema,
                    "SELECT enabled FROM pw_feature_flag WHERE feature_key = 'feature.tts'"));
            assertEquals("review-custom-voice", scalarString(schema,
                    "SELECT value FROM pw_system_config WHERE config_key = 'tts.voice'"));
            assertEquals(0, scalarInt(schema, """
                    SELECT enabled FROM pw_type_registry
                    WHERE registry_type = 'plugin' AND item_key = 'tts'
                    """));
            assertEquals(1, scalarInt(schema,
                    "SELECT COUNT(*) FROM pw_feature_flag WHERE feature_key = 'feature.mask-mode'"));
            assertEquals("ALWAYS", scalarString(schema, """
                    SELECT is_generated FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'pw_platform_ai_offering'
                      AND column_name = 'dynamic_connection_id'
                    """));
            assertEquals(1, scalarInt(schema, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'pw_feature_flag'
                      AND column_name = 'updated_at'
                      AND is_nullable = 'NO'
                      AND column_default IS NOT NULL
                    """));
            assertEquals(0, scalarInt(schema,
                    "SELECT COUNT(*) FROM pw_feature_flag WHERE updated_at IS NULL"));
        });
    }

    private static void requireRealPostgresql() {
        assumeTrue(
                Boolean.getBoolean("openflash.migration.postgresql.enabled"),
                "Set -Dopenflash.migration.postgresql.enabled=true to run temporary-schema PostgreSQL checks");
    }

    private static Flyway flyway(String schema) {
        return Flyway.configure()
                .dataSource(schemaUrl(schema), USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .defaultSchema(schema)
                .schemas(schema)
                .validateOnMigrate(true)
                .load();
    }

    private static Flyway flywayForReadOnlyValidation(String schema) {
        return Flyway.configure()
                .dataSource(schemaUrl(schema), USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .defaultSchema(schema)
                .schemas(schema)
                .ignoreMigrationPatterns("*:pending")
                .load();
    }

    private static Flyway flywayAtVersion84(String schema) {
        return Flyway.configure()
                .dataSource(schemaUrl(schema), USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .defaultSchema(schema)
                .schemas(schema)
                .target("84")
                .load();
    }

    private static void withSchema(SchemaCheck check) throws Exception {
        String schema = SCHEMA_PREFIX
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (!schema.matches("openflash_sdd_migration_[a-f0-9]{12}")) {
            throw new IllegalStateException("Refusing unsafe migration test schema: " + schema);
        }

        try (Connection connection = databaseConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }

        try {
            check.run(schema);
        } finally {
            try (Connection connection = databaseConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private static int scalarInt(String schema, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                    schemaUrl(schema), USERNAME, PASSWORD);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static String scalarString(String schema, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                    schemaUrl(schema), USERNAME, PASSWORD);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static void execute(String schema, String... sqlStatements) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                    schemaUrl(schema), USERNAME, PASSWORD);
                Statement statement = connection.createStatement()) {
            for (String sql : sqlStatements) {
                statement.execute(sql);
            }
        }
    }

    private static Connection databaseConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL, USERNAME, PASSWORD);
    }

    private static String schemaUrl(String schema) {
        String separator = DATABASE_URL.contains("?") ? "&" : "?";
        return DATABASE_URL + separator + "currentSchema=" + schema;
    }

    @FunctionalInterface
    private interface SchemaCheck {
        void run(String schema) throws Exception;
    }
}
