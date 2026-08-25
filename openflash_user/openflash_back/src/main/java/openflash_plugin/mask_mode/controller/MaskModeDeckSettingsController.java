package openflash_plugin.mask_mode.controller;

import openflash_plugin.mask_mode.dto.MaskModeDeckSettingsUpdateCommand;
import openflash_plugin.mask_mode.entity.MaskModeDeckSettings;
import openflash_plugin.mask_mode.service.MaskModeDeckSettingsService;
import openflash_plugin.mask_mode.service.impl.MaskModeFeatureGuard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.dto.ApiResponse;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;

@RestController
public class MaskModeDeckSettingsController {

    private final MaskModeDeckSettingsService maskModeDeckSettingsService;
    private final MaskModeFeatureGuard featureGuard;

    /** 注入遮蔽模式卡包设置服务和功能开关 guard。 */
    public MaskModeDeckSettingsController(
        MaskModeDeckSettingsService maskModeDeckSettingsService,
        MaskModeFeatureGuard featureGuard
    ) {
        this.maskModeDeckSettingsService = maskModeDeckSettingsService;
        this.featureGuard = featureGuard;
    }

    /**
     * 用户打开遮蔽模式插件设置时读取当前卡包遮蔽模式。
     */
    @GetMapping("/api/plugins/mask-mode/decks/{deckId}/settings")
    public ApiResponse<MaskModeDeckSettings> getSettings(@PathVariable Long deckId) {
        featureGuard.ensureMaskModeEnabled();
        return ApiResponse.success(maskModeDeckSettingsService.getForCurrentUser(deckId));
    }

    /**
     * 用户保存遮蔽模式插件设置时写入当前卡包遮蔽模式。
     */
    @PutMapping("/api/plugins/mask-mode/decks/{deckId}/settings")
    public ApiResponse<MaskModeDeckSettings> updateSettings(
        @PathVariable Long deckId,
        @RequestBody(required = false) MaskModeDeckSettingsRequest request
    ) {
        featureGuard.ensureMaskModeEnabled();
        return ApiResponse.success(
            maskModeDeckSettingsService.saveForCurrentUser(deckId, requireValidRequest(request).toCommand()));
    }

    /**
     * 校验页面保存内容存在，避免空请求体变成通用错误。
     */
    private MaskModeDeckSettingsRequest requireValidRequest(MaskModeDeckSettingsRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.DECK_SETTINGS_INVALID);
        }
        return request;
    }

    public record MaskModeDeckSettingsRequest(String mode, Boolean enabled) {
        /**
         * 把页面保存内容转成服务层命令，字段保持和页面请求一一对应。
         */
        MaskModeDeckSettingsUpdateCommand toCommand() {
            return new MaskModeDeckSettingsUpdateCommand(mode, enabled);
        }
    }
}
