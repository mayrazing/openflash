package openflash_core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;

/** 校验 openflash_back 内部管理员 API 的共享 token. */
@Component
public class InternalAdminTokenGuard {

    private final byte[] expectedToken;

    public InternalAdminTokenGuard(
            @Value("${app.admin-internal.token:}") String expectedToken) {
        this.expectedToken = expectedToken == null || expectedToken.isBlank()
                ? null
                : expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    /** token 缺失、空白、配置为空或内容不匹配时拒绝请求. */
    public void requireValid(String actualToken) {
        if (expectedToken == null || actualToken == null || actualToken.isBlank()) {
            throw forbidden();
        }
        byte[] actualBytes = actualToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, actualBytes)) {
            throw forbidden();
        }
    }

    private static AppException forbidden() {
        return new AppException(ErrorCode.FORBIDDEN);
    }
}
