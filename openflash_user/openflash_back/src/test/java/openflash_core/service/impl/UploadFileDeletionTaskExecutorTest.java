package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.AsyncTask;
import openflash_core.entity.CardMedia;
import openflash_core.service.AsyncTaskQueue;
import openflash_core.service.NonRetryableTaskFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class UploadFileDeletionTaskExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void executorDeletesDirectUploadFile() throws Exception {
        Path uploadDir = Files.createDirectory(tempDir.resolve("uploads"));
        Path file = Files.writeString(uploadDir.resolve("safe.jpg"), "x");
        UploadFileDeletionTaskExecutor executor = executor(uploadDir);

        executor.execute(task("{\"relativePath\":\"/uploads/safe.jpg\"}"));

        assertFalse(Files.exists(file));
    }

    @Test
    void bestEffortDeleteAcceptsOnlyExactDirectLocalUploadPaths() throws Exception {
        Path uploadDir = Files.createDirectory(tempDir.resolve("uploads"));
        Path localFile = Files.writeString(uploadDir.resolve("local.jpg"), "x");
        Path remoteLookalike = Files.writeString(uploadDir.resolve("remote.jpg"), "x");
        UploadFileDeleter deleter = new UploadFileDeleter(uploadDir);
        CardMedia local = new CardMedia();
        local.setMediaUrl("/uploads/local.jpg");
        CardMedia remote = new CardMedia();
        remote.setMediaUrl("https://cdn.example/uploads/remote.jpg");

        deleter.delete(List.of(local, remote));

        assertFalse(Files.exists(localFile));
        assertTrue(Files.exists(remoteLookalike));
    }

    @Test
    void ioFailureEscapesSoAsyncConsumerCanRetry() throws Exception {
        Path uploadDir = Files.createDirectory(tempDir.resolve("uploads"));
        Path nonEmptyDirectory = Files.createDirectory(uploadDir.resolve("locked.jpg"));
        Files.writeString(nonEmptyDirectory.resolve("child"), "x");
        UploadFileDeletionTaskExecutor executor = executor(uploadDir);

        AppException error = assertThrows(AppException.class,
            () -> executor.execute(task("{\"relativePath\":\"/uploads/locked.jpg\"}")));

        assertEquals(ErrorCode.UPLOAD_FILE_DELETE_FAILED, error.getErrorCode());
    }

    @Test
    void traversalPathIsRejectedWithoutTouchingOutsideFile() throws Exception {
        Path uploadDir = Files.createDirectory(tempDir.resolve("uploads"));
        Path outsideFile = Files.writeString(tempDir.resolve("outside"), "x");
        UploadFileDeletionTaskExecutor executor = executor(uploadDir);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> executor.execute(task("{\"relativePath\":\"/uploads/../outside\"}")));

        assertInstanceOf(NonRetryableTaskFailure.class, error);
        assertTrue(Files.exists(outsideFile));
    }

    @Test
    void malformedPayloadIsNonRetryable() throws Exception {
        Path uploadDir = Files.createDirectory(tempDir.resolve("uploads"));
        UploadFileDeletionTaskExecutor executor = executor(uploadDir);

        RuntimeException invalidJson = assertThrows(RuntimeException.class,
            () -> executor.execute(task("not-json")));
        RuntimeException extraField = assertThrows(RuntimeException.class,
            () -> executor.execute(task(
                "{\"relativePath\":\"/uploads/safe.jpg\",\"unexpected\":true}")));

        assertInstanceOf(NonRetryableTaskFailure.class, invalidJson);
        assertInstanceOf(NonRetryableTaskFailure.class, extraField);
    }

    @Test
    void executorRegistersUnderProducerTaskType() throws Exception {
        Path uploadDir = Files.createDirectory(tempDir.resolve("uploads"));
        UploadFileDeletionTaskExecutor executor = executor(uploadDir);

        assertEquals(UploadFileDeletionTaskProducer.TASK_TYPE, executor.taskType());
        AsyncTaskHandlerRegistry registry = new AsyncTaskHandlerRegistry(List.of(executor));
        assertSame(executor, registry.getRequired("UPLOAD_FILE_DELETE"));
    }

    @Test
    void producerPersistsCanonicalPathWithSha256BizKeyAndUnlimitedRetries() throws Exception {
        AsyncTaskQueue queue = mock(AsyncTaskQueue.class);
        UploadFileDeletionTaskProducer producer = new UploadFileDeletionTaskProducer(queue);
        when(queue.enqueue(eq(producer), any(), any())).thenReturn(1);

        int inserted = producer.enqueue("/uploads/safe.jpg");

        ArgumentCaptor<String> bizKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UploadFileDeletionTaskProducer.UploadFileDeletePayload> payload =
            ArgumentCaptor.forClass(UploadFileDeletionTaskProducer.UploadFileDeletePayload.class);
        verify(queue).enqueue(eq(producer), bizKey.capture(), payload.capture());
        assertEquals(1, inserted);
        assertEquals("/uploads/safe.jpg", payload.getValue().relativePath());
        assertEquals("UPLOAD_FILE_DELETE:" + sha256("/uploads/safe.jpg"), bizKey.getValue());
        assertEquals(Integer.MAX_VALUE, producer.maxRetryCount());
        assertEquals(0, producer.priority());
    }

    @Test
    void producerRejectsTraversalBeforeEnqueue() {
        AsyncTaskQueue queue = mock(AsyncTaskQueue.class);
        UploadFileDeletionTaskProducer producer = new UploadFileDeletionTaskProducer(queue);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> producer.enqueue("/uploads/../outside"));

        assertInstanceOf(NonRetryableTaskFailure.class, error);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/uploads/emoji😀.jpg",
        "/uploads/\u00a0",
        "/uploads/\u2007",
        "/uploads/\u202f",
        "/uploads/.",
        "/uploads/.."
    })
    void producerRejectsPathsOutsideAsciiFilenamePolicy(String relativePath) {
        AsyncTaskQueue queue = mock(AsyncTaskQueue.class);
        UploadFileDeletionTaskProducer producer = new UploadFileDeletionTaskProducer(queue);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> producer.enqueue(relativePath));

        assertInstanceOf(UploadFileDeletionRejectedException.class, error);
        assertInstanceOf(NonRetryableTaskFailure.class, error);
        verifyNoInteractions(queue);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/uploads/emoji😀.jpg",
        "/uploads/\u00a0",
        "/uploads/\u2007",
        "/uploads/\u202f",
        "/uploads/.",
        "/uploads/.."
    })
    void strictDeleterRejectsPathsOutsideAsciiFilenamePolicy(String relativePath) throws Exception {
        Path uploadDir = Files.createDirectory(tempDir.resolve("uploads"));
        UploadFileDeleter deleter = new UploadFileDeleter(uploadDir);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> deleter.deleteRequired(relativePath));

        assertInstanceOf(UploadFileDeletionRejectedException.class, error);
        assertInstanceOf(NonRetryableTaskFailure.class, error);
    }

    @Test
    void producerAcceptsUuidAndAsciiLengthBoundary() {
        AsyncTaskQueue queue = mock(AsyncTaskQueue.class);
        UploadFileDeletionTaskProducer producer = new UploadFileDeletionTaskProducer(queue);
        String uuidPath = "/uploads/00000000-0000-0000-0000-000000000001.jpg";
        String maxPath = "/uploads/" + "a".repeat(246);

        producer.enqueue(uuidPath);
        producer.enqueue(maxPath);

        verify(queue, times(2)).enqueue(eq(producer), anyString(), any());
    }

    @Test
    void producerRejectsPathLongerThanOwnershipColumnBeforeEnqueue() {
        AsyncTaskQueue queue = mock(AsyncTaskQueue.class);
        UploadFileDeletionTaskProducer producer = new UploadFileDeletionTaskProducer(queue);
        String relativePath = "/uploads/" + "a".repeat(247);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> producer.enqueue(relativePath));

        assertInstanceOf(NonRetryableTaskFailure.class, error);
        verifyNoInteractions(queue);
    }

    private UploadFileDeletionTaskExecutor executor(Path uploadDir) {
        return new UploadFileDeletionTaskExecutor(new ObjectMapper(), new UploadFileDeleter(uploadDir));
    }

    private AsyncTask task(String payload) {
        AsyncTask task = new AsyncTask();
        task.setPayload(payload);
        return task;
    }

    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
