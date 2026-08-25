package openflash_plugin.tts.service.impl;

import org.springframework.stereotype.Service;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.service.FeatureFlagService;

/** 集中维护统一 TTS 插件开关和受支持的模型 key. */
@Service
public class TtsFeatureGuard {

    public static final String FEATURE_TTS = "feature.tts";
    public static final String ENGINE_COSYVOICE3 = "cosyvoice3";
    public static final String ENGINE_PIPER = "piper";

    private final FeatureFlagService featureFlagService;

    public TtsFeatureGuard(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    public boolean isTtsEnabled() {
        return featureFlagService.isEnabled(FEATURE_TTS);
    }

    public void ensureTtsEnabled() {
        if (!isTtsEnabled()) {
            throw new AppException(ErrorCode.FEATURE_DISABLED);
        }
    }
}
