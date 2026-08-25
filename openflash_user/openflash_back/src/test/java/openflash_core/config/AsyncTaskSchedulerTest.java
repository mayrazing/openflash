package openflash_core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.StaticApplicationContext;
import openflash_core.entity.AsyncTask;
import openflash_core.mapper.AsyncTaskMapper;
import openflash_core.service.impl.AsyncTaskConsumer;

class AsyncTaskSchedulerTest {

    @Test
    void consumeTasksSkipsAfterShutdownEvent() {
        RecordingAsyncTaskConsumer consumer = new RecordingAsyncTaskConsumer();
        RecordingAsyncTaskMapper mapper = new RecordingAsyncTaskMapper();
        AsyncTaskScheduler scheduler = new AsyncTaskScheduler(consumer, mapper, properties());

        scheduler.stopSchedulingOnShutdown(contextClosedEvent());
        scheduler.consumeTasks();

        assertEquals(0, consumer.consumeCalls);
    }

    @Test
    void cleanupCompletedTasksSkipsAfterShutdownEvent() {
        RecordingAsyncTaskConsumer consumer = new RecordingAsyncTaskConsumer();
        RecordingAsyncTaskMapper mapper = new RecordingAsyncTaskMapper();
        AsyncTaskScheduler scheduler = new AsyncTaskScheduler(consumer, mapper, properties());

        scheduler.stopSchedulingOnShutdown(contextClosedEvent());
        scheduler.cleanupCompletedTasks();

        assertEquals(0, mapper.deleteCompletedBeforeCalls);
    }

    private AsyncTaskProperties properties() {
        AsyncTaskProperties properties = new AsyncTaskProperties();
        properties.setFixedDelayMillis(1L);
        properties.setCompletedRetentionMillis(1000L);
        properties.setCompletedCleanupBatchSize(10);
        return properties;
    }

    private ContextClosedEvent contextClosedEvent() {
        return new ContextClosedEvent(new StaticApplicationContext());
    }

    private static final class RecordingAsyncTaskConsumer extends AsyncTaskConsumer {
        private int consumeCalls;

        private RecordingAsyncTaskConsumer() {
            super(null, null, null);
        }

        @Override
        public int consumeClaimableBatch() {
            consumeCalls++;
            return 0;
        }
    }

    private static final class RecordingAsyncTaskMapper implements AsyncTaskMapper {
        private int deleteCompletedBeforeCalls;

        @Override
        public int upsertTask(
            String bizKey,
            String taskType,
            String payload,
            Long ownerUserId,
            int maxRetryCount,
            int priority,
            boolean rescheduleFailed
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AsyncTask> findClaimableBatch(LocalDateTime now, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int claimById(Long id, LocalDateTime now, LocalDateTime leaseUntil) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markCompleted(Long id, LocalDateTime leaseUntil) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markRetry(Long id, LocalDateTime leaseUntil, LocalDateTime nextRetryAt, String lastError) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markFailed(Long id, LocalDateTime leaseUntil, String lastError) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteCompletedBefore(LocalDateTime before, int limit) {
            deleteCompletedBeforeCalls++;
            return 0;
        }
    }
}
