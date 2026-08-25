package openflash_plugin.ai_card.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import openflash_core.service.UserAiConfigService;
import openflash_core.service.impl.EffectiveAiProfileResolver;
import openflash_core.dto.ActiveAiSelectionDto;
import openflash_core.common.AiSource;
import openflash_core.service.impl.UnifiedAiSelectionServiceImpl;
import openflash_core.config.AsyncTaskProperties;
import openflash_core.config.AiProperties;
import openflash_core.entity.AsyncTask;
import openflash_core.entity.Card;
import openflash_plugin.ai_card.entity.CardAiCache;
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_core.mapper.AsyncTaskMapper;
import openflash_core.mapper.CardMapper;
import openflash_core.service.AsyncTaskQueue;
import openflash_plugin.ai_card.service.CardAiCacheService;
import openflash_plugin.ai_card.service.DeckAiSettingsService;
import openflash_core.service.impl.AfterCommitScheduler;
import tools.jackson.databind.ObjectMapper;

class CardAiCacheTaskProducerTest {

    @Test
    void cacheLookupUsesContentFingerprintAndBizKeyAddsOwner() throws Exception {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper objectMapper = new ObjectMapper();
        UnifiedAiSelectionServiceImpl selections = mock(UnifiedAiSelectionServiceImpl.class);
        when(selections.requireActive(42L)).thenReturn(new ActiveAiSelectionDto(
                AiSource.USER, "openai-main", null, "OPENAI_COMPAT", "gpt-5.4", null));
        when(selections.requireActive(43L)).thenReturn(new ActiveAiSelectionDto(
                AiSource.PLATFORM, null, "platform-openai", "OPENAI_COMPAT", "gpt-5.4", null));
        AiProperties.AiProfile profile = new AiProperties.AiProfile();
        profile.setName("ai_cache");
        profile.setModel("configured-default");
        profile.setTemperature(0.3d);
        AiProperties properties = new AiProperties();
        properties.setProfiles(List.of(profile));
        properties.setFeatureProfiles(Map.of(CardAiGenerationCore.AI_PROFILE_FEATURE_KEY, profile.getName()));
        CardAiGenerationCore generationCore = new CardAiGenerationCore(
                null, properties, null, enabledGuard(),
                new EffectiveAiProfileResolver(mock(UserAiConfigService.class), selections));
        List<String> cacheLookups = new ArrayList<>();
        CardAiCacheService cacheService = new CardAiCacheService() {
            @Override
            public CardAiCache findUsableCacheNoTouch(Long ownerUserId, String fingerprint) {
                cacheLookups.add(ownerUserId + ":" + fingerprint);
                return null;
            }
        };
        DeckAiSettings settings = new DeckAiSettings();
        settings.setAiExplanationEnabledA(true);
        settings.setAiExplanationEnabledB(false);
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(33L);
        card.setDeckId(3300L);
        card.setSideA("apple");
        card.setSideB("苹果");
        cardMapper.cardsById.put(33L, card);
        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), objectMapper),
                cardMapper, generationCore, cacheService,
                deckSettingsService(Map.of(3300L, settings)), properties(),
                new AfterCommitScheduler(), enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(33L), 42L);
        producer.enqueueAiTasksForCards(List.of(33L), 43L);

        CardAiCacheTaskProducer.AiCacheTaskPayload userPayload = objectMapper.readValue(
                asyncTaskMapper.payloads.get(0), CardAiCacheTaskProducer.AiCacheTaskPayload.class);
        CardAiCacheTaskProducer.AiCacheTaskPayload platformPayload = objectMapper.readValue(
                asyncTaskMapper.payloads.get(1), CardAiCacheTaskProducer.AiCacheTaskPayload.class);
        String userFingerprint = userPayload.build().fingerprint();
        String platformFingerprint = platformPayload.build().fingerprint();
        assertEquals(userFingerprint, platformFingerprint);
        assertEquals(List.of("42:" + userFingerprint, "43:" + platformFingerprint), cacheLookups);
        assertEquals(List.of(
                CardAiCacheTaskProducer.buildBizKey(userFingerprint, 42L),
                CardAiCacheTaskProducer.buildBizKey(platformFingerprint, 43L)), asyncTaskMapper.upsertedBizKeys);
        assertNotEquals(asyncTaskMapper.upsertedBizKeys.get(0), asyncTaskMapper.upsertedBizKeys.get(1));
        assertEquals("gpt-5.4", userPayload.build().model());
        assertEquals("gpt-5.4", platformPayload.build().model());
    }

    @Test
    void enqueueAiTasksForCardsQueuesBothSidesWhenCacheMissing() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(1L);
        card.setDeckId(100L);
        card.setSideA("apple");
        card.setSideB("苹果");
        cardMapper.cardsById.put(1L, card);

        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(enabledExplanationSettings(null, null)),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(1L));

        assertEquals(2, asyncTaskMapper.upsertedBizKeys.size());
        assertTrue(asyncTaskMapper.upsertedBizKeys.get(0).startsWith("AI_CACHE_BUILD:"));
        assertEquals("AI_CACHE_BUILD", asyncTaskMapper.lastTaskType);
        assertEquals(50, asyncTaskMapper.lastPriority);
        assertTrue(asyncTaskMapper.lastRescheduleFailed);
        assertEquals(2, asyncTaskMapper.ownerUserIds.size());
        assertEquals(null, asyncTaskMapper.ownerUserIds.get(0));
        assertEquals(null, asyncTaskMapper.ownerUserIds.get(1));
    }

    @Test
    void enqueueAiTasksForCardsSkipsWhenCacheHit() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(2L);
        card.setDeckId(200L);
        card.setSideA("apple");
        card.setSideB("苹果");
        cardMapper.cardsById.put(2L, card);

        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeGenerationCore(),
                new AlwaysHitCardAiCacheService(),
                deckSettingsService(enabledExplanationSettings(null, null)),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(2L));

        assertEquals(0, asyncTaskMapper.upsertedBizKeys.size());
    }

    @Test
    void enqueueAiTasksForCardsSkipsWhenAiCardDisabled() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(20L);
        card.setDeckId(2000L);
        card.setSideA("apple");
        card.setSideB("苹果");
        cardMapper.cardsById.put(20L, card);

        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(enabledExplanationSettings(null, null)),
                properties(),
                new AfterCommitScheduler(),
                disabledAiCardGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(20L));

        assertEquals(0, asyncTaskMapper.upsertedBizKeys.size());
        assertEquals(0, cardMapper.findByIdCount);
    }

    @Test
    void enqueueAiTasksForCardsSkipsBlankSides() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(3L);
        card.setDeckId(300L);
        card.setSideA("");
        card.setSideB("苹果");
        cardMapper.cardsById.put(3L, card);

        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(enabledExplanationSettings(null, null)),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(3L));

        assertEquals(1, asyncTaskMapper.upsertedBizKeys.size());
    }

    /**
     * 验证卡包关闭词卡解析后，后台预热不会排队。
     */
    @Test
    void enqueueAiTasksForCardsSkipsWhenDeckExplanationDisabled() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(30L);
        card.setDeckId(3000L);
        card.setSideA("apple");
        card.setSideB("苹果");
        cardMapper.cardsById.put(30L, card);

        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(Map.of(3000L, disabledExplanationSettings())),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(30L));

        assertEquals(0, asyncTaskMapper.upsertedBizKeys.size());
    }

    /**
     * 验证 A 面解析开关关闭时，A 面不入队，B 面仍正常入队。
     */
    @Test
    void enqueueAiTasksForCardsSkipsSideAWhenSideAExplanationDisabled() throws Exception {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper objectMapper = new ObjectMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(31L);
        card.setDeckId(3100L);
        card.setSideA("apple");
        card.setSideB("苹果");
        cardMapper.cardsById.put(31L, card);

        DeckAiSettings settings = new DeckAiSettings();
        settings.setAiExplanationEnabledA(false);
        settings.setAiExplanationEnabledB(true);
        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), objectMapper),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(Map.of(3100L, settings)),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(31L));

        assertEquals(1, asyncTaskMapper.payloads.size());
        CardAiCacheTaskProducer.AiCacheTaskPayload payload = objectMapper.readValue(
                asyncTaskMapper.payloads.get(0), CardAiCacheTaskProducer.AiCacheTaskPayload.class);
        assertEquals("苹果", payload.build().prompt());
    }

    /**
     * 验证 B 面解析开关关闭时，B 面不入队，A 面仍正常入队。
     */
    @Test
    void enqueueAiTasksForCardsSkipsSideBWhenSideBExplanationDisabled() throws Exception {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper objectMapper = new ObjectMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(32L);
        card.setDeckId(3200L);
        card.setSideA("apple");
        card.setSideB("苹果");
        cardMapper.cardsById.put(32L, card);

        DeckAiSettings settings = new DeckAiSettings();
        settings.setAiExplanationEnabledA(true);
        settings.setAiExplanationEnabledB(false);
        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), objectMapper),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(Map.of(3200L, settings)),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(32L));

        assertEquals(1, asyncTaskMapper.payloads.size());
        CardAiCacheTaskProducer.AiCacheTaskPayload payload = objectMapper.readValue(
                asyncTaskMapper.payloads.get(0), CardAiCacheTaskProducer.AiCacheTaskPayload.class);
        assertEquals("apple", payload.build().prompt());
    }

    /**
     * 验证带 userId 的预热任务会把 userId 同时写入 payload 和 bizKey。
     */
    @Test
    void enqueueAiTasksForCardsIncludesUserIdInBizKey() {
        RecordingAsyncTaskMapper withUserMapper = new RecordingAsyncTaskMapper();
        RecordingAsyncTaskMapper withoutUserMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(33L);
        card.setDeckId(3300L);
        card.setSideA("apple");
        card.setSideB("苹果");
        cardMapper.cardsById.put(33L, card);

        DeckAiSettings settings = enabledExplanationSettings(null, null);
        CardAiCacheTaskProducer producerWithUser = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(withUserMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(Map.of(3300L, settings)),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());
        CardAiCacheTaskProducer producerWithoutUser = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(withoutUserMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(Map.of(3300L, settings)),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producerWithUser.enqueueAiTasksForCards(List.of(33L), 42L);
        producerWithoutUser.enqueueAiTasksForCards(List.of(33L));

        assertEquals(2, withUserMapper.upsertedBizKeys.size());
        assertEquals(2, withoutUserMapper.upsertedBizKeys.size());
        assertNotEquals(withUserMapper.upsertedBizKeys.get(0), withoutUserMapper.upsertedBizKeys.get(0));
        assertNotEquals(withUserMapper.upsertedBizKeys.get(1), withoutUserMapper.upsertedBizKeys.get(1));
        CardAiCacheTaskProducer.AiCacheTaskPayload withUserPayload = new ObjectMapper().readValue(
                withUserMapper.payloads.get(0),
                CardAiCacheTaskProducer.AiCacheTaskPayload.class);
        assertEquals(42L, withUserPayload.build().userId());
    }

    /**
     * 验证普通预热只入队 userId，不固化 provider/protocol，留给 worker 执行时解析。
     */
    @Test
    void cacheWarmingDefersProviderResolutionToExecution() throws Exception {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper objectMapper = new ObjectMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(35L);
        card.setDeckId(3500L);
        card.setSideA("apple");
        card.setSideB("苹果");
        cardMapper.cardsById.put(35L, card);
        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), objectMapper),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(Map.of(3500L, enabledExplanationSettings(null, null))),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(35L), 55L);

        assertEquals(2, asyncTaskMapper.payloads.size());
        for (String payload : asyncTaskMapper.payloads) {
            var build = objectMapper.readTree(payload).path("build");
            assertEquals(55L, build.path("userId").longValue());
            assertFalse(build.has("provider"));
            assertFalse(build.has("protocol"));
        }
    }

    /**
     * 验证卡包 A/B 词卡解析提示词分别进入两面预热任务。
     */
    @Test
    void enqueueAiTasksForCardsUsesDeckPromptsPerSide() throws Exception {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper objectMapper = new ObjectMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(31L);
        card.setDeckId(3100L);
        card.setSideA("apple");
        card.setSideB("苹果");
        cardMapper.cardsById.put(31L, card);

        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), objectMapper),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(Map.of(3100L, enabledExplanationSettings("promptA", "promptB"))),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(31L));

        assertEquals(2, asyncTaskMapper.payloads.size());
        CardAiCacheTaskProducer.AiCacheTaskPayload sideA = objectMapper.readValue(
                asyncTaskMapper.payloads.get(0),
                CardAiCacheTaskProducer.AiCacheTaskPayload.class);
        CardAiCacheTaskProducer.AiCacheTaskPayload sideB = objectMapper.readValue(
                asyncTaskMapper.payloads.get(1),
                CardAiCacheTaskProducer.AiCacheTaskPayload.class);
        assertEquals("promptA", sideA.build().system());
        assertEquals("promptB", sideB.build().system());
    }

    /**
     * 验证卡包词卡解析提示词为空时，后台预热不继承全局 system。
     */
    @Test
    void enqueueAiTasksForCardsWithNullDeckPromptDoesNotUseGlobalSystem() throws Exception {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper objectMapper = new ObjectMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(32L);
        card.setDeckId(3200L);
        card.setSideA("apple");
        card.setSideB("苹果");
        cardMapper.cardsById.put(32L, card);

        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), objectMapper),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(Map.of(3200L, enabledExplanationSettings(null, null))),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(32L));

        assertEquals(2, asyncTaskMapper.payloads.size());
        CardAiCacheTaskProducer.AiCacheTaskPayload sideA = objectMapper.readValue(
                asyncTaskMapper.payloads.get(0),
                CardAiCacheTaskProducer.AiCacheTaskPayload.class);
        CardAiCacheTaskProducer.AiCacheTaskPayload sideB = objectMapper.readValue(
                asyncTaskMapper.payloads.get(1),
                CardAiCacheTaskProducer.AiCacheTaskPayload.class);
        assertEquals(null, sideA.build().system());
        assertEquals(null, sideB.build().system());
    }

    /**
     * 验证同一批次同一卡包只读取一次 AI 设置，避免批量导入后页面等待后台入队变慢。
     */
    @Test
    void enqueueAiTasksForCardsReusesDeckSettingsWithinBatch() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card first = new Card();
        first.setId(35L);
        first.setDeckId(3500L);
        first.setSideA("apple");
        first.setSideB("苹果");
        Card second = new Card();
        second.setId(36L);
        second.setDeckId(3500L);
        second.setSideA("banana");
        second.setSideB("香蕉");
        cardMapper.cardsById.put(35L, first);
        cardMapper.cardsById.put(36L, second);
        CountingDeckAiSettingsService settingsService = new CountingDeckAiSettingsService(
                Map.of(3500L, enabledExplanationSettings(null, null)));

        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                settingsService,
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(35L, 36L));

        assertEquals(1, settingsService.readCountByDeckId.get(3500L));
        assertEquals(4, asyncTaskMapper.upsertedBizKeys.size());
    }

    /**
     * 验证批量 AI 预热不逐张读取卡片，避免导入大量卡片后后台入队变慢。
     */
    @Test
    void enqueueAiTasksForCardsDoesNotReloadEachCardOneByOne() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card first = new Card();
        first.setId(40L);
        first.setDeckId(4000L);
        first.setSideA("apple");
        first.setSideB("苹果");
        Card second = new Card();
        second.setId(41L);
        second.setDeckId(4000L);
        second.setSideA("banana");
        second.setSideB("香蕉");
        cardMapper.cardsById.put(40L, first);
        cardMapper.cardsById.put(41L, second);

        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(Map.of(4000L, enabledExplanationSettings(null, null))),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(40L, 41L));

        assertEquals(0, cardMapper.findByIdCount);
        assertEquals(4, asyncTaskMapper.upsertedBizKeys.size());
    }

    /**
     * 验证同一批 AI 预热只解析一次全局 profile，避免 A/B 两面重复读取 AI 配置。
     */
    @Test
    void enqueueAiTasksForCardsResolvesAiProfileOncePerBatch() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card first = new Card();
        first.setId(37L);
        first.setDeckId(3700L);
        first.setSideA("apple");
        first.setSideB("苹果");
        Card second = new Card();
        second.setId(38L);
        second.setDeckId(3700L);
        second.setSideA("banana");
        second.setSideB("香蕉");
        cardMapper.cardsById.put(37L, first);
        cardMapper.cardsById.put(38L, second);
        CountingGenerationCore generationCore = new CountingGenerationCore();

        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                generationCore,
                new FakeCardAiCacheService(),
                deckSettingsService(Map.of(3700L, enabledExplanationSettings(null, null))),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(37L, 38L));

        assertEquals(1, generationCore.resolveProfileCount);
        assertEquals(4, asyncTaskMapper.upsertedBizKeys.size());
    }

    /**
     * 验证批量任务内同一 userId 的真实模型只解析一次，不随卡面数量重复读用户配置。
     */
    @Test
    void enqueueAiTasksForCardsPreparesIdentityForEveryEnabledSide() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card first = new Card();
        first.setId(41L);
        first.setDeckId(4100L);
        first.setSideA("apple");
        first.setSideB("苹果");
        Card second = new Card();
        second.setId(42L);
        second.setDeckId(4100L);
        second.setSideA("banana");
        second.setSideB("香蕉");
        cardMapper.cardsById.put(41L, first);
        cardMapper.cardsById.put(42L, second);
        CountingGenerationCore generationCore = new CountingGenerationCore();

        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                generationCore,
                new FakeCardAiCacheService(),
                deckSettingsService(Map.of(4100L, enabledExplanationSettings(null, null))),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueAiTasksForCards(List.of(41L, 42L), 7L);

        assertEquals(4, generationCore.prepareCount);
        assertEquals(4, asyncTaskMapper.upsertedBizKeys.size());
    }

    @Test
    void triggerCardAfterCommitSchedulesAiEnqueue() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = new Card();
        card.setId(4L);
        card.setDeckId(400L);
        card.setSideA("apple");
        card.setSideB("苹果");
        cardMapper.cardsById.put(4L, card);
        RecordingAfterCommitScheduler scheduler = new RecordingAfterCommitScheduler();

        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(enabledExplanationSettings(null, null)),
                properties(),
                scheduler,
                enabledGuard(), installedGate());

        producer.triggerCardAfterCommit(4L);

        assertEquals(List.of(4L), scheduler.scheduledIds);
        assertEquals(2, asyncTaskMapper.upsertedBizKeys.size());
    }

    @Test
    void enqueueWithUserContextQueuesPayloadWithCardContext() throws Exception {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper objectMapper = new ObjectMapper();
        Card card = new Card();
        card.setId(10L);
        card.setDeckId(20L);
        card.setSideA("apple");
        AiProperties.AiProfile profile = new AiProperties.AiProfile();
        profile.setName("ai_cache");
        profile.setModel("qwen");
        profile.setSystem("system");
        profile.setTemperature(0.3d);
        CardAiGenerationCore.PreparedCardAiRequest prepared = new CardAiGenerationCore.PreparedCardAiRequest(
                CardAiPromptSupport.SIDE_A,
                "prompt text",
                "fp-123",
                false,
                profile,
                card,
                null);
        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), objectMapper),
                new RecordingCardMapper(),
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(enabledExplanationSettings(null, null)),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueWithUserContext(prepared, 99L);

        assertEquals(List.of("AI_CACHE_BUILD:fp-123:user:99:card:10"), asyncTaskMapper.upsertedBizKeys);
        assertEquals("AI_CACHE_BUILD", asyncTaskMapper.lastTaskType);
        assertEquals(50, asyncTaskMapper.lastPriority);
        assertTrue(asyncTaskMapper.lastRescheduleFailed);
        assertEquals(List.of(99L), asyncTaskMapper.ownerUserIds);
        CardAiCacheTaskProducer.AiCacheTaskPayload payload = objectMapper.readValue(
                asyncTaskMapper.lastPayload,
                CardAiCacheTaskProducer.AiCacheTaskPayload.class);
        CardAiCacheTaskProducer.AiCacheBuildPayload build = payload.build();
        CardAiCacheTaskProducer.AiCacheNotificationTarget notificationTarget = payload.notificationTarget();
        assertEquals("fp-123", build.fingerprint());
        assertEquals("prompt text", build.prompt());
        assertEquals("ai_cache", build.profileName());
        assertEquals("qwen", build.model());
        assertEquals("system", build.system());
        assertEquals(0.3d, build.temperature());
        assertEquals(99L, build.userId());
        assertEquals(20L, build.deckId());
        assertEquals(99L, notificationTarget.userId());
        assertEquals(10L, notificationTarget.cardId());
        assertEquals(20L, notificationTarget.deckId());
        assertEquals("apple", notificationTarget.cardTitle());
    }

    @Test
    void enqueueWithUserContextUsesRequestedSideTitle() throws Exception {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper objectMapper = new ObjectMapper();
        Card card = new Card();
        card.setId(10L);
        card.setDeckId(20L);
        card.setSideA("apple");
        card.setSideB("苹果");
        CardAiGenerationCore.PreparedCardAiRequest prepared = new CardAiGenerationCore.PreparedCardAiRequest(
                CardAiPromptSupport.SIDE_B,
                "prompt text",
                "fp-123",
                false,
                new AiProperties.AiProfile(),
                card,
                null);
        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), objectMapper),
                new RecordingCardMapper(),
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(enabledExplanationSettings(null, null)),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueWithUserContext(prepared, 99L);

        CardAiCacheTaskProducer.AiCacheTaskPayload payload = objectMapper.readValue(
                asyncTaskMapper.lastPayload,
                CardAiCacheTaskProducer.AiCacheTaskPayload.class);
        assertEquals(99L, payload.build().userId());
        assertEquals(CardAiPromptSupport.SIDE_B, payload.notificationTarget().side());
        assertEquals("苹果", payload.notificationTarget().cardTitle());
    }

    @Test
    void enqueueWithUserContextKeepsDistinctWaitersForSameFingerprint() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper objectMapper = new ObjectMapper();
        AiProperties.AiProfile profile = new AiProperties.AiProfile();
        Card firstCard = new Card();
        firstCard.setId(10L);
        firstCard.setDeckId(20L);
        firstCard.setSideA("apple");
        Card secondCard = new Card();
        secondCard.setId(11L);
        secondCard.setDeckId(21L);
        secondCard.setSideA("apple");
        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), objectMapper),
                new RecordingCardMapper(),
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(enabledExplanationSettings(null, null)),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());

        producer.enqueueWithUserContext(
                new CardAiGenerationCore.PreparedCardAiRequest(CardAiPromptSupport.SIDE_A, "prompt text", "fp-123",
                        false, profile, firstCard, null),
                99L);
        producer.enqueueWithUserContext(
                new CardAiGenerationCore.PreparedCardAiRequest(CardAiPromptSupport.SIDE_A, "prompt text", "fp-123",
                        false, profile, secondCard, null),
                100L);

        assertEquals(
                List.of("AI_CACHE_BUILD:fp-123:user:99:card:10", "AI_CACHE_BUILD:fp-123:user:100:card:11"),
                asyncTaskMapper.upsertedBizKeys);
    }

    @Test
    void enqueueWithUserContextRequiresPreparedFields() {
        CardAiCacheTaskProducer producer = new CardAiCacheTaskProducer(
                new AsyncTaskQueue(new RecordingAsyncTaskMapper(), properties(), new ObjectMapper()),
                new RecordingCardMapper(),
                fakeGenerationCore(),
                new FakeCardAiCacheService(),
                deckSettingsService(enabledExplanationSettings(null, null)),
                properties(),
                new AfterCommitScheduler(),
                enabledGuard(), installedGate());
        Card card = new Card();
        card.setId(10L);
        card.setDeckId(20L);
        card.setSideA("apple");
        AiProperties.AiProfile profile = new AiProperties.AiProfile();

        assertEquals(
                "prepared must not be null",
                assertThrows(NullPointerException.class, () -> producer.enqueueWithUserContext(null, 99L))
                        .getMessage());
        assertEquals(
                "prepared.card must not be null",
                assertThrows(NullPointerException.class, () -> producer.enqueueWithUserContext(
                        new CardAiGenerationCore.PreparedCardAiRequest(CardAiPromptSupport.SIDE_A, "prompt", "fp",
                                false, profile, null, null),
                        99L)).getMessage());
        assertEquals(
                "prepared.fingerprint must not be null",
                assertThrows(NullPointerException.class, () -> producer.enqueueWithUserContext(
                        new CardAiGenerationCore.PreparedCardAiRequest(CardAiPromptSupport.SIDE_A, "prompt", null,
                                false, profile, card, null),
                        99L)).getMessage());
        assertEquals(
                "prepared.prompt must not be null",
                assertThrows(NullPointerException.class, () -> producer.enqueueWithUserContext(
                        new CardAiGenerationCore.PreparedCardAiRequest(CardAiPromptSupport.SIDE_A, null, "fp", false,
                                profile, card, null),
                        99L)).getMessage());
        assertEquals(
                "userId must not be null",
                assertThrows(NullPointerException.class, () -> producer.enqueueWithUserContext(
                        new CardAiGenerationCore.PreparedCardAiRequest(CardAiPromptSupport.SIDE_A, "prompt", "fp",
                                false, profile, card, null),
                        null)).getMessage());
        card.setId(null);
        assertEquals(
                "prepared.card.id must not be null",
                assertThrows(NullPointerException.class, () -> producer.enqueueWithUserContext(
                        new CardAiGenerationCore.PreparedCardAiRequest(CardAiPromptSupport.SIDE_A, "prompt", "fp",
                                false, profile, card, null),
                        99L)).getMessage());
    }

    private AsyncTaskProperties properties() {
        AsyncTaskProperties properties = new AsyncTaskProperties();
        properties.setMaxRetryCount(3);
        return properties;
    }

    private CardAiGenerationCore fakeGenerationCore() {
        return new CardAiGenerationCore(null, null, null, enabledGuard(), new EffectiveAiProfileResolver(null)) {
            /**
             * 返回固定全局 profile，system 用来验证空卡包提示词不会继承。
             */
            @Override
            public AiProperties.AiProfile resolveCardAiProfile() {
                AiProperties.AiProfile profile = new AiProperties.AiProfile();
                profile.setName("ai_cache");
                profile.setModel("qwen");
                profile.setSystem("system");
                profile.setTemperature(0.3d);
                return profile;
            }

            /**
             * 按卡包提示词准备请求，模拟生产逻辑里的 system 覆盖和指纹重算。
             */
            @Override
            public PreparedCardAiRequest prepare(Card card, String side, String deckSystemPrompt) {
                return prepare(card, side, deckSystemPrompt, null);
            }

            /**
             * 按卡包提示词和用户 ID 准备请求，userId 只用于生成配置。
             */
            @Override
            public PreparedCardAiRequest prepare(Card card, String side, String deckSystemPrompt, Long userId) {
                return prepare(card, side, deckSystemPrompt, userId, resolveCardAiProfile());
            }

            /**
             * 按已解析 profile 准备请求，模拟批量任务复用 profile。
             */
            @Override
            public PreparedCardAiRequest prepare(
                    Card card,
                    String side,
                    String deckSystemPrompt,
                    Long userId,
                    AiProperties.AiProfile baseProfile) {
                return prepare(card, side, deckSystemPrompt, userId, baseProfile, null);
            }

            /**
             * 按已解析 profile 和已解析用户模型准备请求，模拟生产的 system 覆盖和模型合并。
             */
            @Override
            public PreparedCardAiRequest prepare(
                    Card card,
                    String side,
                    String deckSystemPrompt,
                    Long userId,
                    AiProperties.AiProfile baseProfile,
                    String resolvedUserModel) {
                String normalizedSide = CardAiPromptSupport.normalizeSide(side);
                String prompt = CardAiPromptSupport.normalizePrompt(card, normalizedSide);
                AiProperties.AiProfile profile = CardAiPromptSupport.withSystem(baseProfile, deckSystemPrompt);
                profile = new EffectiveAiProfileResolver(null).applyModel(profile, resolvedUserModel);
                String fingerprint = CardAiPromptSupport.buildFingerprint(prompt, profile, userId);
                return new PreparedCardAiRequest(normalizedSide, prompt, fingerprint, false, profile, card, userId);
            }

        };
    }

    private final class CountingGenerationCore extends CardAiGenerationCore {
        int resolveProfileCount;
        int prepareCount;

        CountingGenerationCore() {
            super(null, new AiProperties(), null, enabledGuard(), new EffectiveAiProfileResolver(null));
        }

        /**
         * 记录 profile 解析次数。
         */
        @Override
        public AiProperties.AiProfile resolveCardAiProfile() {
            resolveProfileCount += 1;
            AiProperties.AiProfile profile = new AiProperties.AiProfile();
            profile.setName("ai_cache");
            profile.setModel("qwen");
            profile.setSystem("system");
            profile.setTemperature(0.3d);
            return profile;
        }

        @Override
        public PreparedCardAiRequest prepare(
                Card card,
                String side,
                String deckSystemPrompt,
                Long userId,
                AiProperties.AiProfile baseProfile) {
            prepareCount += 1;
            String normalizedSide = CardAiPromptSupport.normalizeSide(side);
            String prompt = CardAiPromptSupport.normalizePrompt(card, normalizedSide);
            AiProperties.AiProfile profile = CardAiPromptSupport.withSystem(baseProfile, deckSystemPrompt);
            return new PreparedCardAiRequest(
                    normalizedSide, prompt, CardAiPromptSupport.buildFingerprint(prompt),
                    false, profile, card, userId);
        }
    }

    /**
     * 创建固定返回卡包设置的测试服务。
     */
    private DeckAiSettingsService deckSettingsService(DeckAiSettings settings) {
        return new StaticDeckAiSettingsService(settings);
    }

    /**
     * 创建按卡包 ID 返回卡包设置的测试服务。
     */
    private DeckAiSettingsService deckSettingsService(Map<Long, DeckAiSettings> settingsByDeckId) {
        return new StaticDeckAiSettingsService(settingsByDeckId);
    }

    /**
     * 创建 A/B 面均开启词卡解析的卡包设置。
     */
    private DeckAiSettings enabledExplanationSettings(String promptA, String promptB) {
        DeckAiSettings settings = new DeckAiSettings();
        settings.setAiExplanationEnabledA(true);
        settings.setAiExplanationEnabledB(true);
        settings.setAiExplanationPromptA(promptA);
        settings.setAiExplanationPromptB(promptB);
        return settings;
    }

    /**
     * 创建 A/B 面均关闭词卡解析的卡包设置。
     */
    private DeckAiSettings disabledExplanationSettings() {
        DeckAiSettings settings = new DeckAiSettings();
        settings.setAiExplanationEnabledA(false);
        settings.setAiExplanationEnabledB(false);
        return settings;
    }

    /**
     * 创建默认开启的 ai-card guard，避免测试替身触发关闭分支。
     */
    private AiCardFeatureGuard enabledGuard() {
        return new StaticAiCardFeatureGuard(true, true);
    }

    /** 创建默认已安装的 ai-card 门控, 让非门控测试只关注自身行为. */
    private AiCardInstallGate installedGate() {
        AiCardInstallGate gate = mock(AiCardInstallGate.class);
        when(gate.isInstalledOnDeck(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        return gate;
    }

    /** 创建关闭 ai-card 的 guard，用于验证任务入口直接跳过。 */
    private AiCardFeatureGuard disabledAiCardGuard() {
        return new StaticAiCardFeatureGuard(false, true);
    }

    private static final class AlwaysHitCardAiCacheService implements CardAiCacheService {
        @Override
        public CardAiCache findUsableCacheNoTouch(Long ownerUserId, String fingerprint) {
            CardAiCache cache = new CardAiCache();
            cache.setContentFingerprint(fingerprint);
            return cache;
        }
    }

    private static final class FakeCardAiCacheService implements CardAiCacheService {
        @Override
        public CardAiCache findUsableCacheNoTouch(Long ownerUserId, String fingerprint) {
            return null;
        }
    }

    private static final class StaticDeckAiSettingsService implements DeckAiSettingsService {
        private final Map<Long, DeckAiSettings> settingsByDeckId;
        private final DeckAiSettings settings;

        /**
         * 创建固定返回设置的卡包设置服务。
         */
        StaticDeckAiSettingsService(DeckAiSettings settings) {
            this.settingsByDeckId = Map.of();
            this.settings = settings;
        }

        /**
         * 创建按卡包 ID 返回设置的卡包设置服务。
         */
        StaticDeckAiSettingsService(Map<Long, DeckAiSettings> settingsByDeckId) {
            this.settingsByDeckId = settingsByDeckId;
            this.settings = null;
        }

        /**
         * 按卡包 ID 返回对应设置；没有映射时返回固定默认设置。
         */
        @Override
        public DeckAiSettings getByDeckId(Long deckId) {
            return settingsByDeckId.getOrDefault(deckId, settings);
        }

        /**
         * 单测不覆盖保存卡包设置。
         */
        @Override
        public DeckAiSettings save(Long deckId, openflash_plugin.ai_card.dto.DeckAiSettingsUpdateCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CountingDeckAiSettingsService implements DeckAiSettingsService {
        private final Map<Long, DeckAiSettings> settingsByDeckId;
        private final Map<Long, Integer> readCountByDeckId = new HashMap<>();

        CountingDeckAiSettingsService(Map<Long, DeckAiSettings> settingsByDeckId) {
            this.settingsByDeckId = settingsByDeckId;
        }

        @Override
        public DeckAiSettings getByDeckId(Long deckId) {
            readCountByDeckId.merge(deckId, 1, Integer::sum);
            return settingsByDeckId.get(deckId);
        }

        @Override
        public DeckAiSettings save(Long deckId, openflash_plugin.ai_card.dto.DeckAiSettingsUpdateCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingCardMapper implements CardMapper {
        final Map<Long, Card> cardsById = new HashMap<>();
        int findByIdCount;

        @Override
        public List<Card> findByDeckId(Long deckId, String keyword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Card> findPageByDeckId(Long deckId, String keyword, String state, Long userId, Integer offset,
                Integer limit, String sort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Long countByDeckId(Long deckId, String keyword, String state, Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public openflash_core.entity.DeckLearningStats selectLearningStats(Long deckId, Long userId,
                java.time.LocalDate today, Integer newCardsLimit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<openflash_core.entity.TopReviewCard> selectTopReviewCards(Long deckId, Long userId, Integer limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Card> findDeduplicationCandidates(Long deckId, Long excludingCardId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Card findById(Long id) {
            findByIdCount += 1;
            return cardsById.get(id);
        }

        @Override
        public List<Card> findByIds(java.util.Collection<Long> ids) {
            return ids.stream()
                    .map(cardsById::get)
                    .filter(card -> card != null)
                    .toList();
        }

        @Override
        public int insert(Card card) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateCard(Card card) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateDeckId(Long id, Long sourceDeckId, Long targetDeckId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateSideAIfEmpty(Long id, String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateSideBIfEmpty(Long id, String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Long> findAllActiveIds() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteByDeckId(Long deckId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingAfterCommitScheduler extends AfterCommitScheduler {
        List<Long> scheduledIds = List.of();

        /**
         * 记录调度的卡片 ID，并立即执行任务。
         */
        @Override
        public void schedule(Collection<Long> cardIds, Consumer<List<Long>> task) {
            scheduledIds = cardIds.stream().filter(id -> id != null).distinct().toList();
            if (!scheduledIds.isEmpty()) {
                task.accept(scheduledIds);
            }
        }
    }

    private static final class RecordingAsyncTaskMapper implements AsyncTaskMapper {
        final List<String> upsertedBizKeys = new ArrayList<>();
        final List<String> payloads = new ArrayList<>();
        final List<Long> ownerUserIds = new ArrayList<>();
        String lastTaskType;
        String lastPayload;
        int lastPriority;
        boolean lastRescheduleFailed;

        @Override
        public int upsertTask(
                String bizKey,
                String taskType,
                String payload,
                Long ownerUserId,
                int maxRetryCount,
                int priority,
                boolean rescheduleFailed) {
            upsertedBizKeys.add(bizKey);
            payloads.add(payload);
            ownerUserIds.add(ownerUserId);
            lastTaskType = taskType;
            lastPayload = payload;
            lastPriority = priority;
            lastRescheduleFailed = rescheduleFailed;
            return 1;
        }

        @Override
        public List<AsyncTask> findClaimableBatch(LocalDateTime now, int limit) {
            return new ArrayList<>();
        }

        @Override
        public int claimById(Long id, LocalDateTime now, LocalDateTime leaseUntil) {
            return 0;
        }

        @Override
        public int markCompleted(Long id, LocalDateTime leaseUntil) {
            return 0;
        }

        @Override
        public int markRetry(Long id, LocalDateTime leaseUntil, LocalDateTime nextRetryAt, String lastError) {
            return 0;
        }

        @Override
        public int markFailed(Long id, LocalDateTime leaseUntil, String lastError) {
            return 0;
        }

        @Override
        public int deleteCompletedBefore(LocalDateTime before, int limit) {
            return 0;
        }
    }
}
