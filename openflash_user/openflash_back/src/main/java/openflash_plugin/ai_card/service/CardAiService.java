package openflash_plugin.ai_card.service;

import openflash_plugin.ai_card.dto.AiCacheStatusResponse;

/**
 * 负责按卡片触发 AI 查询。
 */
public interface CardAiService {

    /**
     * 检查指定面的 AI 缓存状态，未命中时投递后台补齐任务。
     */
    AiCacheStatusResponse checkAiCacheStatus(Long cardId, String side);

    /**
     * 强制重新生成指定面的 AI 解释，覆盖同内容缓存。
     */
    AiCacheStatusResponse regenerateAiCache(Long cardId, String side);
}
