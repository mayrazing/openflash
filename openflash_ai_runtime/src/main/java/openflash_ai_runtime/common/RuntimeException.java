package openflash_ai_runtime.common;

public class RuntimeException extends java.lang.RuntimeException {

    private final RuntimeErrorCode errorCode;

    public RuntimeException(RuntimeErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public RuntimeErrorCode getErrorCode() {
        return errorCode;
    }
}
