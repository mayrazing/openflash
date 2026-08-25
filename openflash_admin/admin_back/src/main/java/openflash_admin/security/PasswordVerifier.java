package openflash_admin.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordVerifier {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

    /** 只接受 BCrypt；旧的无盐 SHA-256 密码不再兼容。 */
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null
                || rawPassword.length() < 12 || rawPassword.length() > 100) {
            return false;
        }
        if (!storedHash.startsWith("$2a$") && !storedHash.startsWith("$2b$")
                && !storedHash.startsWith("$2y$")) {
            return false;
        }
        try {
            return bcrypt.matches(rawPassword, storedHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
