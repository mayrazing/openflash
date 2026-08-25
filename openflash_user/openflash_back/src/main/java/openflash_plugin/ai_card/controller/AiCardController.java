package openflash_plugin.ai_card.controller;

import openflash_plugin.ai_card.service.CardAiService;
import openflash_plugin.ai_card.service.impl.AiCardFeatureGuard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.dto.ApiResponse;
import openflash_plugin.ai_card.dto.AiCacheStatusResponse;

/** 提供 AI 卡片解释相关接口，URL 保持与旧核心接口一致。 */
@RestController
@RequestMapping("/api")
public class AiCardController {

    private final CardAiService cardAiService;
    private final AiCardFeatureGuard featureGuard;

    public AiCardController(CardAiService cardAiService, AiCardFeatureGuard featureGuard) {
        this.cardAiService = cardAiService;
        this.featureGuard = featureGuard;
    }

    /** 查询 AI 缓存状态；命中时返回内容，未命中时入队后台任务并返回 queued。 */
    @GetMapping("/cards/{cardId}/ai-cache-status")
    public ApiResponse<AiCacheStatusResponse> checkAiCacheStatus(
        @PathVariable Long cardId,
        @RequestParam(required = false) String side
    ) {
        featureGuard.ensureAiCardEnabled();
        return ApiResponse.success(cardAiService.checkAiCacheStatus(cardId, side));
    }

    /** 强制重新生成 AI 缓存；已有内容会被后台任务覆盖。 */
    @PostMapping("/cards/{cardId}/ai-cache-regenerate")
    public ApiResponse<AiCacheStatusResponse> regenerateAiCache(
        @PathVariable Long cardId,
        @RequestParam(required = false) String side
    ) {
        featureGuard.ensureAiCardEnabled();
        return ApiResponse.success(cardAiService.regenerateAiCache(cardId, side));
    }

    /** 返回 ai-card 插件内部子功能状态，供设置页隐藏或显示子功能块。 */
    @GetMapping("/plugins/ai-card/features")
    public ApiResponse<AiCardFeatureState> getFeatureState() {
        featureGuard.ensureAiCardEnabled();
        return ApiResponse.success(new AiCardFeatureState(featureGuard.isSideCompletionEnabled()));
    }

    public record AiCardFeatureState(Boolean sideCompletionEnabled) {
    }
}
