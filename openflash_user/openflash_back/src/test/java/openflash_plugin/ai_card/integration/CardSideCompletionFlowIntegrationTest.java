package openflash_plugin.ai_card.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import openflash_core.OpenFlashApplication;
import openflash_core.common.AiSource;
import openflash_core.config.AiProperties;
import openflash_core.entity.Card;
import openflash_core.entity.Deck;
import openflash_core.entity.User;
import openflash_core.entity.UserActiveAiSelection;
import openflash_core.entity.UserAiConfig;
import openflash_core.mapper.CardMapper;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.UserActiveAiSelectionMapper;
import openflash_core.mapper.UserAiConfigMapper;
import openflash_core.mapper.UserMapper;
import openflash_core.service.impl.AiChatGateway;
import openflash_core.service.impl.AsyncTaskConsumer;
import openflash_plugin.ai_card.service.impl.CardSideCompletionTaskProducer;
import openflash_core.service.ProviderOptionsFactory;
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_plugin.ai_card.mapper.DeckAiSettingsMapper;

@SpringBootTest(classes = OpenFlashApplication.class)
@DirtiesContext
@Import(CardSideCompletionFlowIntegrationTest.Config.class)
@EnabledIfEnvironmentVariable(
    named = "OPENFLASH_CARD_SIDE_COMPLETION_INTEGRATION_TEST",
    matches = "(?i)true"
)
@TestPropertySource(properties = {
    "app.ai.encryptor-password=test-password",
    "app.ai.encryptor-salt=test-salt",
    "spring.main.allow-bean-definition-overriding=true"
})
class CardSideCompletionFlowIntegrationTest {

    private static final String DATABASE_URL = environmentOrDefault(
        "OPENFLASH_POSTGRESQL_CONTRACT_URL",
        "jdbc:postgresql://127.0.0.1:5432/openflash_db"
    );
    private static final String DATABASE_USERNAME = environmentOrDefault(
        "OPENFLASH_POSTGRESQL_CONTRACT_USER",
        "postgres"
    );
    private static final String DATABASE_PASSWORD = environmentOrDefault(
        "OPENFLASH_POSTGRESQL_CONTRACT_PASSWORD",
        "root"
    );
    private static final String SCHEMA = "openflash_card_side_flow_"
        + UUID.randomUUID().toString().replace("-", "");
    private static boolean schemaCreated;

    @DynamicPropertySource
    static void useTemporaryPostgresqlSchema(DynamicPropertyRegistry registry) throws SQLException {
        if (DATABASE_URL.toLowerCase(Locale.ROOT).contains("currentschema=")) {
            throw new IllegalArgumentException(
                "OPENFLASH_POSTGRESQL_CONTRACT_URL must identify the database without currentSchema"
            );
        }
        createSchema();
        registry.add("spring.datasource.url", () -> schemaUrl(DATABASE_URL, SCHEMA));
        registry.add("spring.datasource.username", () -> DATABASE_USERNAME);
        registry.add("spring.datasource.password", () -> DATABASE_PASSWORD);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @AfterAll
    static void dropTemporaryPostgresqlSchema() throws SQLException {
        if (!schemaCreated) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        } finally {
            schemaCreated = false;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean(name = "aiChatGateway") @Primary
        AiChatGateway stubAiChatGateway() {
            AiChatGateway gateway = mock(AiChatGateway.class);
            when(gateway.chat(anyString(), any(AiProperties.AiProfile.class), any(Long.class)))
                .thenReturn("n.苹果;v.苹果（俚）");
            return gateway;
        }

        @Bean
        ProviderOptionsFactory providerOptionsFactory() {
            return profile -> null;
        }
    }

    @Autowired DeckMapper deckMapper;
    @Autowired CardMapper cardMapper;
    @Autowired DeckAiSettingsMapper deckAiSettingsMapper;
    @Autowired AsyncTaskConsumer asyncTaskConsumer;
    @Autowired CardSideCompletionTaskProducer trigger;
    @Autowired UserMapper userMapper;
    @Autowired UserAiConfigMapper userAiConfigMapper;
    @Autowired UserActiveAiSelectionMapper userActiveAiSelectionMapper;

    @Test
    void completionTaskFillsBlankSideAndChainsAiTtsTasks() throws Exception {
        User user = new User();
        user.setUsername("integration-user");
        user.setPasswordHash("integration-password-hash");
        user.setNickname("Integration User");
        userMapper.insert(user);

        UserAiConfig aiConfig = new UserAiConfig();
        aiConfig.setUserId(user.getId());
        aiConfig.setProvider("integration-provider");
        aiConfig.setConfigJson("{\"protocol\":\"anthropic\",\"model\":\"integration-model\"}");
        userAiConfigMapper.upsert(aiConfig);
        userActiveAiSelectionMapper.upsert(new UserActiveAiSelection(
            user.getId(), AiSource.USER, aiConfig.getProvider(), null
        ));

        Deck deck = new Deck();
        deck.setUserId(user.getId());
        deck.setName("integration-deck");
        deckMapper.insert(deck);
        DeckAiSettings settings = new DeckAiSettings();
        settings.setDeckId(deck.getId());
        settings.setAiExplanationEnabledA(false);
        settings.setAiExplanationEnabledB(false);
        settings.setAiCompletionEnabled(true);
        settings.setUpdatedAt(LocalDateTime.now());
        deckAiSettingsMapper.upsert(settings);
        Card card = new Card();
        card.setDeckId(deck.getId());
        card.setSideA("apple");
        card.setSideB("");
        cardMapper.insert(card);

        trigger.triggerCardAfterCommit(card.getId(), user.getId());

        asyncTaskConsumer.consumeClaimableBatch();
        asyncTaskConsumer.consumeClaimableBatch();

        Card updated = cardMapper.findById(card.getId());
        assertNotNull(updated);
        assertEquals("n.苹果;v.苹果（俚）", updated.getSideB());
    }

    private static void createSchema() throws SQLException {
        if (!SCHEMA.matches("openflash_card_side_flow_[a-f0-9]{32}")) {
            throw new IllegalStateException("Refusing unsafe integration-test schema: " + SCHEMA);
        }
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
    }

    private static String schemaUrl(String databaseUrl, String schema) {
        String separator = databaseUrl.contains("?") ? "&" : "?";
        return databaseUrl + separator + "currentSchema=" + schema;
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
