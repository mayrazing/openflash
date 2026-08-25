package openflash_core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import openflash_core.config.AsyncTaskProperties;
import openflash_core.entity.AsyncTask;
import openflash_core.mapper.AsyncTaskMapper;
import tools.jackson.databind.ObjectMapper;

class AsyncTaskQueueTest {

    @Test
    void enqueueWritesSerializedPayloadAndSpecValues() {
        RecordingMapper mapper = new RecordingMapper();
        AsyncTaskProperties properties = new AsyncTaskProperties();
        properties.setMaxRetryCount(7);
        AsyncTaskQueue queue = new AsyncTaskQueue(mapper, properties, new ObjectMapper());

        AsyncTaskTypeSpec spec = new AsyncTaskTypeSpec() {
            @Override public String taskType() { return "DEMO"; }
            @Override public int priority() { return 11; }
            @Override public int maxRetryCount() { return 0; }
        };

        int affected = queue.enqueue(spec, "DEMO:42", new DemoPayload("hello", 42));

        assertEquals(1, affected);
        assertEquals("DEMO:42", mapper.lastBizKey);
        assertEquals("DEMO", mapper.lastTaskType);
        assertEquals(11, mapper.lastPriority);
        assertEquals(7, mapper.lastMaxRetryCount);
        assertEquals(false, mapper.lastRescheduleFailed);
        assertEquals("{\"text\":\"hello\",\"value\":42}", mapper.lastPayload);
    }

    @Test
    void enqueueUsesSpecMaxRetryCountWhenPositive() {
        RecordingMapper mapper = new RecordingMapper();
        AsyncTaskProperties properties = new AsyncTaskProperties();
        properties.setMaxRetryCount(7);
        AsyncTaskQueue queue = new AsyncTaskQueue(mapper, properties, new ObjectMapper());

        AsyncTaskTypeSpec spec = new AsyncTaskTypeSpec() {
            @Override public String taskType() { return "DEMO"; }
            @Override public int priority() { return 1; }
            @Override public int maxRetryCount() { return 5; }
        };

        queue.enqueue(spec, "DEMO:1", new DemoPayload("x", 1));
        assertEquals(5, mapper.lastMaxRetryCount);
    }

    @Test
    void ownedQueueWritePassesOwnerAndSharedWritePassesNull() {
        RecordingMapper mapper = new RecordingMapper();
        AsyncTaskQueue queue = new AsyncTaskQueue(mapper, new AsyncTaskProperties(), new ObjectMapper());
        AsyncTaskTypeSpec spec = new AsyncTaskTypeSpec() {
            @Override public String taskType() { return "DEMO"; }
            @Override public int priority() { return 3; }
            @Override public int maxRetryCount() { return 4; }
        };

        queue.enqueueOwned(spec, "owned", new DemoPayload("owned", 1), 8L);
        assertEquals(8L, mapper.lastOwnerUserId);

        queue.enqueue(spec, "shared", new DemoPayload("shared", 2));
        assertEquals(null, mapper.lastOwnerUserId);
    }

    @Test
    void ownedQueueRejectsNullOwner() {
        AsyncTaskQueue queue = new AsyncTaskQueue(
            new RecordingMapper(), new AsyncTaskProperties(), new ObjectMapper());
        AsyncTaskTypeSpec spec = new AsyncTaskTypeSpec() {
            @Override public String taskType() { return "DEMO"; }
            @Override public int priority() { return 0; }
            @Override public int maxRetryCount() { return 1; }
        };

        assertThrows(IllegalArgumentException.class,
            () -> queue.enqueueOwned(spec, "owned", new DemoPayload("owned", 1), null));
    }

    record DemoPayload(String text, int value) {}

    private static final class RecordingMapper implements AsyncTaskMapper {
        String lastBizKey;
        String lastTaskType;
        String lastPayload;
        Long lastOwnerUserId;
        int lastMaxRetryCount;
        int lastPriority;
        boolean lastRescheduleFailed;

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
            this.lastBizKey = bizKey;
            this.lastTaskType = taskType;
            this.lastPayload = payload;
            this.lastOwnerUserId = ownerUserId;
            this.lastMaxRetryCount = maxRetryCount;
            this.lastPriority = priority;
            this.lastRescheduleFailed = rescheduleFailed;
            return 1;
        }

        @Override public List<AsyncTask> findClaimableBatch(LocalDateTime now, int limit) { return new ArrayList<>(); }
        @Override public int claimById(Long id, LocalDateTime now, LocalDateTime leaseUntil) { return 0; }
        @Override public int markCompleted(Long id, LocalDateTime leaseUntil) { return 0; }
        @Override public int markRetry(Long id, LocalDateTime leaseUntil, LocalDateTime nextRetryAt, String lastError) { return 0; }
        @Override public int markFailed(Long id, LocalDateTime leaseUntil, String lastError) { return 0; }
        @Override public int deleteCompletedBefore(LocalDateTime before, int limit) { return 0; }
    }
}
