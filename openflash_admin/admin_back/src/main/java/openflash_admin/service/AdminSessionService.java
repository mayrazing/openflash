package openflash_admin.service;

import openflash_admin.entity.AdminUser;

public interface AdminSessionService {

    AdminUser login(String username, String password);

    AdminUser requireCurrentAdmin();

    void logout();
}
