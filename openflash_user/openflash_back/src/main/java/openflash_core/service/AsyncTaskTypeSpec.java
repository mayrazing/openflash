package openflash_core.service;

/**
 * 任务类型自描述：每个业务 Producer 实现一份，集中持有任务类型常量、优先级、最大重试次数。
 * AsyncTaskQueue 在落库时读取这份描述，避免业务把这些参数散落到调用点。
 */
public interface AsyncTaskTypeSpec {

    String taskType();

    int priority();

    int maxRetryCount();

    /**
     * 返回重复投递同一 bizKey 时，FAILED 历史任务是否重新进入 PENDING。
     */
    default boolean rescheduleFailedOnDuplicate() {
        return false;
    }
}
