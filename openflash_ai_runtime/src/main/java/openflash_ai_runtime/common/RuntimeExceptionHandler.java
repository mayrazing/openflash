package openflash_ai_runtime.common;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

@RestControllerAdvice
public class RuntimeExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RuntimeExceptionHandler.class);

    private final SafeErrorResponseWriter errorResponseWriter;

    public RuntimeExceptionHandler(SafeErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @ExceptionHandler(RuntimeException.class)
    public void handleRuntimeException(
            RuntimeException exception,
            HttpServletResponse response) throws IOException {
        RuntimeErrorCode errorCode = exception.getErrorCode();
        errorResponseWriter.write(response, httpStatus(errorCode), errorCode);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResource(HttpServletResponse response) throws IOException {
        RuntimeErrorCode errorCode = RuntimeErrorCode.NOT_FOUND;
        errorResponseWriter.write(response, HttpStatus.NOT_FOUND, errorCode);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public void handleUnsupportedMethod(HttpServletResponse response) throws IOException {
        RuntimeErrorCode errorCode = RuntimeErrorCode.INVALID_INTERNAL_REQUEST;
        errorResponseWriter.write(response, HttpStatus.METHOD_NOT_ALLOWED, errorCode);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public void handleUnsupportedMediaType(HttpServletResponse response) throws IOException {
        RuntimeErrorCode errorCode = RuntimeErrorCode.INVALID_INTERNAL_REQUEST;
        errorResponseWriter.write(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, errorCode);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public void handleNotAcceptableMediaType(HttpServletResponse response) throws IOException {
        RuntimeErrorCode errorCode = RuntimeErrorCode.INVALID_INTERNAL_REQUEST;
        errorResponseWriter.write(response, HttpStatus.NOT_ACCEPTABLE, errorCode);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        BindException.class,
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        ServletRequestBindingException.class,
        TypeMismatchException.class
    })
    public void handleInvalidRequest(HttpServletResponse response) throws IOException {
        RuntimeErrorCode errorCode = RuntimeErrorCode.INVALID_INTERNAL_REQUEST;
        errorResponseWriter.write(response, httpStatus(errorCode), errorCode);
    }

    @ExceptionHandler(Exception.class)
    public void handleUnknownException(HttpServletResponse response) throws IOException {
        log.error("Unhandled AI runtime exception");
        RuntimeErrorCode errorCode = RuntimeErrorCode.GENERIC_ERROR;
        errorResponseWriter.write(response, httpStatus(errorCode), errorCode);
    }

    private HttpStatus httpStatus(RuntimeErrorCode errorCode) {
        HttpStatus status = HttpStatus.resolve(errorCode.value() / 100);
        return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }
}
