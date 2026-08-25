package openflash_plugin.ai_card.service.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import openflash_core.common.AiErrorCode;
import openflash_core.service.impl.EffectiveAiProfileResolver;
import openflash_core.common.AppLog;
import openflash_core.config.AiProperties;
import openflash_core.entity.Card;
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_core.mapper.CardMapper;
import openflash_core.service.AsyncTaskQueue;
import openflash_core.service.AsyncTaskTypeSpec;
import openflash_plugin.ai_card.service.DeckAiSettingsService;
import openflash_core.service.impl.AfterCommitScheduler;

@Service
public class CardSideCompletionTaskProducer implements AsyncTaskTypeSpec {

    public static final String TASK_TYPE = "CARD_SIDE_COMPLETION";
    public static final String FEATURE_KEY = "card-side-completion";
    public static final String SIDE_A = CardAiPromptSupport.SIDE_A;
    public static final String SIDE_B = CardAiPromptSupport.SIDE_B;

    private static final Logger LOGGER = LoggerFactory.getLogger(CardSideCompletionTaskProducer.class);

    private final AsyncTaskQueue queue;
    private final CardMapper cardMapper;
    private final AiProperties aiProperties;
    private final DeckAiSettingsService deckAiSettingsService;
    private final AfterCommitScheduler afterCommitScheduler;
    private final AiCardFeatureGuard featureGuard;
    private final EffectiveAiProfileResolver effectiveAiProfileResolver;
    private final int taskPriority;

    @Autowired
    public CardSideCompletionTaskProducer(
            AsyncTaskQueue queue,
            CardMapper cardMapper,
            AiProperties aiProperties,
            DeckAiSettingsService deckAiSettingsService,
            AfterCommitScheduler afterCommitScheduler,
            AiCardFeatureGuard featureGuard,
            EffectiveAiProfileResolver effectiveAiProfileResolver,
            @Value("${app.ai-card.tasks.side-completion-priority:30}") int taskPriority) {
        this.queue = queue;
        this.cardMapper = cardMapper;
        this.aiProperties = aiProperties;
        this.deckAiSettingsService = deckAiSettingsService;
        this.afterCommitScheduler = afterCommitScheduler;
        this.featureGuard = featureGuard;
        this.effectiveAiProfileResolver = effectiveAiProfileResolver;
        this.taskPriority = taskPriority;
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public int priority() {
        return taskPriority;
    }

    @Override
    public int maxRetryCount() {
        return 0;
    }

    public static String buildBizKey(Long cardId, String missingSide) {
        return TASK_TYPE + ":" + cardId + ":" + missingSide;
    }

    /** 返回另一面补全入口当前是否能接收后台任务。 */
    public boolean isSideCompletionEnabled() {
        return featureGuard.isSideCompletionEnabled();
    }

    public void enqueueForCards(Collection<Long> cardIds) {
        if (!isSideCompletionEnabled()) {
            return;
        }
        enqueueForCards(cardIds, null);
    }

    /** userId 写入每条 payload，让 executor 完成后能按用户配置触发 AI 缓存预热。 */
    public void enqueueForCards(Collection<Long> cardIds, Long userId) {
        if (!isSideCompletionEnabled()) {
            return;
        }
        List<Long> distinctIds = cardIds.stream().filter(id -> id != null).distinct().toList();
        if (distinctIds.isEmpty()) {
            return;
        }
        AiProperties.AiProfile profile = null;
        Map<Long, DeckAiSettings> settingsByDeckId = new HashMap<>();
        List<Card> cards = cardMapper.findByIds(distinctIds);
        if (cards == null || cards.isEmpty()) {
            return;
        }
        for (Card card : cards) {
            String missingSide = resolveMissingSideOrNull(card);
            if (missingSide == null) {
                continue;
            }
            DeckAiSettings settings = deckCompletionSettingsFor(card, settingsByDeckId);
            if (settings == null || !Boolean.TRUE.equals(settings.getAiCompletionEnabled())) {
                continue;
            }
            if (profile == null) {
                try {
                    profile = aiProperties.resolveProfile(FEATURE_KEY);
                    profile = effectiveAiProfileResolver
                            .requireActiveIdentity(userId, profile)
                            .effectivePromptProfile();
                } catch (RuntimeException ex) {
                    AppLog.warn(LOGGER, AiErrorCode.AI_PROFILE_UNAVAILABLE, "AI profile 不可用，跳过另一面补全任务投递", ex);
                    return;
                }
            }
            String sourceText = SIDE_A.equals(missingSide) ? card.getSideB() : card.getSideA();
            CardSideCompletionTaskPayload payload = CardSideCompletionTaskPayload.from(
                    card.getId(),
                    missingSide,
                    sourceText,
                    profile,
                    profile.getModel(),
                    userId,
                    settings.getAiCompletionPrompt());
            enqueueByOwner(buildBizKey(card.getId(), missingSide), payload, userId);
        }
    }

    private void enqueueByOwner(String bizKey, CardSideCompletionTaskPayload payload, Long userId) {
        if (userId == null) {
            queue.enqueue(this, bizKey, payload);
            return;
        }
        queue.enqueueOwned(this, bizKey, payload, userId);
    }

    public void triggerCardAfterCommit(Long cardId) {
        if (!isSideCompletionEnabled()) {
            return;
        }
        afterCommitScheduler.schedule(List.of(cardId), this::enqueueForCards);
    }

    public void triggerCardAfterCommit(Long cardId, Long userId) {
        if (!isSideCompletionEnabled()) {
            return;
        }
        afterCommitScheduler.schedule(List.of(cardId), ids -> enqueueForCards(ids, userId));
    }

    public void triggerCardsAfterCommit(Collection<Long> cardIds) {
        if (!isSideCompletionEnabled()) {
            return;
        }
        afterCommitScheduler.schedule(cardIds, this::enqueueForCards);
    }

    public void triggerCardsAfterCommit(Collection<Long> cardIds, Long userId) {
        if (!isSideCompletionEnabled()) {
            return;
        }
        afterCommitScheduler.schedule(cardIds, ids -> enqueueForCards(ids, userId));
    }

    private String resolveMissingSideOrNull(Card card) {
        boolean aBlank = isBlank(card.getSideA());
        boolean bBlank = isBlank(card.getSideB());
        if (aBlank == bBlank) {
            return null;
        }
        return aBlank ? SIDE_A : SIDE_B;
    }

    private DeckAiSettings deckCompletionSettingsFor(Card card, Map<Long, DeckAiSettings> settingsByDeckId) {
        return settingsByDeckId.computeIfAbsent(card.getDeckId(), deckAiSettingsService::getByDeckId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record CardSideCompletionTaskPayload(
            Long cardId,
            String missingSide,
            String sourceText,
            String profileName,
            String model,
            String system,
            Double temperature,
            Long userId,
            String aiCompletionPrompt,
            Boolean aiCompletionPromptSnapshotted) {
        public static CardSideCompletionTaskPayload from(
                Long cardId,
                String missingSide,
                String sourceText,
                AiProperties.AiProfile profile) {
            return from(cardId, missingSide, sourceText, profile, null);
        }

        /** userId 写入 payload，供 executor 取出后传给 AI 缓存预热。 */
        public static CardSideCompletionTaskPayload from(
                Long cardId,
                String missingSide,
                String sourceText,
                AiProperties.AiProfile profile,
                Long userId) {
            return new CardSideCompletionTaskPayload(
                    cardId,
                    missingSide,
                    sourceText,
                    profile == null ? null : profile.getName(),
                    profile == null ? null : profile.getModel(),
                    profile == null ? null : profile.getSystem(),
                    profile == null ? null : profile.getTemperature(),
                    userId,
                    null,
                    Boolean.FALSE);
        }

        /**
         * 创建补全任务参数，并快照入队时的卡包补全提示词。
         * system 固定存 null：补全的 system 只来自卡包提示词（executor 必然覆盖），
         * 不复制 profile 的 system，避免 payload 留下永不生效的提示词误导排查。
         */
        public static CardSideCompletionTaskPayload from(
                Long cardId,
                String missingSide,
                String sourceText,
                AiProperties.AiProfile profile,
                String effectiveModel,
                Long userId,
                String aiCompletionPrompt) {
            // model 优先用入队时解析的 per-user 真实模型；为空时回退 profile 的 model。
            String model = (effectiveModel != null && !effectiveModel.trim().isEmpty())
                    ? effectiveModel
                    : (profile == null ? null : profile.getModel());
            return new CardSideCompletionTaskPayload(
                    cardId,
                    missingSide,
                    sourceText,
                    profile == null ? null : profile.getName(),
                    model,
                    null,
                    profile == null ? null : profile.getTemperature(),
                    userId,
                    aiCompletionPrompt,
                    Boolean.TRUE);
        }

        public AiProperties.AiProfile toProfileOrNull() {
            if (profileName == null && model == null && system == null && temperature == null) {
                return null;
            }
            AiProperties.AiProfile profile = new AiProperties.AiProfile();
            profile.setName(profileName);
            profile.setModel(model);
            profile.setSystem(system);
            profile.setTemperature(temperature == null ? 0.0d : temperature);
            return profile;
        }
    }
}
