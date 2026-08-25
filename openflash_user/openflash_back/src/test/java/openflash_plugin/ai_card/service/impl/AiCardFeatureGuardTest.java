package openflash_plugin.ai_card.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.service.FeatureFlagService;

class AiCardFeatureGuardTest {

    @Test
    void sideCompletionRequiresPluginAndDedicatedFeatureFlags() {
        FeatureFlagService flags = new FeatureFlagService(featureKey ->
                !AiCardFeatureGuard.FEATURE_SIDE_COMPLETION.equals(featureKey));
        AiCardFeatureGuard guard = new AiCardFeatureGuard(flags);

        assertFalse(guard.isSideCompletionEnabled());
        assertTrue(guard.isAiCardEnabled());
    }

    @Test
    void ensureAiCardEnabledThrowsFeatureDisabledWhenFlagIsOff() {
        FeatureFlagService flags = new FeatureFlagService(featureKey -> false);
        AiCardFeatureGuard guard = new AiCardFeatureGuard(flags);

        AppException ex = assertThrows(AppException.class, guard::ensureAiCardEnabled);

        assertEquals(ErrorCode.FEATURE_DISABLED, ex.getErrorCode());
    }

    @Test
    void ensureAiCardEnabledAllowsEnabledPlugin() {
        FeatureFlagService flags = new FeatureFlagService(featureKey -> true);
        AiCardFeatureGuard guard = new AiCardFeatureGuard(flags);

        guard.ensureAiCardEnabled();
    }
}
