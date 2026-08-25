package openflash_core.config;

import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import openflash_core.spi.CacheCleanupContributor;

/**
 * 定时清理超出 TTL 的共享缓存。
 * 通过 CacheCleanupContributor 扩展点统一调度各插件的缓存清理，与具体实现解耦。
 */
@Component
public class SharedCacheCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SharedCacheCleanupScheduler.class);

    private final AsyncTaskProperties asyncTaskProperties;
    private final List<CacheCleanupContributor> contributors;

    public SharedCacheCleanupScheduler(
        AsyncTaskProperties asyncTaskProperties,
        List<CacheCleanupContributor> contributors
    ) {
        this.asyncTaskProperties = asyncTaskProperties;
        this.contributors = contributors;
    }

    /** 遍历所有已注册插件，统一清理超出 TTL 的过期缓存。 */
    @Scheduled(fixedDelayString = "#{@asyncTaskProperties.cacheCleanupFixedDelayMillis}")
    public void cleanupExpiredCaches() {
        LocalDateTime before = LocalDateTime.now().minusDays(asyncTaskProperties.getCacheTtlDays());
        int batchSize = asyncTaskProperties.getCompletedCleanupBatchSize();
        int deleted = contributors.stream()
            .mapToInt(contributor -> contributor.cleanupExpiredBefore(before, batchSize))
            .sum();
        if (deleted > 0) {
            LOGGER.info("共享缓存清理完成: deleted={}", deleted);
        }
    }
}
