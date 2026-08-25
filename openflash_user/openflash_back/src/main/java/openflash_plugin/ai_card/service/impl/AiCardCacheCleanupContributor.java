package openflash_plugin.ai_card.service.impl;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import openflash_core.spi.CacheCleanupContributor;
import openflash_plugin.ai_card.service.CardAiCacheService;

/** 把 AI 卡片缓存清理接入核心缓存清理扩展点。 */
@Component
public class AiCardCacheCleanupContributor implements CacheCleanupContributor {

    private final CardAiCacheService cardAiCacheService;

    public AiCardCacheCleanupContributor(CardAiCacheService cardAiCacheService) {
        this.cardAiCacheService = cardAiCacheService;
    }

    /** 清理超出 TTL 的 AI 解释缓存。 */
    @Override
    public int cleanupExpiredBefore(LocalDateTime before, int batchSize) {
        return cardAiCacheService.deleteExpired(before, batchSize);
    }
}
