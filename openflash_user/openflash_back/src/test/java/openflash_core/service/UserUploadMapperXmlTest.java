package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UserUploadMapperXmlTest {

    @Test
    void ownershipLookupLocksExactPathWhileDeleteListingOnlySortsPaths() throws Exception {
        String xml = resource("mapper/UserUploadMapper.xml");

        assertTrue(xml.contains("<select id=\"lockOwnerIdByPath\""));
        assertTrue(xml.contains("SELECT user_id FROM pw_user_upload"));
        assertTrue(xml.contains("WHERE relative_path = #{relativePath}"));
        assertTrue(xml.contains("<select id=\"findPathsByUserId\""));
        assertTrue(xml.contains("WHERE user_id = #{userId} ORDER BY relative_path COLLATE \"C\""));
        assertFalse(xml.contains("<select id=\"lockPathsByUserId\""));
        assertFalse(xml.contains("FORCE INDEX (uk_pw_user_upload_path)"));
    }

    @Test
    void crossUserReferenceQueryJoinsCardOwnership() throws Exception {
        String xml = resource("mapper/CardMediaMapper.xml");

        assertTrue(xml.contains("<select id=\"lockFirstReferenceIdByOtherUser\""));
        assertTrue(xml.contains("select cm.id"));
        assertTrue(xml.contains("from pw_card_media cm"));
        assertTrue(xml.contains("d.user_id &lt;&gt; #{userId}"));
        assertTrue(xml.contains("cm.media_url = #{relativePath}"));
        assertTrue(xml.contains("order by cm.id"));
        assertTrue(xml.contains("limit 1"));
        assertTrue(xml.contains("for update"));
        assertFalse(xml.contains("countReferencesByOtherUser"));
        assertFalse(xml.contains("force index"));
        assertTrue(xml.contains("delete from pw_card_media cm"));
        assertTrue(xml.contains("using pw_card c"));
    }

    private String resource(String name) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
