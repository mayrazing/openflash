package openflash_core.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 在 MVC 解析内部管理 API 参数前校验共享 token。 */
@Component
public class InternalAdminTokenInterceptor implements HandlerInterceptor {

    static final String TOKEN_HEADER = "X-OpenFlash-Admin-Token";

    private final InternalAdminTokenGuard tokenGuard;

    public InternalAdminTokenInterceptor(InternalAdminTokenGuard tokenGuard) {
        this.tokenGuard = tokenGuard;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        tokenGuard.requireValid(request.getHeader(TOKEN_HEADER));
        return true;
    }
}
