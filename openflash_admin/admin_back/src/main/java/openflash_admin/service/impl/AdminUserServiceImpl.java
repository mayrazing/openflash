package openflash_admin.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.entity.AdminUser;
import openflash_admin.mapper.AdminUserMapper;
import openflash_admin.service.AdminUserService;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final int SEARCH_LIMIT = 100;

    private final AdminUserMapper userMapper;

    public AdminUserServiceImpl(AdminUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<AdminUser> search(String query) {
        String normalizedQuery = query == null || query.isBlank() ? "" : query;
        return userMapper.search(normalizedQuery, SEARCH_LIMIT);
    }

    @Transactional
    @Override
    public void updateRole(Long userId, String role) {
        List<Long> activeAdminIds = userMapper.lockActiveAdminIds();
        AdminUser target = requireLockedUser(userId);
        Role requestedRole = parseRole(role);
        if (requestedRole.name().equals(target.getRole())
                && (requestedRole != Role.ADMIN || Boolean.TRUE.equals(target.getAdminApproved()))) {
            return;
        }

        if ("ADMIN".equals(target.getRole())
                && Integer.valueOf(0).equals(target.getBanned())
                && requestedRole == Role.USER) {
            boolean targetIsLocked = activeAdminIds.contains(userId);
            boolean hasAnotherActiveAdmin = activeAdminIds.stream()
                .anyMatch(adminId -> !adminId.equals(userId));
            if (!targetIsLocked || !hasAnotherActiveAdmin) {
                throw new AdminException(AdminErrorCode.LAST_ADMIN_REQUIRED);
            }
        }

        if (userMapper.updateRole(userId, requestedRole.name()) != 1) {
            throw new AdminException(AdminErrorCode.USER_NOT_FOUND);
        }
    }

    private Role parseRole(String role) {
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AdminException(AdminErrorCode.INVALID_ROLE);
        }
    }

    private AdminUser requireLockedUser(Long userId) {
        AdminUser user = userMapper.lockById(userId);
        if (user == null || !Integer.valueOf(0).equals(user.getDeleted())) {
            throw new AdminException(AdminErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private enum Role {
        ADMIN,
        USER
    }
}
