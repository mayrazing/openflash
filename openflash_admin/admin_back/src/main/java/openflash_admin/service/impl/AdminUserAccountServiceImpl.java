package openflash_admin.service.impl;

import org.springframework.stereotype.Service;
import openflash_admin.client.OpenFlashCoreAdminClient;
import openflash_admin.service.AdminSessionService;
import openflash_admin.service.AdminUserAccountService;

@Service
public class AdminUserAccountServiceImpl implements AdminUserAccountService {

    private final AdminSessionService sessionService;
    private final OpenFlashCoreAdminClient coreClient;

    public AdminUserAccountServiceImpl(
            AdminSessionService sessionService,
            OpenFlashCoreAdminClient coreClient) {
        this.sessionService = sessionService;
        this.coreClient = coreClient;
    }

    @Override
    public void setBanned(Long userId, boolean banned) {
        Long actorUserId = sessionService.requireCurrentAdmin().getId();
        coreClient.setUserBanned(actorUserId, userId, banned);
    }

    @Override
    public void deleteUser(Long userId) {
        Long actorUserId = sessionService.requireCurrentAdmin().getId();
        coreClient.deleteUser(actorUserId, userId);
    }
}
