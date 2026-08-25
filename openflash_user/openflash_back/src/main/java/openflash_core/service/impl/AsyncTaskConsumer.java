package openflash_core.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import openflash_core.config.AsyncTaskProperties;
import openflash_core.entity.AsyncTask;
import openflash_core.mapper.AsyncTaskMapper;
import openflash_core.service.NonRetryableTaskFailure;

/**
 * 从统一任务表 claim 任务并分发到对应 executor。
 */
@Service
public class AsyncTaskConsumer {

    static final int MAX_LAST_ERROR_LENGTH = 500;

    private final AsyncTaskMapper asyncTaskMapper;
    private final AsyncTaskProperties asyncTaskProperties;
    private final AsyncTaskHandlerRegistry asyncTaskHandlerRegistry;

    public AsyncTaskConsumer(
        AsyncTaskMapper asyncTaskMapper,
        AsyncTaskProperties asyncTaskProperties,
        AsyncTaskHandlerRegistry asyncTaskHandlerRegistry
    ) {
        this.asyncTaskMapper = asyncTaskMapper;
        this.asyncTaskProperties = asyncTaskProperties;
        this.asyncTaskHandlerRegistry = asyncTaskHandlerRegistry;
    }

    /**
     * 消费一轮可 claim 任务。
     */
    public int consumeClaimableBatch() {
        LocalDateTime now = databaseTime(LocalDateTime.now());
        List<AsyncTask> candidates = asyncTaskMapper.findClaimableBatch(now, asyncTaskProperties.getProcessBatchSize());
        long leaseMillis = asyncTaskProperties.getLeaseMillis();
        int processed = 0;
        for (AsyncTask candidate : candidates) {
            LocalDateTime leaseUntil = databaseTime(now.plus(Duration.ofMillis(leaseMillis)));
            if (asyncTaskMapper.claimById(candidate.getId(), now, leaseUntil) == 0) {
                continue;
            }
            processed++;
            try {
                dispatch(candidate);
                asyncTaskMapper.markCompleted(candidate.getId(), leaseUntil);
            } catch (RuntimeException ex) {
                handleFailure(candidate, leaseUntil, ex);
            }
        }
        return processed;
    }

    private void dispatch(AsyncTask task) {
        asyncTaskHandlerRegistry.getRequired(task.getTaskType()).execute(task);
    }

    private void handleFailure(AsyncTask task, LocalDateTime leaseUntil, RuntimeException ex) {
        String error = normalizeErrorMessage(ex);
        if (containsNonRetryableFailure(ex)) {
            asyncTaskMapper.markFailed(task.getId(), leaseUntil, error);
            return;
        }
        int currentRetryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetryCount = task.getMaxRetryCount() == null ? asyncTaskProperties.getMaxRetryCount() : task.getMaxRetryCount();
        if (currentRetryCount + 1 >= maxRetryCount) {
            asyncTaskMapper.markFailed(task.getId(), leaseUntil, error);
            return;
        }
        LocalDateTime nextRetryAt = databaseTime(LocalDateTime.now().plus(Duration.ofMillis(asyncTaskProperties.getRetryDelayMillis())));
        asyncTaskMapper.markRetry(task.getId(), leaseUntil, nextRetryAt, error);
    }

    private boolean containsNonRetryableFailure(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && visited.add(current)) {
            if (current instanceof NonRetryableTaskFailure) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private LocalDateTime databaseTime(LocalDateTime value) {
        return value.truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * 数据库存的是摘要错误，避免超长异常文本反过来把调度线程打挂。
     */
    static String normalizeErrorMessage(RuntimeException ex) {
        String message = ex.getMessage() == null || ex.getMessage().isBlank()
            ? "异步任务执行失败"
            : ex.getMessage();
        if (message.length() <= MAX_LAST_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_LAST_ERROR_LENGTH);
    }
}
