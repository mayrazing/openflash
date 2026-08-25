package openflash_admin.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordVerifierTest {

    private static final String PASSWORD_HASH =
        new BCryptPasswordEncoder(4).encode("long-password");

    private final PasswordVerifier verifier = new PasswordVerifier();

    @Test
    void rejectsLegacySha256Password() {
        assertFalse(verifier.matches(
            "long-password",
            "9b1f3d9225cf6b879eea8506ac54af91d4f21afcc45a41d185c3bdad4ff04ca6"
        ));
    }

    @Test
    void rejectsWrongPassword() {
        assertFalse(verifier.matches("wrong-password", PASSWORD_HASH));
    }

    @Test
    void matchesBcryptPassword() {
        assertTrue(verifier.matches("long-password", PASSWORD_HASH));
        assertFalse(verifier.matches("wrong-password", PASSWORD_HASH));
    }
}
