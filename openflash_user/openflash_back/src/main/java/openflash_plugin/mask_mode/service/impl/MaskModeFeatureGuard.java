package openflash_plugin.mask_mode.service.impl;

import org.springframework.stereotype.Service;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.service.FeatureFlagService;

/** 集中维护遮蔽模式插件整体开关。 */
@Service
public class MaskModeFeatureGuard {

    public static final String FEATURE_MASK_MODE = "feature.mask-mode";

    private final FeatureFlagService featureFlagService;

    /** 注入功能开关服务，统一读取遮蔽模式插件开关。 */
    public MaskModeFeatureGuard(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    /** 判断遮蔽模式插件整体是否启用。 */
    public boolean isMaskModeEnabled() {
        return featureFlagService.isEnabled(FEATURE_MASK_MODE);
    }

    /** 插件关闭时向直接访问接口的用户返回功能关闭。 */
    public void ensureMaskModeEnabled() {
        if (!isMaskModeEnabled()) {
            throw new AppException(ErrorCode.FEATURE_DISABLED);
        }
    }
}
