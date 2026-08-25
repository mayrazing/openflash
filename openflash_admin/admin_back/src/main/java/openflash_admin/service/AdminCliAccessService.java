package openflash_admin.service;

import java.util.List;
import openflash_admin.dto.UserAccessPage;
import openflash_admin.entity.AdminUser;

public interface AdminCliAccessService {

    UserAccessPage accessForUsers(List<AdminUser> users);

    void updateAccess(Long userId, String cliKey, boolean enabled);
}
