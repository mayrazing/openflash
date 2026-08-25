package openflash_core.controller;

import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import openflash_core.common.AppErrorCode;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.service.CurrentUserService;
import openflash_core.service.UserSseRegistry;

@RestController
@RequestMapping("/api")
public class SseController {

    private static final long NOTIFICATION_TIMEOUT_MILLIS = 30L * 60L * 1000L;
    private static final String ACCOUNT_INVALIDATED_EVENT = "account-invalidated";

    private final UserSseRegistry userSseRegistry;
    private final CurrentUserService currentUserService;

    public SseController(UserSseRegistry userSseRegistry, CurrentUserService currentUserService) {
        this.userSseRegistry = userSseRegistry;
        this.currentUserService = currentUserService;
    }

    /**
     * 为当前登录用户建立 SSE 连接；AI 任务完成后通过此连接推送通知。
     */
    @GetMapping(value = "/sse/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter notifications() {
        Long userId = currentUserService.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(NOTIFICATION_TIMEOUT_MILLIS);
        userSseRegistry.register(userId, emitter);
        emitter.onTimeout(() -> userSseRegistry.remove(userId, emitter));
        emitter.onError(e -> userSseRegistry.remove(userId, emitter));
        emitter.onCompletion(() -> userSseRegistry.remove(userId, emitter));
        try {
            Long revalidatedUserId = currentUserService.getCurrentUserId();
            if (!Objects.equals(userId, revalidatedUserId)) {
                closeInvalidEmitter(userId, emitter, ErrorCode.SESSION_EXPIRED);
            }
        } catch (AppException ex) {
            closeInvalidEmitter(userId, emitter, ex.getErrorCode());
        }
        return emitter;
    }

    private void closeInvalidEmitter(Long userId, SseEmitter emitter, AppErrorCode errorCode) {
        String reason = errorCode.name();
        if (errorCode == ErrorCode.ACCOUNT_BANNED) {
            reason = "BANNED";
        } else if (errorCode == ErrorCode.ACCOUNT_DELETED) {
            reason = "DELETED";
        }
        userSseRegistry.pushAndClose(
            userId,
            emitter,
            ACCOUNT_INVALIDATED_EVENT,
            Map.of("reason", reason, "code", errorCode.value())
        );
    }
}
