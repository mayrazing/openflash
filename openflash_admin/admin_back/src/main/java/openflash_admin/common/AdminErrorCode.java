package openflash_admin.common;

public enum AdminErrorCode {

    UNAUTHORIZED(40101),
    FORBIDDEN(40301),
    WRONG_CREDENTIALS(40002),
    INVALID_ROLE(40008),
    INVALID_REQUEST(40009),
    LOGIN_RATE_LIMITED(42902),
    USER_NOT_FOUND(40401),
    PLATFORM_AI_NOT_FOUND(40401),
    LAST_ADMIN_REQUIRED(40901),
    SELF_ACCOUNT_MUTATION(40902),
    RUNTIME_UNAVAILABLE(50301),
    GENERIC_ERROR(50000);

    private final int value;

    AdminErrorCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
