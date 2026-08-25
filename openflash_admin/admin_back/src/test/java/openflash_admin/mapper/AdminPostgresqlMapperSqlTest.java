package openflash_admin.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
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

class AdminPostgresqlMapperSqlTest {

    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new Configuration();
        for (String resource : new String[] {
                "mapper/AdminPlatformAiMapper.xml",
                "mapper/AdminUserMapper.xml"
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
    void catalogReadsPostgresqlJsonFields() {
        String catalog = sql(
                "openflash_admin.mapper.AdminPlatformAiMapper.findCatalogRows",
                Map.of());

        assertAll(
                () -> assertTrue(catalog.contains("c.config ->> 'displayname'")),
                () -> assertTrue(catalog.contains("c.config ->> 'baseurl'")));
    }

    @Test
    void userLookupAndSearchRemainCaseInsensitive() {
        String lookup = sql(
                "openflash_admin.mapper.AdminUserMapper.findByUsername",
                Map.of("username", "Amy"));
        String search = sql(
                "openflash_admin.mapper.AdminUserMapper.search",
                Map.of("query", "AMY", "limit", 100));

        assertAll(
                () -> assertTrue(lookup.contains("lower(username) = lower(?)")),
                () -> assertTrue(search.contains("u.username ilike")),
                () -> assertTrue(search.contains("u.nickname ilike")));
    }

    private String sql(String statementId, Object parameter) {
        MappedStatement statement = configuration.getMappedStatement(statementId);
        return statement.getBoundSql(parameter).getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
