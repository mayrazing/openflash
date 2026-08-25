package openflash_core.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import openflash_core.service.AsyncTaskQueue;
import openflash_core.service.AsyncTaskTypeSpec;
import org.springframework.stereotype.Service;

/** 持久化上传文件物理删除任务。 */
@Service
public class UploadFileDeletionTaskProducer implements AsyncTaskTypeSpec {

    public static final String TASK_TYPE = "UPLOAD_FILE_DELETE";

    private final AsyncTaskQueue queue;

    public UploadFileDeletionTaskProducer(AsyncTaskQueue queue) {
        this.queue = queue;
    }

    public int enqueue(String relativePath) {
        String normalized = UploadFileDeleter.requireDirectUploadPath(relativePath);
        return queue.enqueue(this, TASK_TYPE + ":" + sha256(normalized),
            new UploadFileDeletePayload(normalized));
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int maxRetryCount() {
        return Integer.MAX_VALUE;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record UploadFileDeletePayload(String relativePath) {}
}
