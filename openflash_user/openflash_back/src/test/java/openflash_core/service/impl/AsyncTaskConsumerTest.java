package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.config.AsyncTaskProperties;
import openflash_core.entity.AsyncTask;
import openflash_core.mapper.AsyncTaskMapper;
import openflash_core.service.AsyncTaskHandler;
import openflash_core.service.NonRetryableTaskFailure;

class AsyncTaskConsumerTest {

    private static final String TEST_AI_TASK_TYPE = "TEST_AI_TASK";
    private static final String TEST_TTS_TASK_TYPE = "TEST_TTS_TASK";

    @Test
    void consumeClaimableBatchDispatchesAiHandlerAndMarksCompleted() {
        RecordingAsyncTaskMapper mapper = new RecordingAsyncTaskMapper(task(1L, TEST_AI_TASK_TYPE, 0, 3));
        RecordingHandler aiHandler = new RecordingHandler(TEST_AI_TASK_TYPE, false);
        AsyncTaskConsumer consumer = newConsumer(mapper, aiHandler, new RecordingHandler(TEST_TTS_TASK_TYPE, false));

        consumer.consumeClaimableBatch();

        assertTrue(aiHandler.called);
        assertTrue(mapper.markCompletedCalled);
    }

    @Test
    void consumeClaimableBatchDispatchesTtsHandlerAndMarksCompleted() {
        RecordingAsyncTaskMapper mapper = new RecordingAsyncTaskMapper(task(2L, TEST_TTS_TASK_TYPE, 0, 3));
        RecordingHandler ttsHandler = new RecordingHandler(TEST_TTS_TASK_TYPE, false);
        AsyncTaskConsumer consumer = newConsumer(mapper, new RecordingHandler(TEST_AI_TASK_TYPE, false), ttsHandler);

        consumer.consumeClaimableBatch();

        assertTrue(ttsHandler.called);
        assertTrue(mapper.markCompletedCalled);
    }

    @Test
    void consumeClaimableBatchMarksRetryBeforeTerminalFailure() {
        RecordingAsyncTaskMapper mapper = new RecordingAsyncTaskMapper(task(3L, TEST_TTS_TASK_TYPE, 0, 3));
        AsyncTaskConsumer consumer = newConsumer(
            mapper,
            new RecordingHandler(TEST_AI_TASK_TYPE, false),
            new RecordingHandler(TEST_TTS_TASK_TYPE, true)
        );

        consumer.consumeClaimableBatch();

        assertTrue(mapper.markRetryCalled);
        assertFalse(mapper.markFailedCalled);
        assertEquals("handler failed", mapper.lastError);
    }

    @Test
    void consumeClaimableBatchMarksNonRetryableFailureFailedWithoutRetry() {
        RecordingAsyncTaskMapper mapper = new RecordingAsyncTaskMapper(task(8L, TEST_AI_TASK_TYPE, 0, 3));
        AsyncTaskConsumer consumer = newConsumer(
            mapper,
            new ThrowingHandler(TEST_AI_TASK_TYPE,
                new TestNonRetryableException(ErrorCode.GENERIC_ERROR))
        );

        consumer.consumeClaimableBatch();

        assertEquals(1, mapper.markFailedCalls);
        assertFalse(mapper.markRetryCalled);
    }

    @Test
    void consumeClaimableBatchFindsNonRetryableMarkerInCauseChain() {
        RecordingAsyncTaskMapper mapper = new RecordingAsyncTaskMapper(task(9L, TEST_AI_TASK_TYPE, 0, 3));
        RuntimeException wrapper = new RuntimeException("executor wrapper",
            new TestNonRetryableException(ErrorCode.GENERIC_ERROR));
        AsyncTaskConsumer consumer = newConsumer(
            mapper,
            new ThrowingHandler(TEST_AI_TASK_TYPE, wrapper)
        );

        consumer.consumeClaimableBatch();

        assertEquals(1, mapper.markFailedCalls);
        assertFalse(mapper.markRetryCalled);
    }

    @Test
    void consumeClaimableBatchMarksNonRetryableFeatureDisabledFailedWithoutRetry() {
        RecordingAsyncTaskMapper mapper = new RecordingAsyncTaskMapper(
            task(11L, TEST_AI_TASK_TYPE, 0, 3));
        AsyncTaskConsumer consumer = newConsumer(
            mapper,
            new ThrowingHandler(TEST_AI_TASK_TYPE,
                new TestNonRetryableException(ErrorCode.FEATURE_DISABLED))
        );

        consumer.consumeClaimableBatch();

        assertEquals(1, mapper.markFailedCalls);
        assertFalse(mapper.markRetryCalled);
        assertEquals(ErrorCode.FEATURE_DISABLED.name() + ":" + ErrorCode.FEATURE_DISABLED.value(),
            mapper.lastError);
    }

    @Test
    void consumeClaimableBatchStopsAtCauseCycleAndRetriesOrdinaryFailure() {
        RecordingAsyncTaskMapper mapper = new RecordingAsyncTaskMapper(
            task(10L, TEST_AI_TASK_TYPE, 0, 3));
        RuntimeException first = new RuntimeException("cycle failure");
        RuntimeException second = new RuntimeException("cycle link");
        first.initCause(second);
        second.initCause(first);
        AsyncTaskConsumer consumer = newConsumer(
            mapper,
            new ThrowingHandler(TEST_AI_TASK_TYPE, first)
        );

        consumer.consumeClaimableBatch();

        assertTrue(mapper.markRetryCalled);
        assertFalse(mapper.markFailedCalled);
        assertEquals("cycle failure", mapper.lastError);
    }

    @Test
    void consumeClaimableBatchMarksFailedWhenRetryLimitReached() {
        RecordingAsyncTaskMapper mapper = new RecordingAsyncTaskMapper(task(4L, TEST_TTS_TASK_TYPE, 2, 3));
        AsyncTaskConsumer consumer = newConsumer(
            mapper,
            new RecordingHandler(TEST_AI_TASK_TYPE, false),
            new RecordingHandler(TEST_TTS_TASK_TYPE, true)
        );

        consumer.consumeClaimableBatch();

        assertTrue(mapper.markFailedCalled);
    }

    @Test
    void consumeClaimableBatchMarksRetryForUnknownTaskType() {
        RecordingAsyncTaskMapper mapper = new RecordingAsyncTaskMapper(task(5L, "UNKNOWN_TASK", 0, 3));
        AsyncTaskConsumer consumer = newConsumer(mapper, new RecordingHandler(TEST_AI_TASK_TYPE, false));

        try (ExpectedErrorLog logs = ExpectedErrorLog.capture(AsyncTaskHandlerRegistry.class)) {
            consumer.consumeClaimableBatch();

            assertTrue(mapper.markRetryCalled);
            assertEquals("未知任务类型: UNKNOWN_TASK", mapper.lastError);
            assertEquals(1, logs.events().size());
            assertEquals(Level.ERROR, logs.events().get(0).getLevel());
        }
    }

    /**
     * 验证一批后台任务只读取一次租约时间，避免批量排队时重复读取动态配置。
     */
    @Test
    void consumeClaimableBatchReadsLeaseMillisOncePerBatch() {
        RecordingAsyncTaskMapper mapper = new RecordingAsyncTaskMapper(
            task(6L, TEST_AI_TASK_TYPE, 0, 3),
            task(7L, TEST_AI_TASK_TYPE, 0, 3)
        );
        CountingAsyncTaskProperties properties = new CountingAsyncTaskProperties();
        properties.setProcessBatchSize(5);
        properties.setLeaseMillis(5000L);
        properties.setRetryDelayMillis(1000L);
        AsyncTaskConsumer consumer = new AsyncTaskConsumer(
            mapper,
            properties,
            new AsyncTaskHandlerRegistry(List.of(new RecordingHandler(TEST_AI_TASK_TYPE, false)))
        );

        consumer.consumeClaimableBatch();

        assertEquals(1, properties.leaseMillisReadCount);
    }


    @Test
    void normalizeErrorMessageTruncatesOverlongMessage() {
        String longMessage = "x".repeat(AsyncTaskConsumer.MAX_LAST_ERROR_LENGTH + 20);

        String normalized = AsyncTaskConsumer.normalizeErrorMessage(new RuntimeException(longMessage));

        assertEquals(AsyncTaskConsumer.MAX_LAST_ERROR_LENGTH, normalized.length());
    }

    private AsyncTaskConsumer newConsumer(RecordingAsyncTaskMapper mapper, AsyncTaskHandler... handlers) {
        return new AsyncTaskConsumer(mapper, properties(), new AsyncTaskHandlerRegistry(List.of(handlers)));
    }

    private AsyncTask task(Long id, String taskType, int retryCount, int maxRetryCount) {
        AsyncTask task = new AsyncTask();
        task.setId(id);
        task.setTaskType(taskType);
        task.setPayload("{}");
        task.setRetryCount(retryCount);
        task.setMaxRetryCount(maxRetryCount);
        return task;
    }

    private AsyncTaskProperties properties() {
        AsyncTaskProperties properties = new AsyncTaskProperties();
        properties.setProcessBatchSize(5);
        properties.setLeaseMillis(5000L);
        properties.setRetryDelayMillis(1000L);
        return properties;
    }

    private static final class RecordingHandler implements AsyncTaskHandler {
        private final String taskType;
        private final boolean fail;
        private boolean called;

        private RecordingHandler(String taskType, boolean fail) {
            this.taskType = taskType;
            this.fail = fail;
        }

        @Override
        public String taskType() {
            return taskType;
        }

        @Override
        public void execute(AsyncTask task) {
            called = true;
            if (fail) {
                throw new RuntimeException("handler failed");
            }
        }
    }

    private static final class ThrowingHandler implements AsyncTaskHandler {
        private final String taskType;
        private final RuntimeException failure;

        private ThrowingHandler(String taskType, RuntimeException failure) {
            this.taskType = taskType;
            this.failure = failure;
        }

        @Override
        public String taskType() {
            return taskType;
        }

        @Override
        public void execute(AsyncTask task) {
            throw failure;
        }
    }

    private static final class TestNonRetryableException extends AppException
            implements NonRetryableTaskFailure {

        private TestNonRetryableException(ErrorCode errorCode) {
            super(errorCode);
        }
    }

    private static final class RecordingAsyncTaskMapper implements AsyncTaskMapper {
        private final List<AsyncTask> tasks;
        private boolean markCompletedCalled;
        private boolean markRetryCalled;
        private boolean markFailedCalled;
        private int markFailedCalls;
        private String lastError;

        private RecordingAsyncTaskMapper(AsyncTask... tasks) {
            this.tasks = List.of(tasks);
        }

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
            return tasks;
        }

        @Override
        public int claimById(Long id, LocalDateTime now, LocalDateTime leaseUntil) {
            return 1;
        }

        @Override
        public int markCompleted(Long id, LocalDateTime leaseUntil) {
            markCompletedCalled = true;
            return 1;
        }

        @Override
        public int markRetry(Long id, LocalDateTime leaseUntil, LocalDateTime nextRetryAt, String lastError) {
            markRetryCalled = true;
            this.lastError = lastError;
            return 1;
        }

        @Override
        public int markFailed(Long id, LocalDateTime leaseUntil, String lastError) {
            markFailedCalled = true;
            markFailedCalls++;
            this.lastError = lastError;
            return 1;
        }

        @Override
        public int deleteCompletedBefore(LocalDateTime before, int limit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CountingAsyncTaskProperties extends AsyncTaskProperties {
        private int leaseMillisReadCount;

        @Override
        public long getLeaseMillis() {
            leaseMillisReadCount++;
            return super.getLeaseMillis();
        }
    }

    /** 捕获预期任务分发错误，同时阻止测试异常日志传播到控制台。 */
    private static final class ExpectedErrorLog implements AutoCloseable {
        private final Logger logger;
        private final boolean originalAdditive;
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private ExpectedErrorLog(Class<?> source) {
            logger = (Logger) LoggerFactory.getLogger(source);
            originalAdditive = logger.isAdditive();
            appender.start();
            logger.addAppender(appender);
            logger.setAdditive(false);
        }

        private static ExpectedErrorLog capture(Class<?> source) {
            return new ExpectedErrorLog(source);
        }

        private List<ILoggingEvent> events() {
            return List.copyOf(appender.list);
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            logger.setAdditive(originalAdditive);
            appender.stop();
        }
    }
}
