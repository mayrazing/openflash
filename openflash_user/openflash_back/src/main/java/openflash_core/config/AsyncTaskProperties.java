package openflash_core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import openflash_core.service.SystemConfigService;

/**
 * 维护统一异步任务编排参数。
 */
@Component
@ConfigurationProperties(prefix = "app.async-task")
public class AsyncTaskProperties {

    private long fixedDelayMillis;
    private int processBatchSize;
    private long leaseMillis;
    private long retryDelayMillis;
    private int maxRetryCount;
    private long completedCleanupFixedDelayMillis;
    private long completedRetentionMillis;
    private int completedCleanupBatchSize;
    private long cacheCleanupFixedDelayMillis;
    private int cacheTtlDays;
    private int cacheTouchMinIntervalHours;
    private SystemConfigService systemConfigService;

    /**
     * 延迟接入系统配置服务，避免配置读取阶段形成循环依赖。
     */
    @Autowired
    @Lazy
    public void setSystemConfigService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /**
     * 读取异步任务轮询间隔，数据库配置优先。
     */
    public long getFixedDelayMillis() {
        if (systemConfigService != null) {
            return systemConfigService.getLong("async-task.fixed-delay-millis", fixedDelayMillis);
        }
        return fixedDelayMillis;
    }

    public void setFixedDelayMillis(long fixedDelayMillis) {
        this.fixedDelayMillis = fixedDelayMillis;
    }

    /**
     * 读取异步任务每批处理数量，数据库配置优先。
     */
    public int getProcessBatchSize() {
        if (systemConfigService != null) {
            return systemConfigService.getInt("async-task.process-batch-size", processBatchSize);
        }
        return processBatchSize;
    }

    public void setProcessBatchSize(int processBatchSize) {
        this.processBatchSize = processBatchSize;
    }

    /**
     * 读取异步任务租约时长，数据库配置优先。
     */
    public long getLeaseMillis() {
        if (systemConfigService != null) {
            return systemConfigService.getLong("async-task.lease-millis", leaseMillis);
        }
        return leaseMillis;
    }

    public void setLeaseMillis(long leaseMillis) {
        this.leaseMillis = leaseMillis;
    }

    public long getRetryDelayMillis() {
        return retryDelayMillis;
    }

    public void setRetryDelayMillis(long retryDelayMillis) {
        this.retryDelayMillis = retryDelayMillis;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public long getCompletedCleanupFixedDelayMillis() {
        return completedCleanupFixedDelayMillis;
    }

    public void setCompletedCleanupFixedDelayMillis(long completedCleanupFixedDelayMillis) {
        this.completedCleanupFixedDelayMillis = completedCleanupFixedDelayMillis;
    }

    public long getCompletedRetentionMillis() {
        return completedRetentionMillis;
    }

    public void setCompletedRetentionMillis(long completedRetentionMillis) {
        this.completedRetentionMillis = completedRetentionMillis;
    }

    public int getCompletedCleanupBatchSize() {
        return completedCleanupBatchSize;
    }

    public void setCompletedCleanupBatchSize(int completedCleanupBatchSize) {
        this.completedCleanupBatchSize = completedCleanupBatchSize;
    }

    public long getCacheCleanupFixedDelayMillis() {
        return cacheCleanupFixedDelayMillis;
    }

    public void setCacheCleanupFixedDelayMillis(long cacheCleanupFixedDelayMillis) {
        this.cacheCleanupFixedDelayMillis = cacheCleanupFixedDelayMillis;
    }

    /**
     * 读取共享缓存保留天数，数据库配置优先。
     */
    public int getCacheTtlDays() {
        if (systemConfigService != null) {
            return systemConfigService.getInt("cache.ttl-days", cacheTtlDays);
        }
        return cacheTtlDays;
    }

    public void setCacheTtlDays(int cacheTtlDays) {
        this.cacheTtlDays = cacheTtlDays;
    }

    /**
     * 读取共享缓存最短刷新间隔，数据库配置优先。
     */
    public int getCacheTouchMinIntervalHours() {
        if (systemConfigService != null) {
            return systemConfigService.getInt("cache.touch-min-interval-hours", cacheTouchMinIntervalHours);
        }
        return cacheTouchMinIntervalHours;
    }

    public void setCacheTouchMinIntervalHours(int cacheTouchMinIntervalHours) {
        this.cacheTouchMinIntervalHours = cacheTouchMinIntervalHours;
    }
}
