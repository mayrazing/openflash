package openflash_plugin.ai_card.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import openflash_core.service.AiGateway;
import openflash_core.common.AppLog;
import openflash_plugin.ai_card.common.AiCardErrorCode;
import openflash_core.config.AiProperties;
import openflash_core.entity.AsyncTask;
import openflash_core.entity.Card;
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_core.mapper.CardMapper;
import openflash_core.service.AsyncTaskHandler;
import openflash_plugin.ai_card.service.DeckAiSettingsService;
import tools.jackson.databind.ObjectMapper;

@Service
public class CardSideCompletionTaskExecutor implements AsyncTaskHandler {

    static final int MAX_OUTPUT_LENGTH = 200;
    private static final Logger LOGGER = LoggerFactory.getLogger(CardSideCompletionTaskExecutor.class);

    private final CardMapper cardMapper;
    private final AiGateway aiChatGateway;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final CardAiCacheTaskProducer cardAiCacheTaskProducer;
    private final AiCardFeatureGuard featureGuard;
    private final DeckAiSettingsService deckAiSettingsService;

    /**
     * 注入另一面补全后台任务需要的卡片读写、AI、配置、开关和卡包设置依赖。
     */
    public CardSideCompletionTaskExecutor(
            CardMapper cardMapper,
            AiGateway aiChatGateway,
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            CardAiCacheTaskProducer cardAiCacheTaskProducer,
            AiCardFeatureGuard featureGuard,
            DeckAiSettingsService deckAiSettingsService) {
        this.cardMapper = cardMapper;
        this.aiChatGateway = aiChatGateway;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.cardAiCacheTaskProducer = cardAiCacheTaskProducer;
        this.featureGuard = featureGuard;
        this.deckAiSettingsService = deckAiSettingsService;
    }

    /**
     * 返回本执行器处理的异步任务类型。
     */
    @Override
    public String taskType() {
        return CardSideCompletionTaskProducer.TASK_TYPE;
    }

    /**
     * 执行另一面补全：全局开关、卡包开关、目标面空值均通过后才调用 AI 并写回。
     */
    @Override
    public void execute(AsyncTask task) {
        if (!featureGuard.isSideCompletionEnabled()) {
            return;
        }

        CardSideCompletionTaskProducer.CardSideCompletionTaskPayload payload = readPayload(task.getPayload());
        if (payload.userId() == null) {
            AppLog.warn(LOGGER, AiCardErrorCode.ASYNC_SIDE_COMPLETION_MISSING_USER_ID,
                    "另一面补全任务缺少 userId，跳过 AI 生成 cardId={} side={}", payload.cardId(), payload.missingSide());
            return;
        }

        Card card = cardMapper.findById(payload.cardId());
        if (card == null) {
            return;
        }
        DeckAiSettings deckSettings = deckAiSettingsService.getByDeckId(card.getDeckId());
        if (!Boolean.TRUE.equals(deckSettings.getAiCompletionEnabled())) {
            return;
        }
        String currentTargetSide = CardSideCompletionTaskProducer.SIDE_A.equals(payload.missingSide())
                ? card.getSideA()
                : card.getSideB();
        if (currentTargetSide != null && !currentTargetSide.trim().isEmpty()) {
            return;
        }

        AiProperties.AiProfile profile = payload.toProfileOrNull();
        if (profile == null) {
            profile = aiProperties.resolveProfile(CardSideCompletionTaskProducer.FEATURE_KEY);
        }
        String completionPrompt = Boolean.TRUE.equals(payload.aiCompletionPromptSnapshotted())
                ? payload.aiCompletionPrompt()
                : deckSettings.getAiCompletionPrompt();
        profile = CardAiPromptSupport.withSystem(profile, completionPrompt);

        String raw = aiChatGateway.chat(payload.sourceText(), profile, payload.userId());
        String cleaned = clean(raw);
        if (cleaned == null || cleaned.isEmpty()) {
            AppLog.error(LOGGER, AiCardErrorCode.ASYNC_SIDE_COMPLETION_PARSE_FAILED, "AI 返回内容清洗后为空");
            throw new IllegalArgumentException("AI 返回内容清洗后为空");
        }

        int affected;
        if (CardSideCompletionTaskProducer.SIDE_A.equals(payload.missingSide())) {
            affected = cardMapper.updateSideAIfEmpty(payload.cardId(), cleaned);
        } else {
            affected = cardMapper.updateSideBIfEmpty(payload.cardId(), cleaned);
        }
        if (affected == 0) {
            LOGGER.info("目标面已被用户手填，跳过补全写回 cardId={} side={}", payload.cardId(), payload.missingSide());
        } else {
            cardAiCacheTaskProducer.triggerCardAfterCommit(payload.cardId(), payload.userId());
        }
    }

    /**
     * 清洗 AI 返回内容，去掉代码块标记和换行，并限制最大长度。
     */
    static String clean(String raw) {
        if (raw == null)
            return null;
        String text = raw.replaceAll("```[a-zA-Z]*", "").replace("```", "");
        text = text.replaceAll("\r\n|\r|\n", " ").trim();
        if (text.isEmpty())
            return "";
        if (text.length() > MAX_OUTPUT_LENGTH) {
            text = text.substring(0, MAX_OUTPUT_LENGTH);
        }
        return text;
    }

    /**
     * 将异步任务 payload 解析为另一面补全参数，解析失败时触发重试。
     */
    private CardSideCompletionTaskProducer.CardSideCompletionTaskPayload readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, CardSideCompletionTaskProducer.CardSideCompletionTaskPayload.class);
        } catch (Exception ex) {
            AppLog.error(LOGGER, AiCardErrorCode.ASYNC_SIDE_COMPLETION_PARSE_FAILED, "另一面补全任务负载解析失败", ex);
            throw new IllegalArgumentException("另一面补全任务负载解析失败", ex);
        }
    }
}
