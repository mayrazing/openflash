package openflash_ai_runtime.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import openflash_ai_runtime.entity.PlatformAiConnection;
import openflash_ai_runtime.entity.PlatformAiOffering;
import openflash_ai_runtime.entity.PlatformAiSecret;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;

class RuntimePostgresqlMapperContractTest {

    private static final String SWITCH = "OPENFLASH_AI_RUNTIME_MAPPER_CONTRACT_TEST";

    @Test
    void realPostgresqlExecutesRuntimeJsonBooleanAndUpsertSql() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv(SWITCH)));
        String rootUrl = environmentOrDefault(
                "OPENFLASH_POSTGRESQL_CONTRACT_URL",
                "jdbc:postgresql://127.0.0.1:5432/openflash_db");
        String username = environmentOrDefault("OPENFLASH_POSTGRESQL_CONTRACT_USER", "postgres");
        String password = environmentOrDefault("OPENFLASH_POSTGRESQL_CONTRACT_PASSWORD", "root");
        String schema = "openflash_runtime_mapper_"
                + UUID.randomUUID().toString().replace("-", "");

        createSchema(rootUrl, username, password, schema);
        String schemaUrl = schemaUrl(rootUrl, schema);
        try {
            createFixture(schemaUrl, username, password);
            DataSource dataSource = new UnpooledDataSource(
                    "org.postgresql.Driver", schemaUrl, username, password);
            SqlSessionFactory factory = sessionFactory(dataSource);

            try (SqlSession session = factory.openSession(false)) {
                PlatformAiConnectionMapper connections = session.getMapper(
                        PlatformAiConnectionMapper.class);
                PlatformAiOfferingMapper offerings = session.getMapper(
                        PlatformAiOfferingMapper.class);
                PlatformAiUserAccessMapper access = session.getMapper(
                        PlatformAiUserAccessMapper.class);
                PlatformAiSecretMapper secrets = session.getMapper(
                        PlatformAiSecretMapper.class);

                PlatformAiConnection connection = new PlatformAiConnection(
                        0, "contract-api", "API", "ANTHROPIC", null,
                        "Contract API", "https://one.example.test/v1", false, true, 3);
                assertEquals(1, connections.insert(connection));
                assertTrue(connection.id() > 0);

                PlatformAiConnection inserted = connections.findByKey("contract-api");
                assertEquals("Contract API", inserted.displayName());
                assertEquals("https://one.example.test/v1", inserted.baseUrl());
                assertFalse(inserted.credentialsConfigured());
                assertTrue(inserted.enabled());

                assertEquals(1, connections.update(
                        "contract-api", "https://two.example.test/v1", false, 4));
                assertEquals(1, connections.setCredentialsConfigured("contract-api", true));
                PlatformAiConnection updated = connections.findById(connection.id());
                assertEquals("Contract API", updated.displayName());
                assertEquals("https://two.example.test/v1", updated.baseUrl());
                assertTrue(updated.credentialsConfigured());
                assertFalse(updated.enabled());

                PlatformAiOffering offering = new PlatformAiOffering(
                        0, connection.id(), "contract-model", "model-a", true, false, 2);
                assertEquals(1, offerings.insert(offering));
                assertTrue(offering.id() > 0);
                assertEquals("model-a", offerings.findByKey("contract-model").modelKey());
                assertEquals(1, offerings.update("contract-model", "model-b", false, 5));
                assertFalse(offerings.findByKey("contract-model").enabled());
                assertEquals(1, offerings.update("contract-model", "model-b", true, 5));
                assertTrue(offerings.findByKey("contract-model").enabled());

                PlatformAiOffering dynamicOffering = new PlatformAiOffering(
                        0, connection.id(), "contract-dynamic", null, false, false, 6);
                assertEquals(1, offerings.insert(dynamicOffering));
                assertEquals(1, offerings.updateEnabledByConnectionId(connection.id(), true));
                assertTrue(offerings.findByKey("contract-dynamic").enabled());
                assertEquals(1, offerings.updateDefaultAccess("contract-model", true));
                assertTrue(offerings.findByKey("contract-model").defaultAccess());

                assertEquals(1, access.upsert(7L, offering.id(), true));
                assertEquals(1, access.upsert(7L, offering.id(), false));

                assertEquals(1, secrets.upsert(new PlatformAiSecret(connection.id(), "enc-one")));
                assertEquals(1, secrets.upsert(new PlatformAiSecret(connection.id(), "enc-two")));
                PlatformAiSecret secret = secrets.findByConnectionId(connection.id());
                assertNotNull(secret);
                assertEquals("enc-two", secret.secretEnc());

                session.rollback();
            }
        } finally {
            dropSchema(rootUrl, username, password, schema);
        }
    }

    private static void createSchema(
            String url, String username, String password, String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement();
                ResultSet version = statement.executeQuery("SELECT VERSION()")) {
            assertTrue(version.next());
            assertTrue(version.getString(1).contains("PostgreSQL"));
            statement.execute("CREATE SCHEMA \"" + schema + "\"");
        }
    }

    private static void dropSchema(
            String url, String username, String password, String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
        }
    }

    private static void createFixture(
            String url, String username, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE pw_user (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO pw_user (id) VALUES (7)");
            statement.execute("""
                    CREATE TABLE pw_platform_ai_connection (
                      id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                      connection_key VARCHAR(64) NOT NULL UNIQUE,
                      kind VARCHAR(16) NOT NULL,
                      protocol VARCHAR(40) NOT NULL,
                      cli_key VARCHAR(64) UNIQUE,
                      config JSON NOT NULL,
                      credentials_configured SMALLINT NOT NULL DEFAULT 0,
                      enabled SMALLINT NOT NULL DEFAULT 1,
                      sort_order INT NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE pw_platform_ai_offering (
                      id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                      connection_id BIGINT NOT NULL REFERENCES pw_platform_ai_connection(id)
                        ON DELETE CASCADE,
                      offering_key VARCHAR(64) NOT NULL UNIQUE,
                      model_key VARCHAR(191),
                      enabled SMALLINT NOT NULL DEFAULT 1,
                      default_access SMALLINT NOT NULL DEFAULT 0,
                      sort_order INT NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE pw_platform_ai_user_access (
                      user_id BIGINT NOT NULL REFERENCES pw_user(id) ON DELETE CASCADE,
                      offering_id BIGINT NOT NULL REFERENCES pw_platform_ai_offering(id)
                        ON DELETE CASCADE,
                      enabled SMALLINT NOT NULL,
                      PRIMARY KEY (user_id, offering_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE pw_platform_ai_secret (
                      connection_id BIGINT PRIMARY KEY
                        REFERENCES pw_platform_ai_connection(id) ON DELETE CASCADE,
                      secret_enc TEXT NOT NULL
                    )
                    """);
        }
    }

    private static SqlSessionFactory sessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(
                new ClassPathResource("mapper/PlatformAiConnectionMapper.xml"),
                new ClassPathResource("mapper/PlatformAiOfferingMapper.xml"),
                new ClassPathResource("mapper/PlatformAiUserAccessMapper.xml"),
                new ClassPathResource("mapper/PlatformAiSecretMapper.xml"));
        return java.util.Objects.requireNonNull(bean.getObject());
    }

    private static String schemaUrl(String rootUrl, String schema) {
        return rootUrl + (rootUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    private static String environmentOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
