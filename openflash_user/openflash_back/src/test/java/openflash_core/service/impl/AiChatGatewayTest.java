package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.ResourceAccessException;
import openflash_core.dto.AiClientConfigDto;
import openflash_core.common.AiErrorCode;
import openflash_core.service.AiProfileResolver;
import openflash_core.service.UserAiConfigProvider;
import openflash_core.service.UserAiConfigService;
import openflash_core.common.CodexAppException;
import openflash_core.config.AiProperties;
import openflash_core.entity.UserAiConfig;
import openflash_core.dto.ActiveAiSelectionDto;
import openflash_core.common.AiSource;
import openflash_core.service.ProviderOptionsFactory;
import openflash_core.client.AiRuntimeCoreClient;
import openflash_core.common.AppException;
import openflash_core.security.OutboundUrlValidator;

class AiChatGatewayTest {

    @Test
    void dispatchValidatorAndPlatformRuntimeUseSameResolvedSelectionSnapshot() {
        UnifiedAiSelectionServiceImpl selections = mock(UnifiedAiSelectionServiceImpl.class);
        ActiveAiSelectionDto selectionA = new ActiveAiSelectionDto(
                AiSource.PLATFORM, null, "platform-a", "OPENAI_COMPAT", "model-a", null);
        ActiveAiSelectionDto selectionB = new ActiveAiSelectionDto(
                AiSource.PLATFORM, null, "platform-b", "OPENAI_COMPAT", "model-b", null);
        when(selections.requireActive(7L)).thenReturn(selectionA, selectionB);
        AiProfileResolver profiles = mock(AiProfileResolver.class);
        AiProperties.AiProfile profile = profile("queued-model", "system", 0.2);
        AiProperties.AiProfile effective = profile("model-a", "system", 0.2);
        when(profiles.applyModel(profile, "model-a")).thenReturn(effective);
        AiRuntimeCoreClient runtime = mock(AiRuntimeCoreClient.class);
        when(runtime.generate(7L, selectionA, "prompt", effective)).thenReturn("answer-a");
        AiChatGateway gateway = new AiChatGateway(
                mock(UserAiClientFactory.class),
                mock(ProviderOptionsFactory.class),
                profiles,
                mock(UserAiConfigService.class),
                runtime,
                selections);
        AtomicReference<ActiveAiSelectionDto> validatedSelection = new AtomicReference<>();

        String content = gateway.chat("prompt", profile, 7L, (selection, dispatchProfile) -> {
            validatedSelection.set(selection);
            assertSame(effective, dispatchProfile);
        });

        assertEquals("answer-a", content);
        assertSame(selectionA, validatedSelection.get());
        verify(selections, times(1)).requireActive(7L);
        verify(runtime).generate(7L, selectionA, "prompt", effective);
        verify(runtime, never()).generate(7L, selectionB, "prompt", effective);
    }

    @Test
    void userRouteUsesExistingFactoryAndNeverCallsPlatformRuntime() {
        Fixture fixture = new Fixture(
                new FakeChatModel("anthropic answer"), config(11L, "anthropic", "user-model"));

        assertEquals("anthropic answer",
                fixture.gateway.chat("apple", profile("profile-model", "system", 0.1), 11L));

        assertEquals(1, fixture.userAiClientFactory.snapshotCalls);
        assertEquals("user-model", fixture.userAiClientFactory.lastConfig.model());
        assertEquals(List.of("selection", "token", "snapshot", "install"), fixture.routeEvents);
        verify(fixture.userAiConfigService).getDecryptedConfig(11L);
        verifyNoInteractions(fixture.runtimeClient);
    }

    @Test
    void userRouteReturnsContentFromModelUsingUserSession() {
        Fixture fixture = new Fixture(
                new FakeChatModel("n.苹果;v.苹果（俚）"), config(11L, "anthropic", "user-model"));

        assertEquals("n.苹果;v.苹果（俚）",
                fixture.gateway.chat("apple", profile("profile-model", "system", 0.1), 11L));
        assertEquals(11L, fixture.userAiClientFactory.lastUserId);
    }

    @Test
    void userRoutePassesPromptSystemAndOptionsToModel() {
        FakeChatModel model = new FakeChatModel("content");
        RecordingOptionsFactory options = new RecordingOptionsFactory(
                fakeOptions("provider-model", 0.7));
        Fixture fixture = new Fixture(model, config(11L, "anthropic", "user-model"), options);

        fixture.gateway.chat("apple", profile("profile-model", "system prompt", 0.2), 11L);

        List<String> texts = model.lastPrompt.getInstructions().stream()
                .map(Message::getText)
                .toList();
        assertEquals(List.of("system prompt", "apple"), texts);
        assertSame(options.options, model.lastPrompt.getOptions());
        assertEquals("user-model", options.lastProfile.getModel());
        assertEquals("system prompt", options.lastProfile.getSystem());
        assertEquals(0.2, options.lastProfile.getTemperature());
    }

    @Test
    void userRouteUsesOnlyUserPromptWhenSystemIsMissing() {
        FakeChatModel model = new FakeChatModel("content");
        Fixture fixture = new Fixture(model, config(11L, "anthropic", "user-model"));

        fixture.gateway.chat("apple", profile("profile-model", null, 0.2), 11L);

        assertEquals(List.of("apple"), model.lastPrompt.getInstructions().stream()
                .map(Message::getText)
                .toList());
    }

    @Test
    void userRouteUsesOnlyUserPromptWhenSystemIsBlank() {
        FakeChatModel model = new FakeChatModel("content");
        Fixture fixture = new Fixture(model, config(11L, "anthropic", "user-model"));

        fixture.gateway.chat("apple", profile("profile-model", "   ", 0.2), 11L);

        assertEquals(List.of("apple"), model.lastPrompt.getInstructions().stream()
                .map(Message::getText)
                .toList());
    }

    @Test
    void userRouteRejectsUnknownProtocolBeforeCreatingClient() {
        Fixture fixture = new Fixture(new FakeChatModel("must not run"),
                activeConfig(11L, "unknown", "FUTURE_PROTOCOL", "model"));

        AppException error = assertThrows(AppException.class,
                () -> fixture.gateway.chat(
                        "prompt", profile("model", "system", 0.1), 11L));

        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, error.getErrorCode());
        assertEquals(0, fixture.userAiClientFactory.snapshotCalls);
        verifyNoInteractions(fixture.runtimeClient);
    }

    @Test
    void missingProfileAndUserKeepExistingErrorsWithoutReadingSelection() {
        Fixture fixture = new Fixture(
                new FakeChatModel("must not run"), config(11L, "anthropic", "model"));

        AppException missingProfile = assertThrows(AppException.class,
                () -> fixture.gateway.chat("prompt", null, 11L));
        AppException missingUser = assertThrows(AppException.class,
                () -> fixture.gateway.chat("prompt", profile("model", "system", 0.1), null));

        assertEquals(AiErrorCode.AI_PROFILE_NOT_CONFIGURED, missingProfile.getErrorCode());
        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, missingUser.getErrorCode());
        verifyNoInteractions(fixture.selectionService);
        verify(fixture.userAiConfigService, never()).getDecryptedConfig(any());
    }

    @Test
    void userRouteRejectsBlankModelResponse() {
        Fixture fixture = new Fixture(
                new FakeChatModel("   "), config(11L, "anthropic", "user-model"));

        AppException error = assertThrows(AppException.class,
                () -> fixture.gateway.chat(
                        "prompt", profile("profile-model", "system", 0.1), 11L));

        assertEquals(AiErrorCode.AI_EMPTY_RESPONSE, error.getErrorCode());
    }

    @Test
    void userRouteRejectsNullModelResponse() {
        Fixture fixture = new Fixture(
                new FakeChatModel((String) null), config(11L, "anthropic", "user-model"));

        AppException error = assertThrows(AppException.class,
                () -> fixture.gateway.chat(
                        "prompt", profile("profile-model", "system", 0.1), 11L));

        assertEquals(AiErrorCode.AI_EMPTY_RESPONSE, error.getErrorCode());
    }

    @Test
    void userRouteMapsConnectionFailureToExistingError() {
        Fixture fixture = new Fixture(
                new FakeChatModel(new ResourceAccessException("connection refused")),
                config(11L, "anthropic", "user-model"));

        try (ExpectedLogCapture logs = ExpectedLogCapture.capture(AiChatGateway.class)) {
            AppException error = assertThrows(AppException.class,
                    () -> fixture.gateway.chat(
                            "prompt", profile("profile-model", "system", 0.1), 11L));

            assertEquals(AiErrorCode.AI_CONNECTION_FAILED, error.getErrorCode());
            assertEquals(1, logs.events().size());
            assertEquals(Level.WARN, logs.events().get(0).getLevel());
        }
    }

    @Test
    void userRouteMapsUnexpectedRuntimeFailureToExistingError() {
        Fixture fixture = new Fixture(
                new FakeChatModel(new RuntimeException("unexpected")),
                config(11L, "anthropic", "user-model"));

        try (ExpectedLogCapture logs = ExpectedLogCapture.capture(AiChatGateway.class)) {
            AppException error = assertThrows(AppException.class,
                    () -> fixture.gateway.chat(
                            "prompt", profile("profile-model", "system", 0.1), 11L));

            assertEquals(AiErrorCode.AI_UPSTREAM_UNAVAILABLE, error.getErrorCode());
            assertEquals(1, logs.events().size());
            assertEquals(Level.WARN, logs.events().get(0).getLevel());
        }
    }

    @Test
    void platformRouteHappensBeforeAnyPersonalConfigDecrypt() {
        UnifiedAiSelectionServiceImpl selection = mock(UnifiedAiSelectionServiceImpl.class);
        UserAiConfigService configs = mock(UserAiConfigService.class);
        UserAiClientFactory clients = mock(UserAiClientFactory.class);
        AiRuntimeCoreClient runtime = mock(AiRuntimeCoreClient.class);
        AiProfileResolver profiles = mock(AiProfileResolver.class);
        AiProperties.AiProfile profile = profile("base", "system", 0.2);
        ActiveAiSelectionDto active = new ActiveAiSelectionDto(
                AiSource.PLATFORM, null, "platform-codex-cli",
                "CODEX_APP_SERVER", "gpt-5.4", "high");
        when(selection.requireActive(7L)).thenReturn(active);
        when(profiles.applyModel(profile, "gpt-5.4")).thenReturn(profile);
        when(runtime.generate(7L, active, "prompt", profile)).thenReturn("answer");
        AiChatGateway gateway = new AiChatGateway(
                clients, mock(ProviderOptionsFactory.class), profiles,
                configs, runtime, selection);

        assertEquals("answer", gateway.chat("prompt", profile, 7L));

        verifyNoInteractions(configs);
        verify(clients, never()).captureGenerationToken(7L);
    }

    @Test
    void platformFailureNeverFallsBackOrChangesSelection() {
        UnifiedAiSelectionServiceImpl selection = mock(UnifiedAiSelectionServiceImpl.class);
        UserAiConfigService configs = mock(UserAiConfigService.class);
        UserAiClientFactory clients = mock(UserAiClientFactory.class);
        AiRuntimeCoreClient runtime = mock(AiRuntimeCoreClient.class);
        AiProfileResolver profiles = mock(AiProfileResolver.class);
        AiProperties.AiProfile profile = profile("base", "system", 0.2);
        ActiveAiSelectionDto active = new ActiveAiSelectionDto(
                AiSource.PLATFORM, null, "platform-codex-cli",
                "CODEX_APP_SERVER", "gpt-5.4", "high");
        CodexAppException unavailable = new CodexAppException(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
        when(selection.requireActive(7L)).thenReturn(active);
        when(profiles.applyModel(profile, "gpt-5.4")).thenReturn(profile);
        when(runtime.generate(7L, active, "prompt", profile)).thenThrow(unavailable);
        AiChatGateway gateway = new AiChatGateway(
                clients, mock(ProviderOptionsFactory.class), profiles,
                configs, runtime, selection);

        assertSame(unavailable, assertThrows(RuntimeException.class,
                () -> gateway.chat("prompt", profile, 7L)));
        verifyNoInteractions(configs);
        verify(selection, never()).activateUserProvider(7L, "deepseek");
    }

    private static AiProperties.AiProfile profile(
            String model, String system, double temperature) {
        AiProperties.AiProfile profile = new AiProperties.AiProfile();
        profile.setModel(model);
        profile.setSystem(system);
        profile.setTemperature(temperature);
        return profile;
    }

    private static UserAiConfig config(Long userId, String provider, String model) {
        return activeConfig(userId, provider, UserAiConfigService.PROTOCOL_ANTHROPIC, model);
    }

    private static UserAiConfig activeConfig(
            Long userId, String provider, String protocol, String model) {
        UserAiConfig config = new UserAiConfig();
        config.setUserId(userId);
        config.setProvider(provider);
        config.setActive(true);
        config.setConfigJson("{\"protocol\":\"" + protocol
                + "\",\"baseUrl\":\"https://api.example.com\","
                + "\"apiKey\":\"sk-test\",\"model\":\""
                + (model == null ? "" : model) + "\"}");
        return config;
    }

    private static ChatOptions fakeOptions(String model, double temperature) {
        return ChatOptions.builder().model(model).temperature(temperature).build();
    }

    private static final class Fixture {
        private final FakeUserAiClientFactory userAiClientFactory;
        private final UserAiConfigService userAiConfigService;
        private final AiRuntimeCoreClient runtimeClient;
        private final UnifiedAiSelectionServiceImpl selectionService;
        private final List<String> routeEvents = new ArrayList<>();
        private final AiChatGateway gateway;

        Fixture(FakeChatModel model, UserAiConfig config) {
            this(model, config,
                    new RecordingOptionsFactory(fakeOptions(config.getConfigValue("model"), 0.1)));
        }

        Fixture(
                FakeChatModel model,
                UserAiConfig config,
                RecordingOptionsFactory providerOptionsFactory) {
            userAiClientFactory = new FakeUserAiClientFactory(model, config, routeEvents);
            userAiConfigService = mock(UserAiConfigService.class);
            when(userAiConfigService.getDecryptedConfig(config.getUserId())).thenAnswer(invocation -> {
                routeEvents.add("snapshot");
                return config;
            });
            runtimeClient = mock(AiRuntimeCoreClient.class);
            selectionService = mock(UnifiedAiSelectionServiceImpl.class);
            when(selectionService.requireActive(config.getUserId())).thenAnswer(invocation -> {
                routeEvents.add("selection");
                return new ActiveAiSelectionDto(
                        AiSource.USER, config.getProvider(), null,
                        config.getConfigValue("protocol"), config.getConfigValue("model"), null);
            });
            gateway = new AiChatGateway(
                    userAiClientFactory,
                    providerOptionsFactory,
                    new EffectiveAiProfileResolver(null),
                    userAiConfigService,
                    runtimeClient,
                    selectionService);
        }
    }

    private static final class FakeUserAiClientFactory extends UserAiClientFactory {
        private final UserAiSession session;
        private Long lastUserId;
        private AiClientConfigDto lastConfig;
        private int snapshotCalls;
        private final List<String> routeEvents;

        FakeUserAiClientFactory(
                ChatModel chatModel, UserAiConfig config, List<String> routeEvents) {
            super(new NoopUserAiConfigProvider(), new AiProperties(), false,
                    OutboundUrlValidator.permissiveForTesting());
            session = new UserAiSession(
                    chatModel, config.getProvider(), config.getConfigValue("model"));
            this.routeEvents = routeEvents;
        }

        @Override
        public GenerationToken captureGenerationToken(Long userId) {
            routeEvents.add("token");
            return super.captureGenerationToken(userId);
        }

        @Override
        public UserAiSession getOrCreate(
                Long userId, AiClientConfigDto config, GenerationToken token) {
            lastUserId = userId;
            lastConfig = config;
            snapshotCalls++;
            routeEvents.add("install");
            return session;
        }
    }

    private static final class NoopUserAiConfigProvider implements UserAiConfigProvider {
        @Override
        public AiClientConfigDto getDecryptedAiClientConfig(Long userId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingOptionsFactory implements ProviderOptionsFactory {
        private final ChatOptions options;
        private AiProperties.AiProfile lastProfile;

        RecordingOptionsFactory(ChatOptions options) {
            this.options = options;
        }

        @Override
        public ChatOptions buildOptions(AiProperties.AiProfile profile) {
            lastProfile = profile;
            return options;
        }
    }

    private static final class FakeChatModel implements ChatModel {
        private final String response;
        private final RuntimeException exception;
        private Prompt lastPrompt;

        FakeChatModel(String response) {
            this.response = response;
            exception = null;
        }

        FakeChatModel(RuntimeException exception) {
            response = null;
            this.exception = exception;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt = prompt;
            if (exception != null)
                throw exception;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
        }
    }
}
