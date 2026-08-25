package openflash_core.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import openflash_core.common.AppLog;
import openflash_core.common.ErrorCode;
import openflash_core.service.AsyncTaskHandler;

/**
 * 维护 taskType 到 handler 的唯一映射，避免消费器硬编码任务类型。
 */
@Component
public class AsyncTaskHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskHandlerRegistry.class);

    private final Map<String, AsyncTaskHandler> handlers;

    public AsyncTaskHandlerRegistry(List<AsyncTaskHandler> handlers) {
        Map<String, AsyncTaskHandler> collected = new LinkedHashMap<>();
        for (AsyncTaskHandler handler : handlers) {
            String taskType = handler.taskType();
            if (taskType == null || taskType.isBlank()) {
                throw new IllegalStateException("异步任务 handler 的 taskType 不能为空: " + handler.getClass().getName());
            }
            AsyncTaskHandler previous = collected.putIfAbsent(taskType, handler);
            if (previous != null) {
                throw new IllegalStateException("重复的异步任务 taskType: " + taskType);
            }
        }
        this.handlers = Map.copyOf(collected);
    }

    public AsyncTaskHandler getRequired(String taskType) {
        AsyncTaskHandler handler = handlers.get(taskType);
        if (handler == null) {
            AppLog.error(log, ErrorCode.ASYNC_UNKNOWN_TASK_TYPE, "未知任务类型: {}", taskType);
            throw new IllegalArgumentException("未知任务类型: " + taskType);
        }
        return handler;
    }
}
