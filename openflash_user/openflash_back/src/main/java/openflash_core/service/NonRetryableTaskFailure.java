package openflash_core.service;

/** 标记异步任务失败后必须直接终止, 不得重试. */
public interface NonRetryableTaskFailure {}
