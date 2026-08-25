package openflash_core.config;

import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import openflash_core.mapper.AsyncTaskMapper;
import openflash_core.service.impl.AsyncTaskConsumer;

/**
 * 周期性消费统一异步任务。
 */
@Component
public class AsyncTaskScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncTaskScheduler.class);
    private static final long CONSUME_TICK_MILLIS = 1_000L;

    private final AsyncTaskConsumer asyncTaskConsumer;
    private final AsyncTaskMapper asyncTaskMapper;
    private final AsyncTaskProperties asyncTaskProperties;
    private long lastConsumeAt;
    private volatile boolean shuttingDown;

    /**
     * 创建异步任务调度器，统一接入任务消费、历史清理和动态调度配置。
     */
    public AsyncTaskScheduler(
        AsyncTaskConsumer asyncTaskConsumer,
        AsyncTaskMapper asyncTaskMapper,
        AsyncTaskProperties asyncTaskProperties
    ) {
        this.asyncTaskConsumer = asyncTaskConsumer;
        this.asyncTaskMapper = asyncTaskMapper;
        this.asyncTaskProperties = asyncTaskProperties;
    }

    /**
     * 每秒检查一次当前消费间隔，满足间隔后再消费任务；动态间隔不是毫秒级精准调度。
     */
    @Scheduled(fixedDelay = CONSUME_TICK_MILLIS)
    public synchronized void consumeTasks() {
        if (shuttingDown) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!shouldConsumeNow(now)) {
            return;
        }
        asyncTaskConsumer.consumeClaimableBatch();
        lastConsumeAt = System.currentTimeMillis();
    }

    /**
     * 判断当前 tick 是否已经达到配置的消费间隔。
     */
    private boolean shouldConsumeNow(long now) {
        long fixedDelayMillis = asyncTaskProperties.getFixedDelayMillis();
        return lastConsumeAt == 0L || now - lastConsumeAt >= fixedDelayMillis;
    }

    /**
     * 定时删除已完成的历史任务，避免队列表无限增长。
     */
    @Scheduled(fixedDelayString = "#{@asyncTaskProperties.completedCleanupFixedDelayMillis}")
    public void cleanupCompletedTasks() {
        if (shuttingDown) {
            return;
        }
        LocalDateTime before = LocalDateTime.now()
            .minus(Duration.ofMillis(asyncTaskProperties.getCompletedRetentionMillis()));
        int deleted = asyncTaskMapper.deleteCompletedBefore(
            before,
            asyncTaskProperties.getCompletedCleanupBatchSize()
        );
        if (deleted > 0) {
            LOGGER.info("异步任务清理完成: deleted={}", deleted);
        }
    }

    /**
     * 应用关闭时阻止新一轮后台任务进入数据库，避免连接池关闭后调度线程继续取连接。
     */
    @EventListener
    public void stopSchedulingOnShutdown(ContextClosedEvent event) {
        shuttingDown = true;
    }
}
