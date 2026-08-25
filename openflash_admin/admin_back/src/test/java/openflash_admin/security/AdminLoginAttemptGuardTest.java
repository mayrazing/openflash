package openflash_admin.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;

class AdminLoginAttemptGuardTest {

    @Test
    void concurrentReservationsCannotExceedAttemptLimit() {
        AdminLoginAttemptGuard guard = AdminLoginAttemptGuard.fromConfigLoader(key ->
            "auth.login.max-attempts".equals(key) ? "2" : "900000");

        try (AdminLoginAttemptGuard.AttemptLease first = guard.beginAttempt("root");
                AdminLoginAttemptGuard.AttemptLease second = guard.beginAttempt("root")) {
            AdminException error = assertThrows(AdminException.class, () -> guard.beginAttempt("root"));
            assertEquals(AdminErrorCode.LOGIN_RATE_LIMITED, error.getErrorCode());
        }

        assertDoesNotThrow(() -> {
            try (AdminLoginAttemptGuard.AttemptLease ignored = guard.beginAttempt("root")) {
                ignored.recordSuccess();
            }
        });
    }
}
