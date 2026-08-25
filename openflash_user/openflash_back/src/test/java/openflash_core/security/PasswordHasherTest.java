package openflash_core.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    void hashUsesSaltedBcrypt() {
        String first = PasswordHasher.hash("long-enough-password");
        String second = PasswordHasher.hash("long-enough-password");

        assertTrue(first.startsWith("$2"));
        assertTrue(second.startsWith("$2"));
        assertNotEquals(first, second);
    }
}
