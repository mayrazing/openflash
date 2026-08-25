package openflash_admin.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import openflash_admin.service.AdminSessionService;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminSessionService sessionService;

    public AdminAuthInterceptor(AdminSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        sessionService.requireCurrentAdmin();
        return true;
    }
}
