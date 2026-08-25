package openflash_core.service.impl;

import openflash_core.entity.AsyncTask;
import openflash_core.service.AsyncTaskHandler;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 校验持久化任务负载并执行严格的直属上传文件删除。 */
@Service
public class UploadFileDeletionTaskExecutor implements AsyncTaskHandler {

    private final ObjectMapper objectMapper;
    private final UploadFileDeleter uploadFileDeleter;

    public UploadFileDeletionTaskExecutor(
        ObjectMapper objectMapper,
        UploadFileDeleter uploadFileDeleter
    ) {
        this.objectMapper = objectMapper;
        this.uploadFileDeleter = uploadFileDeleter;
    }

    @Override
    public String taskType() {
        return UploadFileDeletionTaskProducer.TASK_TYPE;
    }

    @Override
    public void execute(AsyncTask task) {
        UploadFileDeletionTaskProducer.UploadFileDeletePayload payload =
            parseRequiredPayload(task == null ? null : task.getPayload());
        uploadFileDeleter.deleteRequired(payload.relativePath());
    }

    private UploadFileDeletionTaskProducer.UploadFileDeletePayload parseRequiredPayload(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject() || root.size() != 1 || !root.has("relativePath")) {
                throw new IllegalArgumentException("relativePath is required");
            }
            String relativePath = root.get("relativePath").stringValue();
            if (relativePath == null || relativePath.isBlank()) {
                throw new IllegalArgumentException("relativePath is required");
            }
            return new UploadFileDeletionTaskProducer.UploadFileDeletePayload(relativePath);
        } catch (Exception exception) {
            throw new UploadFileDeletionRejectedException("文件删除任务负载无效", exception);
        }
    }
}
