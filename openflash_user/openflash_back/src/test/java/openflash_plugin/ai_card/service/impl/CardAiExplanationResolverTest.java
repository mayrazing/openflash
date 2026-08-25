package openflash_plugin.ai_card.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import openflash_core.common.AiErrorCode;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_plugin.ai_card.common.AiCardErrorCode;
import openflash_plugin.ai_card.dto.AiCacheStatusResponse;
import openflash_core.entity.Card;
import openflash_plugin.ai_card.entity.CardAiCache;
import openflash_plugin.ai_card.entity.DeckAiSettings;
import openflash_plugin.ai_card.service.CardAiCacheService;
import openflash_core.service.CardService;
import openflash_core.service.CurrentUserService;
import openflash_plugin.ai_card.service.DeckAiSettingsService;

class CardAiExplanationResolverTest {

    /** 验证缓存命中前会先确认用户能打开这张卡。 */
    @Test
    void resolveOrQueueChecksCardOwnershipBeforeReturningCacheHit() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        CardAiGenerationCore.PreparedCardAiRequest prepared = preparedRequest("fp-hit");
        Card card = prepared.card();
        CardAiCache cache = new CardAiCache();
        cache.setContent("cached markdown");
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(enabledSettings(null, null));
        when(generationCore.prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq((String) null), eq(1L), any()))
                .thenReturn(prepared);
        when(cacheService.findUsableCacheAndTouchOnServe(1L, "fp-hit")).thenReturn(cache);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate());

        AiCacheStatusResponse response = resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A);

        assertEquals("hit", response.getStatus());
        assertEquals("cached markdown", response.getContent());
        InOrder inOrder = inOrder(cardService, deckAiSettingsService, generationCore, cacheService);
        inOrder.verify(cardService).getBasicCard(10L);
        inOrder.verify(deckAiSettingsService).getByDeckId(20L);
        inOrder.verify(generationCore).prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq((String) null), eq(1L), any());
        inOrder.verify(cacheService).findUsableCacheAndTouchOnServe(1L, "fp-hit");
        verify(taskProducer, never()).enqueueWithUserContext(any(), any());
    }

    /** 验证缓存未命中时会在卡包开关通过后排队。 */
    @Test
    void resolveOrQueueChecksCardOwnershipBeforeEnqueueingCacheMiss() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CardSideCompletionTaskProducer sideCompletionTaskProducer = mock(CardSideCompletionTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        CardAiGenerationCore.PreparedCardAiRequest prepared = preparedRequest("fp-miss");
        Card card = prepared.card();
        card.setSideB("");
        when(currentUserService.getCurrentUserId()).thenReturn(99L);
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(enabledSettings(null, null));
        when(generationCore.prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq((String) null), eq(99L), any()))
                .thenReturn(prepared);
        when(cacheService.findUsableCacheAndTouchOnServe(99L, "fp-miss")).thenReturn(null);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate(), sideCompletionTaskProducer);

        AiCacheStatusResponse response = resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A);

        assertEquals("queued", response.getStatus());
        assertNull(response.getContent());
        InOrder inOrder = inOrder(cardService, deckAiSettingsService, sideCompletionTaskProducer, generationCore,
                cacheService, taskProducer);
        inOrder.verify(cardService).getBasicCard(10L);
        inOrder.verify(deckAiSettingsService).getByDeckId(20L);
        inOrder.verify(sideCompletionTaskProducer).triggerCardAfterCommit(10L, 99L);
        inOrder.verify(generationCore).prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq((String) null), eq(99L), any());
        inOrder.verify(cacheService).findUsableCacheAndTouchOnServe(99L, "fp-miss");
        inOrder.verify(taskProducer).enqueueWithUserContext(prepared, 99L);
    }

    /** 验证点到空面时仍先投递另一面补全任务，再返回空内容错误。 */
    @Test
    void resolveOrQueueTriggersSideCompletionBeforeBlankSideError() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CardSideCompletionTaskProducer sideCompletionTaskProducer = mock(CardSideCompletionTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        Card card = preparedRequest("fp-blank-side").card();
        card.setSideA("");
        card.setSideB("苹果");
        when(currentUserService.getCurrentUserId()).thenReturn(99L);
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(enabledSettings(null, null));
        doThrow(new AppException(AiCardErrorCode.AI_CARD_SIDE_BLANK))
                .when(generationCore).prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq((String) null),
                        eq(99L), any());
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate(), sideCompletionTaskProducer);

        AppException ex = assertThrows(AppException.class,
                () -> resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A));

        assertEquals(AiCardErrorCode.AI_CARD_SIDE_BLANK, ex.getErrorCode());
        InOrder inOrder = inOrder(sideCompletionTaskProducer, generationCore);
        inOrder.verify(sideCompletionTaskProducer).triggerCardAfterCommit(10L, 99L);
        inOrder.verify(generationCore).prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq((String) null),
                eq(99L), any());
        verify(cacheService, never()).findUsableCacheAndTouchOnServe(any(), any());
        verify(taskProducer, never()).enqueueWithUserContext(any(), any());
    }

    /** 验证重新生成会跳过缓存读取，直接投递强制覆盖任务。 */
    @Test
    void regenerateSkipsCacheLookupAndQueuesForcedTask() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        CardAiGenerationCore.PreparedCardAiRequest prepared = preparedRequest("fp-regenerate");
        Card card = prepared.card();
        when(currentUserService.getCurrentUserId()).thenReturn(99L);
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(enabledSettings(null, null));
        when(generationCore.prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq((String) null), eq(99L), any()))
                .thenReturn(prepared);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate());

        AiCacheStatusResponse response = resolver.regenerate(10L, CardAiPromptSupport.SIDE_A);

        assertEquals("queued", response.getStatus());
        verify(cacheService, never()).findUsableCacheAndTouchOnServe(any(), any());
        verify(taskProducer).enqueueRegenerateWithUserContext(prepared, 99L);
    }

    /** 验证无权打开卡片时不会准备 AI 请求。 */
    @Test
    void resolveOrQueueStopsBeforePrepareWhenCardOwnershipCheckFails() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        RuntimeException denied = new RuntimeException("无权限");
        when(cardService.getBasicCard(10L)).thenThrow(denied);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate());

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A));

        assertEquals(denied, ex);
        verify(cardService).getBasicCard(10L);
        verify(deckAiSettingsService, never()).getByDeckId(any());
        verify(generationCore, never()).prepare(any(Card.class), any(), any(), any(), any());
        verify(cacheService, never()).findUsableCacheAndTouchOnServe(any(), any());
        verify(taskProducer, never()).enqueueWithUserContext(any(), any());
    }

    /** 验证全局功能关闭时不会读取卡片。 */
    @Test
    void resolveOrQueueStopsBeforeLoadingCardWhenFeatureDisabled() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        doThrow(new AppException(ErrorCode.FEATURE_DISABLED))
                .when(generationCore).ensureCardAiMarkdownEnabled();
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate());

        AppException ex = assertThrows(
                AppException.class,
                () -> resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A));

        assertEquals(ErrorCode.FEATURE_DISABLED, ex.getErrorCode());
        verify(cardService, never()).getBasicCard(10L);
        verify(deckAiSettingsService, never()).getByDeckId(any());
        verify(cacheService, never()).findUsableCacheAndTouchOnServe(any(), any());
        verify(taskProducer, never()).enqueueWithUserContext(any(), any());
    }

    /** 验证卡包 AI 解释关闭时不会准备、查缓存或排队。 */
    @Test
    void resolveOrQueueStopsBeforePrepareWhenDeckExplanationDisabled() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        Card card = preparedRequest("fp-disabled").card();
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(disabledSettings());
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate());

        AiCacheStatusResponse response = resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A);

        assertEquals("disabled", response.getStatus());
        assertEquals(AiCardErrorCode.AI_EXPLANATION_DISABLED.value(), response.getErrorCode());
        verify(cardService).getBasicCard(10L);
        verify(deckAiSettingsService).getByDeckId(20L);
        verify(generationCore, never()).prepare(any(Card.class), any(), any(), any(), any());
        verify(cacheService, never()).findUsableCacheAndTouchOnServe(any(), any());
        verify(taskProducer, never()).enqueueWithUserContext(any(), any());
    }

    /** 验证 A 面使用卡包 A 面提示词准备 AI 请求。 */
    @Test
    void resolveOrQueueUsesPromptAForSideA() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        CardAiGenerationCore.PreparedCardAiRequest prepared = preparedRequest("fp-prompt-a");
        Card card = prepared.card();
        when(currentUserService.getCurrentUserId()).thenReturn(99L);
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(enabledSettings("prompt A", "prompt B"));
        when(generationCore.prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq("prompt A"), eq(99L), any()))
                .thenReturn(prepared);
        when(cacheService.findUsableCacheAndTouchOnServe(any(), eq("fp-prompt-a"))).thenReturn(null);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate());

        AiCacheStatusResponse response = resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A);

        assertEquals("queued", response.getStatus());
        verify(generationCore).prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq("prompt A"), eq(99L), any());
        verify(taskProducer).enqueueWithUserContext(prepared, 99L);
    }

    /** 验证 B 面使用卡包 B 面提示词准备 AI 请求。 */
    @Test
    void resolveOrQueueUsesPromptBForSideB() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        CardAiGenerationCore.PreparedCardAiRequest prepared = preparedRequest("fp-prompt-b");
        Card card = prepared.card();
        when(currentUserService.getCurrentUserId()).thenReturn(99L);
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(enabledSettings("prompt A", "prompt B"));
        when(generationCore.prepare(eq(card), eq(CardAiPromptSupport.SIDE_B), eq("prompt B"), eq(99L), any()))
                .thenReturn(prepared);
        when(cacheService.findUsableCacheAndTouchOnServe(any(), eq("fp-prompt-b"))).thenReturn(null);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate());

        AiCacheStatusResponse response = resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_B);

        assertEquals("queued", response.getStatus());
        verify(generationCore).prepare(eq(card), eq(CardAiPromptSupport.SIDE_B), eq("prompt B"), eq(99L), any());
        verify(taskProducer).enqueueWithUserContext(prepared, 99L);
    }

    /** 验证 active selection 失效时不查缓存、不入队，直接抛 AI_NOT_CONFIGURED。 */
    @Test
    void resolveOrQueueThrowsAiNotConfiguredWhenUserHasNoAiConfig() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        Card card = preparedRequest("fp-no-config").card();
        AppException revoked = new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(enabledSettings(null, null));
        when(generationCore.prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq((String) null), eq(1L), any()))
                .thenThrow(revoked);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate());

        AppException ex = assertThrows(AppException.class,
                () -> resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A));

        assertEquals(AiErrorCode.AI_NOT_CONFIGURED, ex.getErrorCode());
        verify(cacheService, never()).findUsableCacheAndTouchOnServe(any(), any());
        verify(taskProducer, never()).enqueueWithUserContext(any(), any());
    }

    /** 验证用户已配置 AI 且缓存未命中时正常入队。 */
    @Test
    void resolveOrQueueEnqueuesWhenUserAiConfigured() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        CardAiGenerationCore.PreparedCardAiRequest prepared = preparedRequest("fp-configured");
        Card card = prepared.card();
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(enabledSettings(null, null));
        when(generationCore.prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq((String) null), eq(1L), any()))
                .thenReturn(prepared);
        when(cacheService.findUsableCacheAndTouchOnServe(any(), eq("fp-configured"))).thenReturn(null);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate());

        AiCacheStatusResponse response = resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A);

        assertEquals("queued", response.getStatus());
        verify(taskProducer).enqueueWithUserContext(prepared, 1L);
    }

    /** 验证 active identity 解析成功后可按其 fingerprint 返回缓存。 */
    @Test
    void resolveOrQueueReturnsCacheHitWithoutCheckingAiConfig() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        CardAiGenerationCore.PreparedCardAiRequest prepared = preparedRequest("fp-cache-hit-no-config");
        Card card = prepared.card();
        CardAiCache cache = new CardAiCache();
        cache.setContent("cached");
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(enabledSettings(null, null));
        when(generationCore.prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq((String) null), eq(1L), any()))
                .thenReturn(prepared);
        when(cacheService.findUsableCacheAndTouchOnServe(any(), eq("fp-cache-hit-no-config"))).thenReturn(cache);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate());

        AiCacheStatusResponse response = resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A);

        assertEquals("hit", response.getStatus());
        assertEquals("cached", response.getContent());
        verify(taskProducer, never()).enqueueWithUserContext(any(), any());
    }

    @Test
    void resolveOrQueueRejectsRevokedSelectionBeforeCacheLookup() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        Card card = preparedRequest("old-fingerprint").card();
        AppException revoked = new AppException(AiErrorCode.AI_NOT_CONFIGURED);
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(enabledSettings(null, null));
        when(generationCore.prepare(eq(card), eq(CardAiPromptSupport.SIDE_A), eq((String) null), eq(1L), any()))
                .thenThrow(revoked);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate());

        AppException thrown = assertThrows(AppException.class,
                () -> resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A));

        assertEquals(revoked, thrown);
        verify(cacheService, never()).findUsableCacheAndTouchOnServe(any(), any());
        verify(taskProducer, never()).enqueueWithUserContext(any(), any());
    }

    /** 验证卡包未安装 ai-card 插件时直接拒绝，不读设置、不准备、不查缓存、不排队。 */
    @Test
    void resolveOrQueueStopsWhenAiCardNotInstalledOnDeck() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        AiCardInstallGate installGate = mock(AiCardInstallGate.class);
        Card card = preparedRequest("fp-not-installed").card();
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(installGate.isInstalledOnDeck(20L)).thenReturn(false);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer,
                mock(CurrentUserService.class), deckAiSettingsService,
                installGate);

        AppException ex = assertThrows(AppException.class,
                () -> resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A));

        assertEquals(ErrorCode.FEATURE_DISABLED, ex.getErrorCode());
        verify(deckAiSettingsService, never()).getByDeckId(any());
        verify(generationCore, never()).prepare(any(Card.class), any(), any(), any(), any());
        verify(cacheService, never()).findUsableCacheAndTouchOnServe(any(), any());
        verify(taskProducer, never()).enqueueWithUserContext(any(), any());
    }

    /** 创建已安装 ai-card 插件的门控替身，让流程走到后续检查。 */
    private static AiCardInstallGate installedGate() {
        AiCardInstallGate gate = mock(AiCardInstallGate.class);
        when(gate.isInstalledOnDeck(20L)).thenReturn(true);
        return gate;
    }

    /** 创建测试用的 AI 生成准备结果。 */
    private static CardAiGenerationCore.PreparedCardAiRequest preparedRequest(String fingerprint) {
        Card card = new Card();
        card.setId(10L);
        card.setDeckId(20L);
        card.setSideA("apple");
        return new CardAiGenerationCore.PreparedCardAiRequest(
                CardAiPromptSupport.SIDE_A,
                "prompt",
                fingerprint,
                false,
                null,
                card,
                null);
    }

    /** 创建开启状态的卡包 AI 设置，并写入 A/B 面提示词。 */
    private static DeckAiSettings enabledSettings(String promptA, String promptB) {
        DeckAiSettings settings = new DeckAiSettings();
        settings.setAiExplanationEnabledA(true);
        settings.setAiExplanationEnabledB(true);
        settings.setAiExplanationPromptA(promptA);
        settings.setAiExplanationPromptB(promptB);
        return settings;
    }

    /** 创建 A/B 面均关闭的卡包 AI 设置。 */
    private static DeckAiSettings disabledSettings() {
        DeckAiSettings settings = new DeckAiSettings();
        settings.setAiExplanationEnabledA(false);
        settings.setAiExplanationEnabledB(false);
        return settings;
    }

    /** 验证 A 面开关关闭时，请求 A 面解析返回专用关闭状态。 */
    @Test
    void resolveOrQueueStopsWhenSideAExplanationDisabled() {
        CardService cardService = mock(CardService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        Card card = preparedRequest("fp-sideA-disabled").card();
        card.setSideB("苹果");
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(sideADisabledSettings());
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, mock(CardAiCacheService.class), generationCore, taskProducer,
                mock(CurrentUserService.class), deckAiSettingsService, installedGate());

        AiCacheStatusResponse response = resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_A);

        assertEquals("disabled", response.getStatus());
        assertEquals(AiCardErrorCode.AI_EXPLANATION_DISABLED.value(), response.getErrorCode());
        assertEquals(false, response.getSideCompletionSetupRequired());
        verify(generationCore, never()).prepare(any(Card.class), any(), any(), any(), any());
    }

    /** 验证 B 面开关关闭且另一面补全不可用时，返回合并提示所需状态。 */
    @Test
    void resolveOrQueueStopsWhenSideBExplanationDisabled() {
        CardService cardService = mock(CardService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        Card card = preparedRequest("fp-sideB-disabled").card();
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(sideBDisabledSettings());
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, mock(CardAiCacheService.class), generationCore, taskProducer,
                mock(CurrentUserService.class), deckAiSettingsService, installedGate());

        card.setSideB("");
        DeckAiSettings settings = sideBDisabledSettings();
        settings.setAiCompletionEnabled(false);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(settings);

        AiCacheStatusResponse response = resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_B);

        assertEquals("disabled", response.getStatus());
        assertEquals(AiCardErrorCode.AI_EXPLANATION_DISABLED.value(), response.getErrorCode());
        assertEquals(true, response.getSideCompletionSetupRequired());
        verify(generationCore, never()).prepare(any(Card.class), any(), any(), any(), any());
    }

    /** 验证 A 面关闭时，B 面请求仍能正常排队。 */
    @Test
    void resolveOrQueueAllowsSideBWhenOnlySideADisabled() {
        CardService cardService = mock(CardService.class);
        CardAiCacheService cacheService = mock(CardAiCacheService.class);
        CardAiGenerationCore generationCore = mock(CardAiGenerationCore.class);
        CardAiCacheTaskProducer taskProducer = mock(CardAiCacheTaskProducer.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckAiSettingsService deckAiSettingsService = mock(DeckAiSettingsService.class);
        CardAiGenerationCore.PreparedCardAiRequest prepared = preparedRequest("fp-sideA-off-sideB-ok");
        Card card = prepared.card();
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(cardService.getBasicCard(10L)).thenReturn(card);
        when(deckAiSettingsService.getByDeckId(20L)).thenReturn(sideADisabledSettings());
        when(generationCore.prepare(eq(card), eq(CardAiPromptSupport.SIDE_B), eq((String) null), eq(1L), any()))
                .thenReturn(prepared);
        when(cacheService.findUsableCacheAndTouchOnServe(1L, "fp-sideA-off-sideB-ok")).thenReturn(null);
        CardAiExplanationResolver resolver = new CardAiExplanationResolver(
                cardService, cacheService, generationCore, taskProducer, currentUserService, deckAiSettingsService,
                installedGate());

        AiCacheStatusResponse response = resolver.resolveOrQueue(10L, CardAiPromptSupport.SIDE_B);

        assertEquals("queued", response.getStatus());
        verify(taskProducer).enqueueWithUserContext(prepared, 1L);
    }

    /** A 面关闭、B 面开启的设置。 */
    private static DeckAiSettings sideADisabledSettings() {
        DeckAiSettings settings = new DeckAiSettings();
        settings.setAiExplanationEnabledA(false);
        settings.setAiExplanationEnabledB(true);
        return settings;
    }

    /** B 面关闭、A 面开启的设置。 */
    private static DeckAiSettings sideBDisabledSettings() {
        DeckAiSettings settings = new DeckAiSettings();
        settings.setAiExplanationEnabledA(true);
        settings.setAiExplanationEnabledB(false);
        return settings;
    }
}
