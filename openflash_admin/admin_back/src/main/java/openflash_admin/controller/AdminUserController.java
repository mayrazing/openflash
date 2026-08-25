package openflash_admin.controller;

import java.util.List;
import openflash_admin.dto.AdminRoleRequest;
import openflash_admin.dto.AdminUserPageResponse;
import openflash_admin.dto.AdminUserResponse;
import openflash_admin.dto.UserAccess;
import openflash_admin.dto.UserAccessPage;
import openflash_admin.entity.AdminUser;
import openflash_admin.service.AdminCliAccessService;
import openflash_admin.service.AdminUserAccountService;
import openflash_admin.service.AdminUserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.common.ApiResponse;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService userService;
    private final AdminCliAccessService cliAccessService;
    private final AdminUserAccountService accountService;

    public AdminUserController(
            AdminUserService userService,
            AdminCliAccessService cliAccessService,
            AdminUserAccountService accountService) {
        this.userService = userService;
        this.cliAccessService = cliAccessService;
        this.accountService = accountService;
    }

    @GetMapping
    public ApiResponse<AdminUserPageResponse> search(
        @RequestParam(defaultValue = "") String query
    ) {
        List<AdminUser> foundUsers = userService.search(query);
        var accessPage = cliAccessService.accessForUsers(foundUsers);
        List<AdminUserResponse> users = foundUsers.stream()
            .map(user -> toResponse(user, requireAccess(accessPage, user.getId())))
            .toList();
        return ApiResponse.success(new AdminUserPageResponse(
            accessPage.runtimeAvailable(),
            accessPage.clis(),
            accessPage.offerings(),
            users));
    }

    @PutMapping("/{userId}/role")
    public ApiResponse<Void> updateRole(
        @PathVariable Long userId,
        @RequestBody AdminRoleRequest request
    ) {
        userService.updateRole(userId, request.role());
        return ApiResponse.success(null);
    }

    @PutMapping("/{userId}/cli-access/{cliKey}")
    public ApiResponse<Void> updateCliAccess(
        @PathVariable Long userId,
        @PathVariable String cliKey,
        @RequestBody JsonNode request
    ) {
        cliAccessService.updateAccess(userId, cliKey, requireBoolean(request, "enabled"));
        return ApiResponse.success(null);
    }

    @PutMapping("/{userId}/banned")
    public ApiResponse<Void> setBanned(
        @PathVariable Long userId,
        @RequestBody JsonNode request
    ) {
        accountService.setBanned(userId, requireBoolean(request, "banned"));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> delete(@PathVariable Long userId) {
        accountService.deleteUser(userId);
        return ApiResponse.success(null);
    }

    private boolean requireBoolean(JsonNode request, String field) {
        if (request == null
            || !request.isObject()
            || request.size() != 1
            || !request.has(field)
            || !request.get(field).isBoolean()) {
            throw new AdminException(AdminErrorCode.INVALID_REQUEST);
        }
        return request.get(field).booleanValue();
    }

    private UserAccess requireAccess(
            UserAccessPage page,
            Long userId) {
        UserAccess access = page.accessByUserId().get(userId);
        if (access == null) throw new IllegalStateException("missing platform access projection");
        return access;
    }

    private AdminUserResponse toResponse(AdminUser user, UserAccess access) {
        return new AdminUserResponse(
            user.getId(),
            user.getUsername(),
            user.getNickname(),
            user.getRole(),
            Integer.valueOf(1).equals(user.getBanned()),
            access.cliAccess(),
            access.offeringAccess()
        );
    }

}
