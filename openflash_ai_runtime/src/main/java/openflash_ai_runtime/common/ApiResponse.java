package openflash_ai_runtime.common;

public class ApiResponse<T> {

    private final int code;
    private final T data;

    private ApiResponse(int code, T data) {
        this.code = code;
        this.data = data;
    }

    public static ApiResponse<Void> fail(RuntimeErrorCode errorCode) {
        return new ApiResponse<>(errorCode.value(), null);
    }

    public int getCode() {
        return code;
    }

    public T getData() {
        return data;
    }
}
