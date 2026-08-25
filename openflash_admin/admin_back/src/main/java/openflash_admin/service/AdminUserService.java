package openflash_admin.service;

import java.util.List;
import openflash_admin.entity.AdminUser;

public interface AdminUserService {

    List<AdminUser> search(String query);

    void updateRole(Long userId, String role);
}
