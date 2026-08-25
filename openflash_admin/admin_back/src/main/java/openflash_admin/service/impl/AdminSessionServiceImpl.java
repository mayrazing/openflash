package openflash_admin.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.entity.AdminUser;
import openflash_admin.mapper.AdminUserMapper;
import openflash_admin.security.PasswordVerifier;
import openflash_admin.security.AdminLoginAttemptGuard;
import openflash_admin.service.AdminSessionService;

@Service
public class AdminSessionServiceImpl implements AdminSessionService {

    static final String SESSION_ADMIN_USER_ID = "adminUserId";
    static final String SESSION_ADMIN_AUTH_VERSION = "adminAuthVersion";

    private final AdminUserMapper userMapper;
    private final PasswordVerifier passwordVerifier;
    private final AdminLoginAttemptGuard loginAttemptGuard;

    public AdminSessionServiceImpl(
            AdminUserMapper userMapper,
            PasswordVerifier passwordVerifier,
            AdminLoginAttemptGuard loginAttemptGuard) {
        this.userMapper = userMapper;
        this.passwordVerifier = passwordVerifier;
        this.loginAttemptGuard = loginAttemptGuard;
    }

    @Override
    public AdminUser login(String username, String password) {
        try (AdminLoginAttemptGuard.AttemptLease attempt = loginAttemptGuard.beginAttempt(username)) {
            AdminUser user = userMapper.findByUsername(username);
            if (user == null || !isActive(user)
                || !passwordVerifier.matches(password, user.getPasswordHash())) {
                attempt.recordFailure();
                throw new AdminException(AdminErrorCode.WRONG_CREDENTIALS);
            }
            if (!isAdmin(user)) {
                throw new AdminException(AdminErrorCode.FORBIDDEN);
            }
            attempt.recordSuccess();

            HttpServletRequest request = currentRequest();
            HttpSession previousSession = request.getSession(false);
            if (previousSession != null) {
                previousSession.invalidate();
            }
            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_ADMIN_USER_ID, user.getId());
            session.setAttribute(SESSION_ADMIN_AUTH_VERSION, user.getAuthVersion());
            return user;
        }
    }

    @Override
    public AdminUser requireCurrentAdmin() {
        HttpSession session = currentRequest().getSession(false);
        if (session == null) {
            throw new AdminException(AdminErrorCode.UNAUTHORIZED);
        }

        Object value = session.getAttribute(SESSION_ADMIN_USER_ID);
        Object versionValue = session.getAttribute(SESSION_ADMIN_AUTH_VERSION);
        if (!(value instanceof Long userId) || !(versionValue instanceof Long sessionAuthVersion)) {
            session.invalidate();
            throw new AdminException(AdminErrorCode.UNAUTHORIZED);
        }

        AdminUser user = userMapper.findById(userId);
        if (user == null || !isActive(user)
                || !sessionAuthVersion.equals(user.getAuthVersion())) {
            session.invalidate();
            throw new AdminException(AdminErrorCode.UNAUTHORIZED);
        }
        if (!isAdmin(user)) {
            session.invalidate();
            throw new AdminException(AdminErrorCode.FORBIDDEN);
        }
        return user;
    }

    @Override
    public void logout() {
        HttpSession session = currentRequest().getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private boolean isActive(AdminUser user) {
        return Integer.valueOf(0).equals(user.getDeleted())
            && Integer.valueOf(0).equals(user.getBanned());
    }

    private boolean isAdmin(AdminUser user) {
        return "ADMIN".equals(user.getRole())
            && Boolean.TRUE.equals(user.getAdminApproved());
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("No current HTTP request");
        }
        return attributes.getRequest();
    }
}
