package openflash_plugin.ai_card.service.impl;

import org.springframework.stereotype.Service;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.service.FeatureFlagService;

/** 集中维护 ai-card 插件 active 开关和内部子功能开关。 */
@Service
public class AiCardFeatureGuard {

    public static final String FEATURE_AI_CARD = "feature.ai.card-markdown";
    public static final String FEATURE_SIDE_COMPLETION = "feature.ai.side-completion";

    private final FeatureFlagService featureFlagService;

    public AiCardFeatureGuard(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    /** 判断 ai-card 插件整体是否启用。 */
    public boolean isAiCardEnabled() {
        return featureFlagService.isEnabled(FEATURE_AI_CARD);
    }

    /** 判断补全另一面子功能是否启用。 */
    public boolean isSideCompletionEnabled() {
        return isAiCardEnabled() && featureFlagService.isEnabled(FEATURE_SIDE_COMPLETION);
    }

    /** ai-card 插件关闭时向直接访问接口的用户返回功能关闭。 */
    public void ensureAiCardEnabled() {
        if (!isAiCardEnabled()) {
            throw new AppException(ErrorCode.FEATURE_DISABLED);
        }
    }

}
