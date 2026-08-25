package openflash_core.service.impl;

import openflash_core.service.NonRetryableTaskFailure;

/** 表示文件删除任务包含不可信且重试无法修复的数据。 */
public final class UploadFileDeletionRejectedException
        extends IllegalArgumentException implements NonRetryableTaskFailure {

    public UploadFileDeletionRejectedException(String message) {
        super(message);
    }

    public UploadFileDeletionRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
