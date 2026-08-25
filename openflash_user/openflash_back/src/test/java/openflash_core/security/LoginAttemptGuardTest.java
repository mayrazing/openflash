package openflash_core.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.service.SystemConfigService;

class LoginAttemptGuardTest {

    @Test
    void concurrentReservationsCannotExceedAttemptLimit() {
        SystemConfigService config = mock(SystemConfigService.class);
        when(config.getInt("auth.login.max-attempts", 5)).thenReturn(2);
        when(config.getLong("auth.login.window-millis", 900_000L)).thenReturn(900_000L);
        LoginAttemptGuard guard = new LoginAttemptGuard(config);

        try (LoginAttemptGuard.AttemptLease first = guard.beginAttempt("amy");
                LoginAttemptGuard.AttemptLease second = guard.beginAttempt("amy")) {
            AppException error = assertThrows(AppException.class, () -> guard.beginAttempt("amy"));
            assertEquals(ErrorCode.LOGIN_RATE_LIMITED, error.getErrorCode());
        }

        assertDoesNotThrow(() -> {
            try (LoginAttemptGuard.AttemptLease ignored = guard.beginAttempt("amy")) {
                ignored.recordSuccess();
            }
        });
    }
}
