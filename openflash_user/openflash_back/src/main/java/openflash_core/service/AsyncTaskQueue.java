package openflash_core.service;

import org.springframework.stereotype.Service;
import openflash_core.config.AsyncTaskProperties;
import openflash_core.mapper.AsyncTaskMapper;
import tools.jackson.databind.ObjectMapper;

/**
 * 统一把任意业务 payload 落到 pw_async_task。
 * 业务 Producer 把 spec + payload 交给它，避免 upsertTask 调用点散落。
 */
@Service
public class AsyncTaskQueue {

    private final AsyncTaskMapper asyncTaskMapper;
    private final AsyncTaskProperties asyncTaskProperties;
    private final ObjectMapper objectMapper;

    public AsyncTaskQueue(
        AsyncTaskMapper asyncTaskMapper,
        AsyncTaskProperties asyncTaskProperties,
        ObjectMapper objectMapper
    ) {
        this.asyncTaskMapper = asyncTaskMapper;
        this.asyncTaskProperties = asyncTaskProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 落库一条任务；spec.maxRetryCount() <= 0 时回退到全局配置。
     */
    public int enqueue(AsyncTaskTypeSpec spec, String bizKey, Object payload) {
        return enqueueInternal(spec, bizKey, payload, null);
    }

    /** 落库一条由指定用户拥有的任务。 */
    public int enqueueOwned(
            AsyncTaskTypeSpec spec, String bizKey, Object payload, Long ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("ownerUserId is required");
        }
        return enqueueInternal(spec, bizKey, payload, ownerUserId);
    }

    private int enqueueInternal(
            AsyncTaskTypeSpec spec, String bizKey, Object payload, Long ownerUserId) {
        int maxRetryCount = spec.maxRetryCount() > 0
            ? spec.maxRetryCount()
            : asyncTaskProperties.getMaxRetryCount();
        return asyncTaskMapper.upsertTask(
            bizKey,
            spec.taskType(),
            toJson(payload),
            ownerUserId,
            maxRetryCount,
            spec.priority(),
            spec.rescheduleFailedOnDuplicate()
        );
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("异步任务负载序列化失败", ex);
        }
    }
}
