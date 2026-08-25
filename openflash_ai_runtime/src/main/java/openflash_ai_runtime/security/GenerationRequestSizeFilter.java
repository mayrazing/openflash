package openflash_ai_runtime.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.common.SafeErrorResponseWriter;
import openflash_ai_runtime.validation.GenerationRequestValidator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 在 JSON 反序列化前限制 generic core 生成入口的请求字节数. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GenerationRequestSizeFilter extends OncePerRequestFilter {

    private static final String GENERATION_PATH =
            "/api/internal/core/platform-ai/generations";

    private final SafeErrorResponseWriter errorWriter;

    public GenerationRequestSizeFilter(SafeErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String rawPath = rawPathWithinApplication(request);
        if (!"POST".equals(request.getMethod()) || !isGenerationPath(rawPath)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!rawPath.equals(withoutMatrixParameters(rawPath))) {
            reject(response);
            return;
        }
        if (request.getContentLengthLong() > GenerationRequestValidator.MAX_JSON_BODY_BYTES) {
            reject(response);
            return;
        }
        byte[] body;
        try {
            body = request.getInputStream().readNBytes(
                    GenerationRequestValidator.MAX_JSON_BODY_BYTES + 1);
        } catch (IOException failure) {
            reject(response);
            return;
        }
        if (body.length > GenerationRequestValidator.MAX_JSON_BODY_BYTES) {
            reject(response);
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private static String rawPathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private static boolean isGenerationPath(String rawPath) {
        return GENERATION_PATH.equals(withoutMatrixParameters(rawPath));
    }

    private static String withoutMatrixParameters(String rawPath) {
        StringBuilder normalized = new StringBuilder(rawPath.length());
        boolean insideMatrixParameters = false;
        for (int index = 0; index < rawPath.length(); index++) {
            char current = rawPath.charAt(index);
            if (insideMatrixParameters) {
                if (current == '/') {
                    insideMatrixParameters = false;
                    normalized.append(current);
                }
            } else if (current == ';') {
                insideMatrixParameters = true;
            } else {
                normalized.append(current);
            }
        }
        return normalized.toString();
    }

    private void reject(HttpServletResponse response) throws IOException {
        errorWriter.write(
                response, HttpStatus.BAD_REQUEST, RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    /**
     * 从内存同步重放请求体, 同时提供不会阻塞的单次ReadListener通知.
     * dogtail: 缓存体已完整驻留内存, 所以通知在调用线程完成且不依赖Servlet异步上下文;
     * 若以后改成增量读取请求体, 应改由容器异步输入流驱动通知.
     */
    private static final class CachedBodyServletInputStream extends ServletInputStream {
        private final Object stateLock = new Object();
        private final ByteArrayInputStream input;
        private ReadListener listener;
        private boolean listenerSet;
        private boolean notifyingData;
        private boolean completionNotified;
        private boolean errorNotified;

        private CachedBodyServletInputStream(byte[] body) {
            this.input = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            synchronized (stateLock) {
                return input.available() == 0;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            if (readListener == null) throw new NullPointerException("readListener");
            ReadListener dataCallback = null;
            ReadListener completionCallback = null;
            synchronized (stateLock) {
                if (listenerSet) throw new IllegalStateException("ReadListener already set");
                listenerSet = true;
                listener = readListener;
                if (input.available() == 0) {
                    completionNotified = true;
                    completionCallback = listener;
                } else {
                    notifyingData = true;
                    dataCallback = listener;
                }
            }

            if (dataCallback != null) {
                try {
                    dataCallback.onDataAvailable();
                } catch (Throwable failure) {
                    finishDataNotification();
                    notifyError(failure);
                    return;
                }
                completionCallback = finishDataNotification();
            }
            notifyCompletion(completionCallback);
        }

        @Override
        public int read() {
            int result;
            ReadListener completionCallback;
            synchronized (stateLock) {
                result = input.read();
                completionCallback = prepareCompletionLocked();
            }
            notifyCompletion(completionCallback);
            return result;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            int result;
            ReadListener completionCallback;
            synchronized (stateLock) {
                result = input.read(bytes, offset, length);
                completionCallback = prepareCompletionLocked();
            }
            notifyCompletion(completionCallback);
            return result;
        }

        private ReadListener finishDataNotification() {
            synchronized (stateLock) {
                notifyingData = false;
                return prepareCompletionLocked();
            }
        }

        private ReadListener prepareCompletionLocked() {
            if (listener == null
                    || notifyingData
                    || completionNotified
                    || errorNotified
                    || input.available() != 0) {
                return null;
            }
            completionNotified = true;
            return listener;
        }

        private void notifyCompletion(ReadListener completionCallback) {
            if (completionCallback == null) return;
            try {
                completionCallback.onAllDataRead();
            } catch (Throwable failure) {
                notifyError(failure);
            }
        }

        private void notifyError(Throwable failure) {
            ReadListener errorCallback;
            synchronized (stateLock) {
                if (errorNotified) return;
                errorNotified = true;
                errorCallback = listener;
            }
            try {
                errorCallback.onError(failure);
            } catch (Throwable ignored) {
                // Listener failure is terminal; never leak it into synchronous MVC replay.
            }
        }
    }
}
