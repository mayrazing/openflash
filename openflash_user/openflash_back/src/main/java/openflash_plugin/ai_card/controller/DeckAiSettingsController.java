package openflash_plugin.ai_card.controller;

import openflash_plugin.ai_card.service.DeckAiSettingsService;
import openflash_plugin.ai_card.service.impl.AiCardFeatureGuard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.dto.ApiResponse;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_plugin.ai_card.dto.DeckAiSettingsUpdateCommand;

@RestController
public class DeckAiSettingsController {

    private final DeckAiSettingsService deckAiSettingsService;
    private final AiCardFeatureGuard featureGuard;

    public DeckAiSettingsController(DeckAiSettingsService deckAiSettingsService, AiCardFeatureGuard featureGuard) {
        this.deckAiSettingsService = deckAiSettingsService;
        this.featureGuard = featureGuard;
    }

    /**
     * 用户打开 AI 设置页时读取当前卡包的 AI 开关和提示词。
     */
    @GetMapping("/api/decks/{deckId}/ai-settings")
    public ApiResponse<DeckAiSettings> getAiSettings(@PathVariable Long deckId) {
        featureGuard.ensureAiCardEnabled();
        return ApiResponse.success(deckAiSettingsService.getForCurrentUser(deckId));
    }

    /**
     * 用户点击保存后写入当前卡包的 AI 开关和提示词。
     */
    @PutMapping("/api/decks/{deckId}/ai-settings")
    public ApiResponse<DeckAiSettings> updateAiSettings(
            @PathVariable Long deckId,
            @RequestBody(required = false) DeckAiSettingsRequest request) {
        featureGuard.ensureAiCardEnabled();
        return ApiResponse
                .success(deckAiSettingsService.saveForCurrentUser(deckId, requireValidRequest(request).toCommand()));
    }

    /**
     * 校验页面保存内容存在，避免空请求体变成通用错误。
     */
    private DeckAiSettingsRequest requireValidRequest(DeckAiSettingsRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.DECK_SETTINGS_INVALID);
        }
        return request;
    }

    public record DeckAiSettingsRequest(
            Boolean aiExplanationEnabledA,
            Boolean aiExplanationEnabledB,
            String aiExplanationPromptA,
            String aiExplanationPromptB,
            Boolean aiCompletionEnabled,
            String aiCompletionPrompt) {
        /**
         * 把页面保存内容转成服务层命令，字段保持和页面请求一一对应。
         */
        DeckAiSettingsUpdateCommand toCommand() {
            return new DeckAiSettingsUpdateCommand(
                    aiExplanationEnabledA,
                    aiExplanationEnabledB,
                    aiExplanationPromptA,
                    aiExplanationPromptB,
                    aiCompletionEnabled,
                    aiCompletionPrompt);
        }
    }
}
