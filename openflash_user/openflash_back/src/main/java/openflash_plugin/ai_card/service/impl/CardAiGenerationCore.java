package openflash_plugin.ai_card.service.impl;

import org.springframework.stereotype.Service;
import openflash_core.common.AiErrorCode;
import openflash_core.service.AiGateway;
import openflash_core.config.AiProperties;
import openflash_core.service.impl.EffectiveAiProfileResolver;
import openflash_core.service.impl.EffectiveAiProfileResolver.ActiveAiIdentity;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.Card;
import openflash_core.mapper.CardMapper;

/**
 * 提供后台可复用的 AI 生成上下文和同步生成能力。
 */
@Service
public class CardAiGenerationCore {

    static final String AI_PROFILE_FEATURE_KEY = "card-ai-markdown";

    private final CardMapper cardMapper;
    private final AiProperties aiProperties;
    private final AiGateway aiChatGateway;
    private final AiCardFeatureGuard featureGuard;
    private final EffectiveAiProfileResolver effectiveAiProfileResolver;

    public CardAiGenerationCore(
            CardMapper cardMapper,
            AiProperties aiProperties,
            AiGateway aiChatGateway,
            AiCardFeatureGuard featureGuard,
            EffectiveAiProfileResolver effectiveAiProfileResolver) {
        this.cardMapper = cardMapper;
        this.aiProperties = aiProperties;
        this.aiChatGateway = aiChatGateway;
        this.featureGuard = featureGuard;
        this.effectiveAiProfileResolver = effectiveAiProfileResolver;
    }

    /**
     * 生成某张卡某一面的 AI 内容。
     */
    public GeneratedCardAiContent generate(Long cardId, String side) {
        PreparedCardAiRequest prepared = prepare(cardId, side);
        return generate(prepared);
    }

    /**
     * 按已准备好的上下文执行同步生成。
     */
    public GeneratedCardAiContent generate(PreparedCardAiRequest prepared) {
        ensureCardAiMarkdownEnabled();
        String content = aiChatGateway.chat(
                prepared.prompt(),
                prepared.profile(),
                prepared.userId(),
                (activeSelection, effectiveProfile) -> requireMatchingFingerprint(
                        prepared.fingerprint(), prepared.prompt()));
        return new GeneratedCardAiContent(prepared.fingerprint(), prepared.thinkUsed(), content);
    }

    /**
     * 功能关闭时向前台返回统一的 503 错误。
     */
    public void ensureCardAiMarkdownEnabled() {
        featureGuard.ensureAiCardEnabled();
    }

    /**
     * 按 prompt 文本和用户 ID 直接执行同步生成，供后台 worker 使用。
     */
    public GeneratedCardAiContent generateFromPrompt(
            String fingerprint,
            String prompt,
            AiProperties.AiProfile profile,
            Long userId) {
        Boolean thinkUsed = null;
        String content = aiChatGateway.chat(
                prompt,
                profile,
                userId,
                (activeSelection, effectiveProfile) -> requireMatchingFingerprint(
                        fingerprint, prompt));
        return new GeneratedCardAiContent(fingerprint, thinkUsed, content);
    }

    /**
     * 准备某张卡某一面的 prompt、指纹与 think 策略。
     */
    public PreparedCardAiRequest prepare(Long cardId, String side) {
        Card card = cardMapper.findById(cardId);
        if (card == null) {
            throw new AppException(ErrorCode.CARD_NOT_FOUND);
        }

        return prepare(card, side);
    }

    /**
     * 按全局 profile 准备某张卡某一面的 prompt、指纹与 think 策略。
     */
    public PreparedCardAiRequest prepare(Card card, String side) {
        String normalizedSide = CardAiPromptSupport.normalizeSide(side);
        String prompt = CardAiPromptSupport.normalizePrompt(card, normalizedSide);
        ActiveAiIdentity identity = effectiveAiProfileResolver.requireActiveIdentity(null, resolveCardAiProfile());
        AiProperties.AiProfile profile = identity.effectivePromptProfile();
        String fingerprint = CardAiPromptSupport.buildFingerprint(prompt, identity);
        Boolean thinkUsed = null;
        return new PreparedCardAiRequest(normalizedSide, prompt, fingerprint, thinkUsed, profile, card, null);
    }

    /**
     * 按卡包级提示词准备 AI 请求；deckSystemPrompt 为 null 时 system 传 null（不继承全局 profile
     * system）。
     */
    public PreparedCardAiRequest prepare(Card card, String side, String deckSystemPrompt) {
        return prepare(card, side, deckSystemPrompt, null);
    }

    /** 按卡包级提示词和用户 ID 准备 AI 请求；用户 ID 通过缓存行 owner 字段隔离。 */
    public PreparedCardAiRequest prepare(Card card, String side, String deckSystemPrompt, Long userId) {
        return prepare(card, side, deckSystemPrompt, userId, resolveCardAiProfile());
    }

    /**
     * 按已解析的 profile、卡包级提示词和用户 ID 准备 AI 请求，供批量任务复用 profile。
     */
    public PreparedCardAiRequest prepare(
            Card card,
            String side,
            String deckSystemPrompt,
            Long userId,
            AiProperties.AiProfile baseProfile) {
        String normalizedSide = CardAiPromptSupport.normalizeSide(side);
        String prompt = CardAiPromptSupport.normalizePrompt(card, normalizedSide);
        AiProperties.AiProfile promptProfile = CardAiPromptSupport.withSystem(baseProfile, deckSystemPrompt);
        ActiveAiIdentity identity = effectiveAiProfileResolver.requireActiveIdentity(userId, promptProfile);
        String fingerprint = CardAiPromptSupport.buildFingerprint(prompt, identity);
        Boolean thinkUsed = null;
        return new PreparedCardAiRequest(
                normalizedSide, prompt, fingerprint, thinkUsed,
                identity.effectivePromptProfile(), card, userId);
    }

    /**
     * 兼容旧调用签名；模型和完整 identity 始终由 active selection 统一解析。
     */
    public PreparedCardAiRequest prepare(
            Card card,
            String side,
            String deckSystemPrompt,
            Long userId,
            AiProperties.AiProfile baseProfile,
            String resolvedUserModel) {
        return prepare(card, side, deckSystemPrompt, userId, baseProfile);
    }

    /**
     * 执行前重算目标内容指纹，避免把结果写入其他内容的缓存。
     */
    private void requireMatchingFingerprint(
            String fingerprint,
            String prompt) {
        String activeFingerprint = CardAiPromptSupport.buildFingerprint(prompt);
        if (!java.util.Objects.equals(fingerprint, activeFingerprint)) {
            throw new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        }
    }

    /**
     * 解析卡片解释能力当前绑定的 profile。
     */
    public AiProperties.AiProfile resolveCardAiProfile() {
        return aiProperties.resolveProfile(AI_PROFILE_FEATURE_KEY);
    }

    public record PreparedCardAiRequest(
            String side,
            String prompt,
            String fingerprint,
            Boolean thinkUsed,
            AiProperties.AiProfile profile,
            Card card,
            Long userId) {
    }

    public record GeneratedCardAiContent(
            String fingerprint,
            Boolean thinkUsed,
            String content) {
    }
}
