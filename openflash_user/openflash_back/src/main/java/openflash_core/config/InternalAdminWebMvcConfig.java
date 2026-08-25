package openflash_core.config;

import openflash_core.security.InternalAdminTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 仅为内部管理 API 注册参数解析前 token 校验。 */
@Configuration
public class InternalAdminWebMvcConfig implements WebMvcConfigurer {

    private final InternalAdminTokenInterceptor tokenInterceptor;

    public InternalAdminWebMvcConfig(InternalAdminTokenInterceptor tokenInterceptor) {
        this.tokenInterceptor = tokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
            .addPathPatterns("/api/internal/admin/**");
    }
}
