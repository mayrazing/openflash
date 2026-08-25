package openflash_plugin.ai_card.service.impl;

import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;

/** 测试用固定开关状态 guard，避免每个测试重复 mock feature flag。 */
final class StaticAiCardFeatureGuard extends AiCardFeatureGuard {

    private final boolean aiCardEnabled;
    private final boolean sideCompletionEnabled;

    StaticAiCardFeatureGuard(boolean aiCardEnabled, boolean sideCompletionEnabled) {
        super(null);
        this.aiCardEnabled = aiCardEnabled;
        this.sideCompletionEnabled = sideCompletionEnabled;
    }

    /** 返回测试指定的 ai-card 插件状态。 */
    @Override
    public boolean isAiCardEnabled() {
        return aiCardEnabled;
    }

    /** 返回测试指定的另一面补全状态。 */
    @Override
    public boolean isSideCompletionEnabled() {
        return aiCardEnabled && sideCompletionEnabled;
    }

    /** ai-card 关闭时抛出与生产 guard 一致的功能关闭错误。 */
    @Override
    public void ensureAiCardEnabled() {
        if (!aiCardEnabled) {
            throw new AppException(ErrorCode.FEATURE_DISABLED);
        }
    }
}
