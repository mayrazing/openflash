package openflash_plugin.ai_card.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import openflash_core.service.impl.EffectiveAiProfileResolver;
import openflash_core.common.AiErrorCode;
import openflash_core.config.AsyncTaskProperties;
import openflash_core.config.AiProperties;
import openflash_core.entity.AsyncTask;
import openflash_core.entity.Card;
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_core.mapper.AsyncTaskMapper;
import openflash_core.mapper.CardMapper;
import openflash_core.service.AsyncTaskQueue;
import openflash_plugin.ai_card.service.DeckAiSettingsService;
import openflash_core.service.UserAiConfigService;
import openflash_core.dto.ActiveAiSelectionDto;
import openflash_core.common.AiSource;
import openflash_core.service.impl.UnifiedAiSelectionServiceImpl;
import openflash_core.common.AppException;
import openflash_core.service.impl.AfterCommitScheduler;
import tools.jackson.databind.ObjectMapper;

class CardSideCompletionTaskProducerTest {

    @Test
    void enqueueSkipsRevokedSelectionInsteadOfFallingBackToGlobalModel() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        cardMapper.cardsById.put(24L, card(24L, "apple", ""));
        UnifiedAiSelectionServiceImpl selections = mock(UnifiedAiSelectionServiceImpl.class);
        when(selections.requireActive(55L)).thenThrow(new AppException(AiErrorCode.AI_NOT_CONFIGURED));

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(true),
                new AfterCommitScheduler(),
                enabledGuard(),
                new EffectiveAiProfileResolver(mock(UserAiConfigService.class), selections),
                30);

        producer.enqueueForCards(List.of(24L), 55L);

        assertEquals(0, asyncTaskMapper.upsertedBizKeys.size());
    }

    @Test
    void enqueueOnlyForCardWithExactlyOneBlankSide() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();

        Card onlyA = card(1L, "apple", "");
        Card onlyB = card(2L, "", "苹果");
        Card both = card(3L, "apple", "苹果");
        Card none = card(4L, "", "");
        cardMapper.cardsById.put(1L, onlyA);
        cardMapper.cardsById.put(2L, onlyB);
        cardMapper.cardsById.put(3L, both);
        cardMapper.cardsById.put(4L, none);

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(true),
                new AfterCommitScheduler(),
                enabledGuard(),
                activeResolver(AiSource.USER, "openai-main", "qwen3.5:9b", null),
                30);

        producer.enqueueForCards(List.of(1L, 2L, 3L, 4L), 55L);

        assertEquals(2, asyncTaskMapper.upsertedBizKeys.size());
        assertTrue(asyncTaskMapper.upsertedBizKeys.contains("CARD_SIDE_COMPLETION:1:B"));
        assertTrue(asyncTaskMapper.upsertedBizKeys.contains("CARD_SIDE_COMPLETION:2:A"));
        assertEquals("CARD_SIDE_COMPLETION", asyncTaskMapper.lastTaskType);
        assertEquals(30, asyncTaskMapper.lastPriority);
        assertEquals(2, asyncTaskMapper.ownerUserIds.size());
        assertEquals(55L, asyncTaskMapper.ownerUserIds.get(0));
        assertEquals(55L, asyncTaskMapper.ownerUserIds.get(1));
    }

    @Test
    void enqueueSkipsWhenProfileMissing() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        cardMapper.cardsById.put(5L, card(5L, "apple", ""));

        AiProperties failing = new AiProperties() {
            @Override
            public AiProfile resolveProfile(String featureKey) {
                throw new IllegalStateException("缺配置");
            }
        };

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                failing,
                deckSettingsService(true),
                new AfterCommitScheduler(),
                enabledGuard(),
                activeResolver(AiSource.USER, "openai-main", "qwen3.5:9b", null),
                30);

        producer.enqueueForCards(List.of(5L), 55L);

        assertEquals(0, asyncTaskMapper.upsertedBizKeys.size());
    }

    @Test
    void enqueueSkipsWhenDeckCompletionDisabled() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = card(6L, "apple", "");
        card.setDeckId(60L);
        cardMapper.cardsById.put(6L, card);

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(false),
                new AfterCommitScheduler(),
                enabledGuard(),
                activeResolver(AiSource.USER, "openai-main", "qwen3.5:9b", null),
                30);

        producer.enqueueForCards(List.of(6L), 55L);

        assertEquals(0, asyncTaskMapper.upsertedBizKeys.size());
    }

    @Test
    void enqueueSkipsWhenSideCompletionDisabled() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        cardMapper.cardsById.put(15L, card(15L, "apple", ""));

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(true),
                new AfterCommitScheduler(),
                disabledSideCompletionGuard(),
                activeResolver(AiSource.USER, "openai-main", "qwen3.5:9b", null),
                30);

        producer.enqueueForCards(List.of(15L));

        assertEquals(0, asyncTaskMapper.upsertedBizKeys.size());
        assertEquals(0, cardMapper.findByIdCount);
        assertEquals(0, cardMapper.findByIdsCount);
    }

    /**
     * 验证 ai-card 总开关关闭时，即使另一面补全子开关开启，producer 也不读取卡片、不入队。
     */
    @Test
    void enqueueSkipsWhenAiCardDisabledEvenIfSideCompletionEnabled() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        cardMapper.cardsById.put(16L, card(16L, "apple", ""));

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(true),
                new AfterCommitScheduler(),
                disabledAiCardSideEnabledGuard(),
                activeResolver(AiSource.USER, "openai-main", "qwen3.5:9b", null),
                30);

        producer.enqueueForCards(List.of(16L));

        assertEquals(0, asyncTaskMapper.upsertedBizKeys.size());
        assertEquals(0, cardMapper.findByIdCount);
        assertEquals(0, cardMapper.findByIdsCount);
    }

    @Test
    void enqueueIgnoresNullAndDuplicateIds() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        cardMapper.cardsById.put(7L, card(7L, "apple", ""));

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(true),
                new AfterCommitScheduler(),
                enabledGuard(),
                activeResolver(AiSource.USER, "openai-main", "qwen3.5:9b", null),
                30);

        producer.enqueueForCards(Arrays.asList(7L, null, 7L), 55L);

        assertEquals(1, asyncTaskMapper.upsertedBizKeys.size());
    }

    @Test
    void triggerCardsAfterCommitSchedulesSideCompletionEnqueue() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        cardMapper.cardsById.put(8L, card(8L, "apple", ""));
        RecordingAfterCommitScheduler scheduler = new RecordingAfterCommitScheduler();
        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(true),
                scheduler,
                enabledGuard(),
                activeResolver(AiSource.USER, "openai-main", "qwen3.5:9b", null),
                30);

        producer.triggerCardsAfterCommit(List.of(8L), 55L);

        assertEquals(List.of(8L), scheduler.scheduledIds);
        assertEquals(List.of("CARD_SIDE_COMPLETION:8:B"), asyncTaskMapper.upsertedBizKeys);
    }

    /**
     * 验证带 userId 的 triggerCardAfterCommit 把 userId 写进 payload，供 executor 提取并传给 AI
     * 缓存。
     */
    @Test
    void triggerCardAfterCommitWithUserIdIncludesUserIdInPayload() throws Exception {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper om = new ObjectMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        cardMapper.cardsById.put(9L, card(9L, "apple", ""));

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), om),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(true),
                new AfterCommitScheduler(),
                enabledGuard(),
                activeResolver(AiSource.USER, "openai-main", "qwen3.5:9b", null),
                30);

        producer.triggerCardAfterCommit(9L, 55L);

        assertEquals(1, asyncTaskMapper.upsertedBizKeys.size());
        CardSideCompletionTaskProducer.CardSideCompletionTaskPayload payload = om.readValue(asyncTaskMapper.lastPayload,
                CardSideCompletionTaskProducer.CardSideCompletionTaskPayload.class);
        assertEquals(55L, payload.userId());
        assertEquals(List.of(55L), asyncTaskMapper.ownerUserIds);
    }

    /**
     * 验证补全任务保存入队当时的卡包提示词，用户稍后修改提示词不影响已排队任务。
     */
    @Test
    void enqueueForCardsSnapshotsDeckCompletionPromptInPayload() throws Exception {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper om = new ObjectMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card card = card(10L, "apple", "");
        card.setDeckId(100L);
        cardMapper.cardsById.put(10L, card);
        DeckAiSettings settings = new DeckAiSettings();
        settings.setAiCompletionEnabled(true);
        settings.setAiCompletionPrompt("prompt at enqueue");

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), om),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(settings),
                new AfterCommitScheduler(),
                enabledGuard(),
                activeResolver(AiSource.USER, "openai-main", "qwen3.5:9b", null),
                30);

        producer.enqueueForCards(List.of(10L), 55L);

        assertTrue(asyncTaskMapper.lastPayload.contains("\"aiCompletionPrompt\":\"prompt at enqueue\""));
        CardSideCompletionTaskProducer.CardSideCompletionTaskPayload payload = om.readValue(asyncTaskMapper.lastPayload,
                CardSideCompletionTaskProducer.CardSideCompletionTaskPayload.class);
        assertNull(payload.system());
    }

    /**
     * 验证入队时 payload.model 快照「该用户当前真实模型」，而非全局 profile 的旧默认模型。
     * 用户配的是 deepseek，payload 应写 deepseek，而不是全局 profile 的 qwen3.5:9b。
     */
    @Test
    void enqueueSnapshotsPerUserRealModelInPayload() throws Exception {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper om = new ObjectMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        cardMapper.cardsById.put(20L, card(20L, "apple", ""));

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), om),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(true),
                new AfterCommitScheduler(),
                enabledGuard(),
                activeResolver(AiSource.USER, "openai-main", "deepseek-v4-flash", null),
                30);

        producer.enqueueForCards(List.of(20L), 55L);

        CardSideCompletionTaskProducer.CardSideCompletionTaskPayload payload = om.readValue(asyncTaskMapper.lastPayload,
                CardSideCompletionTaskProducer.CardSideCompletionTaskPayload.class);
        assertEquals("deepseek-v4-flash", payload.model());
    }

    /**
     * Codex 配置只有 model/effort，没有 API Key/Base URL，模型解析仍必须把它视为已配置。
     */
    @Test
    void enqueueRecognizesCodexOnlyActiveConfigModel() throws Exception {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper objectMapper = new ObjectMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        cardMapper.cardsById.put(23L, card(23L, "apple", ""));
        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), objectMapper),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(true),
                new AfterCommitScheduler(),
                enabledGuard(),
                activeResolver(AiSource.PLATFORM, "platform-codex-cli", "gpt-5.4", "high"),
                30);

        producer.enqueueForCards(List.of(23L), 55L);

        CardSideCompletionTaskProducer.CardSideCompletionTaskPayload payload = objectMapper.readValue(
                asyncTaskMapper.lastPayload, CardSideCompletionTaskProducer.CardSideCompletionTaskPayload.class);
        assertEquals("gpt-5.4", payload.model());
    }

    /**
     * 验证 userId 为 null 时 selection 不可用，不回退全局模型。
     */
    @Test
    void enqueueSkipsWhenUserIdNull() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper om = new ObjectMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        cardMapper.cardsById.put(21L, card(21L, "apple", ""));
        UnifiedAiSelectionServiceImpl selections = mock(UnifiedAiSelectionServiceImpl.class);
        when(selections.requireActive(null))
                .thenThrow(new AppException(AiErrorCode.AI_NOT_CONFIGURED));

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), om),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(true),
                new AfterCommitScheduler(),
                enabledGuard(),
                new EffectiveAiProfileResolver(mock(UserAiConfigService.class), selections),
                30);

        producer.enqueueForCards(List.of(21L));

        assertEquals(0, asyncTaskMapper.upsertedBizKeys.size());
    }

    /**
     * 验证用户 active selection 失效时不入队，不回退全局模型。
     */
    @Test
    void enqueueSkipsWhenUserUnconfigured() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        ObjectMapper om = new ObjectMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        cardMapper.cardsById.put(22L, card(22L, "apple", ""));

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), om),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(true),
                new AfterCommitScheduler(),
                enabledGuard(),
                revokedResolver(55L),
                30);

        producer.enqueueForCards(List.of(22L), 55L);

        assertEquals(0, asyncTaskMapper.upsertedBizKeys.size());
    }

    /**
     * 验证批量补全入队不逐张读取卡片，避免导入大量卡片后后台入队变慢。
     */
    @Test
    void enqueueForCardsDoesNotReloadEachCardOneByOne() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card first = card(11L, "apple", "");
        first.setDeckId(110L);
        Card second = card(12L, "banana", "");
        second.setDeckId(110L);
        cardMapper.cardsById.put(11L, first);
        cardMapper.cardsById.put(12L, second);

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeAiProperties(),
                deckSettingsService(true),
                new AfterCommitScheduler(),
                enabledGuard(),
                activeResolver(AiSource.USER, "openai-main", "qwen3.5:9b", null),
                30);

        producer.enqueueForCards(List.of(11L, 12L), 55L);

        assertEquals(0, cardMapper.findByIdCount);
        assertEquals(2, asyncTaskMapper.upsertedBizKeys.size());
    }

    /**
     * 验证同一批次同一卡包只读取一次补全设置，避免批量导入后后台入队重复查设置。
     */
    @Test
    void enqueueForCardsReusesDeckSettingsWithinBatch() {
        RecordingAsyncTaskMapper asyncTaskMapper = new RecordingAsyncTaskMapper();
        RecordingCardMapper cardMapper = new RecordingCardMapper();
        Card first = card(10L, "apple", "");
        first.setDeckId(1000L);
        Card second = card(11L, "banana", "");
        second.setDeckId(1000L);
        cardMapper.cardsById.put(10L, first);
        cardMapper.cardsById.put(11L, second);
        CountingDeckAiSettingsService settingsService = new CountingDeckAiSettingsService(true);

        CardSideCompletionTaskProducer producer = new CardSideCompletionTaskProducer(
                new AsyncTaskQueue(asyncTaskMapper, properties(), new ObjectMapper()),
                cardMapper,
                fakeAiProperties(),
                settingsService,
                new AfterCommitScheduler(),
                enabledGuard(),
                activeResolver(AiSource.USER, "openai-main", "qwen3.5:9b", null),
                30);

        producer.enqueueForCards(List.of(10L, 11L), 55L);

        assertEquals(1, settingsService.readCountByDeckId.get(1000L));
        assertEquals(2, asyncTaskMapper.upsertedBizKeys.size());
    }

    private static Card card(Long id, String a, String b) {
        Card card = new Card();
        card.setId(id);
        card.setSideA(a);
        card.setSideB(b);
        return card;
    }

    private AsyncTaskProperties properties() {
        AsyncTaskProperties props = new AsyncTaskProperties();
        props.setMaxRetryCount(3);
        return props;
    }

    /** 创建默认开启另一面补全的 guard。 */
    private AiCardFeatureGuard enabledGuard() {
        return new StaticAiCardFeatureGuard(true, true);
    }

    /** 创建关闭另一面补全的 guard，用于验证 producer 入口跳过。 */
    private AiCardFeatureGuard disabledSideCompletionGuard() {
        return new StaticAiCardFeatureGuard(true, false);
    }

    /** 创建关闭 ai-card 但开启补全子开关的 guard，用于验证总开关优先级。 */
    private AiCardFeatureGuard disabledAiCardSideEnabledGuard() {
        return new StaticAiCardFeatureGuard(false, true);
    }

    private AiProperties fakeAiProperties() {
        AiProperties p = new AiProperties();
        AiProperties.AiProfile profile = new AiProperties.AiProfile();
        profile.setName("ai_side_completion");
        profile.setModel("qwen3.5:9b");
        profile.setSystem("system");
        profile.setTemperature(0.2d);
        p.setProfiles(List.of(profile));
        p.setFeatureProfiles(Map.of("card-side-completion", "ai_side_completion"));
        return p;
    }

    private EffectiveAiProfileResolver activeResolver(
            AiSource source, String selectionKey, String model, String effort) {
        UnifiedAiSelectionServiceImpl selections = mock(UnifiedAiSelectionServiceImpl.class);
        when(selections.requireActive(org.mockito.ArgumentMatchers.anyLong())).thenReturn(new ActiveAiSelectionDto(
                source,
                source == AiSource.USER ? selectionKey : null,
                source == AiSource.PLATFORM ? selectionKey : null,
                "OPENAI_COMPAT",
                model,
                effort));
        return new EffectiveAiProfileResolver(mock(UserAiConfigService.class), selections);
    }

    private EffectiveAiProfileResolver revokedResolver(Long userId) {
        UnifiedAiSelectionServiceImpl selections = mock(UnifiedAiSelectionServiceImpl.class);
        when(selections.requireActive(userId)).thenThrow(new AppException(AiErrorCode.AI_NOT_CONFIGURED));
        return new EffectiveAiProfileResolver(mock(UserAiConfigService.class), selections);
    }

    private DeckAiSettingsService deckSettingsService(boolean completionEnabled) {
        return new DeckAiSettingsService() {
            @Override
            public DeckAiSettings getByDeckId(Long deckId) {
                DeckAiSettings settings = new DeckAiSettings();
                settings.setDeckId(deckId);
                settings.setAiCompletionEnabled(completionEnabled);
                return settings;
            }

            @Override
            public DeckAiSettings save(Long deckId, openflash_plugin.ai_card.dto.DeckAiSettingsUpdateCommand command) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private DeckAiSettingsService deckSettingsService(DeckAiSettings fixedSettings) {
        return new DeckAiSettingsService() {
            @Override
            public DeckAiSettings getByDeckId(Long deckId) {
                return fixedSettings;
            }

            @Override
            public DeckAiSettings save(Long deckId, openflash_plugin.ai_card.dto.DeckAiSettingsUpdateCommand command) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class CountingDeckAiSettingsService implements DeckAiSettingsService {
        private final boolean completionEnabled;
        private final Map<Long, Integer> readCountByDeckId = new HashMap<>();

        CountingDeckAiSettingsService(boolean completionEnabled) {
            this.completionEnabled = completionEnabled;
        }

        @Override
        public DeckAiSettings getByDeckId(Long deckId) {
            readCountByDeckId.merge(deckId, 1, Integer::sum);
            DeckAiSettings settings = new DeckAiSettings();
            settings.setDeckId(deckId);
            settings.setAiCompletionEnabled(completionEnabled);
            return settings;
        }

        @Override
        public DeckAiSettings save(Long deckId, openflash_plugin.ai_card.dto.DeckAiSettingsUpdateCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingCardMapper implements CardMapper {
        final Map<Long, Card> cardsById = new HashMap<>();
        int findByIdCount;
        int findByIdsCount;

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
            findByIdsCount += 1;
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
        final List<Long> ownerUserIds = new ArrayList<>();
        String lastTaskType;
        int lastPriority;
        String lastPayload;

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
            ownerUserIds.add(ownerUserId);
            lastTaskType = taskType;
            lastPriority = priority;
            lastPayload = payload;
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
