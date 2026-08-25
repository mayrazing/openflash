package openflash_core.service;

import openflash_core.entity.User;

/**
 * 负责提供当前使用中的用户信息。
 */
public interface CurrentUserService {

    /**
     * 获取当前用户 ID。
     */
    Long getCurrentUserId();

    /**
     * 获取当前登录用户。
     */
    User getCurrentUser();

    /**
     * 将用户写入当前会话。
     */
    void login(User user);

    /**
     * 清理当前会话。
     */
    void logout();

    /**
     * 确保用户默认设置存在。
     */
    void ensureUserSettings(Long userId);
}
