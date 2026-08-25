package openflash_core.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import openflash_core.spi.CacheCleanupContributor;

/** 验证 SharedCacheCleanupScheduler 按插件扩展点统一调度缓存清理。 */
class SharedCacheCleanupSchedulerTest {

    private AsyncTaskProperties properties() {
        AsyncTaskProperties p = new AsyncTaskProperties();
        p.setCacheTtlDays(7);
        p.setCompletedCleanupBatchSize(50);
        return p;
    }

    @Test
    void allContributorsCalledWithCorrectArgs() {
        CacheCleanupContributor first = mock(CacheCleanupContributor.class);
        CacheCleanupContributor second = mock(CacheCleanupContributor.class);
        SharedCacheCleanupScheduler scheduler = new SharedCacheCleanupScheduler(properties(), List.of(first, second));

        scheduler.cleanupExpiredCaches();

        verify(first).cleanupExpiredBefore(any(LocalDateTime.class), eq(50));
        verify(second).cleanupExpiredBefore(any(LocalDateTime.class), eq(50));
    }

    @Test
    void emptyContributorListDoesNotThrow() {
        SharedCacheCleanupScheduler scheduler = new SharedCacheCleanupScheduler(properties(), List.of());

        scheduler.cleanupExpiredCaches();
    }
}
