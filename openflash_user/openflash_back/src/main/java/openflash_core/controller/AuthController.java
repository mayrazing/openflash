package openflash_core.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.dto.ApiResponse;
import openflash_core.entity.User;
import openflash_core.service.AuthService;

/**
 * 处理注册、登录、修改密码和当前用户查询。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册账号，并自动登录。
     */
    @PostMapping("/register")
    public ApiResponse<AuthUserResponse> register(@RequestBody RegisterRequest request) {
        return ApiResponse.success(toAuthUserResponse(authService.register(
            request.username(),
            request.password(),
            request.nickname()
        )));
    }

    /**
     * 登录已有账号。
     */
    @PostMapping("/login")
    public ApiResponse<AuthUserResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(toAuthUserResponse(authService.login(
            request.username(),
            request.password()
        )));
    }

    /**
     * 查询当前登录用户。
     */
    @GetMapping("/me")
    public ApiResponse<AuthUserResponse> getCurrentUser() {
        return ApiResponse.success(toAuthUserResponse(authService.getCurrentUser()));
    }

    /**
     * 修改当前账号密码，成功后旧登录态全部失效。
     */
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.currentPassword(), request.newPassword());
        authService.logout();
        return ApiResponse.success(null);
    }

    /**
     * 退出当前账号。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success(null);
    }

    /**
     * 把内部用户对象转成前端需要的安全字段。
     */
    private AuthUserResponse toAuthUserResponse(User user) {
        return new AuthUserResponse(user.getId(), user.getUsername(), user.getNickname());
    }

    public record RegisterRequest(String username, String password, String nickname) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    public record AuthUserResponse(Long id, String username, String nickname) {
    }
}
