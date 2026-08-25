package openflash_plugin.ai_card.service.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import openflash_core.common.AiErrorCode;
import openflash_core.common.AppLog;
import openflash_core.config.AsyncTaskProperties;
import openflash_core.config.AiProperties;
import openflash_core.entity.Card;
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_core.mapper.CardMapper;
import openflash_core.service.AsyncTaskQueue;
import openflash_core.service.AsyncTaskTypeSpec;
import openflash_plugin.ai_card.service.CardAiCacheService;
import openflash_plugin.ai_card.service.DeckAiSettingsService;
import openflash_core.service.impl.AfterCommitScheduler;

/**
 * AI 缓存补齐任务的生产端：自带任务类型常量、payload 结构和 bizKey 规则。
 */
@Service
public class CardAiCacheTaskProducer implements AsyncTaskTypeSpec {

    public static final String TASK_TYPE = "AI_CACHE_BUILD";
    private static final Logger LOGGER = LoggerFactory.getLogger(CardAiCacheTaskProducer.class);

    private final AsyncTaskQueue queue;
    private final CardMapper cardMapper;
    private final CardAiGenerationCore cardAiGenerationCore;
    private final CardAiCacheService cardAiCacheService;
    private final DeckAiSettingsService deckAiSettingsService;
    private final AfterCommitScheduler afterCommitScheduler;
    private final AiCardFeatureGuard featureGuard;
    private final AiCardInstallGate aiCardInstallGate;
    private final int taskPriority;

    @Autowired
    public CardAiCacheTaskProducer(
            AsyncTaskQueue queue,
            CardMapper cardMapper,
            CardAiGenerationCore cardAiGenerationCore,
            CardAiCacheService cardAiCacheService,
            DeckAiSettingsService deckAiSettingsService,
            AfterCommitScheduler afterCommitScheduler,
            AiCardFeatureGuard featureGuard,
            AiCardInstallGate aiCardInstallGate,
            @Value("${app.ai-card.tasks.cache-build-priority:50}") int taskPriority) {
        this.queue = queue;
        this.cardMapper = cardMapper;
        this.cardAiGenerationCore = cardAiGenerationCore;
        this.cardAiCacheService = cardAiCacheService;
        this.deckAiSettingsService = deckAiSettingsService;
        this.afterCommitScheduler = afterCommitScheduler;
        this.featureGuard = featureGuard;
        this.aiCardInstallGate = aiCardInstallGate;
        this.taskPriority = taskPriority;
    }

    CardAiCacheTaskProducer(
            AsyncTaskQueue queue,
            CardMapper cardMapper,
            CardAiGenerationCore cardAiGenerationCore,
            CardAiCacheService cardAiCacheService,
            DeckAiSettingsService deckAiSettingsService,
            AsyncTaskProperties ignoredAsyncTaskProperties,
            AfterCommitScheduler afterCommitScheduler,
            AiCardFeatureGuard featureGuard,
            AiCardInstallGate aiCardInstallGate) {
        this(queue, cardMapper, cardAiGenerationCore, cardAiCacheService, deckAiSettingsService,
                afterCommitScheduler, featureGuard, aiCardInstallGate, 50);
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
        /* fall back to global */ }

    @Override
    public boolean rescheduleFailedOnDuplicate() {
        return true;
    }

    public static String buildBizKey(String fingerprint, Long userId) {
        return TASK_TYPE + ":" + fingerprint + ":user:" + userId;
    }

    public static String buildUserContextBizKey(String fingerprint, Long userId, Long cardId) {
        return buildBizKey(fingerprint, userId) + ":card:" + cardId;
    }

    /**
     * 为一批卡的 A/B 两面补投 AI 缓存任务，不带用户上下文（admin / 后台任务场景）。
     */
    public void enqueueAiTasksForCards(Collection<Long> cardIds) {
        if (!featureGuard.isAiCardEnabled()) {
            return;
        }
        enqueueAiTasksForCards(cardIds, null);
    }

    /**
     * 为一批卡的 A/B 两面补投 AI 缓存任务，userId 只用于生成时读取用户 AI 配置。
     */
    public void enqueueAiTasksForCards(Collection<Long> cardIds, Long userId) {
        if (!featureGuard.isAiCardEnabled()) {
            return;
        }
        List<Long> distinctIds = cardIds.stream().filter(id -> id != null).distinct().toList();
        if (distinctIds.isEmpty()) {
            return;
        }
        List<Card> cards = cardMapper.findByIds(distinctIds);
        if (cards == null || cards.isEmpty()) {
            return;
        }
        Map<Long, DeckAiSettings> settingsByDeckId = new HashMap<>();
        Map<Long, Boolean> installedByDeckId = new HashMap<>();
        BatchProfile batchProfile = new BatchProfile();
        for (Card card : cards) {
            enqueueCardIfExplanationEnabled(card, userId, settingsByDeckId, installedByDeckId, batchProfile);
        }
    }

    public void triggerCardAfterCommit(Long cardId) {
        if (!featureGuard.isAiCardEnabled()) {
            return;
        }
        triggerCardAfterCommit(cardId, null);
    }

    /**
     * 事务提交后异步触发单张卡的预热，userId 只用于生成时读取用户 AI 配置。
     */
    public void triggerCardAfterCommit(Long cardId, Long userId) {
        if (!featureGuard.isAiCardEnabled()) {
            return;
        }
        afterCommitScheduler.schedule(List.of(cardId), ids -> enqueueAiTasksForCards(ids, userId));
    }

    public void triggerCardsAfterCommit(Collection<Long> cardIds) {
        if (!featureGuard.isAiCardEnabled()) {
            return;
        }
        triggerCardsAfterCommit(cardIds, null);
    }

    /**
     * 事务提交后异步触发批量卡的预热，userId 只用于生成时读取用户 AI 配置。
     */
    public void triggerCardsAfterCommit(Collection<Long> cardIds, Long userId) {
        if (!featureGuard.isAiCardEnabled()) {
            return;
        }
        afterCommitScheduler.schedule(cardIds, ids -> enqueueAiTasksForCards(ids, userId));
    }

    /**
     * 供用户主动触发场景使用，将当前卡的指定面入队，payload 携带用户上下文供 SSE 回调。
     */
    public void enqueueWithUserContext(CardAiGenerationCore.PreparedCardAiRequest prepared, Long userId) {
        enqueueWithUserContext(prepared, userId, false);
    }

    /**
     * 供用户主动重新生成场景使用，将当前卡的指定面入队并要求覆盖已有缓存。
     */
    public void enqueueRegenerateWithUserContext(CardAiGenerationCore.PreparedCardAiRequest prepared, Long userId) {
        enqueueWithUserContext(prepared, userId, true);
    }

    /**
     * 将用户触发的 AI 缓存任务入队，forceRegenerate=true 时执行端跳过已有缓存检查。
     */
    private void enqueueWithUserContext(CardAiGenerationCore.PreparedCardAiRequest prepared, Long userId,
            boolean forceRegenerate) {
        if (!featureGuard.isAiCardEnabled()) {
            return;
        }
        Objects.requireNonNull(prepared, "prepared must not be null");
        Card card = Objects.requireNonNull(prepared.card(), "prepared.card must not be null");
        String fingerprint = Objects.requireNonNull(prepared.fingerprint(), "prepared.fingerprint must not be null");
        String prompt = Objects.requireNonNull(prepared.prompt(), "prepared.prompt must not be null");
        Long currentUserId = Objects.requireNonNull(userId, "userId must not be null");
        Long cardId = Objects.requireNonNull(card.getId(), "prepared.card.id must not be null");
        Long deckId = card.getDeckId();
        if (!aiCardInstallGate.isInstalledOnDeck(deckId)) {
            return;
        }
        String side = prepared.side();
        String cardTitle = CardAiPromptSupport.SIDE_B.equals(side) ? card.getSideB() : card.getSideA();
        AiCacheNotificationTarget notificationTarget = new AiCacheNotificationTarget(currentUserId, cardId, deckId,
                cardTitle, side);
        AiCacheTaskPayload payload = new AiCacheTaskPayload(
                AiCacheBuildPayload.from(
                        fingerprint, prompt, prepared.profile(), currentUserId, deckId, forceRegenerate),
                notificationTarget);
        queue.enqueueOwned(this, buildUserContextBizKey(fingerprint, currentUserId, cardId), payload, currentUserId);
    }

    /**
     * 从本轮批量缓存读取卡包 AI 设置，同一卡包只查一次。
     */
    private DeckAiSettings deckAiSettingsFor(Long deckId, Map<Long, DeckAiSettings> settingsByDeckId) {
        return settingsByDeckId.computeIfAbsent(deckId, deckAiSettingsService::getByDeckId);
    }

    /**
     * 校验卡包开启后投递卡片 A/B 两面。
     */
    private void enqueueCardIfExplanationEnabled(
            Card card,
            Long userId,
            Map<Long, DeckAiSettings> settingsByDeckId,
            Map<Long, Boolean> installedByDeckId,
            BatchProfile batchProfile) {
        if (card == null) {
            return;
        }
        boolean installed = installedByDeckId.computeIfAbsent(
                card.getDeckId(), aiCardInstallGate::isInstalledOnDeck);
        if (!installed) {
            return;
        }
        DeckAiSettings settings = deckAiSettingsFor(card.getDeckId(), settingsByDeckId);
        enqueueCardSidesIfNeeded(card, settings, userId, batchProfile);
    }

    /**
     * 为卡片 A/B 两面走同一个投递出口，跳过已被开关关闭的面。
     */
    private void enqueueCardSidesIfNeeded(Card card, DeckAiSettings settings, Long userId, BatchProfile batchProfile) {
        if (Boolean.TRUE.equals(settings.getAiExplanationEnabledA())) {
            enqueueCardSideIfNeeded(card, CardAiPromptSupport.SIDE_A, settings.getAiExplanationPromptA(), userId,
                    batchProfile);
        }
        if (Boolean.TRUE.equals(settings.getAiExplanationEnabledB())) {
            enqueueCardSideIfNeeded(card, CardAiPromptSupport.SIDE_B, settings.getAiExplanationPromptB(), userId,
                    batchProfile);
        }
    }

    private void enqueueCardSideIfNeeded(Card card, String side, String deckPrompt, Long userId,
            BatchProfile batchProfile) {
        String raw = CardAiPromptSupport.SIDE_B.equals(side) ? card.getSideB() : card.getSideA();
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        AiProperties.AiProfile profile = resolveBatchProfile(batchProfile);
        if (profile == null) {
            return;
        }
        CardAiGenerationCore.PreparedCardAiRequest prepared;
        try {
            prepared = cardAiGenerationCore.prepare(card, side, deckPrompt, userId, profile);
        } catch (RuntimeException ex) {
            AppLog.warn(LOGGER, AiErrorCode.AI_PROFILE_UNAVAILABLE, "AI profile 不可用，跳过任务投递", ex);
            return;
        }
        String fingerprint = prepared.fingerprint();
        if (cardAiCacheService.findUsableCacheNoTouch(userId, fingerprint) != null) {
            return;
        }
        enqueueByOwner(buildBizKey(fingerprint, userId),
                AiCacheTaskPayload.from(
                        fingerprint, prepared.prompt(), prepared.profile(), userId, card.getDeckId()), userId);
    }

    private void enqueueByOwner(String bizKey, AiCacheTaskPayload payload, Long userId) {
        if (userId == null) {
            queue.enqueue(this, bizKey, payload);
            return;
        }
        queue.enqueueOwned(this, bizKey, payload, userId);
    }

    /**
     * 批量任务内只解析一次全局 AI profile；解析失败后本批次直接跳过后续卡面。
     */
    private AiProperties.AiProfile resolveBatchProfile(BatchProfile batchProfile) {
        if (batchProfile.resolved) {
            return batchProfile.profile;
        }
        batchProfile.resolved = true;
        try {
            batchProfile.profile = cardAiGenerationCore.resolveCardAiProfile();
        } catch (RuntimeException ex) {
            AppLog.warn(LOGGER, AiErrorCode.AI_PROFILE_UNAVAILABLE, "AI profile 不可用，跳过任务投递", ex);
            batchProfile.profile = null;
        }
        return batchProfile.profile;
    }

    private static final class BatchProfile {
        private boolean resolved;
        private AiProperties.AiProfile profile;
    }

    /** AI 缓存任务负载：把缓存构建信息和通知目标拆开，避免一个 payload 同时背两种职责。 */
    public record AiCacheTaskPayload(
            AiCacheBuildPayload build,
            AiCacheNotificationTarget notificationTarget) {
        /**
         * 创建纯缓存构建任务负载，不带用户通知目标。
         */
        public static AiCacheTaskPayload from(String fingerprint, String prompt, AiProperties.AiProfile profile) {
            return from(fingerprint, prompt, profile, (Long) null);
        }

        /**
         * 创建纯缓存构建任务负载，并显式写入用户 ID。
         */
        public static AiCacheTaskPayload from(
                String fingerprint,
                String prompt,
                AiProperties.AiProfile profile,
                Long userId) {
            return new AiCacheTaskPayload(AiCacheBuildPayload.from(fingerprint, prompt, profile, userId), null);
        }

        /** 创建纯缓存构建任务负载, 同时携带执行前重新校验所需的卡包 ID. */
        public static AiCacheTaskPayload from(
                String fingerprint,
                String prompt,
                AiProperties.AiProfile profile,
                Long userId,
                Long deckId) {
            return new AiCacheTaskPayload(
                    AiCacheBuildPayload.from(fingerprint, prompt, profile, userId, deckId), null);
        }

        /**
         * 创建缓存构建任务负载，可选携带完成后的用户通知目标。
         */
        public static AiCacheTaskPayload from(
                String fingerprint,
                String prompt,
                AiProperties.AiProfile profile,
                AiCacheNotificationTarget notificationTarget) {
            Long userId = notificationTarget == null ? null : notificationTarget.userId();
            Long deckId = notificationTarget == null ? null : notificationTarget.deckId();
            return new AiCacheTaskPayload(AiCacheBuildPayload.from(fingerprint, prompt, profile, userId, deckId),
                    notificationTarget);
        }

        /**
         * 返回只修改强制重生成标记的新 payload，保留通知目标不变。
         */
        public AiCacheTaskPayload withForceRegenerate(boolean forceRegenerate) {
            return new AiCacheTaskPayload(build.withForceRegenerate(forceRegenerate), notificationTarget);
        }

        /**
         * 把旧版 flat payload 映射成当前分层 payload，保证历史队列任务还能消费。
         */
        public static AiCacheTaskPayload fromLegacyFlat(
                String fingerprint,
                String prompt,
                String profileName,
                String model,
                String system,
                Double temperature,
                Long userId,
                Long cardId,
                Long deckId,
                String cardTitle,
                String side) {
            AiCacheBuildPayload build = new AiCacheBuildPayload(
                    fingerprint,
                    prompt,
                    profileName,
                    model,
                    system,
                    temperature,
                    userId,
                    deckId,
                    false);
            AiCacheNotificationTarget notificationTarget = userId == null
                    ? null
                    : new AiCacheNotificationTarget(userId, cardId, deckId, cardTitle, side);
            return new AiCacheTaskPayload(build, notificationTarget);
        }
    }

    /** AI 缓存构建负载：只描述生成和落缓存需要的数据。 */
    public record AiCacheBuildPayload(
            String fingerprint,
            String prompt,
            String profileName,
            String model,
            String system,
            Double temperature,
            Long userId,
            Long deckId,
            Boolean forceRegenerate) {
        /**
         * 从当前 profile 快照创建缓存构建负载。
         */
        public static AiCacheBuildPayload from(String fingerprint, String prompt, AiProperties.AiProfile profile) {
            return from(fingerprint, prompt, profile, null);
        }

        /**
         * 从当前 profile 快照创建缓存构建负载，并保留用户 ID 以隔离用户缓存。
         */
        public static AiCacheBuildPayload from(
                String fingerprint,
                String prompt,
                AiProperties.AiProfile profile,
                Long userId) {
            return from(fingerprint, prompt, profile, userId, null, false);
        }

        public static AiCacheBuildPayload from(
                String fingerprint,
                String prompt,
                AiProperties.AiProfile profile,
                Long userId,
                Long deckId) {
            return from(fingerprint, prompt, profile, userId, deckId, false);
        }

        /**
         * 从当前 profile 快照创建缓存构建负载，并标记是否强制覆盖已有缓存。
         */
        public static AiCacheBuildPayload from(
                String fingerprint,
                String prompt,
                AiProperties.AiProfile profile,
                Long userId,
                Long deckId,
                boolean forceRegenerate) {
            return new AiCacheBuildPayload(
                    fingerprint,
                    prompt,
                    profile == null ? null : profile.getName(),
                    profile == null ? null : profile.getModel(),
                    profile == null ? null : profile.getSystem(),
                    profile == null ? null : profile.getTemperature(),
                    userId,
                    deckId,
                    forceRegenerate);
        }

        /**
         * 还原任务创建时保存的 AI profile；没有快照时返回 null，让执行器使用当前配置。
         */
        public AiProperties.AiProfile toProfileOrNull() {
            if (model == null && system == null && temperature == null && profileName == null) {
                return null;
            }
            AiProperties.AiProfile profile = new AiProperties.AiProfile();
            profile.setName(profileName);
            profile.setModel(model);
            profile.setSystem(system);
            profile.setTemperature(temperature == null ? 0.0d : temperature);
            return profile;
        }

        /**
         * 返回只修改强制重生成标记的新构建负载。
         */
        public AiCacheBuildPayload withForceRegenerate(boolean forceRegenerate) {
            return new AiCacheBuildPayload(
                    fingerprint, prompt, profileName, model, system, temperature, userId, deckId, forceRegenerate);
        }
    }

    /** AI 缓存就绪通知目标：只描述生成完成后要通知哪个用户、哪张卡。 */
    public record AiCacheNotificationTarget(
            Long userId,
            Long cardId,
            Long deckId,
            String cardTitle,
            String side) {
    }
}
