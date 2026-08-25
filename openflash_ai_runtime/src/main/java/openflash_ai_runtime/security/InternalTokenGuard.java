package openflash_ai_runtime.security;

import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.common.RuntimeException;
import openflash_ai_runtime.config.AiRuntimeProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalTokenGuard {

    public static final String ADMIN_TOKEN_HEADER = "X-OpenFlash-Ai-Runtime-Admin-Token";
    public static final String CORE_TOKEN_HEADER = "X-OpenFlash-Ai-Runtime-Core-Token";

    private final AiRuntimeProperties properties;

    public InternalTokenGuard(AiRuntimeProperties properties) {
        this.properties = properties;
    }

    public Scope requireAdmin(String presentedToken) {
        requireToken(properties.getInternal().getAdminToken(), presentedToken);
        return Scope.ADMIN;
    }

    public Scope requireCore(String presentedToken) {
        requireToken(properties.getInternal().getCoreToken(), presentedToken);
        return Scope.CORE;
    }

    private void requireToken(String configuredToken, String presentedToken) {
        if (configuredToken == null || configuredToken.isBlank()
            || presentedToken == null || presentedToken.isBlank()) {
            throw new RuntimeException(RuntimeErrorCode.FORBIDDEN);
        }
        boolean matches = MessageDigest.isEqual(
            configuredToken.getBytes(StandardCharsets.UTF_8),
            presentedToken.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new RuntimeException(RuntimeErrorCode.FORBIDDEN);
        }
    }

    public enum Scope {
        ADMIN,
        CORE
    }
}
