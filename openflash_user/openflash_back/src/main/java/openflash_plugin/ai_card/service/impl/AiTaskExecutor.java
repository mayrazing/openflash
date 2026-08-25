package openflash_plugin.ai_card.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import openflash_core.common.AppLog;
import openflash_plugin.ai_card.common.AiCardErrorCode;
import openflash_plugin.ai_card.entity.AiCacheReadyNotification;
import openflash_core.entity.AsyncTask;
import openflash_plugin.ai_card.entity.CardAiCache;
import openflash_core.service.AsyncTaskHandler;
import openflash_plugin.ai_card.service.CardAiCacheService;
import openflash_core.service.UserSseRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 执行 AI 缓存构建任务。
 */
@Service
public class AiTaskExecutor implements AsyncTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(AiTaskExecutor.class);

    private final CardAiCacheService cardAiCacheService;
    private final CardAiGenerationCore cardAiGenerationCore;
    private final ObjectMapper objectMapper;
    private final UserSseRegistry userSseRegistry;
    private final AiCardFeatureGuard featureGuard;
    private final AiCardInstallGate installGate;

    @Autowired
    public AiTaskExecutor(
            CardAiCacheService cardAiCacheService,
            CardAiGenerationCore cardAiGenerationCore,
            ObjectMapper objectMapper,
            UserSseRegistry userSseRegistry,
            AiCardFeatureGuard featureGuard,
            AiCardInstallGate installGate) {
        this.cardAiCacheService = cardAiCacheService;
        this.cardAiGenerationCore = cardAiGenerationCore;
        this.objectMapper = objectMapper;
        this.userSseRegistry = userSseRegistry;
        this.featureGuard = featureGuard;
        this.installGate = installGate;
    }

    /** 测试兼容构造器; 生产环境始终通过上面的 Spring 构造器注入安装门控. */
    public AiTaskExecutor(
            CardAiCacheService cardAiCacheService,
            CardAiGenerationCore cardAiGenerationCore,
            ObjectMapper objectMapper,
            UserSseRegistry userSseRegistry,
            AiCardFeatureGuard featureGuard) {
        this(cardAiCacheService, cardAiGenerationCore, objectMapper, userSseRegistry, featureGuard, null);
    }

    /**
     * 返回该执行器消费的统一任务类型。
     */
    @Override
    public String taskType() {
        return CardAiCacheTaskProducer.TASK_TYPE;
    }

    /**
     * 先按 fingerprint 判定是否已有结果，缺失时再同步生成并写回。
     */
    @Override
    public void execute(AsyncTask task) {
        if (!featureGuard.isAiCardEnabled()) {
            return;
        }
        CardAiCacheTaskProducer.AiCacheTaskPayload payload = readPayload(task.getPayload());
        CardAiCacheTaskProducer.AiCacheBuildPayload build = payload.build();
        Long userId = build.userId() != null
                ? build.userId()
                : (payload.notificationTarget() != null ? payload.notificationTarget().userId() : null);
        if (userId == null) {
            AppLog.warn(log, AiCardErrorCode.ASYNC_AI_TASK_MISSING_USER_ID, "AI_CACHE_BUILD 任务缺少 userId，跳过生成");
            return;
        }
        if (!isStillInstalled(payload)) {
            return;
        }
        CardAiCache existingCache = Boolean.TRUE.equals(build.forceRegenerate())
                ? null
                : cardAiCacheService.findUsableCacheNoTouch(userId, build.fingerprint());
        if (existingCache != null) {
            pushReadyNotificationIfNeeded(payload);
            return;
        }
        openflash_core.config.AiProperties.AiProfile profile = build.toProfileOrNull();
        if (profile == null) {
            profile = cardAiGenerationCore.resolveCardAiProfile();
        }
        CardAiGenerationCore.GeneratedCardAiContent generated = cardAiGenerationCore.generateFromPrompt(
                build.fingerprint(),
                build.prompt(),
                profile,
                userId);
        if (!isStillInstalled(payload)) {
            return;
        }
        cardAiCacheService.saveReadyFromBackground(
                userId,
                generated.fingerprint(),
                build.prompt(),
                generated.content(),
                generated.thinkUsed());
        pushReadyNotificationIfNeeded(payload);
    }

    private boolean isStillInstalled(CardAiCacheTaskProducer.AiCacheTaskPayload payload) {
        if (installGate == null) {
            return true;
        }
        Long deckId = payload.build().deckId() != null
                ? payload.build().deckId()
                : (payload.notificationTarget() == null ? null : payload.notificationTarget().deckId());
        return deckId != null && installGate.isInstalledOnDeck(deckId);
    }

    /**
     * 读取当前嵌套 payload，并兼容队列里未消费的旧版 flat payload。
     */
    private CardAiCacheTaskProducer.AiCacheTaskPayload readPayload(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (hasCurrentBuildPayload(root)) {
                return objectMapper.readValue(payload, CardAiCacheTaskProducer.AiCacheTaskPayload.class);
            }
            LegacyAiCacheTaskPayload legacyPayload = objectMapper.readValue(payload, LegacyAiCacheTaskPayload.class);
            return legacyPayload.toCurrentPayload();
        } catch (Exception ex) {
            AppLog.error(log, AiCardErrorCode.ASYNC_AI_TASK_PARSE_FAILED, "AI 任务负载解析失败", ex);
            throw new IllegalArgumentException("AI 任务负载解析失败", ex);
        }
    }

    /**
     * 判断任务 JSON 是否已经使用 build/notificationTarget 的新结构。
     */
    private boolean hasCurrentBuildPayload(JsonNode root) {
        JsonNode build = root.path("build");
        return !build.isMissingNode() && !build.isNull();
    }

    /**
     * 旧版 AI 任务 flat payload 兼容壳，只用于消费历史队列数据。
     */
    private record LegacyAiCacheTaskPayload(
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
        private CardAiCacheTaskProducer.AiCacheTaskPayload toCurrentPayload() {
            return CardAiCacheTaskProducer.AiCacheTaskPayload.fromLegacyFlat(
                    fingerprint,
                    prompt,
                    profileName,
                    model,
                    system,
                    temperature,
                    userId,
                    cardId,
                    deckId,
                    cardTitle,
                    side);
        }
    }

    /**
     * 有通知目标时推送 SSE；纯后台预热任务没有通知目标，直接跳过。
     */
    private void pushReadyNotificationIfNeeded(CardAiCacheTaskProducer.AiCacheTaskPayload payload) {
        CardAiCacheTaskProducer.AiCacheNotificationTarget target = payload.notificationTarget();
        if (target == null || target.userId() == null) {
            return;
        }
        AiCacheReadyNotification notification = new AiCacheReadyNotification(
                target.cardId(),
                target.deckId(),
                target.cardTitle(),
                target.side());
        userSseRegistry.push(target.userId(), AiCacheReadyNotification.EVENT_NAME, notification);
    }
}
