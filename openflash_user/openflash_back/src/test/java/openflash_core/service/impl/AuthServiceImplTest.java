package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.security.PasswordHasher;
import openflash_core.security.LoginAttemptGuard;
import openflash_core.entity.User;
import openflash_core.mapper.UserMapper;
import openflash_core.service.CurrentUserService;
import openflash_core.service.SystemConfigService;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private CurrentUserService currentUserService;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
            userMapper,
            currentUserService,
            new LoginAttemptGuard(org.mockito.Mockito.mock(SystemConfigService.class))
        );
    }

    @Test
    void bannedUserCannotLogin() {
        User user = user(8L, 1);
        when(userMapper.findByUsername("amy")).thenReturn(user);

        AppException error = assertThrows(
            AppException.class,
            () -> service.login("amy", "long-current-password")
        );

        assertEquals(ErrorCode.ACCOUNT_BANNED, error.getErrorCode());
        verify(currentUserService, never()).login(any());
    }

    @Test
    void userWithUnknownBanStateCannotLogin() {
        User user = user(8L, null);
        when(userMapper.findByUsername("amy")).thenReturn(user);

        AppException error = assertThrows(
            AppException.class,
            () -> service.login("amy", "long-current-password")
        );

        assertEquals(ErrorCode.ACCOUNT_BANNED, error.getErrorCode());
        verify(currentUserService, never()).login(any());
    }

    @Test
    void registrationRejectsPasswordShorterThanTwelveCharacters() {
        AppException error = assertThrows(
            AppException.class,
            () -> service.register("amy", "short-pass", "Amy")
        );

        assertEquals(ErrorCode.PASSWORD_LENGTH_INVALID, error.getErrorCode());
        verify(userMapper, never()).insert(any());
    }

    @Test
    void registrationNeverClaimsLegacyLocalUserData() {
        when(userMapper.findByUsername("amy")).thenReturn(null);
        service.register("amy", "long-enough-password", "Amy");

        verify(userMapper).insert(any(User.class));
        verify(userMapper, never()).findByUsername("local_user");
    }

    @Test
    void publicRegistrationCannotCreateReservedRootIdentity() {
        AppException error = assertThrows(
            AppException.class,
            () -> service.register("Root", "long-enough-password", "Root"));

        assertEquals(ErrorCode.USERNAME_TAKEN, error.getErrorCode());
        verify(userMapper, never()).insert(any());
    }

    @Test
    void loginWithRootUsernameIsNotRejectedAsUsernameTaken() {
        when(userMapper.findByUsername("root")).thenReturn(null);

        AppException error = assertThrows(
            AppException.class,
            () -> service.login("root", "whatever-password")
        );

        assertEquals(ErrorCode.WRONG_CREDENTIALS, error.getErrorCode());
        verify(userMapper).findByUsername("root");
    }

    @Test
    void loginRejectsLegacySha256Hash() {
        User user = user(8L, 0);
        user.setPasswordHash("2bb80d537b1da3e38bd30361aa855686bde0eacd7"
            + "162fef6a25fe97bf527a25b");
        when(userMapper.findByUsername("amy")).thenReturn(user);

        AppException error = assertThrows(
            AppException.class,
            () -> service.login("amy", "long-current-password")
        );

        assertEquals(ErrorCode.WRONG_CREDENTIALS, error.getErrorCode());
        verify(currentUserService, never()).login(any());
    }

    @Test
    void changePasswordAcceptsBcryptCurrentPasswordAndWritesBcrypt() {
        User user = user(8L, 0);
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(userMapper.updatePasswordHashAndIncrementAuthVersion(
                eq(8L), eq(user.getPasswordHash()), anyString()))
            .thenReturn(1);

        service.changePassword("long-current-password", "new-secure-password");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updatePasswordHashAndIncrementAuthVersion(
            eq(8L), eq(user.getPasswordHash()), hashCaptor.capture());
        assertTrue(PasswordHasher.matches("new-secure-password", hashCaptor.getValue()));
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        User user = user(8L, 0);
        when(currentUserService.getCurrentUser()).thenReturn(user);

        AppException error = assertThrows(
            AppException.class,
            () -> service.changePassword("wrong-password", "new-secure-password")
        );

        assertEquals(ErrorCode.CURRENT_PASSWORD_INCORRECT, error.getErrorCode());
        verify(userMapper, never()).updatePasswordHashAndIncrementAuthVersion(
            any(), anyString(), anyString());
    }

    @Test
    void changePasswordRejectsShortNewPassword() {
        when(currentUserService.getCurrentUser()).thenReturn(user(8L, 0));

        AppException error = assertThrows(
            AppException.class,
            () -> service.changePassword("long-current-password", "too-short")
        );

        assertEquals(ErrorCode.PASSWORD_LENGTH_INVALID, error.getErrorCode());
        verify(userMapper, never()).updatePasswordHashAndIncrementAuthVersion(
            any(), anyString(), anyString());
    }

    @Test
    void repeatedWrongCurrentPasswordsAreRateLimited() {
        when(currentUserService.getCurrentUser()).thenReturn(user(8L, 0));
        for (int attempt = 0; attempt < 5; attempt++) {
            AppException error = assertThrows(
                AppException.class,
                () -> service.changePassword("wrong-password", "new-secure-password")
            );
            assertEquals(ErrorCode.CURRENT_PASSWORD_INCORRECT, error.getErrorCode());
        }

        AppException limited = assertThrows(
            AppException.class,
            () -> service.changePassword("wrong-password", "new-secure-password")
        );

        assertEquals(ErrorCode.LOGIN_RATE_LIMITED, limited.getErrorCode());
    }

    @Test
    void repeatedWrongPasswordsAreRateLimited() {
        when(userMapper.findByUsername("amy")).thenReturn(user(8L, 0));
        for (int attempt = 0; attempt < 5; attempt++) {
            AppException error = assertThrows(
                AppException.class,
                () -> service.login("amy", "wrong-password")
            );
            assertEquals(ErrorCode.WRONG_CREDENTIALS, error.getErrorCode());
        }

        AppException limited = assertThrows(
            AppException.class,
            () -> service.login("amy", "wrong-password")
        );

        assertEquals(42902, limited.getErrorCode().value());
    }

    private static User user(Long id, Integer banned) {
        User user = new User();
        user.setId(id);
        user.setUsername("amy");
        user.setPasswordHash(PasswordHasher.hash("long-current-password"));
        user.setBanned(banned);
        user.setAuthVersion(0L);
        user.setDeleted(0);
        return user;
    }
}
