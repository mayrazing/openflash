package openflash_core.common;

import jakarta.servlet.http.HttpServletRequest;
import openflash_core.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<?> handleAppException(AppException ex, HttpServletRequest request) {
        int code = ex.getErrorCode().value();
        HttpStatus status = ex.getErrorCode() == ErrorCode.DECK_NOT_FOUND
            ? HttpStatus.NOT_FOUND
            : HttpStatus.resolve(code / 100);
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return buildResponse(request, status, ApiResponse.fail(code));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableMessage(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        if (isInternalAdminRequest(request)) {
            return buildResponse(request, HttpStatus.BAD_REQUEST,
                ApiResponse.fail(ErrorCode.INTERNAL_ADMIN_REQUEST_INVALID.value()));
        }
        AppLog.error(log, ErrorCode.GENERIC_ERROR, "Unhandled exception", ex);
        return buildResponse(request, HttpStatus.INTERNAL_SERVER_ERROR,
            ApiResponse.fail(ErrorCode.GENERIC_ERROR.value()));
    }

    /**
     * 处理 MVC 异步请求超时，避免 SSE 长连接自然超时被记录为未处理异常。
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<?> handleAsyncRequestTimeout(
        AsyncRequestTimeoutException ex,
        HttpServletRequest request
    ) {
        return buildResponse(request, HttpStatus.SERVICE_UNAVAILABLE,
            ApiResponse.fail(ErrorCode.GENERIC_ERROR.value()));
    }

    /** 客户端已断开，响应不可再写入，直接结束异常处理。 */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex) {
        log.debug("Async response no longer usable because client disconnected", ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex, HttpServletRequest request) {
        AppLog.error(log, ErrorCode.GENERIC_ERROR, "Unhandled exception", ex);
        return buildResponse(request, HttpStatus.INTERNAL_SERVER_ERROR,
            ApiResponse.fail(ErrorCode.GENERIC_ERROR.value()));
    }

    private ResponseEntity<?> buildResponse(HttpServletRequest request, HttpStatus status,
                                             ApiResponse<Void> body) {
        if (isSseRequest(request)) {
            return ResponseEntity.status(status).build();
        }
        return ResponseEntity.status(status).body(body);
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri != null && requestUri.startsWith("/api/sse/")) {
            return true;
        }
        try {
            return MediaType.parseMediaTypes(request.getHeader("Accept")).stream()
                .anyMatch(this::isTextEventStream);
        } catch (InvalidMediaTypeException ex) {
            return false;
        }
    }

    private boolean isInternalAdminRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri != null && requestUri.startsWith("/api/internal/admin/");
    }

    private boolean isTextEventStream(MediaType mediaType) {
        return MediaType.TEXT_EVENT_STREAM.getType().equalsIgnoreCase(mediaType.getType())
            && MediaType.TEXT_EVENT_STREAM.getSubtype().equalsIgnoreCase(mediaType.getSubtype());
    }
}
