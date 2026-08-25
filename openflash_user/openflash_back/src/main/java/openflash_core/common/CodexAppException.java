package openflash_core.common;

import openflash_core.service.NonRetryableTaskFailure;

/** 将 Codex provider 失败标记为不可重试的应用异常. */
public final class CodexAppException extends AppException implements NonRetryableTaskFailure {

    public CodexAppException(AppErrorCode errorCode) {
        super(errorCode);
    }
}
