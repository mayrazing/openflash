package openflash_ai_runtime.common;

public enum RuntimeErrorCode {

    INVALID_INTERNAL_REQUEST(40001),
    FORBIDDEN(40301),
    NOT_FOUND(40401),
    UNAVAILABLE(50301),
    GENERIC_ERROR(50000);

    private final int value;

    RuntimeErrorCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
