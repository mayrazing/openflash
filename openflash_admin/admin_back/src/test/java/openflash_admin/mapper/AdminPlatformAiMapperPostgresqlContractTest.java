package openflash_admin.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import openflash_admin.mapper.AdminPlatformAiMapper.CatalogRow;
import openflash_admin.mapper.AdminPlatformAiMapper.EnabledOfferingRow;
import openflash_admin.mapper.AdminPlatformAiMapper.UserAccessOverrideRow;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;

class AdminPlatformAiMapperPostgresqlContractTest {

    private static final String SWITCH =
        "OPENFLASH_ADMIN_PLATFORM_AI_MAPPER_CONTRACT_TEST";

    @Test
    void realPostgresqlAndMybatisPreserveNullableCatalogAndApplyAccessFilters()
            throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv(SWITCH)));
        String rootUrl = environmentOrDefault(
            "OPENFLASH_POSTGRESQL_CONTRACT_URL",
            "jdbc:postgresql://127.0.0.1:5432/openflash_db");
        String username = environmentOrDefault("OPENFLASH_POSTGRESQL_CONTRACT_USER", "postgres");
        String password = environmentOrDefault("OPENFLASH_POSTGRESQL_CONTRACT_PASSWORD", "root");
        String schema = "openflash_admin_platform_ai_"
            + UUID.randomUUID().toString().replace("-", "");

        createSchema(rootUrl, username, password, schema);
        String schemaUrl = jdbcUrlForSchema(rootUrl, schema);
        try {
            createFixture(schemaUrl, username, password);
            DataSource dataSource = new UnpooledDataSource(
                "org.postgresql.Driver", schemaUrl, username, password);
            SqlSessionFactory sessionFactory = sessionFactory(dataSource);

            try (SqlSession session = sessionFactory.openSession()) {
                AdminPlatformAiMapper mapper = session.getMapper(AdminPlatformAiMapper.class);

                assertCatalogContract(mapper);
                assertEnabledOfferingContract(mapper);
                assertUserOverrideContract(mapper);
                assertPointReadContract(mapper);
            }
        } finally {
            dropSchema(rootUrl, username, password, schema);
        }
    }

    private static void assertCatalogContract(AdminPlatformAiMapper mapper) {
        List<CatalogRow> rows = mapper.findCatalogRows();

        CatalogRow empty = rows.stream()
            .filter(row -> "empty-api".equals(row.connectionKey()))
            .findFirst()
            .orElseThrow();
        assertThat(empty.baseUrl()).isEqualTo("https://empty.example.test/v1");
        assertThat(empty.credentialsConfigured()).isTrue();
        assertThat(empty.connectionEnabled()).isTrue();
        assertThat(empty.offeringId()).isNull();
        assertThat(empty.offeringKey()).isNull();
        assertThat(empty.modelKey()).isNull();
        assertThat(empty.offeringEnabled()).isNull();
        assertThat(empty.defaultAccess()).isNull();
        assertThat(empty.offeringSortOrder()).isNull();

        CatalogRow disabled = rows.stream()
            .filter(row -> "disabled-model".equals(row.offeringKey()))
            .findFirst()
            .orElseThrow();
        assertThat(disabled.connectionEnabled()).isTrue();
        assertThat(disabled.offeringEnabled()).isFalse();
        assertThat(disabled.defaultAccess()).isTrue();
    }

    private static void assertEnabledOfferingContract(AdminPlatformAiMapper mapper) {
        assertThat(mapper.findEnabledOfferings())
            .extracting(EnabledOfferingRow::offeringKey)
            .containsExactly("enabled-model", "codex-dynamic", "codex-named");

        EnabledOfferingRow enabled = mapper.findEnabledOfferings().get(0);
        assertThat(enabled.connectionKey()).isEqualTo("enabled-api");
        assertThat(enabled.defaultAccess()).isTrue();
    }

    private static void assertUserOverrideContract(AdminPlatformAiMapper mapper) {
        List<Long> userIds = java.util.stream.LongStream.rangeClosed(1, 100)
            .boxed()
            .toList();

        assertThat(mapper.findUserAccessOverrides(userIds))
            .containsExactly(
                new UserAccessOverrideRow(1L, "enabled-model", false),
                new UserAccessOverrideRow(2L, "enabled-model", true));
    }

    private static void assertPointReadContract(AdminPlatformAiMapper mapper) {
        EnabledOfferingRow enabled = mapper.findEnabledOfferingByKey("enabled-model");
        assertThat(enabled).isNotNull();
        assertThat(enabled.connectionKey()).isEqualTo("enabled-api");
        assertThat(enabled.offeringKey()).isEqualTo("enabled-model");
        assertThat(mapper.findEnabledOfferingByKey("disabled-model")).isNull();
        assertThat(mapper.findEnabledOfferingByKey("disabled-connection-model")).isNull();

        CatalogRow cli = mapper.findCliOffering("codex");
        assertThat(cli).isNotNull();
        assertThat(cli.connectionKey()).isEqualTo("codex-cli");
        assertThat(cli.offeringKey()).isEqualTo("codex-dynamic");
        assertThat(cli.modelKey()).isNull();
        assertThat(cli.connectionEnabled()).isTrue();
        assertThat(cli.offeringEnabled()).isTrue();
        assertThat(mapper.findCliOffering("missing-cli")).isNull();
    }

    private static void createSchema(
            String rootUrl,
            String username,
            String password,
            String schema) throws Exception {
        try (Connection root = DriverManager.getConnection(rootUrl, username, password);
                Statement statement = root.createStatement()) {
            try (ResultSet version = statement.executeQuery("SELECT VERSION()")) {
                assertThat(version.next()).isTrue();
                assertThat(version.getString(1)).containsIgnoringCase("PostgreSQL");
            }
            statement.execute("CREATE SCHEMA \"" + schema + "\"");
        }
    }

    private static void dropSchema(
            String rootUrl,
            String username,
            String password,
            String schema) throws Exception {
        try (Connection root = DriverManager.getConnection(rootUrl, username, password);
                Statement statement = root.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
        }
    }

    private static void createFixture(
            String schemaUrl,
            String username,
            String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
                Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE pw_user (
                  id BIGINT NOT NULL,
                  PRIMARY KEY (id)
                )
                """);
            statement.execute("""
                CREATE TABLE pw_platform_ai_connection (
                  id BIGINT GENERATED BY DEFAULT AS IDENTITY,
                  connection_key VARCHAR(64) NOT NULL,
                  kind VARCHAR(16) NOT NULL,
                  protocol VARCHAR(40) NOT NULL,
                  cli_key VARCHAR(64) DEFAULT NULL,
                  config JSON NOT NULL,
                  credentials_configured SMALLINT NOT NULL DEFAULT 0,
                  enabled SMALLINT NOT NULL DEFAULT 1,
                  sort_order INT NOT NULL DEFAULT 0,
                  PRIMARY KEY (id),
                  CONSTRAINT uk_platform_ai_connection_key UNIQUE (connection_key),
                  CONSTRAINT uk_platform_ai_cli_key UNIQUE (cli_key)
                )
                """);
            statement.execute("""
                CREATE TABLE pw_platform_ai_offering (
                  id BIGINT GENERATED BY DEFAULT AS IDENTITY,
                  connection_id BIGINT NOT NULL,
                  offering_key VARCHAR(64) NOT NULL,
                  model_key VARCHAR(191) DEFAULT NULL,
                  enabled SMALLINT NOT NULL DEFAULT 1,
                  default_access SMALLINT NOT NULL DEFAULT 0,
                  sort_order INT NOT NULL DEFAULT 0,
                  PRIMARY KEY (id),
                  CONSTRAINT uk_platform_ai_offering_key UNIQUE (offering_key),
                  CONSTRAINT fk_platform_ai_offering_connection FOREIGN KEY (connection_id)
                    REFERENCES pw_platform_ai_connection (id) ON DELETE CASCADE
                )
                """);
            statement.execute("""
                CREATE TABLE pw_platform_ai_user_access (
                  user_id BIGINT NOT NULL,
                  offering_id BIGINT NOT NULL,
                  enabled SMALLINT NOT NULL,
                  PRIMARY KEY (user_id, offering_id),
                  CONSTRAINT fk_platform_ai_access_user FOREIGN KEY (user_id)
                    REFERENCES pw_user (id) ON DELETE CASCADE,
                  CONSTRAINT fk_platform_ai_access_offering FOREIGN KEY (offering_id)
                    REFERENCES pw_platform_ai_offering (id) ON DELETE CASCADE
                )
                """);
            statement.executeUpdate("""
                INSERT INTO pw_platform_ai_connection
                  (connection_key, kind, protocol, cli_key, config,
                   credentials_configured, enabled, sort_order)
                VALUES
                  ('empty-api', 'API', 'ANTHROPIC', NULL,
                   json_build_object('baseUrl', 'https://empty.example.test/v1'), 1, 1, 1),
                  ('enabled-api', 'API', 'ANTHROPIC', NULL,
                   json_build_object('baseUrl', 'https://enabled.example.test/v1'), 0, 1, 2),
                  ('disabled-api', 'API', 'ANTHROPIC', NULL,
                   json_build_object('baseUrl', 'https://disabled.example.test/v1'), 0, 0, 3),
                  ('codex-cli', 'CLI', 'CODEX_APP_SERVER', 'codex',
                   json_build_object(), 0, 1, 4)
                """);
            statement.executeUpdate("""
                INSERT INTO pw_platform_ai_offering
                  (connection_id, offering_key, model_key, enabled, default_access, sort_order)
                SELECT id, 'enabled-model', 'model-a', 1, 1, 1
                  FROM pw_platform_ai_connection WHERE connection_key='enabled-api'
                UNION ALL
                SELECT id, 'disabled-model', 'model-b', 0, 1, 2
                  FROM pw_platform_ai_connection WHERE connection_key='enabled-api'
                UNION ALL
                SELECT id, 'disabled-connection-model', 'model-c', 1, 1, 1
                  FROM pw_platform_ai_connection WHERE connection_key='disabled-api'
                UNION ALL
                SELECT id, 'codex-dynamic', NULL, 1, 0, 1
                  FROM pw_platform_ai_connection WHERE connection_key='codex-cli'
                UNION ALL
                SELECT id, 'codex-named', 'gpt-5.4', 1, 0, 2
                  FROM pw_platform_ai_connection WHERE connection_key='codex-cli'
                """);
        }

        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
                PreparedStatement users = connection.prepareStatement(
                    "INSERT INTO pw_user (id) VALUES (?)")) {
            for (long userId = 1; userId <= 101; userId++) {
                users.setLong(1, userId);
                users.addBatch();
            }
            users.executeBatch();
        }

        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO pw_platform_ai_user_access (user_id, offering_id, enabled)
                SELECT 1, id, 0 FROM pw_platform_ai_offering
                  WHERE offering_key='enabled-model'
                UNION ALL
                SELECT 2, id, 1 FROM pw_platform_ai_offering
                  WHERE offering_key='enabled-model'
                UNION ALL
                SELECT 3, id, 1 FROM pw_platform_ai_offering
                  WHERE offering_key='disabled-model'
                UNION ALL
                SELECT 4, id, 1 FROM pw_platform_ai_offering
                  WHERE offering_key='disabled-connection-model'
                UNION ALL
                SELECT 101, id, 1 FROM pw_platform_ai_offering
                  WHERE offering_key='enabled-model'
                """);
        }
    }

    private static SqlSessionFactory sessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(new ClassPathResource(
            "mapper/AdminPlatformAiMapper.xml"));
        return java.util.Objects.requireNonNull(bean.getObject());
    }

    private static String jdbcUrlForSchema(String rootUrl, String schema) {
        String separator = rootUrl.contains("?") ? "&" : "?";
        return rootUrl + separator + "currentSchema=" + schema;
    }

    private static String environmentOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
