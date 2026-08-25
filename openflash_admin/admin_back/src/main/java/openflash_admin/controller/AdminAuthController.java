package openflash_admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import openflash_admin.common.ApiResponse;
import openflash_admin.dto.AdminLoginRequest;
import openflash_admin.dto.CurrentAdminResponse;
import openflash_admin.entity.AdminUser;
import openflash_admin.service.AdminSessionService;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminSessionService sessionService;

    public AdminAuthController(AdminSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/login")
    public ApiResponse<CurrentAdminResponse> login(@RequestBody AdminLoginRequest request) {
        return ApiResponse.success(toResponse(
            sessionService.login(request.username(), request.password())));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentAdminResponse> me() {
        return ApiResponse.success(toResponse(sessionService.requireCurrentAdmin()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        sessionService.logout();
        return ApiResponse.success(null);
    }

    private CurrentAdminResponse toResponse(AdminUser user) {
        return new CurrentAdminResponse(
            user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }
}
