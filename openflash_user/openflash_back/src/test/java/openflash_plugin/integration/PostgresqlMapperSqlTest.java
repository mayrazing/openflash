package openflash_plugin.integration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostgresqlMapperSqlTest {

    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new Configuration();
        for (String resource : new String[] {
                "mapper/AsyncTaskMapper.xml",
                "mapper/CardMediaMapper.xml",
                "mapper/CardMapper.xml",
                "mapper/CardProgressMapper.xml",
                "mapper/PluginInstallMapper.xml",
                "mapper/PracticeSessionStoreMapper.xml",
                "mapper/UserActiveAiSelectionMapper.xml",
                "mapper/UserAiConfigMapper.xml",
                "mapper/UserFeatureFlagMapper.xml",
                "mapper/UserMapper.xml",
                "mapper/UserPlatformAiPreferenceMapper.xml",
                "mapper/UserUploadMapper.xml",
                "openflash_plugin/ai_card/mapper/AiCardUserTaskCleanupMapper.xml",
                "openflash_plugin/ai_card/mapper/CardAiCacheMapper.xml",
                "openflash_plugin/ai_card/mapper/DeckAiSettingsMapper.xml",
                "openflash_plugin/mask_mode/mapper/MaskModeDeckSettingsMapper.xml"
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
    void duplicateWritesUsePostgresqlConflictHandling() {
        assertConflict(
                "openflash_core.mapper.AsyncTaskMapper.upsertTask",
                Map.of("rescheduleFailed", false),
                "on conflict (biz_key) do update");
        assertConflict(
                "openflash_core.mapper.UserAiConfigMapper.upsert",
                Map.of(),
                "on conflict (user_id, provider) do update");
        assertConflict(
                "openflash_core.mapper.UserFeatureFlagMapper.upsertUserEnabled",
                Map.of(),
                "on conflict (user_id, feature_key) do update");
        assertConflict(
                "openflash_core.mapper.UserActiveAiSelectionMapper.upsert",
                Map.of(),
                "on conflict (user_id) do update");
        assertConflict(
                "openflash_core.mapper.UserPlatformAiPreferenceMapper.upsert",
                Map.of(),
                "on conflict (user_id, offering_id) do update");
        assertConflict(
                "openflash_core.mapper.PracticeSessionStoreMapper.upsert",
                Map.of(),
                "on conflict (user_id, deck_id) do update");
        assertConflict(
                "openflash_plugin.ai_card.mapper.CardAiCacheMapper.saveReady",
                Map.of(),
                "on conflict (owner_user_id, content_fingerprint) do update");
        assertConflict(
                "openflash_plugin.ai_card.mapper.DeckAiSettingsMapper.upsert",
                Map.of(),
                "on conflict (deck_id) do update");
        assertConflict(
                "openflash_plugin.mask_mode.mapper.MaskModeDeckSettingsMapper.upsert",
                Map.of(),
                "on conflict (deck_id) do update");

        String pluginInstall = sql(
                "openflash_core.mapper.PluginInstallMapper.insert",
                Map.of());
        assertAll(
                () -> assertTrue(pluginInstall.contains("on conflict (user_id, deck_id, plugin_id) do nothing")),
                () -> assertFalse(pluginInstall.contains("insert ignore")));
    }

    @Test
    void paginationAndBoundedDeletesUsePostgresqlSyntax() {
        Map<String, Object> page = new HashMap<>();
        page.put("keyword", null);
        page.put("state", null);
        page.put("sort", "created_desc");
        String pageSql = sql("openflash_core.mapper.CardMapper.findPageByDeckId", page);
        String taskDelete = sql(
                "openflash_core.mapper.AsyncTaskMapper.deleteCompletedBefore",
                Map.of());
        String cacheDelete = sql(
                "openflash_plugin.ai_card.mapper.CardAiCacheMapper.deleteExpired",
                Map.of());

        assertAll(
                () -> assertTrue(pageSql.contains("limit ? offset ?")),
                () -> assertFalse(pageSql.contains("limit ?, ?")),
                () -> assertTrue(taskDelete.contains("with rows_to_delete as")),
                () -> assertTrue(taskDelete.contains("using rows_to_delete")),
                () -> assertTrue(cacheDelete.contains("with rows_to_delete as")),
                () -> assertTrue(cacheDelete.contains("using rows_to_delete")));
    }

    @Test
    void aggregateJsonAndBinaryQueriesUsePostgresqlSyntax() {
        String topReview = sql(
                "openflash_core.mapper.CardMapper.selectTopReviewCards",
                Map.of());
        String cacheDelete = sql(
                "openflash_plugin.ai_card.mapper.CardAiCacheMapper.deleteExpired",
                Map.of());
        String taskCleanup = sql(
                "openflash_plugin.ai_card.mapper.AiCardUserTaskCleanupMapper.deleteByUserId",
                Map.of());
        String uploadPaths = sql(
                "openflash_core.mapper.UserUploadMapper.findPathsByUserId",
                Map.of());

        assertAll(
                () -> assertTrue(topReview.contains("having coalesce(sum(cp.reps), 0) > 0")),
                () -> assertFalse(topReview.contains("having reps > 0")),
                () -> assertFalse(cacheDelete.contains("having content")),
                () -> assertTrue(taskCleanup.contains("payload is json")),
                () -> assertTrue(taskCleanup.contains("payload::jsonb")),
                () -> assertTrue(taskCleanup.contains("#>>")),
                () -> assertFalse(taskCleanup.contains("json_valid")),
                () -> assertTrue(uploadPaths.contains("collate \"c\"")),
                () -> assertFalse(uploadPaths.contains("order by binary")));
    }

    @Test
    void usernameLookupRemainsCaseInsensitive() {
        String lookup = sql(
                "openflash_core.mapper.UserMapper.findByUsername",
                Map.of("username", "Amy"));

        assertTrue(lookup.contains("lower(username) = lower(?)"));
    }

    @Test
    void booleanParametersTargetingSmallintColumnsAreConvertedExplicitly() {
        String userBan = sql(
                "openflash_core.mapper.UserMapper.updateBannedAndIncrementAuthVersion",
                Map.of());
        String featureOverride = sql(
                "openflash_core.mapper.UserFeatureFlagMapper.upsertUserEnabled",
                Map.of());

        assertAll(
                () -> assertTrue(userBan.contains("banned = case when ? then 1 else 0 end")),
                () -> assertTrue(featureOverride.contains("values (?, ?, case when ? then 1 else 0 end)")));
    }

    @Test
    void joinedDeletesAndMediaLookupUsePostgresqlSyntax() {
        String mediaLookup = sql(
                "openflash_core.mapper.CardMediaMapper.lockFirstReferenceIdByOtherUser",
                Map.of());
        String mediaDelete = sql(
                "openflash_core.mapper.CardMediaMapper.deleteByDeckId",
                Map.of());
        String progressDelete = sql(
                "openflash_core.mapper.CardProgressMapper.deleteByDeckId",
                Map.of());

        assertAll(
                () -> assertFalse(mediaLookup.contains("force index")),
                () -> assertTrue(mediaDelete.contains("delete from pw_card_media cm using pw_card c")),
                () -> assertTrue(progressDelete.contains("delete from pw_card_progress cp using pw_card c")));
    }

    private void assertConflict(String statementId, Object parameter, String conflictClause) {
        String sql = sql(statementId, parameter);
        assertAll(
                () -> assertTrue(sql.contains(conflictClause), statementId),
                () -> assertFalse(sql.contains("on duplicate key"), statementId),
                () -> assertFalse(sql.contains("last_insert_id"), statementId),
                () -> assertFalse(sql.contains("values("), statementId));
    }

    private String sql(String statementId, Object parameter) {
        MappedStatement statement = configuration.getMappedStatement(statementId);
        return statement.getBoundSql(parameter).getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
