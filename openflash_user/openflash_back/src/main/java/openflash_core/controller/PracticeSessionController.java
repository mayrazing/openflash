package openflash_core.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.dto.ApiResponse;
import openflash_core.mapper.PracticeSessionStoreMapper;
import openflash_core.service.CurrentUserService;
import tools.jackson.databind.ObjectMapper;

/**
 * 练习断点存储接口，收敛为单一 session。
 */
@RestController
@RequestMapping("/api/session-store")
public class PracticeSessionController {

    private final PracticeSessionStoreMapper storeMapper;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public PracticeSessionController(
        PracticeSessionStoreMapper storeMapper,
        CurrentUserService currentUserService,
        ObjectMapper objectMapper
    ) {
        this.storeMapper = storeMapper;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存练习断点数据。
     */
    @PutMapping("/{deckId}/session")
    public ApiResponse<Void> save(
        @PathVariable Long deckId,
        @RequestBody Object data
    ) {
        Long userId = currentUserService.getCurrentUserId();
        storeMapper.upsert(userId, deckId, objectMapper.writeValueAsString(data));
        return ApiResponse.success(null);
    }

    /**
     * 读取练习断点数据，不存在时返回 null。
     */
    @GetMapping("/{deckId}/session")
    public ApiResponse<Object> load(
        @PathVariable Long deckId
    ) {
        Long userId = currentUserService.getCurrentUserId();
        String json = storeMapper.findData(userId, deckId);
        if (json == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(objectMapper.readValue(json, Object.class));
    }

    /**
     * 清除练习断点数据。
     */
    @DeleteMapping("/{deckId}/session")
    public ApiResponse<Void> clear(
        @PathVariable Long deckId
    ) {
        Long userId = currentUserService.getCurrentUserId();
        storeMapper.delete(userId, deckId);
        return ApiResponse.success(null);
    }
}
