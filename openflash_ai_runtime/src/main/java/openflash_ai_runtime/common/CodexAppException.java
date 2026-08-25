package openflash_ai_runtime.common;

/** 保存可测试的 Codex 内部失败分类, 不携带原始进程错误. */
public final class CodexAppException extends java.lang.RuntimeException {

    private final AiErrorCode errorCode;

    public CodexAppException(AiErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public AiErrorCode getErrorCode() {
        return errorCode;
    }
}
