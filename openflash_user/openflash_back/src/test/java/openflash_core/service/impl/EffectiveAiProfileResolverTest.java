package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import openflash_core.common.AiErrorCode;
import openflash_core.service.UserAiConfigService;
import openflash_core.config.AiProperties;
import openflash_core.dto.ActiveAiSelectionDto;
import openflash_core.common.AiSource;
import openflash_core.common.AppException;

class EffectiveAiProfileResolverTest {

        @Test
        void productionResolverReadsModelFromUnifiedSelectionWithoutDecryptingConfig() {
                UserAiConfigService configs = mock(UserAiConfigService.class);
                UnifiedAiSelectionServiceImpl selection = mock(UnifiedAiSelectionServiceImpl.class);
                when(selection.requireActive(7L)).thenReturn(new ActiveAiSelectionDto(
                                AiSource.PLATFORM, null, "platform-codex-cli",
                                "CODEX_APP_SERVER", "gpt-5.4", "high"));
                EffectiveAiProfileResolver resolver = new EffectiveAiProfileResolver(configs, selection);

                assertEquals("gpt-5.4", resolver.readUserModel(7L));
        }

        @Test
        void requireActiveIdentityUsesUserProviderKeyAndEffectivePromptProfile() {
                UnifiedAiSelectionServiceImpl selection = mock(UnifiedAiSelectionServiceImpl.class);
                when(selection.requireActive(7L)).thenReturn(new ActiveAiSelectionDto(
                                AiSource.USER, "openai-main", null,
                                "OPENAI_COMPAT", "gpt-5.4", null));
                EffectiveAiProfileResolver resolver = new EffectiveAiProfileResolver(
                                mock(UserAiConfigService.class), selection);
                AiProperties.AiProfile promptProfile = profile(
                                "card-ai", "configured-default", "explain briefly", 0.2);

                EffectiveAiProfileResolver.ActiveAiIdentity identity = resolver.requireActiveIdentity(7L,
                                promptProfile);

                assertEquals(AiSource.USER, identity.source());
                assertEquals("openai-main", identity.selectionKey());
                assertEquals("gpt-5.4", identity.model());
                assertNull(identity.reasoningEffort());
                assertEquals("card-ai", identity.effectivePromptProfile().getName());
                assertEquals("gpt-5.4", identity.effectivePromptProfile().getModel());
                assertEquals("explain briefly", identity.effectivePromptProfile().getSystem());
                assertEquals(0.2, identity.effectivePromptProfile().getTemperature());
                assertEquals("configured-default", promptProfile.getModel());
        }

        @Test
        void requireActiveIdentityUsesPlatformOfferingKeyAndEffort() {
                UnifiedAiSelectionServiceImpl selection = mock(UnifiedAiSelectionServiceImpl.class);
                when(selection.requireActive(9L)).thenReturn(new ActiveAiSelectionDto(
                                AiSource.PLATFORM, null, "platform-codex-cli",
                                "CODEX_APP_SERVER", "gpt-5.4", "high"));
                EffectiveAiProfileResolver resolver = new EffectiveAiProfileResolver(
                                mock(UserAiConfigService.class), selection);

                EffectiveAiProfileResolver.ActiveAiIdentity identity = resolver.requireActiveIdentity(
                                9L, profile("side-completion", "configured-default", "complete", 0.1));

                assertEquals(AiSource.PLATFORM, identity.source());
                assertEquals("platform-codex-cli", identity.selectionKey());
                assertEquals("gpt-5.4", identity.model());
                assertEquals("high", identity.reasoningEffort());
                assertEquals("gpt-5.4", identity.effectivePromptProfile().getModel());
        }

        @Test
        void requireActiveIdentityPreservesMissingSelectionError() {
                UnifiedAiSelectionServiceImpl selection = mock(UnifiedAiSelectionServiceImpl.class);
                AppException missing = new AppException(AiErrorCode.AI_NOT_CONFIGURED);
                when(selection.requireActive(null)).thenThrow(missing);
                EffectiveAiProfileResolver resolver = new EffectiveAiProfileResolver(
                                mock(UserAiConfigService.class), selection);

                AppException thrown = assertThrows(AppException.class,
                                () -> resolver.requireActiveIdentity(null, profile(
                                                "card-ai", "configured-default", "system", 0.2)));

                assertSame(missing, thrown);
        }

        @Test
        void applyModelPreservesPromptOptionsAndOverridesOnlyModel() {
                EffectiveAiProfileResolver resolver = new EffectiveAiProfileResolver(
                                mock(UserAiConfigService.class));
                AiProperties.AiProfile base = new AiProperties.AiProfile();
                base.setName("side");
                base.setModel("base");
                base.setSystem("system");
                base.setTemperature(0.2);

                AiProperties.AiProfile result = resolver.applyModel(base, "selected");

                assertEquals("selected", result.getModel());
                assertEquals("side", result.getName());
                assertEquals("system", result.getSystem());
                assertEquals(0.2, result.getTemperature());
                assertNull(resolver.applyModel(null, "selected"));
        }

        private static AiProperties.AiProfile profile(
                        String name, String model, String system, double temperature) {
                AiProperties.AiProfile profile = new AiProperties.AiProfile();
                profile.setName(name);
                profile.setModel(model);
                profile.setSystem(system);
                profile.setTemperature(temperature);
                return profile;
        }
}
