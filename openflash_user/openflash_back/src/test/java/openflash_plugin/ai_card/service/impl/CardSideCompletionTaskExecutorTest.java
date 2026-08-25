package openflash_plugin.ai_card.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import openflash_core.common.AiErrorCode;
import openflash_core.service.AiGateway;
import openflash_core.config.AiProperties;
import openflash_core.entity.AsyncTask;
import openflash_core.entity.Card;
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_core.mapper.CardMapper;
import openflash_plugin.ai_card.service.DeckAiSettingsService;
import openflash_core.common.CodexAppException;
import tools.jackson.databind.ObjectMapper;

class CardSideCompletionTaskExecutorTest {

    /**
     * 验证空白目标面会写入 AI 生成内容。
     */
    @Test
    void executeWritesGeneratedTextToBlankSide() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(10L);
        card.setDeckId(100L);
        card.setSideA("apple");
        card.setSideB("");
        cardMapper.cardsById.put(10L, card);

        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("n.苹果;v.苹果（俚）");
        ObjectMapper om = new ObjectMapper();

        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper, aiChatGateway, fakeAiProperties(), om,
                new RecordingCacheTaskProducer(),
                enabledGuard(),
                deckSettingsService(enabledDeckSettings(null)));

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(om.writeValueAsString(payloadFor(10L, "B", "apple")));
        executor.execute(task);

        assertEquals("n.苹果;v.苹果（俚）", cardMapper.lastWrittenSideBValueForId.get(10L));
        assertEquals("apple", aiChatGateway.lastPrompt);
        assertEquals(42L, aiChatGateway.lastUserId);
    }

    /**
     * 验证 AI 返回空内容时任务抛错以便重试。
     */
    @Test
    void executeRetriesWhenAiReturnsBlank() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(11L);
        card.setDeckId(110L);
        card.setSideA("apple");
        card.setSideB("");
        cardMapper.cardsById.put(11L, card);

        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("   ");
        ObjectMapper om = new ObjectMapper();
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper, aiChatGateway, fakeAiProperties(), om,
                new RecordingCacheTaskProducer(),
                enabledGuard(),
                deckSettingsService(enabledDeckSettings(null)));

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(om.writeValueAsString(payloadFor(11L, "B", "apple")));

        try (ExpectedLogCapture logs = ExpectedLogCapture.capture(CardSideCompletionTaskExecutor.class)) {
            assertThrows(IllegalArgumentException.class, () -> executor.execute(task));
            assertEquals(1, logs.events().size());
            assertEquals(Level.ERROR, logs.events().get(0).getLevel());
        }
    }

    /**
     * 验证 AI 返回过长内容时写回前会被截断。
     */
    @Test
    void executeTruncatesOverlongOutput() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(12L);
        card.setDeckId(120L);
        card.setSideA("apple");
        card.setSideB("");
        cardMapper.cardsById.put(12L, card);

        String longText = "x".repeat(500);
        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway(longText);
        ObjectMapper om = new ObjectMapper();
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper, aiChatGateway, fakeAiProperties(), om,
                new RecordingCacheTaskProducer(),
                enabledGuard(),
                deckSettingsService(enabledDeckSettings(null)));

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(om.writeValueAsString(payloadFor(12L, "B", "apple")));
        executor.execute(task);

        assertEquals(200, cardMapper.lastWrittenSideBValueForId.get(12L).length());
    }

    /**
     * 验证目标面已有文字时不会覆盖用户看到的内容。
     */
    @Test
    void executeSkipsWhenTargetSideAlreadyFilled() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(13L);
        card.setDeckId(130L);
        card.setSideA("apple");
        card.setSideB("已被用户手填");
        cardMapper.cardsById.put(13L, card);
        cardMapper.updateSideBAffectedRowsOverride = 0;

        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("AI生成内容");
        ObjectMapper om = new ObjectMapper();
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper, aiChatGateway, fakeAiProperties(), om,
                new RecordingCacheTaskProducer(),
                enabledGuard(),
                deckSettingsService(enabledDeckSettings(null)));

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(om.writeValueAsString(payloadFor(13L, "B", "apple")));
        executor.execute(task);

        assertEquals("已被用户手填", cardMapper.cardsById.get(13L).getSideB());
    }

    /**
     * 验证卡片不存在时不会生成或写回内容。
     */
    @Test
    void executeSkipsWhenCardNotFound() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("AI 内容");
        ObjectMapper om = new ObjectMapper();
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper, aiChatGateway, fakeAiProperties(), om,
                new RecordingCacheTaskProducer(),
                enabledGuard(),
                deckSettingsService(enabledDeckSettings(null)));

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(om.writeValueAsString(payloadFor(999L, "B", "apple")));
        executor.execute(task);

        assertTrue(cardMapper.lastWrittenSideBValueForId.isEmpty());
    }

    /**
     * 验证成功写回后才触发 AI 解释缓存任务。
     */
    @Test
    void executeChainsOnlyAiTriggerAfterSuccessfulWrite() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(20L);
        card.setDeckId(200L);
        card.setSideA("apple");
        card.setSideB("");
        cardMapper.cardsById.put(20L, card);

        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("n.苹果");
        ObjectMapper om = new ObjectMapper();
        RecordingCacheTaskProducer ai = new RecordingCacheTaskProducer();

        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper, aiChatGateway, fakeAiProperties(), om, ai, enabledGuard(),
                deckSettingsService(enabledDeckSettings(null)));

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(om.writeValueAsString(payloadFor(20L, "B", "apple")));
        executor.execute(task);

        assertEquals(List.of(20L), ai.triggeredCardIds);
    }

    /**
     * 验证写回被跳过时不会触发 AI 解释缓存任务。
     */
    @Test
    void executeDoesNotChainWhenWriteSkipped() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(21L);
        card.setDeckId(210L);
        card.setSideA("apple");
        card.setSideB("已填");
        cardMapper.cardsById.put(21L, card);

        RecordingCacheTaskProducer ai = new RecordingCacheTaskProducer();
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper, new FakeAiChatGateway("AI"), fakeAiProperties(), new ObjectMapper(), ai, enabledGuard(),
                deckSettingsService(enabledDeckSettings(null)));

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(new ObjectMapper().writeValueAsString(payloadFor(21L, "B", "apple")));
        executor.execute(task);

        assertTrue(ai.triggeredCardIds.isEmpty());
    }

    /**
     * 验证关闭另一面补全开关时后台任务静默跳过，不读取任务内容和外部依赖。
     */
    @Test
    void executeSkipsBeforePayloadReadWhenFeatureDisabled() {
        FakeCardMapper cardMapper = new FakeCardMapper();
        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("AI 内容");
        RecordingCacheTaskProducer ai = new RecordingCacheTaskProducer();
        StaticDeckAiSettingsService deckSettingsService = deckSettingsService(enabledDeckSettings(null));
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper,
                aiChatGateway,
                fakeAiProperties(),
                new ObjectMapper(),
                ai,
                disabledSideCompletionGuard(),
                deckSettingsService);

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload("{not-json");

        executor.execute(task);

        assertEquals(0, cardMapper.findByIdCalls);
        assertEquals(0, deckSettingsService.getByDeckIdCalls);
        assertEquals(0, aiChatGateway.chatCount);
        assertTrue(ai.triggeredCardIds.isEmpty());
    }

    /**
     * 验证 ai-card 总开关关闭时，即使补全子开关开启，executor 也不读卡、不调用 AI、不写回。
     */
    @Test
    void executeSkipsWhenAiCardDisabledEvenIfSideCompletionEnabled() {
        FakeCardMapper cardMapper = new FakeCardMapper();
        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("AI 内容");
        RecordingCacheTaskProducer ai = new RecordingCacheTaskProducer();
        StaticDeckAiSettingsService deckSettingsService = deckSettingsService(enabledDeckSettings(null));
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper,
                aiChatGateway,
                fakeAiProperties(),
                new ObjectMapper(),
                ai,
                disabledAiCardSideEnabledGuard(),
                deckSettingsService);

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload("{not-json");

        executor.execute(task);

        assertEquals(0, cardMapper.findByIdCalls);
        assertEquals(0, deckSettingsService.getByDeckIdCalls);
        assertEquals(0, aiChatGateway.chatCount);
        assertTrue(cardMapper.lastWrittenSideAValueForId.isEmpty());
        assertTrue(cardMapper.lastWrittenSideBValueForId.isEmpty());
        assertTrue(ai.triggeredCardIds.isEmpty());
    }

    /**
     * 验证卡包关闭另一面补全时，后台任务不生成内容、不写回页面可见卡面。
     */
    @Test
    void executeSkipsWhenDeckCompletionDisabled() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(30L);
        card.setDeckId(300L);
        card.setSideA("apple");
        card.setSideB("");
        cardMapper.cardsById.put(30L, card);
        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("AI 内容");
        ObjectMapper om = new ObjectMapper();
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper,
                aiChatGateway,
                fakeAiProperties(),
                om,
                new RecordingCacheTaskProducer(),
                enabledGuard(),
                deckSettingsService(disabledDeckSettings()));

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(om.writeValueAsString(payloadFor(30L, "B", "apple")));
        executor.execute(task);

        assertEquals(0, aiChatGateway.chatCount);
        assertTrue(cardMapper.lastWrittenSideBValueForId.isEmpty());
    }

    /**
     * 验证卡包补全提示词会作为本次 AI 对话 system 使用。
     */
    @Test
    void executeUsesDeckCompletionPromptAsProfileSystem() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(31L);
        card.setDeckId(310L);
        card.setSideA("apple");
        card.setSideB("");
        cardMapper.cardsById.put(31L, card);
        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("AI 内容");
        ObjectMapper om = new ObjectMapper();
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper,
                aiChatGateway,
                fakeAiProperties(),
                om,
                new RecordingCacheTaskProducer(),
                enabledGuard(),
                deckSettingsService(enabledDeckSettings("deck prompt")));

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(om.writeValueAsString(payloadFor(31L, "B", "apple")));
        executor.execute(task);

        assertEquals("deck prompt", aiChatGateway.lastProfileSystem);
    }

    /**
     * 验证执行补全任务时使用 payload 快照提示词，不使用用户后来改掉的卡包提示词。
     */
    @Test
    void executeUsesPayloadCompletionPromptSnapshot() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(33L);
        card.setDeckId(330L);
        card.setSideA("apple");
        card.setSideB("");
        cardMapper.cardsById.put(33L, card);
        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("AI 内容");
        ObjectMapper om = new ObjectMapper();
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper,
                aiChatGateway,
                fakeAiProperties(),
                om,
                new RecordingCacheTaskProducer(),
                enabledGuard(),
                deckSettingsService(enabledDeckSettings("prompt after enqueue")));

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload("""
                {
                  "cardId": 33,
                  "missingSide": "B",
                  "sourceText": "apple",
                  "profileName": "ai_side_completion",
                  "model": "qwen3.5:9b",
                  "system": "system",
                  "temperature": 0.2,
                  "userId": 42,
                  "aiCompletionPrompt": "prompt at enqueue",
                  "aiCompletionPromptSnapshotted": true
                }
                """);
        executor.execute(task);

        assertEquals("prompt at enqueue", aiChatGateway.lastProfileSystem);
        assertEquals("apple", aiChatGateway.lastPrompt);
        assertEquals(42L, aiChatGateway.lastUserId);
    }

    /** Codex 错误必须在任何卡面写入和后续缓存触发前终止任务。 */
    @Test
    void codexFailureWritesNoCardSideAndTriggersNoCache() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(34L);
        card.setDeckId(340L);
        card.setSideA("apple");
        card.setSideB("");
        cardMapper.cardsById.put(34L, card);
        CodexAppException failure = new CodexAppException(AiErrorCode.AI_CODEX_RUNTIME_FAILED);
        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway(failure);
        RecordingCacheTaskProducer cacheTaskProducer = new RecordingCacheTaskProducer();
        ObjectMapper objectMapper = new ObjectMapper();
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper,
                aiChatGateway,
                fakeAiProperties(),
                objectMapper,
                cacheTaskProducer,
                enabledGuard(),
                deckSettingsService(enabledDeckSettings("deck completion prompt")));
        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(objectMapper.writeValueAsString(payloadFor(34L, "B", "apple")));

        assertThrows(CodexAppException.class, () -> executor.execute(task));

        assertTrue(cardMapper.lastWrittenSideAValueForId.isEmpty());
        assertTrue(cardMapper.lastWrittenSideBValueForId.isEmpty());
        assertTrue(cacheTaskProducer.triggeredCardIds.isEmpty());
    }

    /**
     * 验证卡包补全提示词为空时，本次 AI 对话 system 为空，不继承 payload 或全局 profile。
     */
    @Test
    void executeUsesNullProfileSystemWhenDeckCompletionPromptNull() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(32L);
        card.setDeckId(320L);
        card.setSideA("apple");
        card.setSideB("");
        cardMapper.cardsById.put(32L, card);
        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("AI 内容");
        ObjectMapper om = new ObjectMapper();
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper,
                aiChatGateway,
                fakeAiProperties(),
                om,
                new RecordingCacheTaskProducer(),
                enabledGuard(),
                deckSettingsService(enabledDeckSettings(null)));

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(om.writeValueAsString(payloadFor(32L, "B", "apple")));
        executor.execute(task);

        assertNull(aiChatGateway.lastProfileSystem);
    }

    /**
     * 验证补全写回成功后，AI 缓存预热携带 payload 里的 userId，用于生成时读取用户配置。
     */
    @Test
    void executeChainsAiTriggerWithUserIdFromPayload() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(22L);
        card.setDeckId(220L);
        card.setSideA("apple");
        card.setSideB("");
        cardMapper.cardsById.put(22L, card);

        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("n.苹果");
        ObjectMapper om = new ObjectMapper();
        RecordingCacheTaskProducer ai = new RecordingCacheTaskProducer();

        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper, aiChatGateway, fakeAiProperties(), om, ai, enabledGuard(),
                deckSettingsService(enabledDeckSettings(null)));

        AiProperties.AiProfile profile = fakeAiProperties().resolveProfile(CardSideCompletionTaskProducer.FEATURE_KEY);
        CardSideCompletionTaskProducer.CardSideCompletionTaskPayload payload = CardSideCompletionTaskProducer.CardSideCompletionTaskPayload
                .from(22L, "B", "apple", profile, 42L);

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(om.writeValueAsString(payload));
        executor.execute(task);

        assertEquals(List.of(22L), ai.triggeredCardIds);
        assertEquals(List.of(42L), ai.triggeredUserIds);
        assertEquals(42L, aiChatGateway.lastUserId);
    }

    /**
     * 验证旧队列任务缺少 userId 时，后台任务跳过生成，不改页面内容。
     */
    @Test
    void executeSkipsWhenPayloadHasNoUserId() throws Exception {
        FakeCardMapper cardMapper = new FakeCardMapper();
        Card card = new Card();
        card.setId(23L);
        card.setDeckId(230L);
        card.setSideA("apple");
        card.setSideB("");
        cardMapper.cardsById.put(23L, card);

        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("n.苹果");
        ObjectMapper om = new ObjectMapper();
        RecordingCacheTaskProducer ai = new RecordingCacheTaskProducer();

        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper, aiChatGateway, fakeAiProperties(), om, ai, enabledGuard(),
                deckSettingsService(enabledDeckSettings(null)));

        AiProperties.AiProfile profile = fakeAiProperties().resolveProfile(CardSideCompletionTaskProducer.FEATURE_KEY);
        CardSideCompletionTaskProducer.CardSideCompletionTaskPayload payload = CardSideCompletionTaskProducer.CardSideCompletionTaskPayload
                .from(23L, "B", "apple", profile, null);

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload(om.writeValueAsString(payload));
        try (ExpectedLogCapture logs = ExpectedLogCapture.capture(CardSideCompletionTaskExecutor.class)) {
            executor.execute(task);

            assertTrue(cardMapper.lastWrittenSideBValueForId.isEmpty());
            assertTrue(ai.triggeredCardIds.isEmpty());
            assertTrue(ai.triggeredUserIds.isEmpty());
            assertEquals(0, aiChatGateway.chatCount);
            assertNull(aiChatGateway.lastUserId);
            assertEquals(1, logs.events().size());
            assertEquals(Level.WARN, logs.events().get(0).getLevel());
        }
    }

    /**
     * 验证全局开关关闭时，后台任务不读取任务内容，也不读取卡包设置。
     */
    @Test
    void executeSkipsDeckSettingsWhenFeatureDisabled() {
        FakeCardMapper cardMapper = new FakeCardMapper();
        FakeAiChatGateway aiChatGateway = new FakeAiChatGateway("AI 内容");
        StaticDeckAiSettingsService deckSettingsService = deckSettingsService(enabledDeckSettings("deck prompt"));
        CardSideCompletionTaskExecutor executor = new CardSideCompletionTaskExecutor(
                cardMapper,
                aiChatGateway,
                fakeAiProperties(),
                new ObjectMapper(),
                new RecordingCacheTaskProducer(),
                disabledSideCompletionGuard(),
                deckSettingsService);

        AsyncTask task = new AsyncTask();
        task.setTaskType(CardSideCompletionTaskProducer.TASK_TYPE);
        task.setPayload("{not-json");
        executor.execute(task);

        assertEquals(0, cardMapper.findByIdCalls);
        assertEquals(0, deckSettingsService.getByDeckIdCalls);
        assertEquals(0, aiChatGateway.chatCount);
    }

    /** 创建默认开启另一面补全的 guard。 */
    private static AiCardFeatureGuard enabledGuard() {
        return new StaticAiCardFeatureGuard(true, true);
    }

    /** 创建关闭另一面补全的 guard。 */
    private static AiCardFeatureGuard disabledSideCompletionGuard() {
        return new StaticAiCardFeatureGuard(true, false);
    }

    /** 创建关闭 ai-card 但开启补全子开关的 guard，用于验证总开关优先级。 */
    private static AiCardFeatureGuard disabledAiCardSideEnabledGuard() {
        return new StaticAiCardFeatureGuard(false, true);
    }

    /**
     * 创建固定返回卡包 AI 设置的服务。
     */
    private StaticDeckAiSettingsService deckSettingsService(DeckAiSettings settings) {
        return new StaticDeckAiSettingsService(settings);
    }

    /**
     * 创建开启另一面补全的卡包设置。
     */
    private DeckAiSettings enabledDeckSettings(String aiCompletionPrompt) {
        DeckAiSettings settings = new DeckAiSettings();
        settings.setAiCompletionEnabled(true);
        settings.setAiCompletionPrompt(aiCompletionPrompt);
        return settings;
    }

    /**
     * 创建关闭另一面补全的卡包设置。
     */
    private DeckAiSettings disabledDeckSettings() {
        DeckAiSettings settings = new DeckAiSettings();
        settings.setAiCompletionEnabled(false);
        return settings;
    }

    /**
     * 创建带 payload profile 的另一面补全任务参数。
     */
    private CardSideCompletionTaskProducer.CardSideCompletionTaskPayload payloadFor(Long id, String side, String src) {
        AiProperties.AiProfile profile = fakeAiProperties().resolveProfile(CardSideCompletionTaskProducer.FEATURE_KEY);
        return CardSideCompletionTaskProducer.CardSideCompletionTaskPayload.from(id, side, src, profile, 42L);
    }

    /**
     * 创建用于单测的 AI profile 配置。
     */
    private AiProperties fakeAiProperties() {
        AiProperties p = new AiProperties();
        AiProperties.AiProfile profile = new AiProperties.AiProfile();
        profile.setName("ai_side_completion");
        profile.setModel("qwen3.5:9b");
        profile.setSystem("system");
        profile.setTemperature(0.2d);
        p.setProfiles(List.of(profile));
        p.setFeatureProfiles(Map.of(CardSideCompletionTaskProducer.FEATURE_KEY, "ai_side_completion"));
        return p;
    }

    private static final class FakeAiChatGateway implements AiGateway {
        private final String content;
        private final RuntimeException failure;
        int chatCount;
        String lastPrompt;
        String lastProfileSystem;
        Long lastUserId;

        /**
         * 创建返回固定文字的 AI 网关，便于验证后台任务是否发起生成。
         */
        FakeAiChatGateway(String content) {
            this.content = content;
            this.failure = null;
        }

        /** 创建固定抛出 Codex 错误的网关。 */
        FakeAiChatGateway(RuntimeException failure) {
            this.content = null;
            this.failure = failure;
        }

        /**
         * 记录本次补全调用上下文，便于断言任务参数。
         */
        @Override
        public String chat(String prompt, AiProperties.AiProfile profile, Long userId) {
            chatCount++;
            lastPrompt = prompt;
            lastProfileSystem = profile == null ? null : profile.getSystem();
            lastUserId = userId;
            if (failure != null)
                throw failure;
            return content;
        }

        @Override
        public String chat(
                String prompt,
                AiProperties.AiProfile profile,
                Long userId,
                AiDispatchValidator validator) {
            throw new UnsupportedOperationException("not used by side-completion executor");
        }
    }

    private static final class StaticDeckAiSettingsService implements DeckAiSettingsService {
        private final DeckAiSettings settings;
        int getByDeckIdCalls;

        /**
         * 创建固定返回卡包 AI 设置的服务。
         */
        StaticDeckAiSettingsService(DeckAiSettings settings) {
            this.settings = settings;
        }

        /**
         * 记录卡包设置读取次数并返回固定设置。
         */
        @Override
        public DeckAiSettings getByDeckId(Long deckId) {
            getByDeckIdCalls++;
            return settings;
        }

        /**
         * 单测不覆盖保存卡包 AI 设置。
         */
        @Override
        public DeckAiSettings save(Long deckId, openflash_plugin.ai_card.dto.DeckAiSettingsUpdateCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingCacheTaskProducer extends CardAiCacheTaskProducer {
        private final List<Long> triggeredCardIds = new java.util.ArrayList<>();
        private final List<Long> triggeredUserIds = new java.util.ArrayList<>();

        /**
         * 创建只记录触发卡片的缓存任务生产器。
         */
        RecordingCacheTaskProducer() {
            super(null, null, null, null, null, null, null, enabledGuard(), null);
        }

        /**
         * 记录成功写回后触发的卡片 ID（无 userId 路径）。
         */
        @Override
        public void triggerCardAfterCommit(Long cardId) {
            triggeredCardIds.add(cardId);
            triggeredUserIds.add(null);
        }

        /**
         * 记录成功写回后触发的卡片 ID 和 userId（per-user 路径）。
         */
        @Override
        public void triggerCardAfterCommit(Long cardId, Long userId) {
            triggeredCardIds.add(cardId);
            triggeredUserIds.add(userId);
        }
    }

    private static final class FakeCardMapper implements CardMapper {
        final Map<Long, Card> cardsById = new HashMap<>();
        final Map<Long, String> lastWrittenSideAValueForId = new HashMap<>();
        final Map<Long, String> lastWrittenSideBValueForId = new HashMap<>();
        Integer updateSideAAffectedRowsOverride;
        Integer updateSideBAffectedRowsOverride;
        int findByIdCalls;

        /**
         * 按 ID 返回内存里的卡片并记录查询次数。
         */
        @Override
        public Card findById(Long id) {
            findByIdCalls++;
            return cardsById.get(id);
        }

        /**
         * 本单测不使用批量读取卡片。
         */
        @Override
        public List<Card> findByIds(java.util.Collection<Long> ids) {
            throw new UnsupportedOperationException();
        }

        /**
         * 仅在 A 面为空时写入内容。
         */
        @Override
        public int updateSideAIfEmpty(Long id, String value) {
            if (updateSideAAffectedRowsOverride != null)
                return updateSideAAffectedRowsOverride;
            Card card = cardsById.get(id);
            if (card == null)
                return 0;
            String existing = card.getSideA();
            if (existing != null && !existing.isEmpty())
                return 0;
            card.setSideA(value);
            lastWrittenSideAValueForId.put(id, value);
            return 1;
        }

        /**
         * 仅在 B 面为空时写入内容。
         */
        @Override
        public int updateSideBIfEmpty(Long id, String value) {
            if (updateSideBAffectedRowsOverride != null)
                return updateSideBAffectedRowsOverride;
            Card card = cardsById.get(id);
            if (card == null)
                return 0;
            String existing = card.getSideB();
            if (existing != null && !existing.isEmpty())
                return 0;
            card.setSideB(value);
            lastWrittenSideBValueForId.put(id, value);
            return 1;
        }

        /**
         * 本单测不使用按卡包查卡片。
         */
        @Override
        public List<Card> findByDeckId(Long deckId, String keyword) {
            throw new UnsupportedOperationException();
        }

        /**
         * 本单测不使用分页查卡片。
         */
        @Override
        public List<Card> findPageByDeckId(Long deckId, String keyword, String state, Long userId, Integer offset,
                Integer limit, String sort) {
            throw new UnsupportedOperationException();
        }

        /**
         * 本单测不使用卡片计数。
         */
        @Override
        public Long countByDeckId(Long deckId, String keyword, String state, Long userId) {
            throw new UnsupportedOperationException();
        }

        /**
         * 本单测不使用学习统计查询。
         */
        @Override
        public openflash_core.entity.DeckLearningStats selectLearningStats(Long deckId, Long userId,
                java.time.LocalDate today, Integer newCardsLimit) {
            throw new UnsupportedOperationException();
        }

        /**
         * 本单测不使用复习卡片推荐查询。
         */
        @Override
        public List<openflash_core.entity.TopReviewCard> selectTopReviewCards(Long deckId, Long userId, Integer limit) {
            throw new UnsupportedOperationException();
        }

        /**
         * 本单测不使用去重候选查询。
         */
        @Override
        public List<Card> findDeduplicationCandidates(Long deckId, Long excludingCardId) {
            throw new UnsupportedOperationException();
        }

        /**
         * 本单测不使用插入卡片。
         */
        @Override
        public int insert(Card card) {
            throw new UnsupportedOperationException();
        }

        /**
         * 本单测不使用完整更新卡片。
         */
        @Override
        public int updateCard(Card card) {
            throw new UnsupportedOperationException();
        }

        /**
         * 本单测不使用更新卡片所属卡包。
         */
        @Override
        public int updateDeckId(Long id, Long sourceDeckId, Long targetDeckId) {
            throw new UnsupportedOperationException();
        }

        /**
         * 本单测不使用全部启用卡片 ID 查询。
         */
        @Override
        public List<Long> findAllActiveIds() {
            throw new UnsupportedOperationException();
        }

        /**
         * 本单测不使用 AI 缓存查询。
         */

        /**
         * 本单测不使用 Admin 分批重建目标查询。
         */

        /**
         * 本单测不使用按 ID 删除卡片。
         */
        @Override
        public int deleteById(Long id) {
            throw new UnsupportedOperationException();
        }

        /**
         * 本单测不使用按卡包删除卡片。
         */
        @Override
        public int deleteByDeckId(Long deckId) {
            throw new UnsupportedOperationException();
        }
    }
}
