package openflash_ai_runtime.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.common.RuntimeException;
import openflash_ai_runtime.common.SafeErrorResponseWriter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalAccessFilter extends OncePerRequestFilter {

    private static final String ROOT_PATH = "/";
    private static final String HEALTH_PATH = "/health";
    private static final String ADMIN_PATH = "/api/internal/admin";
    private static final String CORE_PATH = "/api/internal/core";

    private final InternalTokenGuard guard;
    private final SafeErrorResponseWriter errorResponseWriter;

    public InternalAccessFilter(
            InternalTokenGuard guard,
            SafeErrorResponseWriter errorResponseWriter) {
        this.guard = guard;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String rawPath = rawPathWithinApplication(request);
        if ("GET".equals(request.getMethod())
                && (ROOT_PATH.equals(rawPath) || HEALTH_PATH.equals(rawPath))) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (isPathOrSlashSubpath(rawPath, ADMIN_PATH)) {
                guard.requireAdmin(request.getHeader(InternalTokenGuard.ADMIN_TOKEN_HEADER));
            } else if (isPathOrSlashSubpath(rawPath, CORE_PATH)) {
                guard.requireCore(request.getHeader(InternalTokenGuard.CORE_TOKEN_HEADER));
            } else {
                writeForbidden(response);
                return;
            }
        } catch (RuntimeException exception) {
            writeForbidden(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String rawPathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private boolean isPathOrSlashSubpath(String rawPath, String basePath) {
        return rawPath.equals(basePath) || rawPath.startsWith(basePath + "/");
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        errorResponseWriter.write(response, HttpStatus.FORBIDDEN, RuntimeErrorCode.FORBIDDEN);
    }
}
