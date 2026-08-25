package openflash_plugin.ai_card.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CardAiCacheMapperXmlTest {

    @Test
    void saveReadyStoresPromptFingerprintForPromptIdentityInspection() throws Exception {
        String xml = readResource("openflash_plugin/ai_card/mapper/CardAiCacheMapper.xml");

        assertTrue(xml.contains("prompt_fingerprint"));
        assertTrue(xml.contains("on conflict (owner_user_id, content_fingerprint) do update"));
        assertTrue(xml.contains("prompt_fingerprint = excluded.prompt_fingerprint"));
    }

    @Test
    void migrationAddsUniquePromptFingerprintAndCollapsesExistingDuplicates() throws Exception {
        String migration = readResource("db/migration/V11__dedupe_card_ai_cache_by_prompt.sql");

        assertTrue(migration.contains("prompt_fingerprint"));
        assertTrue(migration.contains("uk_pw_card_ai_cache_prompt_fingerprint"));
        assertTrue(migration.contains("delete older"));
    }

    @Test
    void migrationNormalizesPromptFingerprintForCaseAndWhitespace() throws Exception {
        String migration = readResource("db/migration/V12__normalize_card_ai_cache_prompt_identity.sql");

        assertTrue(migration.contains("LOWER"));
        assertTrue(migration.contains("REGEXP_REPLACE"));
        assertTrue(migration.contains("uk_pw_card_ai_cache_prompt_fingerprint"));
    }

    /**
     * 验证最终迁移删除同词条唯一键，让不同用户同词条缓存各自保存。
     */
    @Test
    void migrationDropsUniquePromptFingerprintForUserScopedCache() throws Exception {
        String migration = readResource("db/migration/V22__drop_card_ai_cache_prompt_unique_key.sql");

        assertTrue(migration.contains("DROP INDEX `uk_pw_card_ai_cache_prompt_fingerprint`"));
        assertTrue(migration.contains("pw_card_ai_cache"));
    }

    @Test
    void cacheRowsAndQueriesArePartitionedByOwner() throws Exception {
        String xml = readResource("openflash_plugin/ai_card/mapper/CardAiCacheMapper.xml");
        String migration = readResource("db/migration/V63__scope_ai_cache_by_owner.sql");

        assertTrue(xml.contains("owner_user_id = #{ownerUserId}"));
        assertTrue(xml.contains("owner_user_id, content_fingerprint"));
        assertTrue(migration.contains("ADD COLUMN `owner_user_id`"));
        assertTrue(migration.contains("`owner_user_id`, `content_fingerprint`"));
    }

    @Test
    void migrationRemovesLegacyUnownedCacheAndRequiresOwner() throws Exception {
        String migration = readResource("db/migration/V67__remove_unowned_ai_cache.sql");

        assertTrue(migration.contains("WHERE `owner_user_id` IS NULL"));
        assertTrue(migration.contains("MODIFY COLUMN `owner_user_id` BIGINT NOT NULL"));
        assertTrue(migration.indexOf("WHERE `owner_user_id` IS NULL")
                < migration.indexOf("MODIFY COLUMN `owner_user_id` BIGINT NOT NULL"));
    }

    private String readResource(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
