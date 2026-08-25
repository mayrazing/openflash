package openflash_plugin.ai_card.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import openflash_core.common.AiErrorCode;
import openflash_core.config.AiProperties;
import openflash_core.entity.AsyncTask;
import openflash_core.service.ProviderOptionsFactory;
import openflash_plugin.ai_card.entity.AiCacheReadyNotification;
import openflash_plugin.ai_card.entity.CardAiCache;
import openflash_plugin.ai_card.service.CardAiCacheService;
import openflash_core.service.UserAiConfigService;
import openflash_core.common.CodexAppException;
import openflash_core.entity.UserAiConfig;
import openflash_core.service.impl.AiChatGateway;
import openflash_core.service.impl.EffectiveAiProfileResolver;
import openflash_core.service.impl.UserAiClientFactory;
import openflash_core.dto.ActiveAiSelectionDto;
import openflash_core.common.AiSource;
import openflash_core.service.impl.UnifiedAiSelectionServiceImpl;
import openflash_core.client.AiRuntimeCoreClient;
import openflash_core.service.UserSseRegistry;
import tools.jackson.databind.ObjectMapper;

class AiTaskExecutorTest {

        @Test
        void queuedTaskSkipsProviderWhenPluginWasUninstalled() throws Exception {
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                FakeCardAiGenerationCore generationCore = new FakeCardAiGenerationCore();
                AiCardInstallGate installGate = mock(AiCardInstallGate.class);
                when(installGate.isInstalledOnDeck(13L)).thenReturn(false);
                AiTaskExecutor executor = new AiTaskExecutor(
                                cacheService,
                                generationCore,
                                new ObjectMapper(),
                                new RecordingUserSseRegistry(),
                                enabledGuard(),
                                installGate);

                executor.execute(taskWithUser("fp-1", "apple", 7L, 11L, 13L, "Apple"));

                assertFalse(generationCore.generateCalled);
                assertNull(cacheService.savedContent);
        }

        @Test
        void queuedTaskSkipsSaveWhenPluginWasUninstalledDuringGeneration() throws Exception {
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                FakeCardAiGenerationCore generationCore = new FakeCardAiGenerationCore();
                AiCardInstallGate installGate = mock(AiCardInstallGate.class);
                when(installGate.isInstalledOnDeck(13L)).thenReturn(true, false);
                AiTaskExecutor executor = new AiTaskExecutor(
                                cacheService,
                                generationCore,
                                new ObjectMapper(),
                                new RecordingUserSseRegistry(),
                                enabledGuard(),
                                installGate);

                executor.execute(taskWithUser("fp-1", "apple", 7L, 11L, 13L, "Apple"));

                assertTrue(generationCore.generateCalled);
                assertNull(cacheService.savedContent);
        }

        @Test
        void activeSelectionAtDispatchSavesUnderContentFingerprint()
                        throws Exception {
                ActiveAiSelectionDto selectionA = new ActiveAiSelectionDto(
                                AiSource.PLATFORM, null, "platform-a", "OPENAI_COMPAT", "model-a", null);
                UnifiedAiSelectionServiceImpl selections = mock(UnifiedAiSelectionServiceImpl.class);
                when(selections.requireActive(7L)).thenReturn(selectionA);
                AiRuntimeCoreClient runtimeClient = mock(AiRuntimeCoreClient.class);
                when(runtimeClient.generate(anyLong(), any(ActiveAiSelectionDto.class), anyString(), any()))
                                .thenAnswer(invocation -> "content-"
                                                + ((ActiveAiSelectionDto) invocation.getArgument(1)).offeringKey());
                EffectiveAiProfileResolver profileResolver = new EffectiveAiProfileResolver(
                                mock(UserAiConfigService.class), selections);
                AiChatGateway gateway = new AiChatGateway(
                                mock(UserAiClientFactory.class),
                                mock(ProviderOptionsFactory.class),
                                profileResolver,
                                mock(UserAiConfigService.class),
                                runtimeClient,
                                selections);
                CardAiGenerationCore generationCore = new CardAiGenerationCore(
                                null, null, gateway, enabledGuard(), profileResolver);
                AiProperties.AiProfile profile = new AiProperties.AiProfile();
                profile.setName("ai_cache");
                profile.setModel("model-a");
                profile.setSystem("deck explanation prompt");
                profile.setTemperature(0.2d);
                String queuedFingerprint = CardAiPromptSupport.buildFingerprint("apple");
                AsyncTask task = new AsyncTask();
                task.setPayload(new ObjectMapper().writeValueAsString(
                                CardAiCacheTaskProducer.AiCacheTaskPayload.from(
                                                queuedFingerprint, "apple", profile, 7L)));
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                AiTaskExecutor executor = new AiTaskExecutor(
                                cacheService,
                                generationCore,
                                new ObjectMapper(),
                                new RecordingUserSseRegistry(),
                                enabledGuard());

                executor.execute(task);

                assertEquals("content-platform-a", cacheService.savedContent);
                assertEquals(queuedFingerprint, cacheService.savedFingerprint);
        }

        @Test
        void executeSkipsGenerationWhenCacheAlreadyExists() throws Exception {
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                cacheService.readyCache = new CardAiCache();
                cacheService.readyCache.setContent("cached");
                FakeCardAiGenerationCore generationCore = new FakeCardAiGenerationCore();
                RecordingUserSseRegistry registry = new RecordingUserSseRegistry();
                AiTaskExecutor executor = new AiTaskExecutor(cacheService, generationCore, new ObjectMapper(), registry,
                                enabledGuard());

                executor.execute(task("fp-1", "apple"));

                assertFalse(generationCore.generateCalled);
                assertEquals(0, registry.pushCalls.size());
        }

        @Test
        void pushesReadyNotificationWhenCacheAlreadyExistsAndUserIdPresent() throws Exception {
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                cacheService.readyCache = new CardAiCache();
                cacheService.readyCache.setContent("cached");
                FakeCardAiGenerationCore generationCore = new FakeCardAiGenerationCore();
                RecordingUserSseRegistry registry = new RecordingUserSseRegistry();
                AiTaskExecutor executor = new AiTaskExecutor(cacheService, generationCore, new ObjectMapper(), registry,
                                enabledGuard());

                executor.execute(taskWithUser("fp-1", "apple", 7L, 11L, 13L, "Apple"));

                assertFalse(generationCore.generateCalled);
                assertEquals(1, registry.pushCalls.size());
                PushCall pushCall = registry.pushCalls.get(0);
                assertEquals(7L, pushCall.userId());
                assertEquals(AiCacheReadyNotification.EVENT_NAME, pushCall.eventName());
                AiCacheReadyNotification notification = pushCall.payload();
                assertEquals(11L, notification.getCardId());
                assertEquals(13L, notification.getDeckId());
                assertEquals("Apple", notification.getCardTitle());
                assertEquals(CardAiPromptSupport.SIDE_A, notification.getSide());
        }

        @Test
        void forceRegenerateGeneratesAndSavesWhenCacheAlreadyExists() throws Exception {
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                cacheService.readyCache = new CardAiCache();
                cacheService.readyCache.setContent("cached");
                FakeCardAiGenerationCore generationCore = new FakeCardAiGenerationCore();
                RecordingUserSseRegistry registry = new RecordingUserSseRegistry();
                AiTaskExecutor executor = new AiTaskExecutor(cacheService, generationCore, new ObjectMapper(), registry,
                                enabledGuard());

                executor.execute(taskWithUser("fp-1", "apple", 7L, 11L, 13L, "Apple", true));

                assertTrue(generationCore.generateCalled);
                assertEquals("fresh", cacheService.savedContent);
                assertEquals(1, registry.pushCalls.size());
        }

        @Test
        void executeSkipsGenerationAndSaveWhenCacheMissesWithoutUserContext() throws Exception {
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                FakeCardAiGenerationCore generationCore = new FakeCardAiGenerationCore();
                RecordingUserSseRegistry registry = new RecordingUserSseRegistry();
                AiTaskExecutor executor = new AiTaskExecutor(cacheService, generationCore, new ObjectMapper(), registry,
                                enabledGuard());

                try (ExpectedLogCapture logs = ExpectedLogCapture.capture(AiTaskExecutor.class)) {
                        executor.execute(task("fp-1", "apple"));

                        assertFalse(generationCore.generateCalled);
                        assertNull(generationCore.lastUserId);
                        assertNull(cacheService.savedContent);
                        assertEquals(1, logs.events().size());
                        assertEquals(Level.WARN, logs.events().get(0).getLevel());
                }
        }

        @Test
        void executeSkipsGenerationWhenFeatureDisabled() throws Exception {
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                FakeCardAiGenerationCore generationCore = new FakeCardAiGenerationCore();
                RecordingUserSseRegistry registry = new RecordingUserSseRegistry();
                AiTaskExecutor executor = new AiTaskExecutor(cacheService, generationCore, new ObjectMapper(), registry,
                                disabledGuard());

                executor.execute(task("fp-1", "apple"));

                assertFalse(generationCore.generateCalled);
                assertNull(cacheService.savedContent);
                assertEquals(0, registry.pushCalls.size());
        }

        @Test
        void executeSkipsLegacyPayloadWithoutUserContext() throws Exception {
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                FakeCardAiGenerationCore generationCore = new FakeCardAiGenerationCore();
                RecordingUserSseRegistry registry = new RecordingUserSseRegistry();
                AiTaskExecutor executor = new AiTaskExecutor(cacheService, generationCore, new ObjectMapper(), registry,
                                enabledGuard());
                AsyncTask task = new AsyncTask();
                task.setPayload("""
                                {
                                  "fingerprint": "fp-legacy",
                                  "prompt": "apple",
                                  "profileName": "card-ai",
                                  "model": "model-a",
                                  "system": "system-a",
                                  "temperature": 0.7
                                }
                                """);

                try (ExpectedLogCapture logs = ExpectedLogCapture.capture(AiTaskExecutor.class)) {
                        executor.execute(task);

                        assertFalse(generationCore.generateCalled);
                        assertNull(generationCore.lastUserId);
                        assertNull(cacheService.savedContent);
                        assertEquals(1, logs.events().size());
                        assertEquals(Level.WARN, logs.events().get(0).getLevel());
                }
        }

        @Test
        void executeUsesUserIdFromLegacyPayload() throws Exception {
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                FakeCardAiGenerationCore generationCore = new FakeCardAiGenerationCore();
                RecordingUserSseRegistry registry = new RecordingUserSseRegistry();
                AiTaskExecutor executor = new AiTaskExecutor(cacheService, generationCore, new ObjectMapper(), registry,
                                enabledGuard());
                AsyncTask task = new AsyncTask();
                task.setPayload("""
                                {
                                  "fingerprint": "fp-legacy-user",
                                  "prompt": "apple",
                                  "profileName": "card-ai",
                                  "model": "model-a",
                                  "system": "system-a",
                                  "temperature": 0.7,
                                  "userId": 88
                                }
                                """);

                executor.execute(task);

                assertTrue(generationCore.generateCalled);
                assertEquals(88L, generationCore.lastUserId);
                assertEquals("fresh", cacheService.savedContent);
        }

        @Test
        void pushesToSseRegistryWhenUserIdPresent() throws Exception {
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                FakeCardAiGenerationCore generationCore = new FakeCardAiGenerationCore();
                RecordingUserSseRegistry registry = new RecordingUserSseRegistry();
                AiTaskExecutor executor = new AiTaskExecutor(cacheService, generationCore, new ObjectMapper(), registry,
                                enabledGuard());

                executor.execute(taskWithUser("fp-1", "apple", 7L, 11L, 13L, "Apple"));

                assertEquals(7L, generationCore.lastUserId);
                assertEquals(1, registry.pushCalls.size());
                PushCall pushCall = registry.pushCalls.get(0);
                assertEquals(7L, pushCall.userId());
                assertEquals(AiCacheReadyNotification.EVENT_NAME, pushCall.eventName());
                AiCacheReadyNotification notification = pushCall.payload();
                assertEquals(11L, notification.getCardId());
                assertEquals(13L, notification.getDeckId());
                assertEquals("Apple", notification.getCardTitle());
                assertEquals(CardAiPromptSupport.SIDE_A, notification.getSide());
                assertEquals("fresh", cacheService.savedContent);
        }

        @Test
        void skipsSsePushWhenUserIdNull() throws Exception {
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                FakeCardAiGenerationCore generationCore = new FakeCardAiGenerationCore();
                RecordingUserSseRegistry registry = new RecordingUserSseRegistry();
                AiTaskExecutor executor = new AiTaskExecutor(cacheService, generationCore, new ObjectMapper(), registry,
                                enabledGuard());

                try (ExpectedLogCapture logs = ExpectedLogCapture.capture(AiTaskExecutor.class)) {
                        executor.execute(taskWithUser("fp-1", "apple", null, 11L, 13L, "Apple"));

                        assertEquals(0, registry.pushCalls.size());
                        assertEquals(1, logs.events().size());
                        assertEquals(Level.WARN, logs.events().get(0).getLevel());
                }
        }

        @Test
        void pushesToSseRegistryWhenUserIdPresentAndContextHasNulls() throws Exception {
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                FakeCardAiGenerationCore generationCore = new FakeCardAiGenerationCore();
                RecordingUserSseRegistry registry = new RecordingUserSseRegistry();
                AiTaskExecutor executor = new AiTaskExecutor(cacheService, generationCore, new ObjectMapper(), registry,
                                enabledGuard());

                executor.execute(taskWithUser("fp-1", "apple", 7L, 11L, 13L, null));

                assertEquals("fresh", cacheService.savedContent);
                assertEquals(1, registry.pushCalls.size());
                PushCall pushCall = registry.pushCalls.get(0);
                assertEquals(7L, pushCall.userId());
                assertEquals(AiCacheReadyNotification.EVENT_NAME, pushCall.eventName());
                AiCacheReadyNotification notification = pushCall.payload();
                assertEquals(11L, notification.getCardId());
                assertEquals(13L, notification.getDeckId());
                assertNull(notification.getCardTitle());
        }

        /** 验证任务入队后切换 provider 时，仍按目标内容生成并保存。 */
        @Test
        void queuedTaskUsesCurrentProviderAfterProviderSwitch() throws Exception {
                UserAiConfig apiConfig = apiConfig(7L);
                RoutingFixture fixture = routingFixture(apiConfig);
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                AiTaskExecutor executor = new AiTaskExecutor(
                                cacheService,
                                fixture.generationCore,
                                new ObjectMapper(),
                                new RecordingUserSseRegistry(),
                                enabledGuard());
                AsyncTask queuedUnderApi = taskWithSelection("apple", 7L, selectionFor(apiConfig));
                fixture.activeConfig.set(codexConfig(7L));
                when(fixture.runtimeClient.generate(
                                anyLong(), any(ActiveAiSelectionDto.class), anyString(), any()))
                                .thenReturn("codex-content");

                executor.execute(queuedUnderApi);

                assertEquals("codex-content", cacheService.savedContent);
                assertEquals(CardAiPromptSupport.buildFingerprint("apple"), cacheService.savedFingerprint);
                assertEquals(1, cacheService.saveReadyFromBackgroundCalls);
                verify(fixture.runtimeClient).generate(
                                anyLong(), any(ActiveAiSelectionDto.class), anyString(), any());
                verify(fixture.userAiClientFactory, never()).getOrCreate(anyLong());
        }

        /**
         * 验证 provider 切换不会使旧缓存失效，命中时 Codex/API 两个 transport 都不运行。
         */
        @Test
        void cacheHitAfterProviderSwitchCallsNeitherTransport() throws Exception {
                RoutingFixture fixture = routingFixture(apiConfig(7L));
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                cacheService.readyCache = new CardAiCache();
                AiTaskExecutor executor = new AiTaskExecutor(
                                cacheService,
                                fixture.generationCore,
                                new ObjectMapper(),
                                new RecordingUserSseRegistry(),
                                enabledGuard());
                AsyncTask queuedUnderApi = taskWithConfiguredProfile("fp-hit", "apple", 7L);
                fixture.activeConfig.set(codexConfig(7L));

                executor.execute(queuedUnderApi);

                verify(fixture.runtimeClient, never()).generate(
                                anyLong(), any(ActiveAiSelectionDto.class), anyString(), any());
                verify(fixture.userAiClientFactory, never()).captureGenerationToken(anyLong());
                verify(fixture.userAiConfigService, never()).getDecryptedConfig(anyLong());
                assertNull(cacheService.savedContent);
        }

        /** Codex 失败时不落缓存，并原样向上抛不可重试错误。 */
        @Test
        void codexFailureSavesNoCache() throws Exception {
                UserAiConfig codexConfig = codexConfig(7L);
                RoutingFixture fixture = routingFixture(codexConfig);
                FakeCardAiCacheService cacheService = new FakeCardAiCacheService();
                AiTaskExecutor executor = new AiTaskExecutor(
                                cacheService,
                                fixture.generationCore,
                                new ObjectMapper(),
                                new RecordingUserSseRegistry(),
                                enabledGuard());
                CodexAppException failure = new CodexAppException(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
                when(fixture.runtimeClient.generate(
                                anyLong(), any(ActiveAiSelectionDto.class), anyString(), any()))
                                .thenThrow(failure);

                assertThrows(CodexAppException.class,
                                () -> executor.execute(taskWithSelection(
                                                "apple", 7L, selectionFor(codexConfig))));

                assertNull(cacheService.savedContent);
                assertEquals(0, cacheService.saveReadyFromBackgroundCalls);
                verify(fixture.userAiClientFactory, never()).getOrCreate(anyLong());
        }

        private AsyncTask task(String fingerprint, String prompt) throws Exception {
                return taskWithUser(fingerprint, prompt, null, null, null, null);
        }

        private AsyncTask taskWithConfiguredProfile(String fingerprint, String prompt, Long userId) throws Exception {
                AiProperties.AiProfile profile = new AiProperties.AiProfile();
                profile.setName("ai_cache");
                profile.setModel("queued-model");
                profile.setSystem("deck explanation prompt");
                profile.setTemperature(0.2d);
                AsyncTask task = new AsyncTask();
                task.setPayload(new ObjectMapper().writeValueAsString(
                                CardAiCacheTaskProducer.AiCacheTaskPayload.from(fingerprint, prompt, profile, userId)));
                return task;
        }

        private AsyncTask taskWithSelection(
                        String prompt, Long userId, ActiveAiSelectionDto selection) throws Exception {
                AiProperties.AiProfile profile = new AiProperties.AiProfile();
                profile.setName("ai_cache");
                profile.setModel(selection.model());
                profile.setSystem("deck explanation prompt");
                profile.setTemperature(0.2d);
                String fingerprint = CardAiPromptSupport.buildFingerprint(prompt);
                AsyncTask task = new AsyncTask();
                task.setPayload(new ObjectMapper().writeValueAsString(
                                CardAiCacheTaskProducer.AiCacheTaskPayload.from(
                                                fingerprint, prompt, profile, userId)));
                return task;
        }

        private RoutingFixture routingFixture(UserAiConfig initialConfig) {
                AtomicReference<UserAiConfig> activeConfig = new AtomicReference<>(initialConfig);
                UserAiConfigService userAiConfigService = mock(UserAiConfigService.class);
                when(userAiConfigService.getDecryptedConfig(anyLong())).thenAnswer(ignored -> activeConfig.get());
                UserAiClientFactory userAiClientFactory = mock(UserAiClientFactory.class);
                AiRuntimeCoreClient runtimeClient = mock(AiRuntimeCoreClient.class);
                AiCardFeatureGuard featureGuard = mock(AiCardFeatureGuard.class);
                UnifiedAiSelectionServiceImpl selection = mock(UnifiedAiSelectionServiceImpl.class);
                when(selection.requireActive(anyLong())).thenAnswer(ignored -> {
                        return selectionFor(activeConfig.get());
                });
                AiChatGateway gateway = new AiChatGateway(
                                userAiClientFactory,
                                mock(ProviderOptionsFactory.class),
                                new EffectiveAiProfileResolver(null),
                                userAiConfigService,
                                runtimeClient,
                                selection);
                CardAiGenerationCore generationCore = new CardAiGenerationCore(
                                null, null, gateway, featureGuard, new EffectiveAiProfileResolver(null));
                return new RoutingFixture(
                                activeConfig, userAiConfigService, userAiClientFactory, runtimeClient, generationCore);
        }

        private ActiveAiSelectionDto selectionFor(UserAiConfig config) {
                boolean platform = UserAiConfigService.CODEX_PROVIDER_KEY.equals(config.getProvider());
                return new ActiveAiSelectionDto(
                                platform ? AiSource.PLATFORM : AiSource.USER,
                                platform ? null : config.getProvider(),
                                platform ? AiRuntimeCoreClient.CODEX_OFFERING_KEY : null,
                                config.getConfigValue("protocol"),
                                config.getConfigValue("model"),
                                platform ? config.getConfigValue("reasoningEffort") : null);
        }

        private UserAiConfig apiConfig(Long userId) {
                return activeConfig(userId, "api-provider", UserAiConfigService.PROTOCOL_ANTHROPIC,
                                "api-model", "medium");
        }

        private UserAiConfig codexConfig(Long userId) {
                return activeConfig(userId, UserAiConfigService.CODEX_PROVIDER_KEY,
                                UserAiConfigService.PROTOCOL_CODEX_APP_SERVER, "codex-model", "high");
        }

        private UserAiConfig activeConfig(
                        Long userId, String provider, String protocol, String model, String reasoningEffort) {
                UserAiConfig config = new UserAiConfig();
                config.setUserId(userId);
                config.setProvider(provider);
                config.setActive(true);
                config.setConfigJson("{\"protocol\":\"" + protocol + "\",\"model\":\"" + model
                                + "\",\"reasoningEffort\":\"" + reasoningEffort + "\"}");
                return config;
        }

        private record RoutingFixture(
                        AtomicReference<UserAiConfig> activeConfig,
                        UserAiConfigService userAiConfigService,
                        UserAiClientFactory userAiClientFactory,
                        AiRuntimeCoreClient runtimeClient,
                        CardAiGenerationCore generationCore) {
        }

        private AsyncTask taskWithUser(
                        String fingerprint,
                        String prompt,
                        Long userId,
                        Long cardId,
                        Long deckId,
                        String cardTitle) throws Exception {
                return taskWithUser(fingerprint, prompt, userId, cardId, deckId, cardTitle, false);
        }

        private AsyncTask taskWithUser(
                        String fingerprint,
                        String prompt,
                        Long userId,
                        Long cardId,
                        Long deckId,
                        String cardTitle,
                        boolean forceRegenerate) throws Exception {
                AsyncTask task = new AsyncTask();
                task.setPayload(new ObjectMapper().writeValueAsString(
                                CardAiCacheTaskProducer.AiCacheTaskPayload.from(
                                                fingerprint,
                                                prompt,
                                                null,
                                                new CardAiCacheTaskProducer.AiCacheNotificationTarget(
                                                                userId,
                                                                cardId,
                                                                deckId,
                                                                cardTitle,
                                                                CardAiPromptSupport.SIDE_A))
                                                .withForceRegenerate(forceRegenerate)));
                return task;
        }

        private record PushCall(Long userId, String eventName, AiCacheReadyNotification payload) {
        }

        private static final class FakeCardAiCacheService implements CardAiCacheService {
                private CardAiCache readyCache;
                private String savedFingerprint;
                private String savedContent;
                private int saveReadyFromBackgroundCalls;

                @Override
                public CardAiCache findUsableCacheNoTouch(Long ownerUserId, String fingerprint) {
                        return readyCache;
                }

                @Override
                public void saveReadyFromBackground(Long ownerUserId, String fingerprint, String prompt, String content,
                                Boolean thinkUsed) {
                        saveReadyFromBackgroundCalls++;
                        savedFingerprint = fingerprint;
                        savedContent = content;
                }
        }

        private static final class FakeCardAiGenerationCore extends CardAiGenerationCore {
                private boolean generateCalled;
                private Long lastUserId;

                private FakeCardAiGenerationCore() {
                        super(null, null, null, enabledGuard(), new EffectiveAiProfileResolver(null));
                }

                @Override
                public CardAiGenerationCore.GeneratedCardAiContent generateFromPrompt(
                                String fingerprint,
                                String prompt,
                                openflash_core.config.AiProperties.AiProfile profile,
                                Long userId) {
                        generateCalled = true;
                        lastUserId = userId;
                        return new CardAiGenerationCore.GeneratedCardAiContent(fingerprint, true, "fresh");
                }

                @Override
                public openflash_core.config.AiProperties.AiProfile resolveCardAiProfile() {
                        openflash_core.config.AiProperties.AiProfile profile = new openflash_core.config.AiProperties.AiProfile();
                        profile.setName("card-ai");
                        return profile;
                }

        }

        /** 创建默认开启的 ai-card guard。 */
        private static AiCardFeatureGuard enabledGuard() {
                return new StaticAiCardFeatureGuard(true, true);
        }

        /** 创建关闭 ai-card 的 guard。 */
        private static AiCardFeatureGuard disabledGuard() {
                return new StaticAiCardFeatureGuard(false, true);
        }

        private static final class RecordingUserSseRegistry extends UserSseRegistry {
                private final List<PushCall> pushCalls = new ArrayList<>();

                @Override
                public void push(Long userId, String eventName, Object payload) {
                        pushCalls.add(new PushCall(userId, eventName, (AiCacheReadyNotification) payload));
                }
        }
}
