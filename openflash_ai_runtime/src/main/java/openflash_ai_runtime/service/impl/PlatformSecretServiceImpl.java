package openflash_ai_runtime.service.impl;

import java.util.Base64;
import java.util.HexFormat;
import openflash_ai_runtime.entity.PlatformAiConnection;
import openflash_ai_runtime.entity.PlatformAiSecret;
import openflash_ai_runtime.mapper.PlatformAiConnectionMapper;
import openflash_ai_runtime.mapper.PlatformAiSecretMapper;
import openflash_ai_runtime.common.RuntimeErrorCode;
import openflash_ai_runtime.config.AiRuntimeProperties;
import openflash_ai_runtime.service.PlatformSecretService;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 加密保存并短暂解密平台凭证, 不向控制器暴露密文. */
@Service
public class PlatformSecretServiceImpl implements PlatformSecretService {

    private static final int MIN_PASSWORD_BYTES = 32;
    private static final int MIN_SALT_BYTES = 16;

    private final PlatformAiConnectionMapper connectionMapper;
    private final PlatformAiSecretMapper secretMapper;
    private final AiRuntimeProperties properties;

    @Autowired
    public PlatformSecretServiceImpl(
            ObjectProvider<PlatformAiConnectionMapper> connectionProvider,
            ObjectProvider<PlatformAiSecretMapper> secretProvider,
            AiRuntimeProperties properties) {
        this(connectionProvider.getIfAvailable(), secretProvider.getIfAvailable(), properties);
    }

    PlatformSecretServiceImpl(
            PlatformAiConnectionMapper connectionMapper,
            PlatformAiSecretMapper secretMapper,
            AiRuntimeProperties properties) {
        this.connectionMapper = connectionMapper;
        this.secretMapper = secretMapper;
        this.properties = properties;
    }

    /** 在一个数据库事务内替换密文并更新安全布尔标记. */
    @Transactional
    public void replace(String connectionKey, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw invalidRequest();
        if (connectionMapper == null || secretMapper == null) throw unavailable();
        PlatformAiConnection connection = connectionMapper.findByKey(connectionKey);
        if (connection == null) throw notFound();
        String encrypted;
        try {
            encrypted = encryptor().encrypt(apiKey);
        } catch (openflash_ai_runtime.common.RuntimeException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable();
        }
        secretMapper.upsert(new PlatformAiSecret(connection.id(), encrypted));
        connectionMapper.setCredentialsConfigured(connectionKey, true);
    }

    /** 仅供 runtime transport 调用, 缺失或无法解密时安全失败. */
    public String requirePlaintext(long connectionId) {
        if (secretMapper == null) throw unavailable();
        PlatformAiSecret secret = secretMapper.findByConnectionId(connectionId);
        if (secret == null || secret.secretEnc() == null || secret.secretEnc().isBlank()) {
            throw unavailable();
        }
        try {
            String plaintext = encryptor().decrypt(secret.secretEnc());
            if (plaintext == null || plaintext.isBlank()) throw unavailable();
            return plaintext;
        } catch (openflash_ai_runtime.common.RuntimeException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private TextEncryptor encryptor() {
        String password = properties.getPlatformSecret().getPassword();
        String salt = properties.getPlatformSecret().getSalt();
        if (password == null || password.isBlank() || salt == null || salt.isBlank()) {
            throw unavailable();
        }
        try {
            byte[] passwordBytes = Base64.getDecoder().decode(password);
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            if (passwordBytes.length < MIN_PASSWORD_BYTES || saltBytes.length < MIN_SALT_BYTES) {
                throw unavailable();
            }
            String normalizedPassword = Base64.getEncoder().encodeToString(passwordBytes);
            String normalizedSalt = Base64.getEncoder().encodeToString(saltBytes);
            if (!normalizedPassword.equals(password) || !normalizedSalt.equals(salt)) {
                throw unavailable();
            }
            String hexSalt = HexFormat.of().formatHex(saltBytes);
            return Encryptors.delux(normalizedPassword, hexSalt);
        } catch (IllegalArgumentException failure) {
            throw unavailable();
        }
    }

    private static openflash_ai_runtime.common.RuntimeException invalidRequest() {
        return new openflash_ai_runtime.common.RuntimeException(
                RuntimeErrorCode.INVALID_INTERNAL_REQUEST);
    }

    private static openflash_ai_runtime.common.RuntimeException notFound() {
        return new openflash_ai_runtime.common.RuntimeException(RuntimeErrorCode.NOT_FOUND);
    }

    private static openflash_ai_runtime.common.RuntimeException unavailable() {
        return new openflash_ai_runtime.common.RuntimeException(RuntimeErrorCode.UNAVAILABLE);
    }
}
