package openflash_plugin.ai_card.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_plugin.ai_card.dto.AiCacheStatusResponse;
import openflash_core.entity.Card;
import openflash_plugin.ai_card.entity.CardAiCache;
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_plugin.ai_card.common.AiCardErrorCode;
import openflash_plugin.ai_card.service.CardAiCacheService;
import openflash_core.service.CardService;
import openflash_core.service.CurrentUserService;
import openflash_plugin.ai_card.service.DeckAiSettingsService;

/**
 * 编排"打开 AI 解释"流程：缓存命中则返回内容，未命中则排队后台构建。
 */
@Service
public class CardAiExplanationResolver {

    private final CardService cardService;
    private final CardAiCacheService cardAiCacheService;
    private final CardAiGenerationCore cardAiGenerationCore;
    private final CardAiCacheTaskProducer cardAiCacheTaskProducer;
    private final CurrentUserService currentUserService;
    private final DeckAiSettingsService deckAiSettingsService;
    private final AiCardInstallGate aiCardInstallGate;
    private final CardSideCompletionTaskProducer cardSideCompletionTaskProducer;

    /**
     * 注入打开 AI 解释时需要的归属检查、安装门控、缓存、排队和卡包设置服务。
     */
    @Autowired
    public CardAiExplanationResolver(
            CardService cardService,
            CardAiCacheService cardAiCacheService,
            CardAiGenerationCore cardAiGenerationCore,
            CardAiCacheTaskProducer cardAiCacheTaskProducer,
            CurrentUserService currentUserService,
            DeckAiSettingsService deckAiSettingsService,
            AiCardInstallGate aiCardInstallGate,
            CardSideCompletionTaskProducer cardSideCompletionTaskProducer) {
        this.cardService = cardService;
        this.cardAiCacheService = cardAiCacheService;
        this.cardAiGenerationCore = cardAiGenerationCore;
        this.cardAiCacheTaskProducer = cardAiCacheTaskProducer;
        this.currentUserService = currentUserService;
        this.deckAiSettingsService = deckAiSettingsService;
        this.aiCardInstallGate = aiCardInstallGate;
        this.cardSideCompletionTaskProducer = cardSideCompletionTaskProducer;
    }

    CardAiExplanationResolver(
            CardService cardService,
            CardAiCacheService cardAiCacheService,
            CardAiGenerationCore cardAiGenerationCore,
            CardAiCacheTaskProducer cardAiCacheTaskProducer,
            CurrentUserService currentUserService,
            DeckAiSettingsService deckAiSettingsService,
            AiCardInstallGate aiCardInstallGate) {
        this(cardService, cardAiCacheService, cardAiGenerationCore, cardAiCacheTaskProducer, currentUserService,
                deckAiSettingsService, aiCardInstallGate, null);
    }

    /**
     * 打开 AI 解释：卡包关闭时直接拒绝，缓存命中返回内容，未命中排队生成。
     * 查缓存前解析 active identity；选择失效时不复用旧缓存，也不回退其他 AI。
     */
    public AiCacheStatusResponse resolveOrQueue(Long cardId, String side) {
        cardAiGenerationCore.ensureCardAiMarkdownEnabled();
        Card card = loadOwnedCard(cardId);
        ensureAiCardInstalledOnDeck(card);
        DeckAiSettings settings = loadDeckAiSettings(card);
        String normalizedSide = CardAiPromptSupport.normalizeSide(side);
        Long userId = currentUserService.getCurrentUserId();
        boolean sideCompletionSetupRequired = needsSideCompletionSetup(card, settings);
        enqueueSideCompletionIfNeeded(card, userId);
        if (!isSideExplanationEnabled(settings, normalizedSide)) {
            return explanationDisabled(sideCompletionSetupRequired);
        }
        CardAiGenerationCore.PreparedCardAiRequest prepared =
                prepareExplanation(card, normalizedSide, settings, userId);
        CardAiCache cache = findReadyCache(userId, prepared);
        if (cache != null) {
            return cacheHit(cache, sideCompletionSetupRequired);
        }

        enqueueCacheBuild(prepared, userId);
        return cacheQueued(sideCompletionSetupRequired);
    }

    /**
     * 强制重新生成 AI 解释：复用打开入口的所有权限和开关校验，但不读取已有缓存。
     */
    public AiCacheStatusResponse regenerate(Long cardId, String side) {
        cardAiGenerationCore.ensureCardAiMarkdownEnabled();
        Card card = loadOwnedCard(cardId);
        ensureAiCardInstalledOnDeck(card);
        DeckAiSettings settings = loadDeckAiSettings(card);
        String normalizedSide = CardAiPromptSupport.normalizeSide(side);
        if (!isSideExplanationEnabled(settings, normalizedSide)) {
            throw new AppException(AiCardErrorCode.AI_EXPLANATION_DISABLED);
        }
        Long userId = currentUserService.getCurrentUserId();
        CardAiGenerationCore.PreparedCardAiRequest prepared =
                prepareExplanation(card, normalizedSide, settings, userId);
        enqueueCacheRegeneration(prepared, userId);
        return cacheQueued(false);
    }

    /**
     * 加载当前用户归属范围内的基础卡片信息。
     */
    private Card loadOwnedCard(Long cardId) {
        return cardService.getBasicCard(cardId);
    }

    /**
     * 卡所在卡包未安装 ai-card 插件时拒绝，与功能关闭同错误。
     * 防绕过界面直调接口、以及卸载插件后残留卡包 AI 设置仍触发生成的场景。
     */
    private void ensureAiCardInstalledOnDeck(Card card) {
        if (!aiCardInstallGate.isInstalledOnDeck(card.getDeckId())) {
            throw new AppException(ErrorCode.FEATURE_DISABLED);
        }
    }

    /**
     * 读取卡片所在卡包的 AI 设置。
     */
    private DeckAiSettings loadDeckAiSettings(Card card) {
        return deckAiSettingsService.getByDeckId(card.getDeckId());
    }

    /** 若打开卡片时一面为空，复用补全任务生产者尝试投递后台任务。 */
    private void enqueueSideCompletionIfNeeded(Card card, Long userId) {
        if (cardSideCompletionTaskProducer != null) {
            cardSideCompletionTaskProducer.triggerCardAfterCommit(card.getId(), userId);
        }
    }

    /**
     * 返回当前请求面的解析开关是否开启。
     */
    private boolean isSideExplanationEnabled(DeckAiSettings settings, String normalizedSide) {
        return CardAiPromptSupport.SIDE_B.equals(normalizedSide)
                ? Boolean.TRUE.equals(settings.getAiExplanationEnabledB())
                : Boolean.TRUE.equals(settings.getAiExplanationEnabledA());
    }

    /**
     * 返回当前卡片是否需要提示用户开启另一面补全。
     */
    private boolean needsSideCompletionSetup(Card card, DeckAiSettings settings) {
        if (missingSideOrNull(card) == null) {
            return false;
        }
        return cardSideCompletionTaskProducer == null
                || !cardSideCompletionTaskProducer.isSideCompletionEnabled()
                || !Boolean.TRUE.equals(settings.getAiCompletionEnabled());
    }

    /**
     * 找出唯一为空的卡面；两面同空或同非空时不需要补全提示。
     */
    private String missingSideOrNull(Card card) {
        boolean sideABlank = isBlank(card.getSideA());
        boolean sideBBlank = isBlank(card.getSideB());
        if (sideABlank == sideBBlank) {
            return null;
        }
        return sideABlank ? CardAiPromptSupport.SIDE_A : CardAiPromptSupport.SIDE_B;
    }

    /**
     * 判断文本是否为空白。
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 按卡面选择卡包提示词，用入口已读取的用户模型准备缓存指纹和生成请求（不再重复读用户配置）。
     */
    private CardAiGenerationCore.PreparedCardAiRequest prepareExplanation(Card card, String normalizedSide,
            DeckAiSettings settings, Long userId) {
        String deckPrompt = CardAiPromptSupport.SIDE_B.equals(normalizedSide)
                ? settings.getAiExplanationPromptB()
                : settings.getAiExplanationPromptA();
        return cardAiGenerationCore.prepare(
                card, normalizedSide, deckPrompt, userId, cardAiGenerationCore.resolveCardAiProfile());
    }

    /**
     * 查询可直接展示的 AI 解释缓存，并刷新缓存使用时间。
     */
    private CardAiCache findReadyCache(
            Long userId, CardAiGenerationCore.PreparedCardAiRequest prepared) {
        return cardAiCacheService.findUsableCacheAndTouchOnServe(
                userId, prepared.fingerprint());
    }

    /**
     * 使用当前登录用户上下文排队后台生成 AI 解释。
     */
    private void enqueueCacheBuild(CardAiGenerationCore.PreparedCardAiRequest prepared, Long userId) {
        cardAiCacheTaskProducer.enqueueWithUserContext(prepared, userId);
    }

    /**
     * 使用当前登录用户上下文排队强制重生成 AI 解释。
     */
    private void enqueueCacheRegeneration(CardAiGenerationCore.PreparedCardAiRequest prepared, Long userId) {
        cardAiCacheTaskProducer.enqueueRegenerateWithUserContext(prepared, userId);
    }

    /**
     * 返回已命中的 AI 解释内容。
     */
    private AiCacheStatusResponse cacheHit(CardAiCache cache, boolean sideCompletionSetupRequired) {
        return AiCacheStatusResponse.hit(cache.getContent(), sideCompletionSetupRequired);
    }

    /**
     * 返回 AI 解释已进入后台队列的状态。
     */
    private AiCacheStatusResponse cacheQueued(boolean sideCompletionSetupRequired) {
        return AiCacheStatusResponse.queued(sideCompletionSetupRequired);
    }

    /**
     * 返回卡包未开启当前面解析的状态。
     */
    private AiCacheStatusResponse explanationDisabled(boolean sideCompletionSetupRequired) {
        return AiCacheStatusResponse.disabled(AiCardErrorCode.AI_EXPLANATION_DISABLED,
                sideCompletionSetupRequired);
    }
}
