package openflash_ai_runtime.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuntimePostgresqlMapperSqlTest {

    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new Configuration();
        for (String resource : new String[] {
                "mapper/PlatformAiConnectionMapper.xml",
                "mapper/PlatformAiOfferingMapper.xml",
                "mapper/PlatformAiSecretMapper.xml",
                "mapper/PlatformAiUserAccessMapper.xml"
        }) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                new XMLMapperBuilder(
                        input,
                        configuration,
                        resource,
                        configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void connectionQueriesUsePostgresqlJsonOperators() {
        String find = sql(
                "openflash_ai_runtime.mapper.PlatformAiConnectionMapper.findAll",
                Map.of());
        String insert = sql(
                "openflash_ai_runtime.mapper.PlatformAiConnectionMapper.insert",
                Map.of());
        String update = sql(
                "openflash_ai_runtime.mapper.PlatformAiConnectionMapper.update",
                Map.of());
        String offering = sql(
                "openflash_ai_runtime.mapper.PlatformAiOfferingMapper.findUsableByUserId",
                Map.of());

        assertAll(
                () -> assertTrue(find.contains("config ->> 'displayname'")),
                () -> assertTrue(find.contains("config ->> 'baseurl'")),
                () -> assertTrue(insert.contains("json_build_object")),
                () -> assertTrue(update.contains("jsonb_set")),
                () -> assertTrue(offering.contains("c.config ->> 'baseurl'")),
                () -> assertFalse(find.contains("json_extract")),
                () -> assertFalse(update.contains("json_set")));
    }

    @Test
    void accessAndSecretWritesUsePostgresqlConflictHandling() {
        String access = sql(
                "openflash_ai_runtime.mapper.PlatformAiUserAccessMapper.upsert",
                Map.of());
        String secret = sql(
                "openflash_ai_runtime.mapper.PlatformAiSecretMapper.upsert",
                Map.of());

        assertAll(
                () -> assertTrue(access.contains(
                        "on conflict (user_id, offering_id) do update")),
                () -> assertTrue(secret.contains(
                        "on conflict (connection_id) do update")),
                () -> assertFalse(access.contains("on duplicate key")),
                () -> assertFalse(secret.contains("on duplicate key")));
    }

    @Test
    void booleanParametersTargetingSmallintColumnsAreConvertedExplicitly() {
        String connectionInsert = sql(
                "openflash_ai_runtime.mapper.PlatformAiConnectionMapper.insert",
                Map.of());
        String connectionUpdate = sql(
                "openflash_ai_runtime.mapper.PlatformAiConnectionMapper.update",
                Map.of());
        String credentialsUpdate = sql(
                "openflash_ai_runtime.mapper.PlatformAiConnectionMapper.setCredentialsConfigured",
                Map.of());
        String offeringInsert = sql(
                "openflash_ai_runtime.mapper.PlatformAiOfferingMapper.insert",
                Map.of());
        String offeringUpdate = sql(
                "openflash_ai_runtime.mapper.PlatformAiOfferingMapper.update",
                Map.of());
        String offeringEnabled = sql(
                "openflash_ai_runtime.mapper.PlatformAiOfferingMapper.updateEnabledByConnectionId",
                Map.of());
        String defaultAccess = sql(
                "openflash_ai_runtime.mapper.PlatformAiOfferingMapper.updateDefaultAccess",
                Map.of());
        String userAccess = sql(
                "openflash_ai_runtime.mapper.PlatformAiUserAccessMapper.upsert",
                Map.of());

        assertAll(
                () -> assertTrue(connectionInsert.contains("case when ? then 1 else 0 end")),
                () -> assertTrue(connectionUpdate.contains("enabled = case when ? then 1 else 0 end")),
                () -> assertTrue(credentialsUpdate.contains("credentials_configured = case when ? then 1 else 0 end")),
                () -> assertTrue(offeringInsert.contains("case when ? then 1 else 0 end")),
                () -> assertTrue(offeringUpdate.contains("enabled = case when ? then 1 else 0 end")),
                () -> assertTrue(offeringEnabled.contains("enabled = case when ? then 1 else 0 end")),
                () -> assertTrue(defaultAccess.contains("default_access = case when ? then 1 else 0 end")),
                () -> assertTrue(userAccess.contains("values (?, ?, case when ? then 1 else 0 end)")));
    }

    private String sql(String statementId, Object parameter) {
        MappedStatement statement = configuration.getMappedStatement(statementId);
        return statement.getBoundSql(parameter).getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
