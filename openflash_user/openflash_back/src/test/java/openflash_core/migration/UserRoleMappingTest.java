package openflash_core.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserRoleMappingTest {

    @Test
    void userRoleIsReadFromTheDatabaseButNotAcceptedOnInsert() throws IOException {
        String userSource = Files.readString(Path.of(
                "src/main/java/openflash_core/entity/User.java"));
        String mapperXml = Files.readString(Path.of(
                "src/main/resources/mapper/UserMapper.xml"));

        assertTrue(userSource.contains("private String role;"));
        assertTrue(userSource.contains("public String getRole()"));
        assertTrue(userSource.contains("public void setRole(String role)"));
        assertTrue(userSource.contains("private Boolean adminApproved;"));
        assertTrue(userSource.contains("public Boolean getAdminApproved()"));
        assertTrue(mapperXml.contains("column=\"role\" property=\"role\""));
        assertTrue(mapperXml.contains("column=\"admin_approved\" property=\"adminApproved\""));
        assertTrue(statement(mapperXml, "<select id=\"findByUsername\"", "</select>").contains("role"));
        assertTrue(statement(mapperXml, "<select id=\"findById\"", "</select>").contains("role"));

        String insertSql = statement(mapperXml, "<insert id=\"insert\"", "</insert>");
        assertFalse(insertSql.contains("role"));
        assertFalse(insertSql.contains("admin_approved"));

        String activeAdmins = statement(
            mapperXml, "<select id=\"lockActiveAdminIds\"", "</select>");
        assertTrue(activeAdmins.contains("admin_approved = 1"));
    }

    private static String statement(String mapperXml, String startMarker, String endMarker) {
        int start = mapperXml.indexOf(startMarker);
        int end = mapperXml.indexOf(endMarker, start);
        return mapperXml.substring(start, end);
    }
}
