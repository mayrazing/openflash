package openflash_core.controller;

import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.security.InternalAdminTokenGuard;
import openflash_core.service.impl.InternalUserAdminServiceImpl;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 暴露仅供受信任 admin_back 调用的账号封禁和永久删除入口。 */
@RestController
@RequestMapping("/api/internal/admin")
public class InternalUserAdminController {

    private static final String TOKEN_HEADER = "X-OpenFlash-Admin-Token";

    private final InternalAdminTokenGuard tokenGuard;
    private final InternalUserAdminServiceImpl service;

    public InternalUserAdminController(
            InternalAdminTokenGuard tokenGuard,
            InternalUserAdminServiceImpl service) {
        this.tokenGuard = tokenGuard;
        this.service = service;
    }

    @PutMapping("/users/{userId}/banned")
    public UpdateResponse setBanned(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @PathVariable Long userId,
            @RequestBody(required = false) JsonNode request) {
        tokenGuard.requireValid(token);
        requirePositive(userId);
        if (request == null || !request.isObject() || request.size() != 2
                || !request.has("actorUserId")
                || !request.get("actorUserId").isIntegralNumber()
                || !request.get("actorUserId").canConvertToLong()
                || !request.has("banned")
                || !request.get("banned").isBoolean()) {
            throw invalidRequest();
        }
        long actorUserId = request.get("actorUserId").longValue();
        requirePositive(actorUserId);
        service.setBanned(actorUserId, userId, request.get("banned").booleanValue());
        return new UpdateResponse(true);
    }

    @DeleteMapping("/users/{userId}")
    public DeleteResponse delete(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @PathVariable Long userId,
            @RequestParam(required = false) Long actorUserId) {
        tokenGuard.requireValid(token);
        requirePositive(userId);
        requirePositive(actorUserId);
        service.deleteUser(actorUserId, userId);
        return new DeleteResponse(true);
    }

    private void requirePositive(Long userId) {
        if (userId == null || userId <= 0) {
            throw invalidRequest();
        }
    }

    private AppException invalidRequest() {
        return new AppException(ErrorCode.INTERNAL_ADMIN_REQUEST_INVALID);
    }

    public record UpdateResponse(boolean updated) {}

    public record DeleteResponse(boolean deleted) {}
}
