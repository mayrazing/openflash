package openflash_core.service;

import openflash_core.entity.User;

/**
 * 负责注册、登录、修改密码和退出登录。
 */
public interface AuthService {

    /**
     * 注册新用户，并写入当前登录态。
     */
    User register(String username, String password, String nickname);

    /**
     * 校验用户名密码并建立登录态。
     */
    User login(String username, String password);

    /**
     * 返回当前登录用户。
     */
    User getCurrentUser();

    /**
     * 校验当前密码并替换为新密码。
     */
    void changePassword(String currentPassword, String newPassword);

    /**
     * 清理当前登录态。
     */
    void logout();
}
