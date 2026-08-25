package openflash_core.common;

public class AppException extends RuntimeException {

    private final AppErrorCode errorCode;

    public AppException(AppErrorCode errorCode) {
        super(errorCode.name() + ":" + errorCode.value());
        this.errorCode = errorCode;
    }

    public AppErrorCode getErrorCode() {
        return errorCode;
    }
}
