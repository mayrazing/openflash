package openflash_core.config;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/** 用户 AI provider API Key 加解密配置. */
@Configuration
public class AiEncryptorConfig {

    private static final String INSECURE_DEFAULT_PASSWORD = "openflash-dev-password";

    @Bean
    public TextEncryptor aiTextEncryptor(
            @Value("${app.ai.encryptor-password}") String password,
            @Value("${app.ai.encryptor-salt}") String salt) {
        requireDeploymentSecret(password, "AI_ENCRYPTOR_PASSWORD");
        requireDeploymentSecret(salt, "AI_ENCRYPTOR_SALT");
        if (INSECURE_DEFAULT_PASSWORD.equals(password)) {
            throw new IllegalStateException(
                "AI_ENCRYPTOR_PASSWORD must not use the repository default value");
        }
        return Encryptors.text(password, toHex(salt));
    }

    private void requireDeploymentSecret(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentName + " must be configured");
        }
    }

    /** 把任意字符串转成 Encryptors.text 要求的 hex 格式。 */
    private String toHex(String value) {
        return HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8));
    }
}
