package openflash_plugin.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;

class CorePostgresqlMapperContractTest {

    private static final String SWITCH = "OPENFLASH_CORE_MAPPER_CONTRACT_TEST";

    @Test
    void realPostgresqlExecutesConvertedCoreMapperSql() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv(SWITCH)));
        String rootUrl = environmentOrDefault(
                "OPENFLASH_POSTGRESQL_CONTRACT_URL",
                "jdbc:postgresql://127.0.0.1:5432/openflash_db");
        String username = environmentOrDefault("OPENFLASH_POSTGRESQL_CONTRACT_USER", "postgres");
        String password = environmentOrDefault("OPENFLASH_POSTGRESQL_CONTRACT_PASSWORD", "root");
        String schema = "openflash_core_mapper_"
                + UUID.randomUUID().toString().replace("-", "");

        createSchema(rootUrl, username, password, schema);
        String schemaUrl = schemaUrl(rootUrl, schema);
        try {
            Flyway.configure()
                    .dataSource(schemaUrl, username, password)
                    .locations("classpath:db/migration")
                    .defaultSchema(schema)
                    .schemas(schema)
                    .load()
                    .migrate();

            DataSource dataSource = new UnpooledDataSource(
                    "org.postgresql.Driver", schemaUrl, username, password);
            SqlSessionFactory factory = sessionFactory(dataSource);
            try (SqlSession session = factory.openSession(false)) {
                long userId = insertUserAndCheckCaseInsensitiveLogin(session);
                long deckId = insertDeck(session, userId);
                long cardId = insertCard(session, deckId);

                checkSmallintBooleanAndConflictWrites(session, userId, deckId);
                checkAiSelectionAndSettingsWrites(session, userId, deckId);
                checkAsyncAndJsonCleanup(session, userId);
                checkPaginationCacheAndJoinedDeletes(session, userId, deckId, cardId);
                checkUploadOrdering(session, userId);

                session.rollback();
            }
        } finally {
            dropSchema(rootUrl, username, password, schema);
        }
    }

    private static long insertUserAndCheckCaseInsensitiveLogin(SqlSession session) {
        Map<String, Object> user = new HashMap<>();
        user.put("username", "ContractAmy");
        user.put("passwordHash", "hash");
        user.put("nickname", "Amy");
        assertEquals(1, session.insert("openflash_core.mapper.UserMapper.insert", user));
        long userId = ((Number) user.get("id")).longValue();

        Object found = session.selectOne(
                "openflash_core.mapper.UserMapper.findByUsername",
                Map.of("username", "contractamy"));
        assertNotNull(found);
        assertEquals(1, session.update(
                "openflash_core.mapper.UserMapper.updateBannedAndIncrementAuthVersion",
                Map.of("id", userId, "banned", true)));
        return userId;
    }

    private static long insertDeck(SqlSession session, long userId) throws Exception {
        try (Statement statement = session.getConnection().createStatement();
                ResultSet rows = statement.executeQuery(
                        "INSERT INTO pw_deck (user_id, name) VALUES (" + userId
                                + ", 'contract deck') RETURNING id")) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private static long insertCard(SqlSession session, long deckId) throws Exception {
        try (Statement statement = session.getConnection().createStatement();
                ResultSet rows = statement.executeQuery(
                        "INSERT INTO pw_card (deck_id, side_a, side_b) VALUES (" + deckId
                                + ", 'front', 'back') RETURNING id")) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private static void checkSmallintBooleanAndConflictWrites(
            SqlSession session, long userId, long deckId) {
        Map<String, Object> feature = Map.of(
                "featureKey", "feature.tts", "userId", userId, "enabled", true);
        assertEquals(1, session.insert(
                "openflash_core.mapper.UserFeatureFlagMapper.upsertUserEnabled", feature));
        assertEquals(1, session.insert(
                "openflash_core.mapper.UserFeatureFlagMapper.upsertUserEnabled", feature));

        Map<String, Object> plugin = Map.of(
                "userId", userId, "deckId", deckId, "pluginId", "tts");
        assertEquals(1, session.insert("openflash_core.mapper.PluginInstallMapper.insert", plugin));
        assertEquals(0, session.insert("openflash_core.mapper.PluginInstallMapper.insert", plugin));

        Map<String, Object> practice = Map.of(
                "userId", userId, "deckId", deckId, "data", "{\"step\":1}");
        assertEquals(1, session.insert(
                "openflash_core.mapper.PracticeSessionStoreMapper.upsert", practice));
        assertEquals(1, session.insert(
                "openflash_core.mapper.PracticeSessionStoreMapper.upsert", practice));
    }

    private static void checkAiSelectionAndSettingsWrites(
            SqlSession session, long userId, long deckId) {
        Map<String, Object> aiConfig = new HashMap<>();
        aiConfig.put("userId", userId);
        aiConfig.put("provider", "contract-provider");
        aiConfig.put("configJson", "{\"model\":\"one\"}");
        assertEquals(1, session.insert("openflash_core.mapper.UserAiConfigMapper.upsert", aiConfig));
        aiConfig.put("configJson", "{\"model\":\"two\"}");
        assertEquals(1, session.insert("openflash_core.mapper.UserAiConfigMapper.upsert", aiConfig));

        Map<String, Object> active = new HashMap<>();
        active.put("userId", userId);
        active.put("source", "USER");
        active.put("userProviderKey", "contract-provider");
        active.put("offeringId", null);
        assertEquals(1, session.insert(
                "openflash_core.mapper.UserActiveAiSelectionMapper.upsert", active));
        assertEquals(1, session.insert(
                "openflash_core.mapper.UserActiveAiSelectionMapper.upsert", active));

        Long offeringId = scalarLong(session,
                "SELECT id FROM pw_platform_ai_offering ORDER BY id LIMIT 1");
        assertNotNull(offeringId);
        Map<String, Object> preference = Map.of(
                "userId", userId, "offeringId", offeringId,
                "model", "gpt-contract", "reasoningEffort", "medium");
        assertEquals(1, session.insert(
                "openflash_core.mapper.UserPlatformAiPreferenceMapper.upsert", preference));
        assertEquals(1, session.insert(
                "openflash_core.mapper.UserPlatformAiPreferenceMapper.upsert", preference));

        Map<String, Object> deckSettings = new HashMap<>();
        deckSettings.put("deckId", deckId);
        deckSettings.put("aiExplanationEnabledA", true);
        deckSettings.put("aiExplanationEnabledB", false);
        deckSettings.put("aiExplanationPromptA", "explain a");
        deckSettings.put("aiExplanationPromptB", "explain b");
        deckSettings.put("aiCompletionEnabled", true);
        deckSettings.put("aiCompletionPrompt", "complete");
        deckSettings.put("updatedAt", LocalDateTime.now());
        assertEquals(1, session.insert(
                "openflash_plugin.ai_card.mapper.DeckAiSettingsMapper.upsert", deckSettings));
        assertEquals(1, session.insert(
                "openflash_plugin.ai_card.mapper.DeckAiSettingsMapper.upsert", deckSettings));

        Map<String, Object> mask = Map.of(
                "deckId", deckId, "mode", "MASK_A", "enabled", true);
        assertEquals(1, session.insert(
                "openflash_plugin.mask_mode.mapper.MaskModeDeckSettingsMapper.upsert", mask));
        assertEquals(1, session.insert(
                "openflash_plugin.mask_mode.mapper.MaskModeDeckSettingsMapper.upsert", mask));
    }

    private static void checkAsyncAndJsonCleanup(SqlSession session, long userId) {
        Map<String, Object> task = new HashMap<>();
        task.put("bizKey", "contract-task");
        task.put("taskType", "AI_CACHE_BUILD");
        task.put("payload", "{\"userId\":" + userId + "}");
        task.put("ownerUserId", userId);
        task.put("maxRetryCount", 3);
        task.put("priority", 1);
        task.put("rescheduleFailed", true);
        assertEquals(1, session.insert("openflash_core.mapper.AsyncTaskMapper.upsertTask", task));
        assertEquals(1, session.insert("openflash_core.mapper.AsyncTaskMapper.upsertTask", task));

        assertEquals(1, session.delete(
                "openflash_plugin.ai_card.mapper.AiCardUserTaskCleanupMapper.deleteByUserId",
                Map.of("userId", userId)));
        task.put("bizKey", "contract-invalid-json");
        task.put("payload", "not-json");
        assertEquals(1, session.insert("openflash_core.mapper.AsyncTaskMapper.upsertTask", task));
        assertEquals(0, session.delete(
                "openflash_plugin.ai_card.mapper.AiCardUserTaskCleanupMapper.deleteByUserId",
                Map.of("userId", userId)));

        execute(session, "UPDATE pw_async_task SET status='COMPLETED', updated_at=now() - interval '2 days'");
        assertTrue(session.delete(
                "openflash_core.mapper.AsyncTaskMapper.deleteCompletedBefore",
                Map.of("before", LocalDateTime.now(), "limit", 10)) >= 1);
    }

    private static void checkPaginationCacheAndJoinedDeletes(
            SqlSession session, long userId, long deckId, long cardId) {
        Map<String, Object> page = new HashMap<>();
        page.put("deckId", deckId);
        page.put("userId", userId);
        page.put("keyword", null);
        page.put("state", null);
        page.put("sort", "created_desc");
        page.put("limit", 10);
        page.put("offset", 0);
        assertEquals(1, session.selectList(
                "openflash_core.mapper.CardMapper.findPageByDeckId", page).size());
        session.selectList(
                "openflash_core.mapper.CardMapper.selectTopReviewCards",
                Map.of("deckId", deckId, "userId", userId, "limit", 10));

        String fingerprint = "a".repeat(64);
        Map<String, Object> cache = Map.of(
                "ownerUserId", userId,
                "fingerprint", fingerprint,
                "promptFingerprint", "b".repeat(64),
                "prompt", "unused prompt",
                "content", "generated",
                "thinkUsed", true,
                "accessedAt", LocalDateTime.now().minusDays(2),
                "generatedAt", LocalDateTime.now().minusDays(2));
        assertEquals(1, session.insert(
                "openflash_plugin.ai_card.mapper.CardAiCacheMapper.saveReady", cache));
        assertEquals(1, session.insert(
                "openflash_plugin.ai_card.mapper.CardAiCacheMapper.saveReady", cache));
        assertEquals(1, session.delete(
                "openflash_plugin.ai_card.mapper.CardAiCacheMapper.deleteExpired",
                Map.of("before", LocalDateTime.now(), "limit", 10)));

        execute(session, "INSERT INTO pw_card_media (card_id, card_side, media_url, sort_order)"
                + " VALUES (" + cardId + ", 'A', '/uploads/contract.png', 0)");
        execute(session, "INSERT INTO pw_card_progress"
                + " (user_id, card_id, direction, state, step)"
                + " VALUES (" + userId + ", " + cardId + ", 'A_TO_B', 'new', 0)");
        session.selectOne(
                "openflash_core.mapper.CardMediaMapper.lockFirstReferenceIdByOtherUser",
                Map.of("relativePath", "/uploads/contract.png", "userId", userId + 1));
        assertEquals(1, session.delete(
                "openflash_core.mapper.CardMediaMapper.deleteByDeckId",
                Map.of("deckId", deckId)));
        assertEquals(1, session.delete(
                "openflash_core.mapper.CardProgressMapper.deleteByDeckId",
                Map.of("deckId", deckId)));
    }

    private static void checkUploadOrdering(SqlSession session, long userId) {
        session.insert("openflash_core.mapper.UserUploadMapper.insert",
                Map.of("userId", userId, "relativePath", "/uploads/b.png"));
        session.insert("openflash_core.mapper.UserUploadMapper.insert",
                Map.of("userId", userId, "relativePath", "/uploads/A.png"));
        assertEquals(
                java.util.List.of("/uploads/A.png", "/uploads/b.png"),
                session.selectList(
                        "openflash_core.mapper.UserUploadMapper.findPathsByUserId",
                        Map.of("userId", userId)));
    }

    private static Long scalarLong(SqlSession session, String sql) {
        try (Statement statement = session.getConnection().createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getLong(1) : null;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void execute(SqlSession session, String sql) {
        try (Statement statement = session.getConnection().createStatement()) {
            statement.execute(sql);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void createSchema(
            String url, String username, String password, String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement()) {
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

    private static SqlSessionFactory sessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setMapperLocations(
                new ClassPathResource("mapper/AsyncTaskMapper.xml"),
                new ClassPathResource("mapper/CardMapper.xml"),
                new ClassPathResource("mapper/CardMediaMapper.xml"),
                new ClassPathResource("mapper/CardProgressMapper.xml"),
                new ClassPathResource("mapper/PluginInstallMapper.xml"),
                new ClassPathResource("mapper/PracticeSessionStoreMapper.xml"),
                new ClassPathResource("mapper/UserActiveAiSelectionMapper.xml"),
                new ClassPathResource("mapper/UserAiConfigMapper.xml"),
                new ClassPathResource("mapper/UserFeatureFlagMapper.xml"),
                new ClassPathResource("mapper/UserMapper.xml"),
                new ClassPathResource("mapper/UserPlatformAiPreferenceMapper.xml"),
                new ClassPathResource("mapper/UserUploadMapper.xml"),
                new ClassPathResource(
                        "openflash_plugin/ai_card/mapper/AiCardUserTaskCleanupMapper.xml"),
                new ClassPathResource(
                        "openflash_plugin/ai_card/mapper/CardAiCacheMapper.xml"),
                new ClassPathResource(
                        "openflash_plugin/ai_card/mapper/DeckAiSettingsMapper.xml"),
                new ClassPathResource(
                        "openflash_plugin/mask_mode/mapper/MaskModeDeckSettingsMapper.xml"));
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
