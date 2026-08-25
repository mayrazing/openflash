package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserMapperXmlTest {

    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new Configuration();
        String resource = "mapper/UserMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void userLookupsLoadAuthVersion() {
        assertTrue(sql("findByUsername", "amy").contains("auth_version"));
        assertTrue(sql("findById", 8L).contains("auth_version"));
        assertTrue(sql("lockById", Map.of("id", 8L)).contains("auth_version"));
    }

    @Test
    void banStatusUpdateAlwaysAdvancesAuthVersion() {
        String sql = sql(
            "updateBannedAndIncrementAuthVersion",
            Map.of("id", 8L, "banned", true)
        );

        assertTrue(sql.contains("auth_version = auth_version + 1"));
    }

    @Test
    void passwordChangeAdvancesAuthVersionInTheSameUpdate() {
        String sql = sql(
            "updatePasswordHashAndIncrementAuthVersion",
            Map.of("id", 8L, "expectedHash", "old", "passwordHash", "new")
        );

        assertTrue(sql.contains("password_hash = ?"));
        assertTrue(sql.contains("auth_version = auth_version + 1"));
        assertTrue(sql.contains("where id = ? and password_hash = ? and deleted = 0"));
    }

    private String sql(String id, Object parameter) {
        MappedStatement statement = configuration.getMappedStatement(
            "openflash_core.mapper.UserMapper." + id
        );
        return statement.getBoundSql(parameter).getSql()
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}
