package openflash_core.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 负责把明文密码转换成固定长度摘要。
 */
public final class PasswordHasher {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(12);

    private PasswordHasher() {
    }

    /**
     * 使用 BCrypt 计算密码摘要。
     */
    public static String hash(String rawPassword) {
        return BCRYPT.encode(rawPassword);
    }

    /** 只校验 BCrypt；旧的无盐 SHA-256 密码不再接受。 */
    public static boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        if (!storedHash.startsWith("$2a$") && !storedHash.startsWith("$2b$")
                && !storedHash.startsWith("$2y$")) {
            return false;
        }
        try {
            return BCRYPT.matches(rawPassword, storedHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
