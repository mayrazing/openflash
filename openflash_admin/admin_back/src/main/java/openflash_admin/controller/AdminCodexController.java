package openflash_admin.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import openflash_admin.client.AiRuntimeAdminClient.LoginSnapshot;
import openflash_admin.common.AdminErrorCode;
import openflash_admin.common.AdminException;
import openflash_admin.common.ApiResponse;
import openflash_admin.dto.AdminCodexResponse;
import openflash_admin.service.AdminCodexService;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/admin/codex")
public class AdminCodexController {

    private final AdminCodexService codexService;

    public AdminCodexController(AdminCodexService codexService) {
        this.codexService = codexService;
    }

    @GetMapping
    public ApiResponse<AdminCodexResponse> snapshot() {
        return ApiResponse.success(codexService.snapshot());
    }

    @PutMapping("/enabled")
    public ApiResponse<Void> setEnabled(@RequestBody JsonNode request) {
        codexService.setEnabled(requireEnabled(request));
        return ApiResponse.success(null);
    }

    @PostMapping("/login")
    public ApiResponse<LoginSnapshot> startLogin() {
        return ApiResponse.success(codexService.startLogin());
    }

    @DeleteMapping("/login")
    public ApiResponse<LoginSnapshot> cancelLogin() {
        return ApiResponse.success(codexService.cancelLogin());
    }

    @DeleteMapping("/account")
    public ApiResponse<Void> logoutAccount() {
        codexService.logoutAccount();
        return ApiResponse.success(null);
    }

    private boolean requireEnabled(JsonNode request) {
        if (request == null
            || !request.isObject()
            || request.size() != 1
            || !request.has("enabled")
            || !request.get("enabled").isBoolean()) {
            throw new AdminException(AdminErrorCode.INVALID_REQUEST);
        }
        return request.get("enabled").booleanValue();
    }
}
