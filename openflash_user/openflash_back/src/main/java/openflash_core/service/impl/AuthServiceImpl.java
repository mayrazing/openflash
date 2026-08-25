package openflash_core.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.security.PasswordHasher;
import openflash_core.security.LoginAttemptGuard;
import openflash_core.entity.User;
import openflash_core.mapper.UserMapper;
import openflash_core.service.AuthService;
import openflash_core.service.CurrentUserService;

/** 负责处理用户注册、登录和密码修改。 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final CurrentUserService currentUserService;
    private final LoginAttemptGuard loginAttemptGuard;

    public AuthServiceImpl(
            UserMapper userMapper,
            CurrentUserService currentUserService,
            LoginAttemptGuard loginAttemptGuard) {
        this.userMapper = userMapper;
        this.currentUserService = currentUserService;
        this.loginAttemptGuard = loginAttemptGuard;
    }

    /** 注册独立的新用户, 不接管任何已有账号的数据。 */
    @Override
    @Transactional
    public User register(String username, String password, String nickname) {
        String normalizedUsername = normalizeUsername(username);
        if ("root".equalsIgnoreCase(normalizedUsername)) {
            throw new AppException(ErrorCode.USERNAME_TAKEN);
        }
        String normalizedPassword = normalizeNewPassword(password);
        String normalizedNickname = normalizeNickname(nickname, normalizedUsername);

        if (userMapper.findByUsername(normalizedUsername) != null) {
            throw new AppException(ErrorCode.USERNAME_TAKEN);
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(PasswordHasher.hash(normalizedPassword));
        user.setNickname(normalizedNickname);
        userMapper.insert(user);

        currentUserService.ensureUserSettings(user.getId());
        currentUserService.login(user);
        return currentUserService.getCurrentUser();
    }

    /**
     * 登录并建立当前会话。
     */
    @Override
    public User login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedPassword = normalizeLoginPassword(password);
        try (LoginAttemptGuard.AttemptLease attempt = loginAttemptGuard.beginAttempt(normalizedUsername)) {
            User user = userMapper.findByUsername(normalizedUsername);
            if (user == null
                || !PasswordHasher.matches(normalizedPassword, user.getPasswordHash())) {
                attempt.recordFailure();
                throw new AppException(ErrorCode.WRONG_CREDENTIALS);
            }
            if (!Integer.valueOf(0).equals(user.getBanned())) {
                throw new AppException(ErrorCode.ACCOUNT_BANNED);
            }
            attempt.recordSuccess();

            currentUserService.ensureUserSettings(user.getId());
            currentUserService.login(user);
            return currentUserService.getCurrentUser();
        }
    }

    /**
     * 获取当前登录用户。
     */
    @Override
    public User getCurrentUser() {
        return currentUserService.getCurrentUser();
    }

    /** 校验当前密码，写入 BCrypt 新密码，并让该账号已有用户会话失效。 */
    @Override
    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        User user = currentUserService.getCurrentUser();
        String normalizedCurrentPassword = normalizeLoginPassword(currentPassword);
        String normalizedNewPassword = normalizeNewPassword(newPassword);
        String expectedHash = user.getPasswordHash();

        try (LoginAttemptGuard.AttemptLease attempt =
                loginAttemptGuard.beginAttempt(user.getUsername())) {
            if (!PasswordHasher.matches(normalizedCurrentPassword, expectedHash)) {
                attempt.recordFailure();
                throw new AppException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
            }

            int updated = userMapper.updatePasswordHashAndIncrementAuthVersion(
                user.getId(),
                expectedHash,
                PasswordHasher.hash(normalizedNewPassword)
            );
            if (updated != 1) {
                throw new AppException(ErrorCode.SESSION_EXPIRED);
            }
            attempt.recordSuccess();
        }
    }

    /**
     * 退出登录并清理会话。
     */
    @Override
    public void logout() {
        currentUserService.logout();
    }

    /**
     * 规范化用户名，并校验基础格式。
     */
    private String normalizeUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new AppException(ErrorCode.USERNAME_BLANK);
        }

        String normalized = username.trim();
        if (normalized.length() < 3 || normalized.length() > 50) {
            throw new AppException(ErrorCode.USERNAME_LENGTH_INVALID);
        }
        return normalized;
    }

    /**
     * 校验密码基本长度。
     */
    private String normalizeNewPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new AppException(ErrorCode.PASSWORD_BLANK);
        }

        String normalized = password.trim();
        if (normalized.length() < 12 || normalized.length() > 100) {
            throw new AppException(ErrorCode.PASSWORD_LENGTH_INVALID);
        }
        return normalized;
    }

    /** 登录和修改密码都只接受当前的 12 到 100 位规则。 */
    private String normalizeLoginPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new AppException(ErrorCode.PASSWORD_BLANK);
        }
        String normalized = password.trim();
        if (normalized.length() < 12 || normalized.length() > 100) {
            throw new AppException(ErrorCode.PASSWORD_LENGTH_INVALID);
        }
        return normalized;
    }

    /**
     * 规范化昵称，未传时默认使用用户名。
     */
    private String normalizeNickname(String nickname, String username) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return username;
        }

        String normalized = nickname.trim();
        if (normalized.length() > 50) {
            throw new AppException(ErrorCode.NICKNAME_TOO_LONG);
        }
        return normalized;
    }
}
