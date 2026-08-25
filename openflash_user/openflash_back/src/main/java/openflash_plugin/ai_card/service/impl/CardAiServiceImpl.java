package openflash_plugin.ai_card.service.impl;

import org.springframework.stereotype.Service;
import openflash_plugin.ai_card.dto.AiCacheStatusResponse;
import openflash_plugin.ai_card.service.CardAiService;

/**
 * 负责暴露卡片 AI 查询入口。
 */
@Service
public class CardAiServiceImpl implements CardAiService {

    private final CardAiExplanationResolver cardAiExplanationResolver;

    public CardAiServiceImpl(CardAiExplanationResolver cardAiExplanationResolver) {
        this.cardAiExplanationResolver = cardAiExplanationResolver;
    }

    /**
     * 检查 AI 缓存状态；缓存未命中时投递后台任务。
     */
    @Override
    public AiCacheStatusResponse checkAiCacheStatus(Long cardId, String side) {
        return cardAiExplanationResolver.resolveOrQueue(cardId, side);
    }

    /**
     * 强制重新生成 AI 解释，跳过已有缓存命中判断。
     */
    @Override
    public AiCacheStatusResponse regenerateAiCache(Long cardId, String side) {
        return cardAiExplanationResolver.regenerate(cardId, side);
    }

}
