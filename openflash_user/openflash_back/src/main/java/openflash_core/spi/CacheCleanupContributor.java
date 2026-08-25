package openflash_core.spi;

import java.time.LocalDateTime;

/**
 * 缓存清理扩展点，插件通过它让核心调度器统一触发过期缓存清理。
 */
public interface CacheCleanupContributor {

    /** 清理指定时间之前的缓存，返回删除数量。 */
    int cleanupExpiredBefore(LocalDateTime before, int batchSize);
}
