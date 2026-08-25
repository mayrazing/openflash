package openflash_admin.common;

public class AdminException extends RuntimeException {

    private final AdminErrorCode errorCode;

    public AdminException(AdminErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public AdminErrorCode getErrorCode() {
        return errorCode;
    }
}
