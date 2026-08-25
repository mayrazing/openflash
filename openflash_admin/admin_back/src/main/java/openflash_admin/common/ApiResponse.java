package openflash_admin.common;

public class ApiResponse<T> {

    private final Integer code;
    private final T data;

    public ApiResponse(Integer code, T data) {
        this.code = code;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, data);
    }

    public static ApiResponse<Void> fail(int errorCode) {
        return new ApiResponse<>(errorCode, null);
    }

    public Integer getCode() {
        return code;
    }

    public T getData() {
        return data;
    }
}
