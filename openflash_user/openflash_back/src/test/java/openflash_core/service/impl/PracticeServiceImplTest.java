package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import openflash_core.common.AppException;
import openflash_core.entity.Card;
import openflash_core.entity.CardDirectionProgresses;
import openflash_core.entity.CardFsrs;
import openflash_core.entity.CardProgress;
import openflash_core.entity.DeckSettings;
import openflash_core.entity.DirectionProgressSnapshot;
import openflash_core.entity.PendingPracticeSummary;
import openflash_core.entity.PracticeItem;
import openflash_core.entity.PracticeQueue;
import openflash_core.entity.ProgressUpdateResult;
import openflash_core.dto.ReviewRequest;
import openflash_core.mapper.CardProgressMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.service.CardService;
import openflash_core.service.CurrentUserService;
import openflash_core.service.FsrsService;
import openflash_core.service.TypeRegistryService;

class PracticeServiceImplTest {

    @Test
    void reviewCardUsesDeckSettingRetentionInsteadOfRequestValue() {
        Long userId = 1L;
        Long cardId = 10L;
        Long deckId = 20L;
        Card existing = card(
                cardId,
                "learning",
                direction("review", LocalDate.now().minusDays(1), LocalDate.now()),
                direction("new", null, LocalDate.now()));
        existing.setDeckId(deckId);
        Card updatedCard = card(
                cardId,
                "learning",
                direction("review", LocalDate.now(), LocalDate.now().plusDays(5)),
                direction("new", null, LocalDate.now()));
        updatedCard.setDeckId(deckId);

        CardService cardService = mock(CardService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        FsrsService fsrsService = mock(FsrsService.class);
        CardProgressStore progressStore = mock(CardProgressStore.class);
        DeckSettingsMapper deckSettingsMapper = mock(DeckSettingsMapper.class);
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(cardService.getCard(cardId)).thenReturn(existing, updatedCard);
        CardProgress current = progress(cardId, userId, "A_TO_B", "review", 3, 20.0);
        CardProgress scheduled = progress(cardId, userId, "A_TO_B", "review", 4, 25.0);
        when(progressStore.getOrCreateProgress(userId, cardId, "A_TO_B")).thenReturn(current);
        when(fsrsService.schedule(current, 3, 0.94)).thenReturn(scheduled);
        DeckSettings settings = new DeckSettings();
        settings.setDeckId(deckId);
        settings.setTargetRetention(new BigDecimal("0.9400"));
        when(deckSettingsMapper.findByDeckId(deckId)).thenReturn(settings);
        PracticeServiceImpl service = new PracticeServiceImpl(
                cardService,
                currentUserService,
                cardProgressMapper,
                fsrsService,
                progressStore,
                typeRegistryService(List.of()),
                scheduler(40, 70, 3, 120, 40),
                deckSettingsMapper);

        ReviewRequest request = new ReviewRequest();
        request.setDirection("a2b");
        request.setRating(3);
        request.setTargetRetention(0.71);

        ProgressUpdateResult result = service.reviewCard(cardId, request);

        assertSame(updatedCard, result.getCard());
        verify(deckSettingsMapper).findByDeckId(deckId);
        verify(fsrsService).schedule(current, 3, 0.94);
    }

    @Test
    void reviewCardUsesDefaultRetentionWhenDeckSettingIsMissing() {
        Long userId = 1L;
        Long cardId = 11L;
        Long deckId = 21L;
        Card existing = card(
                cardId,
                "learning",
                direction("review", LocalDate.now().minusDays(1), LocalDate.now()),
                direction("new", null, LocalDate.now()));
        existing.setDeckId(deckId);
        Card updatedCard = card(
                cardId,
                "learning",
                direction("review", LocalDate.now(), LocalDate.now().plusDays(5)),
                direction("new", null, LocalDate.now()));
        updatedCard.setDeckId(deckId);

        CardService cardService = mock(CardService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        FsrsService fsrsService = mock(FsrsService.class);
        CardProgressStore progressStore = mock(CardProgressStore.class);
        DeckSettingsMapper deckSettingsMapper = mock(DeckSettingsMapper.class);
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(cardService.getCard(cardId)).thenReturn(existing, updatedCard);
        CardProgress current = progress(cardId, userId, "A_TO_B", "review", 3, 20.0);
        CardProgress scheduled = progress(cardId, userId, "A_TO_B", "review", 4, 25.0);
        when(progressStore.getOrCreateProgress(userId, cardId, "A_TO_B")).thenReturn(current);
        when(fsrsService.schedule(current, 3, 0.9)).thenReturn(scheduled);
        when(deckSettingsMapper.findByDeckId(deckId)).thenReturn(null);
        PracticeServiceImpl service = new PracticeServiceImpl(
                cardService,
                currentUserService,
                cardProgressMapper,
                fsrsService,
                progressStore,
                typeRegistryService(List.of()),
                scheduler(40, 70, 3, 120, 40),
                deckSettingsMapper);

        ReviewRequest request = new ReviewRequest();
        request.setDirection("a2b");
        request.setRating(3);

        ProgressUpdateResult result = service.reviewCard(cardId, request);

        assertSame(updatedCard, result.getCard());
        verify(deckSettingsMapper).findByDeckId(deckId);
        verify(fsrsService).schedule(current, 3, 0.9);
    }

    @Test
    void includesUnlearnedDirectionAfterOtherDirectionWasStarted() {
        LocalDate today = LocalDate.now();
        Card partialCard = card(
                10L,
                "learning",
                direction("review", today.minusDays(1), today.plusDays(1)),
                direction("new", null, today));
        CardService cardService = mock(CardService.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(partialCard));
        PracticeServiceImpl service = service(cardService, scheduler(40, 70, 3, 120, 40));

        PracticeQueue b2aQueue = service.buildDailyQueue(1L, 10, "b2a");
        PracticeQueue a2bQueue = service.buildDailyQueue(1L, 10, "a2b");
        PracticeQueue randomQueue = service.buildDailyQueue(1L, 10, "random");

        assertEquals(List.of("b2a"), directions(b2aQueue));
        assertEquals(1, b2aQueue.getReviewCardCount());
        assertEquals(0, b2aQueue.getNewCardCount());
        assertEquals(List.of(), directions(a2bQueue));
        assertEquals(List.of("b2a"), directions(randomQueue));
    }

    @Test
    void stillLimitsFullyNewCardsByCardCount() {
        Card first = card(1L, "new", direction("new", null, LocalDate.now()), direction("new", null, LocalDate.now()));
        Card second = card(2L, "new", direction("new", null, LocalDate.now()), direction("new", null, LocalDate.now()));
        CardService cardService = mock(CardService.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(first, second));
        PracticeServiceImpl service = service(cardService, scheduler(40, 70, 3, 120, 40));

        PracticeQueue queue = service.buildDailyQueue(1L, 1, "random");

        assertEquals(2, queue.getItems().size());
        assertEquals(2, queue.getNewItemCount());
        assertEquals(1, queue.getNewCardCount());
    }

    /**
     * 验证数据库只启用 a2b 时 b2a 练习入口会被拒绝。
     */
    @Test
    void rejectsB2aWhenDatabaseOnlyEnablesA2b() {
        PracticeServiceImpl service = new PracticeServiceImpl(
                null,
                null,
                null,
                null,
                null,
                typeRegistryService(List.of("a2b")),
                scheduler(40, 70, 3, 120, 40));

        assertThrows(AppException.class, () -> service.buildDailyQueue(1L, 10, "b2a"));
    }

    /**
     * 验证数据库只启用 a2b 时不传模式不能绕过 random 校验。
     */
    @Test
    void rejectsBlankModeWhenDatabaseOnlyEnablesA2b() {
        CardService cardService = mock(CardService.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of());
        PracticeServiceImpl service = new PracticeServiceImpl(
                cardService,
                null,
                null,
                null,
                null,
                typeRegistryService(List.of("a2b")),
                scheduler(40, 70, 3, 120, 40));

        assertThrows(AppException.class, () -> service.buildDailyQueue(1L, 10, " "));
    }

    /**
     * 验证数据库未配置练习模式时仍保留旧的 b2a 入口。
     */
    @Test
    void keepsB2aAvailableWhenDatabaseModesAreEmpty() {
        Card card = card(1L, "new", direction("new", null, LocalDate.now()), direction("new", null, LocalDate.now()));
        CardService cardService = mock(CardService.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(card));
        PracticeServiceImpl service = service(cardService, scheduler(40, 70, 3, 120, 40));

        PracticeQueue queue = service.buildDailyQueue(1L, 1, "b2a");

        assertEquals(List.of("b2a"), directions(queue));
    }

    /**
     * 验证数据库未配置练习模式时不传模式仍进入 random 练习。
     */
    @Test
    void keepsRandomDefaultWhenDatabaseModesAreEmpty() {
        Card card = card(1L, "new", direction("new", null, LocalDate.now()), direction("new", null, LocalDate.now()));
        CardService cardService = mock(CardService.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(card));
        PracticeServiceImpl service = service(cardService, scheduler(40, 70, 3, 120, 40));

        PracticeQueue queue = service.buildDailyQueue(1L, 1, null);

        assertEquals(List.of("a2b", "b2a"), directions(queue));
    }

    @Test
    void randomModeUsesLoadSmoothedReviewCards() {
        LocalDate today = LocalDate.now();
        Card highRisk = card(
                1L,
                "learning",
                direction("learning", today.minusDays(1), today, 1, 5.0),
                direction("review", today.minusDays(1), today.plusDays(10), 3, 50.0));
        Card lowRisk = card(
                2L,
                "learning",
                direction("review", today.minusDays(1), today, 3, 80.0),
                direction("review", today.minusDays(1), today.plusDays(10), 3, 50.0));
        CardService cardService = mock(CardService.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(lowRisk, highRisk));
        PracticeServiceImpl service = service(cardService, scheduler(1, 70, 3, 120, 40));

        PracticeQueue queue = service.buildDailyQueue(1L, 10, "random");

        assertEquals(List.of(1L), queue.getItems().stream().map(PracticeItem::getCardId).distinct().toList());
        assertEquals(1, queue.getReviewBacklogCount());
        assertEquals(false, queue.getNewCardsPaused());
        assertEquals(1, queue.getTargetReviewItemCount());
        assertEquals(70, queue.getMaxReviewItemCount());
    }

    /**
     * 验证真实练习入口会读取今天已完成题数，达到目标后不再展示复习题。
     */
    @Test
    void buildDailyQueueUsesMapperReviewedCountForToday() {
        Long userId = 8L;
        LocalDate today = LocalDate.now();
        Card dueCard = card(
                1L,
                "learning",
                direction("review", today.minusDays(1), today, 3, 80.0),
                direction("review", today.minusDays(1), today.plusDays(10), 3, 80.0));
        Card newCard = card(
                2L,
                "new",
                direction("new", null, today),
                direction("new", null, today));
        CardService cardService = mock(CardService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(dueCard, newCard));
        PracticeServiceImpl service = serviceWithReviewedCount(cardService, userId, cardProgressMapper, 40);

        PracticeQueue queue = service.buildDailyQueue(1L, 10, "random");

        assertEquals(0, queue.getReviewItemCount());
        assertEquals(0, queue.getNewItemCount());
        assertEquals(0, queue.getNewCardCount());
        assertEquals(0, queue.getItems().size());
        verify(cardProgressMapper, times(1)).countReviewedDirectionsToday(eq(userId), any(LocalDate.class),
                any(Long.class));
    }

    /**
     * 验证今日额度用完后，另一面首次练习不会绕过真实练习入口的每日门控。
     */
    @Test
    void buildDailyQueueHidesPendingNewDirectionWhenDailyTargetUsed() {
        Long userId = 8L;
        LocalDate today = LocalDate.now();
        Card partialCard = card(
                10L,
                "learning",
                direction("review", today.minusDays(1), today.plusDays(1)),
                direction("new", null, today));
        Card newCard = card(
                11L,
                "new",
                direction("new", null, today),
                direction("new", null, today));
        CardService cardService = mock(CardService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(partialCard, newCard));
        PracticeServiceImpl service = serviceWithReviewedCount(cardService, userId, cardProgressMapper, 40);

        PracticeQueue queue = service.buildDailyQueue(1L, 10, "random");

        assertEquals(0, queue.getReviewItemCount());
        assertEquals(0, queue.getItems().size());
        verify(cardProgressMapper, times(1)).countReviewedDirectionsToday(eq(userId), any(LocalDate.class),
                any(Long.class));
    }

    /**
     * 验证练习入口返回给页面的积压数按卡片去重，不把同一卡两面算成两张卡。
     */
    @Test
    void buildDailyQueueReportsBacklogAsCardCount() {
        Long userId = 8L;
        LocalDate today = LocalDate.now();
        Card dualDueCard = card(
                10L,
                "learning",
                direction("review", today.minusDays(2), today, 3, 80.0),
                direction("review", today.minusDays(2), today, 3, 80.0));
        CardService cardService = mock(CardService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(dualDueCard));
        PracticeServiceImpl service = serviceWithReviewedCount(cardService, userId, cardProgressMapper, 70);

        PracticeQueue queue = service.buildDailyQueue(1L, 10, "random");

        assertEquals(0, queue.getItems().size());
        assertEquals(1, queue.getReviewBacklogCount());
        verify(cardProgressMapper, times(1)).countReviewedDirectionsToday(eq(userId), any(LocalDate.class),
                any(Long.class));
    }

    /**
     * 验证今日额度用完后，空模式摘要也不会统计另一面首次练习。
     */
    @Test
    void blankModeSummaryHidesPendingNewDirectionWhenDailyTargetUsed() {
        Long userId = 8L;
        LocalDate today = LocalDate.now();
        Card partialCard = card(
                10L,
                "learning",
                direction("review", today.minusDays(1), today.plusDays(1)),
                direction("new", null, today));
        Card newCard = card(
                11L,
                "new",
                direction("new", null, today),
                direction("new", null, today));
        CardService cardService = mock(CardService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(partialCard, newCard));
        PracticeServiceImpl service = serviceWithReviewedCount(cardService, userId, cardProgressMapper, 40);

        PendingPracticeSummary summary = service.getPendingPracticeSummary(1L, 10, null);

        assertEquals(0, summary.getPendingTotal());
        assertEquals(0, summary.getPendingReview());
        assertEquals(0, summary.getPendingNew());
        verify(cardProgressMapper, times(1)).countReviewedDirectionsToday(eq(userId), any(LocalDate.class),
                any(Long.class));
    }

    @Test
    void includesCardReviewedTodayWhenNextReviewDateStillToday() {
        LocalDate today = LocalDate.now();
        Card dueAgainToday = card(
                1L,
                "learning",
                direction("review", today, today, 1, 5.0),
                direction("review", today.minusDays(1), today.plusDays(10), 3, 80.0));
        CardService cardService = mock(CardService.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(dueAgainToday));
        PracticeServiceImpl service = service(cardService, scheduler(40, 70, 3, 120, 40));

        PracticeQueue queue = service.buildDailyQueue(1L, 10, "a2b");

        assertEquals(List.of(1L), queue.getItems().stream().map(PracticeItem::getCardId).toList());
        assertEquals(List.of("a2b"), directions(queue));
    }

    @Test
    void randomModeCountsNewCardAsTwoDirectionsWhenApplyingDailyLimit() {
        LocalDate today = LocalDate.now();
        List<Card> reviewCards = LongStream.rangeClosed(1, 69)
                .mapToObj(id -> card(
                        id,
                        "learning",
                        direction("review", today.minusDays(1), today, 3, 80.0),
                        direction("review", today.minusDays(1), today.plusDays(10), 3, 80.0)))
                .toList();
        Card newCard = card(100L, "new", direction("new", null, today), direction("new", null, today));
        CardService cardService = mock(CardService.class);
        when(cardService.listCards(1L, null)).thenReturn(
                java.util.stream.Stream.concat(reviewCards.stream(), java.util.stream.Stream.of(newCard)).toList());
        PracticeServiceImpl service = service(cardService, scheduler(69, 70, 3, 120, 40));

        PracticeQueue queue = service.buildDailyQueue(1L, 10, "random");

        assertEquals(69, queue.getItems().size());
        assertEquals(0, queue.getNewItemCount());
        assertEquals(0, queue.getNewCardCount());
        assertEquals(69, queue.getReviewItemCount());
    }

    @Test
    void a2bModeSchedulesOnlyA2bDirectionsBeforeApplyingLimit() {
        LocalDate today = LocalDate.now();
        Card b2aHighRisk = card(
                1L,
                "learning",
                direction("review", today.minusDays(1), today.plusDays(10), 3, 80.0),
                direction("learning", today.minusDays(1), today, 1, 5.0));
        Card a2bLowRisk = card(
                2L,
                "learning",
                direction("review", today.minusDays(1), today, 3, 80.0),
                direction("review", today.minusDays(1), today.plusDays(10), 3, 80.0));
        CardService cardService = mock(CardService.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(b2aHighRisk, a2bLowRisk));
        PracticeServiceImpl service = service(cardService, scheduler(1, 1, 3, 120, 40));

        PracticeQueue queue = service.buildDailyQueue(1L, 10, "a2b");

        assertEquals(List.of(2L), queue.getItems().stream().map(PracticeItem::getCardId).toList());
        assertEquals(List.of("a2b"), directions(queue));
        assertEquals(0, queue.getReviewBacklogCount());
    }

    @Test
    void blankModeSummaryIncludesPendingNewDirectionLikeRandomQueue() {
        LocalDate today = LocalDate.now();
        Card partialCard = card(
                10L,
                "learning",
                direction("review", today.minusDays(1), today.plusDays(1)),
                direction("new", null, today));
        CardService cardService = mock(CardService.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(partialCard));
        PracticeServiceImpl service = service(cardService, scheduler(40, 70, 3, 120, 40));

        PendingPracticeSummary summary = service.getPendingPracticeSummary(1L, 10, null);

        assertEquals(1, summary.getPendingReview());
        assertEquals(1, summary.getPendingTotal());
        assertEquals(0, summary.getPendingNew());
    }

    /**
     * 验证 21 天掌握线不再让卡片自动进入会了收集本。
     */
    @Test
    void reviewCardDoesNotMoveTwentyOneDayQualifiedCardToMastered() {
        Long userId = 1L;
        Long cardId = 10L;
        Card existing = card(
                cardId,
                "learning",
                direction("review", LocalDate.now().minusDays(1), LocalDate.now(), 3, 20.0, 4),
                direction("review", LocalDate.now().minusDays(1), LocalDate.now(), 3, 30.0, 5));
        Card updatedCard = card(
                cardId,
                "learning",
                direction("review", LocalDate.now().minusDays(1), LocalDate.now(), 3, 25.0, 5),
                direction("review", LocalDate.now().minusDays(1), LocalDate.now(), 3, 30.0, 5));
        CardService cardService = mock(CardService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        FsrsService fsrsService = mock(FsrsService.class);
        CardProgressStore progressStore = mock(CardProgressStore.class);
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(cardService.getCard(cardId)).thenReturn(existing, updatedCard);
        CardProgress current = progress(cardId, userId, "A_TO_B", "review", 4, 20.0);
        CardProgress scheduled = progress(cardId, userId, "A_TO_B", "review", 5, 25.0);
        when(progressStore.getOrCreateProgress(userId, cardId, "A_TO_B")).thenReturn(current);
        when(fsrsService.schedule(current, 3, 0.9)).thenReturn(scheduled);
        PracticeServiceImpl service = new PracticeServiceImpl(
                cardService,
                currentUserService,
                cardProgressMapper,
                fsrsService,
                progressStore,
                typeRegistryService(List.of()),
                scheduler(40, 70, 3, 120, 40));

        ReviewRequest request = new ReviewRequest();
        request.setDirection("a2b");
        request.setRating(3);
        request.setTargetRetention(0.9);
        ProgressUpdateResult result = service.reviewCard(cardId, request);

        assertFalse(result.getMastered());
        assertSame(updatedCard, result.getCard());
        ArgumentCaptor<CardProgress> captor = ArgumentCaptor.forClass(CardProgress.class);
        verify(cardProgressMapper).updateByUserIdAndCardIdAndDirection(captor.capture());
        List<CardProgress> progressUpdates = captor.getAllValues();
        List<CardProgress> withMasteredAt = progressUpdates.stream()
                .filter(p -> p.getMasteredAt() != null)
                .toList();
        assertEquals(0, withMasteredAt.size());
        verify(progressStore, never()).ensureDirectionalProgressRows(userId, cardId);
    }

    @Test
    void moveToGraduatedSetsBothDirectionsToGraduated() {
        Long userId = 1L;
        Long cardId = 10L;
        Card card = card(
                cardId,
                "learning",
                direction("review", LocalDate.now().minusDays(5), LocalDate.now().plusDays(180), 3, 185.0, 10),
                direction("review", LocalDate.now().minusDays(5), LocalDate.now().plusDays(200), 2, 200.0, 12));
        Card graduatedCard = card(
                cardId,
                "graduated",
                direction("graduated", LocalDate.now().minusDays(5), LocalDate.now().plusDays(180), 3, 185.0, 10),
                direction("graduated", LocalDate.now().minusDays(5), LocalDate.now().plusDays(200), 2, 200.0, 12));
        CardService cardService = mock(CardService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        CardProgressStore progressStore = mock(CardProgressStore.class);
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(cardService.getCard(cardId)).thenReturn(card, graduatedCard);
        when(progressStore.ensureDirectionalProgressRows(userId, cardId)).thenReturn(List.of(
                progress(cardId, userId, "A_TO_B", "review", 10, 185.0),
                progress(cardId, userId, "B_TO_A", "review", 12, 200.0)));
        PracticeServiceImpl service = new PracticeServiceImpl(
                cardService,
                currentUserService,
                cardProgressMapper,
                null,
                progressStore,
                typeRegistryService(List.of()),
                scheduler(40, 70, 3, 120, 40),
                null);

        Card result = service.moveToGraduated(cardId);

        assertSame(graduatedCard, result);
        ArgumentCaptor<CardProgress> captor = ArgumentCaptor.forClass(CardProgress.class);
        verify(cardProgressMapper, times(2)).updateByUserIdAndCardIdAndDirection(captor.capture());
        List<CardProgress> graduated = captor.getAllValues();
        assertEquals(2, graduated.size());
        assertTrue(graduated.stream().allMatch(p -> "graduated".equals(p.getState())));
        assertTrue(graduated.stream().allMatch(p -> p.getMasteredAt() != null));
        assertTrue(graduated.stream().anyMatch(p -> "A_TO_B".equals(p.getDirection())));
        assertTrue(graduated.stream().anyMatch(p -> "B_TO_A".equals(p.getDirection())));
    }

    private static List<String> directions(PracticeQueue queue) {
        return queue.getItems().stream()
                .map(PracticeItem::getDirection)
                .sorted()
                .toList();
    }

    /**
     * 创建返回指定启用模式的类型注册服务替身。
     */
    private static TypeRegistryService typeRegistryService(List<String> enabledModes) {
        TypeRegistryService service = mock(TypeRegistryService.class);
        when(service.getEnabledPracticeModeKeys()).thenReturn(enabledModes);
        return service;
    }

    /**
     * 创建只启用默认模式注册的练习服务，方便测试替换复习调度参数。
     */
    private static PracticeServiceImpl service(CardService cardService, PracticeReviewScheduler scheduler) {
        return new PracticeServiceImpl(
                cardService,
                null,
                null,
                null,
                null,
                typeRegistryService(List.of()),
                scheduler);
    }

    /**
     * 创建带今日已做数门控的练习服务，避免门控测试重复搭建相同依赖。
     */
    private static PracticeServiceImpl serviceWithReviewedCount(
            CardService cardService,
            Long userId,
            CardProgressMapper cardProgressMapper,
            int reviewedCount) {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(cardProgressMapper.countReviewedDirectionsToday(eq(userId), any(LocalDate.class), any(Long.class)))
                .thenReturn(reviewedCount);
        return new PracticeServiceImpl(
                cardService,
                currentUserService,
                cardProgressMapper,
                null,
                null,
                typeRegistryService(List.of()),
                scheduler(40, 70, 3, 120, 40),
                null);
    }

    /**
     * 创建固定参数的复习调度器，测试可直接控制目标、上限和积压阈值。
     */
    private static PracticeReviewScheduler scheduler(int target, int max, int deferralDays, int pause, int resume) {
        return new PracticeReviewScheduler(new PracticeReviewSchedulerConfig(target, max, deferralDays, pause, resume));
    }

    private static Card card(Long id, String state, DirectionProgressSnapshot a2b, DirectionProgressSnapshot b2a) {
        Card card = new Card();
        card.setId(id);
        card.setState(state);
        CardDirectionProgresses progresses = new CardDirectionProgresses();
        progresses.setA2b(a2b);
        progresses.setB2a(b2a);
        card.setDirectionProgresses(progresses);
        card.setFirstLearnedDate(resolveFirstLearnedDate(a2b, b2a));
        return card;
    }

    private static DirectionProgressSnapshot direction(String state, LocalDate lastReviewDate,
            LocalDate nextReviewDate) {
        return direction(state, lastReviewDate, nextReviewDate, 3, 30.0);
    }

    private static DirectionProgressSnapshot direction(
            String state,
            LocalDate lastReviewDate,
            LocalDate nextReviewDate,
            int lastRating,
            double stability) {
        return direction(state, lastReviewDate, nextReviewDate, lastRating, stability, 3);
    }

    /**
     * 创建带复习次数的方向快照，用来表达掌握标准里的两面达标。
     */
    private static DirectionProgressSnapshot direction(
            String state,
            LocalDate lastReviewDate,
            LocalDate nextReviewDate,
            int lastRating,
            double stability,
            int reps) {
        CardFsrs fsrs = new CardFsrs();
        fsrs.setState(state);
        fsrs.setLastReviewDate(lastReviewDate);
        fsrs.setNextReviewDate(nextReviewDate);
        fsrs.setLastRating(lastRating);
        fsrs.setStability(stability);
        fsrs.setReps(reps);
        fsrs.setDifficulty(5.0);

        DirectionProgressSnapshot snapshot = new DirectionProgressSnapshot();
        snapshot.setState(state);
        snapshot.setFsrs(fsrs);
        snapshot.setFirstLearnedDate(lastReviewDate);
        return snapshot;
    }

    /**
     * 创建可被评分流程读写的方向进度，覆盖 FSRS 掌握判定所需字段。
     */
    private static CardProgress progress(Long cardId, Long userId, String direction, String state, int reps,
            double stability) {
        CardProgress progress = new CardProgress();
        progress.setId("A_TO_B".equals(direction) ? 1L : 2L);
        progress.setCardId(cardId);
        progress.setUserId(userId);
        progress.setDirection(direction);
        progress.setState(state);
        progress.setReps(reps);
        progress.setStability(stability);
        progress.setDifficulty(5.0);
        progress.setLastReviewDate(LocalDate.now().minusDays(1));
        progress.setNextReviewDate(LocalDate.now());
        progress.setLastRating(3);
        return progress;
    }

    @Test
    void reviewCardDoesNotGraduateWhenBothDirectionsStabilityOver180AndRatingGood() {
        Long userId = 1L;
        Long cardId = 20L;
        // 评分前卡片：两个方向 stability 都 >= 180，masteredAt 已设置
        Card existing = card(
                cardId,
                "learning",
                direction("review", LocalDate.now().minusDays(100), LocalDate.now(), 3, 150.0, 10),
                direction("review", LocalDate.now().minusDays(100), LocalDate.now(), 3, 120.0, 8));
        // FSRS 调度后：两个方向 stability >= 180
        Card updatedCard = card(
                cardId,
                "learning",
                direction("review", LocalDate.now(), LocalDate.now().plusDays(185), 2, 185.0, 11),
                direction("review", LocalDate.now().minusDays(100), LocalDate.now().plusDays(200), 3, 200.0, 8));
        CardService cardService = mock(CardService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        FsrsService fsrsService = mock(FsrsService.class);
        CardProgressStore progressStore = mock(CardProgressStore.class);
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(cardService.getCard(cardId)).thenReturn(existing, updatedCard);
        CardProgress current = progress(cardId, userId, "A_TO_B", "review", 10, 150.0);
        CardProgress scheduled = progress(cardId, userId, "A_TO_B", "review", 11, 185.0);
        when(progressStore.getOrCreateProgress(userId, cardId, "A_TO_B")).thenReturn(current);
        when(fsrsService.schedule(current, 2, 0.9)).thenReturn(scheduled);
        PracticeServiceImpl service = new PracticeServiceImpl(
                cardService,
                currentUserService,
                cardProgressMapper,
                fsrsService,
                progressStore,
                typeRegistryService(List.of()),
                scheduler(40, 70, 3, 120, 40),
                null);

        ReviewRequest request = new ReviewRequest();
        request.setDirection("a2b");
        request.setRating(2); // GOOD
        request.setTargetRetention(0.9);
        ProgressUpdateResult result = service.reviewCard(cardId, request);

        assertSame(updatedCard, result.getCard());
        assertEquals(Boolean.FALSE, result.getGraduated());
        verify(cardService, times(2)).getCard(cardId);
        verify(cardProgressMapper, times(1)).updateByUserIdAndCardIdAndDirection(any());
        verify(progressStore, never()).ensureDirectionalProgressRows(userId, cardId);
    }

    @Test
    void reviewCardTriggersGraduationWhenBothDirectionsStabilityOver180AndRatingEasy() {
        Long userId = 1L;
        Long cardId = 23L;
        Card existing = card(
                cardId,
                "learning",
                direction("review", LocalDate.now().minusDays(100), LocalDate.now(), 3, 170.0, 12),
                direction("review", LocalDate.now().minusDays(100), LocalDate.now(), 3, 190.0, 12));
        Card updatedCard = card(
                cardId,
                "learning",
                direction("review", LocalDate.now(), LocalDate.now().plusDays(210), 3, 210.0, 13),
                direction("review", LocalDate.now().minusDays(100), LocalDate.now().plusDays(190), 3, 190.0, 12));
        Card graduatedCard = card(
                cardId,
                "graduated",
                direction("graduated", LocalDate.now(), LocalDate.now().plusDays(210), 3, 210.0, 13),
                direction("graduated", LocalDate.now().minusDays(100), LocalDate.now().plusDays(190), 3, 190.0, 12));
        CardService cardService = mock(CardService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        FsrsService fsrsService = mock(FsrsService.class);
        CardProgressStore progressStore = mock(CardProgressStore.class);
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(cardService.getCard(cardId)).thenReturn(existing, updatedCard, graduatedCard);
        CardProgress current = progress(cardId, userId, "A_TO_B", "review", 12, 170.0);
        CardProgress scheduled = progress(cardId, userId, "A_TO_B", "review", 13, 210.0);
        when(progressStore.getOrCreateProgress(userId, cardId, "A_TO_B")).thenReturn(current);
        when(fsrsService.schedule(current, 3, 0.9)).thenReturn(scheduled);
        when(progressStore.ensureDirectionalProgressRows(userId, cardId)).thenReturn(List.of(
                progress(cardId, userId, "A_TO_B", "review", 13, 210.0),
                progress(cardId, userId, "B_TO_A", "review", 12, 190.0)));
        PracticeServiceImpl service = new PracticeServiceImpl(
                cardService,
                currentUserService,
                cardProgressMapper,
                fsrsService,
                progressStore,
                typeRegistryService(List.of()),
                scheduler(40, 70, 3, 120, 40),
                null);

        ReviewRequest request = new ReviewRequest();
        request.setDirection("a2b");
        request.setRating(3);
        request.setTargetRetention(0.9);
        ProgressUpdateResult result = service.reviewCard(cardId, request);

        assertSame(graduatedCard, result.getCard());
        ArgumentCaptor<CardProgress> captor = ArgumentCaptor.forClass(CardProgress.class);
        verify(cardProgressMapper, times(3)).updateByUserIdAndCardIdAndDirection(captor.capture());
        List<CardProgress> graduatedRows = captor.getAllValues().stream()
                .filter(p -> "graduated".equals(p.getState()))
                .toList();
        assertEquals(2, graduatedRows.size());
    }

    @Test
    void reviewCardDoesNotGraduateWhenRatingIsAgain() {
        Long userId = 1L;
        Long cardId = 21L;
        Card existing = card(
                cardId,
                "learning",
                direction("review", LocalDate.now().minusDays(100), LocalDate.now(), 3, 150.0, 10),
                direction("review", LocalDate.now().minusDays(100), LocalDate.now(), 3, 120.0, 8));
        Card updatedCard = card(
                cardId,
                "learning",
                direction("review", LocalDate.now(), LocalDate.now().plusDays(1), 1, 185.0, 11),
                direction("review", LocalDate.now().minusDays(100), LocalDate.now().plusDays(200), 3, 200.0, 8));
        CardService cardService = mock(CardService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        FsrsService fsrsService = mock(FsrsService.class);
        CardProgressStore progressStore = mock(CardProgressStore.class);
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(cardService.getCard(cardId)).thenReturn(existing, updatedCard);
        CardProgress current = progress(cardId, userId, "A_TO_B", "review", 10, 150.0);
        CardProgress scheduled = progress(cardId, userId, "A_TO_B", "relearning", 11, 5.0);
        when(progressStore.getOrCreateProgress(userId, cardId, "A_TO_B")).thenReturn(current);
        when(fsrsService.schedule(current, 1, 0.9)).thenReturn(scheduled);
        PracticeServiceImpl service = new PracticeServiceImpl(
                cardService,
                currentUserService,
                cardProgressMapper,
                fsrsService,
                progressStore,
                typeRegistryService(List.of()),
                scheduler(40, 70, 3, 120, 40),
                null);

        ReviewRequest request = new ReviewRequest();
        request.setDirection("a2b");
        request.setRating(1); // AGAIN
        request.setTargetRetention(0.9);
        ProgressUpdateResult result = service.reviewCard(cardId, request);

        // 没有触发毕业，返回普通更新结果
        assertSame(updatedCard, result.getCard());
        verify(cardProgressMapper, times(1)).updateByUserIdAndCardIdAndDirection(any());
    }

    @Test
    void reviewCardDoesNotGraduateWhenOnlyOneDirectionReaches180() {
        Long userId = 1L;
        Long cardId = 22L;
        Card existing = card(
                cardId,
                "learning",
                direction("review", LocalDate.now().minusDays(50), LocalDate.now(), 3, 80.0, 8),
                direction("review", LocalDate.now().minusDays(100), LocalDate.now(), 3, 120.0, 8));
        // 评分后 a2b 达到 185，但 b2a 只有 120
        Card updatedCard = card(
                cardId,
                "learning",
                direction("review", LocalDate.now(), LocalDate.now().plusDays(185), 3, 185.0, 9),
                direction("review", LocalDate.now().minusDays(100), LocalDate.now().plusDays(50), 3, 120.0, 8));
        CardService cardService = mock(CardService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        FsrsService fsrsService = mock(FsrsService.class);
        CardProgressStore progressStore = mock(CardProgressStore.class);
        when(currentUserService.getCurrentUserId()).thenReturn(userId);
        when(cardService.getCard(cardId)).thenReturn(existing, updatedCard);
        CardProgress current = progress(cardId, userId, "A_TO_B", "review", 8, 80.0);
        CardProgress scheduled = progress(cardId, userId, "A_TO_B", "review", 9, 185.0);
        when(progressStore.getOrCreateProgress(userId, cardId, "A_TO_B")).thenReturn(current);
        when(fsrsService.schedule(current, 3, 0.9)).thenReturn(scheduled);
        PracticeServiceImpl service = new PracticeServiceImpl(
                cardService,
                currentUserService,
                cardProgressMapper,
                fsrsService,
                progressStore,
                typeRegistryService(List.of()),
                scheduler(40, 70, 3, 120, 40),
                null);

        ReviewRequest request = new ReviewRequest();
        request.setDirection("a2b");
        request.setRating(3); // EASY
        request.setTargetRetention(0.9);
        ProgressUpdateResult result = service.reviewCard(cardId, request);

        assertSame(updatedCard, result.getCard());
        verify(cardProgressMapper, times(1)).updateByUserIdAndCardIdAndDirection(any());
    }

    @Test
    void skipsGraduatedCardInPendingNewDirectionItems() {
        LocalDate today = LocalDate.now();
        // graduated 卡：一个方向 graduated，另一个方向 new
        Card graduatedWithNewDirection = card(
                1L,
                "graduated",
                direction("graduated", today.minusDays(200), today.plusDays(200), 3, 200.0, 15),
                direction("new", null, today));
        CardService cardService = mock(CardService.class);
        when(cardService.listCards(1L, null)).thenReturn(List.of(graduatedWithNewDirection));
        PracticeServiceImpl service = service(cardService, scheduler(40, 70, 3, 120, 40));

        PracticeQueue queue = service.buildDailyQueue(1L, 10, "random");

        // graduated 卡不应该出现在队列中
        assertEquals(0, queue.getItems().size());
    }

    private static LocalDate resolveFirstLearnedDate(DirectionProgressSnapshot a2b, DirectionProgressSnapshot b2a) {
        if (a2b.getFirstLearnedDate() == null)
            return b2a.getFirstLearnedDate();
        if (b2a.getFirstLearnedDate() == null)
            return a2b.getFirstLearnedDate();
        return a2b.getFirstLearnedDate().isBefore(b2a.getFirstLearnedDate())
                ? a2b.getFirstLearnedDate()
                : b2a.getFirstLearnedDate();
    }
}
