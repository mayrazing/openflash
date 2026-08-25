package openflash_core.service.impl;

import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import openflash_core.common.AppException;
import openflash_core.common.AppLog;
import openflash_core.common.ErrorCode;
import openflash_core.entity.User;
import openflash_core.entity.UserSettings;
import openflash_core.mapper.UserMapper;
import openflash_core.mapper.UserSettingsMapper;
import openflash_core.service.CurrentUserService;

/**
 * 负责准备当前默认用户，并返回用户 ID。
 */
@Service
public class CurrentUserServiceImpl implements CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserServiceImpl.class);
    private static final String SESSION_USER_ID = "currentUserId";
    private static final String SESSION_AUTH_VERSION = "currentAuthVersion";

    private final UserMapper userMapper;
    private final UserSettingsMapper userSettingsMapper;

    public CurrentUserServiceImpl(UserMapper userMapper, UserSettingsMapper userSettingsMapper) {
        this.userMapper = userMapper;
        this.userSettingsMapper = userSettingsMapper;
    }

    /**
     * 获取当前默认用户 ID，不存在时自动创建。
     */
    @Override
    @Transactional
    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    /**
     * 获取当前登录用户，不存在时提示重新登录。
     */
    @Override
    @Transactional
    public User getCurrentUser() {
        Long userId = getSessionUserId();
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        User user = userMapper.findById(userId);
        if (user == null) {
            logout();
            throw new AppException(ErrorCode.ACCOUNT_DELETED);
        }
        if (!Integer.valueOf(0).equals(user.getBanned())) {
            logout();
            throw new AppException(ErrorCode.ACCOUNT_BANNED);
        }
        if (!Objects.equals(user.getAuthVersion(), getSessionAuthVersion())) {
            logout();
            throw new AppException(ErrorCode.SESSION_EXPIRED);
        }

        ensureUserSettings(user.getId());
        return user;
    }

    /**
     * 将用户写入当前会话。
     */
    @Override
    public void login(User user) {
        if (user == null || user.getId() == null) {
            AppLog.error(log, ErrorCode.INTERNAL_USER_NOT_FOUND, "登录用户不能为空");
            throw new IllegalArgumentException("登录用户不能为空");
        }
        HttpSession session = getSession(true);
        session.setAttribute(SESSION_USER_ID, user.getId());
        session.setAttribute(SESSION_AUTH_VERSION, user.getAuthVersion());
    }

    /**
     * 清理当前会话。
     */
    @Override
    public void logout() {
        HttpSession session = getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /**
     * 确保当前用户有一份默认设置。
     */
    @Override
    @Transactional
    public void ensureUserSettings(Long userId) {
        UserSettings settings = userSettingsMapper.findByUserId(userId);
        if (settings != null) {
            return;
        }

        UserSettings defaultSettings = new UserSettings();
        defaultSettings.setUserId(userId);
        defaultSettings.setTheme("light");
        defaultSettings.setSoundEnabled(true);
        defaultSettings.setLanguage("en");
        userSettingsMapper.insert(defaultSettings);
    }

    /**
     * 读取会话里的用户 ID。
     */
    private Long getSessionUserId() {
        HttpSession session = getSession(false);
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute(SESSION_USER_ID);
        return value instanceof Long ? (Long) value : null;
    }

    /** 读取登录时记录的账号认证版本。 */
    private Long getSessionAuthVersion() {
        HttpSession session = getSession(false);
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute(SESSION_AUTH_VERSION);
        return value instanceof Long ? (Long) value : null;
    }

    /**
     * 按需获取当前请求会话。
     */
    private HttpSession getSession(boolean create) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("当前请求不存在");
        }
        return attributes.getRequest().getSession(create);
    }
}
