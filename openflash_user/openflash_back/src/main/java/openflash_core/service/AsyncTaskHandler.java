package openflash_core.service;

import openflash_core.entity.AsyncTask;

/**
 * 统一异步任务执行器入口，用 taskType 注册到消费分发层。
 */
public interface AsyncTaskHandler {

    String taskType();

    void execute(AsyncTask task);
}
