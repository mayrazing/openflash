package openflash_plugin.ai_card.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import openflash_core.service.AiGateway;
import openflash_core.common.AiErrorCode;
import openflash_core.service.UserAiConfigService;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.config.AiProperties;
import openflash_core.entity.Card;
import openflash_core.service.impl.EffectiveAiProfileResolver;
import openflash_core.dto.ActiveAiSelectionDto;
import openflash_core.common.AiSource;
import openflash_core.service.impl.UnifiedAiSelectionServiceImpl;

class CardAiGenerationCoreTest {

        @Test
        void prepareUsesContentFingerprintAndActiveSelectionProfile() {
                UnifiedAiSelectionServiceImpl selections = mock(UnifiedAiSelectionServiceImpl.class);
                when(selections.requireActive(7L))
                                .thenReturn(new ActiveAiSelectionDto(
                                                AiSource.USER, "openai-main", null, "OPENAI_COMPAT", "gpt-5.4", null))
                                .thenReturn(new ActiveAiSelectionDto(
                                                AiSource.PLATFORM, null, "platform-openai", "OPENAI_COMPAT", "gpt-5.4",
                                                null));
                AiProperties.AiProfile base = profile("card-ai", "configured-default", "global", 0.2);
                CardAiGenerationCore core = new CardAiGenerationCore(
                                null, null, new RecordingAiChatGateway(), enabledGuard(),
                                new EffectiveAiProfileResolver(mock(UserAiConfigService.class), selections));

                CardAiGenerationCore.PreparedCardAiRequest user = core.prepare(
                                card("apple", "苹果"), "A", "deck", 7L, base);
                CardAiGenerationCore.PreparedCardAiRequest platform = core.prepare(
                                card("apple", "苹果"), "A", "deck", 7L, base);

                assertEquals(user.fingerprint(), platform.fingerprint());
                assertEquals("gpt-5.4", user.profile().getModel());
                assertEquals("deck", user.profile().getSystem());
        }

        @Test
        void generateFromPromptRejectsRevokedSelectionBeforeGatewayCall() {
                UnifiedAiSelectionServiceImpl selections = mock(UnifiedAiSelectionServiceImpl.class);
                AppException revoked = new AppException(AiErrorCode.AI_NOT_CONFIGURED);
                when(selections.requireActive(88L)).thenThrow(revoked);
                EffectiveAiProfileResolver resolver = new EffectiveAiProfileResolver(
                                mock(UserAiConfigService.class), selections);
                RecordingAiChatGateway gateway = new RecordingAiChatGateway(revoked);
                CardAiGenerationCore core = new CardAiGenerationCore(
                                null, null, gateway, enabledGuard(), resolver);

                AppException thrown = assertThrows(AppException.class, () -> core.generateFromPrompt(
                                "stale-fingerprint", "prompt", profile("card-ai", "gpt-5.4", "deck", 0.2), 88L));

                assertSame(revoked, thrown);
                assertEquals(0, gateway.chatCount);
        }

        @Test
        void generateFromPromptAllowsDifferentActiveOfferingForSameContent() {
                AiProperties.AiProfile profile = profile("card-ai", "gpt-5.4", "deck", 0.2);
                EffectiveAiProfileResolver queuedResolver = activeResolver(
                                AiSource.USER, "openai-main", "gpt-5.4", null);
                String queuedFingerprint = CardAiPromptSupport.buildFingerprint(
                                "prompt", queuedResolver.requireActiveIdentity(88L, profile));
                EffectiveAiProfileResolver currentResolver = activeResolver(
                                AiSource.PLATFORM, "platform-openai", "gpt-5.4", null);
                RecordingAiChatGateway gateway = new RecordingAiChatGateway(new ActiveAiSelectionDto(
                                AiSource.PLATFORM, null, "platform-openai", "OPENAI_COMPAT", "gpt-5.4", null));
                CardAiGenerationCore core = new CardAiGenerationCore(
                                null, null, gateway, enabledGuard(), currentResolver);

                core.generateFromPrompt(queuedFingerprint, "prompt", profile, 88L);

                assertEquals(1, gateway.chatCount);
        }

        /**
         * 验证功能开关关闭时，同步生成直接失败且不请求本地 AI。
         */
        @Test
        void generateThrowsWhenCardAiMarkdownFeatureDisabled() {
                RecordingAiChatGateway aiChatGateway = new RecordingAiChatGateway(new ActiveAiSelectionDto(
                                AiSource.USER, "openai-main", null, "OPENAI_COMPAT", "user-model", null));
                CardAiGenerationCore core = new CardAiGenerationCore(null, null, aiChatGateway, disabledGuard(),
                                activeResolver(AiSource.USER, "openai-main", "qwen", null));
                CardAiGenerationCore.PreparedCardAiRequest prepared = preparedRequest();

                AppException ex = assertThrows(AppException.class, () -> core.generate(prepared));

                assertEquals(ErrorCode.FEATURE_DISABLED, ex.getErrorCode());
                assertEquals(0, aiChatGateway.chatCount);
        }

        /**
         * 验证 generate 直接把 prepared 里的 userId 传给 AI 网关。
         */
        @Test
        void generatePassesPreparedUserIdToAiCall() {
                RecordingAiChatGateway aiChatGateway = new RecordingAiChatGateway();
                EffectiveAiProfileResolver resolver = activeResolver(
                                AiSource.USER, "openai-main", "qwen", null);
                CardAiGenerationCore core = new CardAiGenerationCore(
                                null,
                                null,
                                aiChatGateway,
                                enabledGuard(),
                                resolver);
                CardAiGenerationCore.PreparedCardAiRequest prepared = preparedRequest(false, 99L, resolver);

                core.generate(prepared);

                assertEquals(99L, aiChatGateway.lastUserId);
        }

        /**
         * 验证按 prompt 直接生成时，userId 会传给 AI 网关并参与会话选择。
         */
        @Test
        void generateFromPromptPassesUserIdToAiCall() {
                RecordingAiChatGateway aiChatGateway = new RecordingAiChatGateway();
                EffectiveAiProfileResolver resolver = activeResolver(
                                AiSource.USER, "openai-main", "qwen", null);
                CardAiGenerationCore core = new CardAiGenerationCore(
                                null,
                                aiProperties(true),
                                aiChatGateway,
                                enabledGuard(),
                                resolver);
                AiProperties.AiProfile profile = profile("card-ai", "qwen", "system", 0.2);
                String fingerprint = CardAiPromptSupport.buildFingerprint(
                                "prompt", resolver.requireActiveIdentity(88L, profile));

                core.generateFromPrompt(fingerprint, "prompt", profile, 88L);

                assertEquals(88L, aiChatGateway.lastUserId);
        }

        /**
         * 验证词卡解释缓存未命中后，卡包提示词和用户 ID 原样进入统一网关。
         */
        @Test
        void generatePreparedExplanationPassesDeckPromptAndUserIdToGateway() {
                RecordingAiChatGateway aiChatGateway = new RecordingAiChatGateway(new ActiveAiSelectionDto(
                                AiSource.USER, "openai-main", null, "OPENAI_COMPAT", "user-model", null));
                AiProperties.AiProfile profile = profile("card-ai", "global-model", "global-system", 0.2);
                AiProperties properties = new AiProperties();
                properties.setProfiles(List.of(profile));
                properties.setFeatureProfiles(Map.of(CardAiGenerationCore.AI_PROFILE_FEATURE_KEY, profile.getName()));
                EffectiveAiProfileResolver resolver = activeResolver(
                                AiSource.USER, "openai-main", "user-model", null);
                CardAiGenerationCore core = new CardAiGenerationCore(
                                null,
                                properties,
                                aiChatGateway,
                                enabledGuard(),
                                resolver);

                CardAiGenerationCore.PreparedCardAiRequest prepared = core.prepare(
                                card("apple", "苹果"), "A", "deck explanation prompt", 42L, profile);
                core.generate(prepared);

                assertEquals("apple", aiChatGateway.lastPrompt);
                assertEquals("deck explanation prompt", aiChatGateway.lastProfileSystem);
                assertEquals(42L, aiChatGateway.lastUserId);
        }

        /**
         * 验证不同用户 ID 不影响指纹，缓存身份只由卡面内容决定。
         */
        @Test
        void fingerprintIgnoresUserId() {
                AiProperties.AiProfile profile = profile("ai_cache", "qwen3:4b", "system", 0.2);

                String fp1 = CardAiPromptSupport.buildFingerprint("apple", profile, 1L);
                String fp2 = CardAiPromptSupport.buildFingerprint("apple", profile, 2L);

                assertEquals(fp1, fp2);
        }

        /**
         * 验证模型配置内容变化不影响缓存指纹，配置变更后由重新生成按钮覆盖缓存。
         */
        @Test
        void fingerprintIgnoresProfileContent() {
                AiProperties.AiProfile first = profile("ai_cache", "qwen3:4b", "system-a", 0.2);
                AiProperties.AiProfile second = profile("ai_cache", "qwen3.5:9b", "system-a", 0.2);

                String firstFingerprint = CardAiPromptSupport.buildFingerprint("apple", first);
                String secondFingerprint = CardAiPromptSupport.buildFingerprint("apple", second);

                assertEquals(firstFingerprint, secondFingerprint);
        }

        /**
         * 验证 profile 字段含换行时仍不影响指纹，缓存身份只看卡面内容。
         */
        @Test
        void fingerprintIgnoresProfileFieldBoundaries() {
                AiProperties.AiProfile first = profile("ai\ncache", "qwen", "system", 0.2);
                AiProperties.AiProfile second = profile("ai", "cache\nqwen", "system", 0.2);

                String firstFingerprint = CardAiPromptSupport.buildFingerprint("apple", first, 1L);
                String secondFingerprint = CardAiPromptSupport.buildFingerprint("apple", second, 1L);

                assertEquals(firstFingerprint, secondFingerprint);
        }

        /**
         * 验证卡包提示词会覆盖全局 profile 的 system，页面生成时使用卡包自己的提示词。
         */
        @Test
        void prepareWithDeckPromptOverridesProfileSystem() {
                CardAiGenerationCore core = coreWithProfile(profile("card-ai", "qwen", "global-system", 0.2));
                Card card = card("apple", "苹果");

                CardAiGenerationCore.PreparedCardAiRequest prepared = core.prepare(card, "A", "deck-system", 7L);

                assertEquals("deck-system", prepared.profile().getSystem());
                assertEquals("global-system", core.resolveCardAiProfile().getSystem());
        }

        /**
         * 验证卡包提示词为空时不继承全局 system，避免页面误用全局提示词。
         */
        @Test
        void prepareWithNullDeckPromptDoesNotInheritGlobalSystem() {
                CardAiGenerationCore core = coreWithProfile(profile("card-ai", "qwen", "global-system", 0.2));
                Card card = card("apple", "苹果");

                CardAiGenerationCore.PreparedCardAiRequest prepared = core.prepare(card, "A", null, 7L);

                assertNull(prepared.profile().getSystem());
                assertEquals("global-system", core.resolveCardAiProfile().getSystem());
        }

        /**
         * 验证 prepare 生成的请求对象会保留 userId。
         */
        @Test
        void prepareKeepsUserIdInRequest() {
                CardAiGenerationCore core = coreWithProfile(profile("card-ai", "qwen", "system", 0.2));
                Card card = card("apple", "苹果");

                CardAiGenerationCore.PreparedCardAiRequest prepared = core.prepare(
                                card, "A", "deck-system", 77L, core.resolveCardAiProfile());

                assertEquals(77L, prepared.userId());
        }

        /**
         * 验证 system prompt 变化不影响内容缓存指纹。
         */
        @Test
        void deckPromptDoesNotChangeFingerprint() {
                CardAiGenerationCore core = coreWithProfile(profile("card-ai", "qwen", "global-system", 0.2));
                Card card = card("apple", "苹果");

                String firstFingerprint = core.prepare(card, "A", "deck-system-a", 7L).fingerprint();
                String secondFingerprint = core.prepare(card, "A", "deck-system-b", 7L).fingerprint();
                String nullDeckFingerprint = core.prepare(card, "A", null, 7L).fingerprint();

                assertEquals(firstFingerprint, secondFingerprint);
                assertEquals(nullDeckFingerprint, firstFingerprint);
        }

        /**
         * 验证替换 system 会创建新 profile，不会修改全局 profile。
         */
        @Test
        void withSystemCopiesProfileWithoutMutatingBaseProfile() {
                AiProperties.AiProfile base = profile("card-ai", "qwen", "global-system", 0.2);

                AiProperties.AiProfile copy = CardAiPromptSupport.withSystem(base, "deck-system");

                assertEquals("card-ai", copy.getName());
                assertEquals("qwen", copy.getModel());
                assertEquals(0.2, copy.getTemperature());
                assertEquals("deck-system", copy.getSystem());
                assertEquals("global-system", base.getSystem());
        }

        /**
         * 创建具备同步生成所需字段的已准备请求。
         */
        private CardAiGenerationCore.PreparedCardAiRequest preparedRequest() {
                return preparedRequest(false);
        }

        /**
         * 创建具备指定思考模式的已准备请求。
         */
        private CardAiGenerationCore.PreparedCardAiRequest preparedRequest(boolean thinkUsed) {
                return preparedRequest(thinkUsed, null);
        }

        /**
         * 创建具备指定思考模式和 userId 的已准备请求。
         */
        private CardAiGenerationCore.PreparedCardAiRequest preparedRequest(boolean thinkUsed, Long userId) {
                return preparedRequest(thinkUsed, userId,
                                activeResolver(AiSource.USER, "openai-main", "qwen", null));
        }

        private CardAiGenerationCore.PreparedCardAiRequest preparedRequest(
                        boolean thinkUsed, Long userId, EffectiveAiProfileResolver resolver) {
                AiProperties.AiProfile profile = profile("card-ai", "qwen", "system", 0.2);
                String fingerprint = userId == null
                                ? "fp-1"
                                : CardAiPromptSupport.buildFingerprint("prompt");
                return new CardAiGenerationCore.PreparedCardAiRequest(
                                CardAiPromptSupport.SIDE_A,
                                "prompt",
                                fingerprint,
                                thinkUsed,
                                profile,
                                null,
                                userId);
        }

        /**
         * 创建测试用 profile。
         */
        private AiProperties.AiProfile profile(String name, String model, String system, double temperature) {
                AiProperties.AiProfile profile = new AiProperties.AiProfile();
                profile.setName(name);
                profile.setModel(model);
                profile.setSystem(system);
                profile.setTemperature(temperature);
                return profile;
        }

        /**
         * 创建仅依赖本地配置的生成核心，避免测试访问数据库。
         */
        private CardAiGenerationCore coreWithProfile(AiProperties.AiProfile profile) {
                AiProperties properties = new AiProperties();
                properties.setProfiles(List.of(profile));
                properties.setFeatureProfiles(Map.of(CardAiGenerationCore.AI_PROFILE_FEATURE_KEY, profile.getName()));
                return new CardAiGenerationCore(null, properties, new RecordingAiChatGateway(), enabledGuard(),
                                activeResolver(AiSource.USER, "openai-main", profile.getModel(), null));
        }

        /**
         * 创建测试用 AI 属性。
         */
        private AiProperties aiProperties(boolean think) {
                return new AiProperties();
        }

        /**
         * 创建测试用词卡。
         */
        private Card card(String sideA, String sideB) {
                Card card = new Card();
                card.setSideA(sideA);
                card.setSideB(sideB);
                return card;
        }

        /**
         * 创建关闭 ai-card 的 guard，避免测试依赖数据库。
         */
        private static AiCardFeatureGuard disabledGuard() {
                return new StaticAiCardFeatureGuard(false, true);
        }

        /**
         * 创建开启 ai-card 的 guard，让测试走到实际 AI 调用。
         */
        private static AiCardFeatureGuard enabledGuard() {
                return new StaticAiCardFeatureGuard(true, true);
        }

        /**
         * 验证 active 模型变化不影响内容缓存指纹。
         */
        @Test
        void prepareIgnoresActiveModelInFingerprint() {
                Card card = new Card();
                card.setId(1L);
                card.setSideA("apple");
                card.setSideB("");
                AiProperties.AiProfile base = profile("ai_cache", "qwen3.5:9b", "system", 0.1);

                CardAiGenerationCore deepseekCore = new CardAiGenerationCore(
                                null, aiProperties(true), new RecordingAiChatGateway(), enabledGuard(),
                                activeResolver(AiSource.USER, "provider", "deepseek-v4-flash", null));
                CardAiGenerationCore qwenCore = new CardAiGenerationCore(
                                null, aiProperties(true), new RecordingAiChatGateway(), enabledGuard(),
                                activeResolver(AiSource.USER, "provider", "qwen3.5:9b", null));

                String fpDeepseek = deepseekCore.prepare(card, "A", null, 7L, base).fingerprint();
                String fpQwen = qwenCore.prepare(card, "A", null, 7L, base).fingerprint();

                assertEquals(fpQwen, fpDeepseek);
        }

        /**
         * 验证 userId 为 null 时 active identity 不可用，不回退全局模型。
         */
        @Test
        void prepareRejectsNullUserIdInsteadOfFallingBackToGlobalModel() {
                Card card = new Card();
                card.setId(1L);
                card.setSideA("apple");
                card.setSideB("");
                AiProperties.AiProfile base = profile("ai_cache", "qwen3.5:9b", "system", 0.1);

                UnifiedAiSelectionServiceImpl selections = mock(UnifiedAiSelectionServiceImpl.class);
                when(selections.requireActive(null)).thenThrow(new AppException(AiErrorCode.AI_NOT_CONFIGURED));
                CardAiGenerationCore core = new CardAiGenerationCore(
                                null, aiProperties(true), new RecordingAiChatGateway(), enabledGuard(),
                                new EffectiveAiProfileResolver(mock(UserAiConfigService.class), selections));

                AppException ex = assertThrows(AppException.class,
                                () -> core.prepare(card, "A", null, null, base));

                assertEquals(AiErrorCode.AI_NOT_CONFIGURED, ex.getErrorCode());
        }

        private EffectiveAiProfileResolver activeResolver(
                        AiSource source, String selectionKey, String model, String effort) {
                UnifiedAiSelectionServiceImpl selections = mock(UnifiedAiSelectionServiceImpl.class);
                when(selections.requireActive(org.mockito.ArgumentMatchers.anyLong()))
                                .thenReturn(new ActiveAiSelectionDto(
                                                source,
                                                source == AiSource.USER ? selectionKey : null,
                                                source == AiSource.PLATFORM ? selectionKey : null,
                                                "OPENAI_COMPAT",
                                                model,
                                                effort));
                return new EffectiveAiProfileResolver(mock(UserAiConfigService.class), selections);
        }

        /**
         * 记录 AI 是否被请求，验证关闭功能时页面不会触发生成。
         */
        private static class RecordingAiChatGateway implements AiGateway {

                private final ActiveAiSelectionDto activeSelection;
                private final RuntimeException selectionFailure;
                int chatCount;
                String lastPrompt;
                String lastProfileSystem;
                Long lastUserId;

                RecordingAiChatGateway() {
                        this(new ActiveAiSelectionDto(
                                        AiSource.USER, "openai-main", null, "OPENAI_COMPAT", "qwen", null));
                }

                RecordingAiChatGateway(ActiveAiSelectionDto activeSelection) {
                        this.activeSelection = activeSelection;
                        this.selectionFailure = null;
                }

                RecordingAiChatGateway(RuntimeException selectionFailure) {
                        this.activeSelection = null;
                        this.selectionFailure = selectionFailure;
                }

                @Override
                public String chat(
                                String prompt,
                                AiProperties.AiProfile profile,
                                Long userId) {
                        chatCount++;
                        lastPrompt = prompt;
                        lastProfileSystem = profile == null ? null : profile.getSystem();
                        lastUserId = userId;
                        return "content";
                }

                @Override
                public String chat(
                                String prompt,
                                AiProperties.AiProfile profile,
                                Long userId,
                                AiDispatchValidator validator) {
                        if (selectionFailure != null) {
                                throw selectionFailure;
                        }
                        AiProperties.AiProfile effectiveProfile = new EffectiveAiProfileResolver(null)
                                        .applyModel(profile, activeSelection.model());
                        validator.validate(activeSelection, effectiveProfile);
                        return chat(prompt, effectiveProfile, userId);
                }
        }
}
