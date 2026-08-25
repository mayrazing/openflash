package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import openflash_core.dto.AiClientConfigDto;
import openflash_core.common.AiErrorCode;
import openflash_core.service.UserAiConfigService;
import openflash_core.entity.UserAiConfig;
import openflash_core.mapper.UserAiConfigMapper;
import openflash_core.mapper.UserActiveAiSelectionMapper;
import openflash_core.common.AppException;

class UserAiConfigServiceImplTest {

    @Test
    void mapperComputesPersonalActiveStateFromUnifiedSelection() throws Exception {
        String xml = new ClassPathResource("mapper/UserAiConfigMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(xml.contains("JOIN pw_user_active_ai_selection"));
        assertTrue(xml.contains("s.source = 'USER'"));
        assertTrue(xml.contains("s.user_provider_key = c.provider"));
        assertTrue(!xml.contains("INSERT INTO pw_user_ai_config (user_id, provider, config, is_active)"));
    }

    @Test
    void listProvidersUsesCurrentUserScope() {
        UserAiConfig row = config("deepseek", "{}");
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        when(mapper.findAllByUserId(7L)).thenReturn(List.of(row));

        assertEquals(List.of(row), svc(mapper).listProviders(7L));

        verify(mapper).findAllByUserId(7L);
    }

    @Test
    void findProviderUsesUserScopedLookup() {
        UserAiConfig row = config("deepseek", "{}");
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        when(mapper.findByUserIdAndProvider(7L, "deepseek")).thenReturn(row);

        assertSame(row, svc(mapper).findProvider(7L, "deepseek"));

        verify(mapper).findByUserIdAndProvider(7L, "deepseek");
    }

    @Test
    void saveProviderStoresAnthropicConfigAndEncryptsKey() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        TextEncryptor encryptor = mock(TextEncryptor.class);
        when(encryptor.encrypt("sk-plain")).thenReturn("ENC");

        svc(mapper, encryptor).saveProvider(
                7L, "deepseek", "DeepSeek", "https://web", "note",
                "https://api.example.test", "sk-plain", "deepseek-chat", "high");

        verify(encryptor).encrypt("sk-plain");
        verify(mapper).upsert(argThat(row -> row.getUserId().equals(7L)
                && "deepseek".equals(row.getProvider())
                && "ANTHROPIC".equals(row.getConfigValue("protocol"))
                && "DeepSeek".equals(row.getConfigValue("displayName"))
                && "https://web".equals(row.getConfigValue("website"))
                && "note".equals(row.getConfigValue("note"))
                && "https://api.example.test".equals(row.getConfigValue("baseUrl"))
                && "ENC".equals(row.getConfigValue("apiKeyEnc"))
                && "deepseek-chat".equals(row.getConfigValue("model"))
                && "high".equals(row.getConfigValue("reasoningEffort"))
                && row.getConfigValue("apiKey") == null));
    }

    @Test
    void saveProviderKeepsExistingEncryptedKeyWhenPlainKeyBlank() {
        UserAiConfig existing = config("deepseek",
                "{\"protocol\":\"ANTHROPIC\",\"apiKeyEnc\":\"OLD\",\"model\":\"old\"}");
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        when(mapper.findByUserIdAndProvider(7L, "deepseek")).thenReturn(existing);
        TextEncryptor encryptor = mock(TextEncryptor.class);

        svc(mapper, encryptor).saveProvider(
                7L, "deepseek", "DeepSeek", "", "",
                "https://api.example.test", "   ", "deepseek-chat");

        verify(encryptor, never()).encrypt(any());
        verify(mapper).upsert(argThat(row -> "OLD".equals(row.getConfigValue("apiKeyEnc"))));
    }

    @Test
    void saveProviderRejectsBlankKeyWhenNoSavedKeyExists() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);

        AppException failure = assertThrows(AppException.class,
                () -> svc(mapper).saveProvider(
                        7L, "deepseek", "DeepSeek", "", "",
                        "https://api.example.test", null, "deepseek-chat"));

        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, failure.getErrorCode());
        verify(mapper, never()).upsert(any());
    }

    @Test
    void saveProviderRejectsReservedCodexKey() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);

        AppException failure = assertThrows(AppException.class,
                () -> svc(mapper).saveProvider(
                        7L, UserAiConfigService.CODEX_PROVIDER_KEY, "Codex", "", "",
                        "https://api.example.test", "sk", "model"));

        assertEquals(AiErrorCode.AI_PROVIDER_RESERVED, failure.getErrorCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void migratedLegacyCodexKeyRemainsEditableAsPersonalProvider() {
        String migratedKey = "_codex_z";
        UserAiConfig existing = config(migratedKey,
                "{\"protocol\":\"ANTHROPIC\",\"apiKeyEnc\":\"OLD\",\"model\":\"old\"}");
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        when(mapper.findByUserIdAndProvider(7L, migratedKey)).thenReturn(existing);

        svc(mapper).saveProvider(
                7L, migratedKey, "Legacy Codex API", "", "",
                "https://api.example.test", "", "new-model");

        verify(mapper).upsert(argThat(row -> migratedKey.equals(row.getProvider())
                && "new-model".equals(row.getConfigValue("model"))));
    }

    @Test
    void createProviderGeneratesStableKeyFromDisplayName() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        TextEncryptor encryptor = mock(TextEncryptor.class);
        when(encryptor.encrypt("sk-plain")).thenReturn("ENC");

        svc(mapper, encryptor).createProvider(
                7L, "DeepSeek Chat", "", "", "https://api.example.test",
                "sk-plain", "deepseek-chat");

        verify(mapper).upsert(argThat(row -> "deepseek-chat".equals(row.getProvider())));
    }

    @Test
    void createProviderAppendsSuffixWhenGeneratedKeyExists() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        when(mapper.findByUserIdAndProvider(7L, "deepseek"))
                .thenReturn(config("deepseek", "{}"));
        TextEncryptor encryptor = mock(TextEncryptor.class);
        when(encryptor.encrypt("sk-plain")).thenReturn("ENC");

        svc(mapper, encryptor).createProvider(
                7L, "DeepSeek", "", "", "https://api.example.test",
                "sk-plain", "deepseek-chat");

        verify(mapper).upsert(argThat(row -> "deepseek-2".equals(row.getProvider())));
    }

    @Test
    void createProviderSkipsReservedCodexKey() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        TextEncryptor encryptor = mock(TextEncryptor.class);
        when(encryptor.encrypt("sk-plain")).thenReturn("ENC");

        svc(mapper, encryptor).createProvider(
                7L, "Codex CLI", "", "", "https://api.example.test",
                "sk-plain", "model");

        verify(mapper).upsert(argThat(row -> "codex-cli-2".equals(row.getProvider())));
    }

    @Test
    void deleteProviderDeletesMatchingUserSelection() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        UserActiveAiSelectionMapper selections = mock(UserActiveAiSelectionMapper.class);
        when(mapper.deleteByUserIdAndProvider(7L, "deepseek")).thenReturn(1);

        svc(mapper, mock(TextEncryptor.class), selections)
                .deleteProvider(7L, "deepseek");

        verify(mapper).deleteByUserIdAndProvider(7L, "deepseek");
        verify(selections).deleteUserProviderSelection(7L, "deepseek");
    }

    @Test
    void selectionDeleteLeavesNonActiveProvidersUntouched() throws Exception {
        String xml = new ClassPathResource("mapper/UserActiveAiSelectionMapper.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(xml.contains("<delete id=\"deleteUserProviderSelection\">"));
        assertTrue(xml.contains("user_id = #{userId}"));
        assertTrue(xml.contains("source = 'USER'"));
        assertTrue(xml.contains("user_provider_key = #{providerKey}"));
    }

    @Test
    void recreatingDeletedProviderKeyDoesNotActivateIt() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        UserActiveAiSelectionMapper selections = mock(UserActiveAiSelectionMapper.class);
        TextEncryptor encryptor = mock(TextEncryptor.class);
        when(mapper.deleteByUserIdAndProvider(7L, "deepseek")).thenReturn(1);
        when(encryptor.encrypt("sk-new")).thenReturn("ENC");
        UserAiConfigService service = svc(mapper, encryptor, selections);

        service.deleteProvider(7L, "deepseek");
        clearInvocations(selections);
        service.saveProvider(
                7L, "deepseek", "DeepSeek", "", "",
                "https://api.example.test", "sk-new", "deepseek-chat");

        verifyNoInteractions(selections);
    }

    @Test
    void deleteProviderMapsMissingRowToNotConfigured() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);

        AppException failure = assertThrows(AppException.class,
                () -> svc(mapper).deleteProvider(7L, "deepseek"));

        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, failure.getErrorCode());
    }

    @Test
    void deleteProviderRejectsReservedCodexKey() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);

        AppException failure = assertThrows(AppException.class,
                () -> svc(mapper).deleteProvider(7L, UserAiConfigService.CODEX_PROVIDER_KEY));

        assertEquals(AiErrorCode.AI_PROVIDER_RESERVED, failure.getErrorCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void getDecryptedAiClientConfigReturnsAnthropicFields() {
        UserAiConfig row = config("deepseek", """
                {"protocol":"ANTHROPIC","baseUrl":"https://api.example.test",
                 "apiKeyEnc":"ENC","model":"deepseek-chat","reasoningEffort":"high"}
                """);
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        when(mapper.findActiveByUserId(7L)).thenReturn(row);
        TextEncryptor encryptor = mock(TextEncryptor.class);
        when(encryptor.decrypt("ENC")).thenReturn("plain");

        AiClientConfigDto result = svc(mapper, encryptor).getDecryptedAiClientConfig(7L);

        assertEquals("deepseek", result.provider());
        assertEquals("https://api.example.test", result.baseUrl());
        assertEquals("deepseek-chat", result.model());
        assertEquals("plain", result.apiKey());
        assertEquals("high", result.reasoningEffort());
    }

    @Test
    void getDecryptedConfigRejectsUnknownProtocolWithoutDecrypting() {
        UserAiConfig row = config("custom", "{\"protocol\":\"OPENAI\",\"apiKeyEnc\":\"ENC\"}");
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        when(mapper.findActiveByUserId(7L)).thenReturn(row);
        TextEncryptor encryptor = mock(TextEncryptor.class);

        AppException failure = assertThrows(AppException.class,
                () -> svc(mapper, encryptor).getDecryptedConfig(7L));

        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, failure.getErrorCode());
        verifyNoInteractions(encryptor);
    }

    @Test
    void getDecryptedConfigReportsCipherFailure() {
        UserAiConfig row = config("deepseek",
                "{\"protocol\":\"ANTHROPIC\",\"apiKeyEnc\":\"ENC\",\"model\":\"model\"}");
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        when(mapper.findActiveByUserId(7L)).thenReturn(row);
        TextEncryptor encryptor = mock(TextEncryptor.class);
        when(encryptor.decrypt("ENC")).thenThrow(new RuntimeException("bad cipher"));

        try (ExpectedErrorLog logs = ExpectedErrorLog.capture(UserAiConfigServiceImpl.class)) {
            AppException failure = assertThrows(AppException.class,
                    () -> svc(mapper, encryptor).getDecryptedConfig(7L));

            assertEquals(AiErrorCode.AI_KEY_DECRYPT_FAILED, failure.getErrorCode());
            assertDecryptFailureLogged(logs);
        }
    }

    @Test
    void missingPersonalActiveRowRemainsNotConfigured() {
        AppException failure = assertThrows(AppException.class,
                () -> svc(mock(UserAiConfigMapper.class)).getDecryptedConfig(7L));

        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, failure.getErrorCode());
    }

    @Test
    void discoveryUsesSubmittedPlainKeyWithoutReadingSavedConfig() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);

        assertEquals("new-key", svc(mapper)
                .resolveDiscoveryApiKey(7L, "deepseek", "new-key"));

        verifyNoInteractions(mapper);
    }

    @Test
    void discoveryDecryptsCurrentUsersSavedKey() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        when(mapper.findByUserIdAndProvider(7L, "deepseek"))
                .thenReturn(config("deepseek", "{\"apiKeyEnc\":\"ENC\"}"));
        TextEncryptor encryptor = mock(TextEncryptor.class);
        when(encryptor.decrypt("ENC")).thenReturn("saved-key");

        assertEquals("saved-key", svc(mapper, encryptor)
                .resolveDiscoveryApiKey(7L, "deepseek", ""));

        verify(mapper).findByUserIdAndProvider(7L, "deepseek");
    }

    @Test
    void discoveryTreatsNullAndWhitespacePlainKeysAsSavedKeyRequests() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        when(mapper.findByUserIdAndProvider(7L, "deepseek"))
                .thenReturn(config("deepseek", "{\"apiKeyEnc\":\"ENC\"}"));
        TextEncryptor encryptor = mock(TextEncryptor.class);
        when(encryptor.decrypt("ENC")).thenReturn("saved-key");
        UserAiConfigService service = svc(mapper, encryptor);

        assertEquals("saved-key", service.resolveDiscoveryApiKey(7L, "deepseek", null));
        assertEquals("saved-key", service.resolveDiscoveryApiKey(7L, "deepseek", "   "));
    }

    @Test
    void discoveryCannotReadAnotherUsersProvider() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);

        AppException failure = assertThrows(AppException.class,
                () -> svc(mapper).resolveDiscoveryApiKey(7L, "deepseek", ""));

        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, failure.getErrorCode());
        verify(mapper).findByUserIdAndProvider(7L, "deepseek");
    }

    @Test
    void discoveryRejectsMissingProviderWhenNoPlainKeySubmitted() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);

        AppException failure = assertThrows(AppException.class,
                () -> svc(mapper).resolveDiscoveryApiKey(7L, null, "  "));

        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, failure.getErrorCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void discoveryReportsCipherFailure() {
        UserAiConfigMapper mapper = mock(UserAiConfigMapper.class);
        when(mapper.findByUserIdAndProvider(7L, "deepseek"))
                .thenReturn(config("deepseek", "{\"apiKeyEnc\":\"ENC\"}"));
        TextEncryptor encryptor = mock(TextEncryptor.class);
        when(encryptor.decrypt("ENC")).thenThrow(new RuntimeException("bad cipher"));

        try (ExpectedErrorLog logs = ExpectedErrorLog.capture(UserAiConfigServiceImpl.class)) {
            AppException failure = assertThrows(AppException.class,
                    () -> svc(mapper, encryptor)
                            .resolveDiscoveryApiKey(7L, "deepseek", ""));

            assertEquals(AiErrorCode.AI_KEY_DECRYPT_FAILED, failure.getErrorCode());
            assertDecryptFailureLogged(logs);
        }
    }

    private static UserAiConfigService svc(UserAiConfigMapper mapper) {
        return svc(mapper, mock(TextEncryptor.class));
    }

    private static UserAiConfigService svc(
            UserAiConfigMapper mapper, TextEncryptor encryptor) {
        return svc(mapper, encryptor, mock(UserActiveAiSelectionMapper.class));
    }

    private static UserAiConfigService svc(
            UserAiConfigMapper mapper, TextEncryptor encryptor,
            UserActiveAiSelectionMapper selections) {
        return new UserAiConfigServiceImpl(mapper, encryptor, selections);
    }

    private static UserAiConfig config(String provider, String configJson) {
        UserAiConfig row = new UserAiConfig();
        row.setUserId(7L);
        row.setProvider(provider);
        row.setConfigJson(configJson);
        return row;
    }

    private static void assertDecryptFailureLogged(ExpectedErrorLog logs) {
        assertEquals(1, logs.events().size());
        assertEquals(Level.ERROR, logs.events().get(0).getLevel());
        assertTrue(logs.events().get(0).getFormattedMessage()
                .contains("[E:AI_KEY_DECRYPT_FAILED:"));
    }

    /** 捕获预期解密错误且禁止向 root appender/控制台传播. */
    private static final class ExpectedErrorLog implements AutoCloseable {
        private final Logger logger;
        private final boolean originalAdditive;
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private ExpectedErrorLog(Class<?> source) {
            logger = (Logger) LoggerFactory.getLogger(source);
            originalAdditive = logger.isAdditive();
            appender.start();
            logger.addAppender(appender);
            logger.setAdditive(false);
        }

        private static ExpectedErrorLog capture(Class<?> source) {
            return new ExpectedErrorLog(source);
        }

        private List<ILoggingEvent> events() {
            return List.copyOf(appender.list);
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            logger.setAdditive(originalAdditive);
            appender.stop();
        }
    }
}
