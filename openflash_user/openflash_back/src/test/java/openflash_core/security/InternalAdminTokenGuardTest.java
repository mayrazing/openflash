package openflash_core.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;

class InternalAdminTokenGuardTest {

    @Test
    void missingTokenIsForbidden() {
        assertForbidden(new InternalAdminTokenGuard("expected-token"), null);
    }

    @Test
    void blankTokenIsForbidden() {
        assertForbidden(new InternalAdminTokenGuard("expected-token"), "   ");
    }

    @Test
    void incorrectTokenIsForbidden() {
        assertForbidden(new InternalAdminTokenGuard("expected-token"), "incorrect-token");
    }

    @Test
    void blankConfiguredTokenRejectsEveryRequest() {
        assertForbidden(new InternalAdminTokenGuard("  "), "expected-token");
    }

    @Test
    void correctTokenPasses() {
        InternalAdminTokenGuard guard = new InternalAdminTokenGuard("expected-token");

        assertDoesNotThrow(() -> guard.requireValid("expected-token"));
    }

    private static void assertForbidden(InternalAdminTokenGuard guard, String token) {
        AppException failure = assertThrows(AppException.class, () -> guard.requireValid(token));

        assertSame(ErrorCode.FORBIDDEN, failure.getErrorCode());
    }
}
