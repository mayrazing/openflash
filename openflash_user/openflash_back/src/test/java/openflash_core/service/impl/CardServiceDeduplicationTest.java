package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.entity.Card;
import openflash_core.entity.CardBatchCreateItem;
import openflash_core.entity.CardBatchCreateResult;
import openflash_core.entity.CardBatchMoveResult;
import openflash_core.entity.DeckCardStats;
import openflash_core.entity.DeckLearningStats;
import openflash_core.entity.CardProgress;
import openflash_core.entity.Deck;
import openflash_core.entity.DeckSettings;
import openflash_core.mapper.CardMapper;
import openflash_core.mapper.CardMediaMapper;
import openflash_core.mapper.CardProgressMapper;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.mapper.UserUploadMapper;
import openflash_core.spi.CardChangeEvent;
import org.springframework.context.ApplicationEventPublisher;
import openflash_core.service.CurrentUserService;
import openflash_core.service.UserUploadAccessGuard;

class CardServiceDeduplicationTest {

    @Test
    void createRejectsUnownedUploadBeforeCardInsert() {
        Fixture fixture = new Fixture();
        when(fixture.userUploadMapper.lockOwnerIdByPath("/uploads/other.jpg")).thenReturn(8L);

        AppException error = assertThrows(AppException.class,
            () -> fixture.service.createCard(
                1L, "word", "词", List.of("/uploads/other.jpg"), List.of()));

        assertEquals(ErrorCode.UPLOAD_MEDIA_ACCESS_DENIED, error.getErrorCode());
        verify(fixture.cardMapper, never()).insert(any(Card.class));
        verify(fixture.cardMediaMapper, never()).deleteByCardId(any());
    }

    @Test
    void updateRejectsUnownedUploadBeforeCardOrMediaMutation() {
        Fixture fixture = new Fixture();
        Card existing = existingCard(10L, "old", "旧");
        when(fixture.cardMapper.findById(10L)).thenReturn(existing);
        when(fixture.cardMapper.findDeduplicationCandidates(1L, 10L)).thenReturn(List.of());
        when(fixture.userUploadMapper.lockOwnerIdByPath("/uploads/other.jpg")).thenReturn(8L);

        AppException error = assertThrows(AppException.class,
            () -> fixture.service.updateCard(
                10L, "new", "新", List.of("/uploads/other.jpg"), List.of()));

        assertEquals(ErrorCode.UPLOAD_MEDIA_ACCESS_DENIED, error.getErrorCode());
        verify(fixture.cardMapper, never()).updateCard(any(Card.class));
        verify(fixture.cardMediaMapper, never()).deleteByCardId(any());
    }

    @Test
    void createAllowsCurrentOwnerUpload() {
        Fixture fixture = new Fixture();
        when(fixture.userUploadMapper.lockOwnerIdByPath("/uploads/own.jpg")).thenReturn(7L);

        fixture.service.createCard(
            1L, "word", "词", List.of("/uploads/own.jpg"), List.of());

        verify(fixture.cardMapper).insert(any(Card.class));
        verify(fixture.userUploadMapper).lockOwnerIdByPath("/uploads/own.jpg");
    }

    @Test
    void createAllowsRemoteUrlContainingUploadsSegment() {
        Fixture fixture = new Fixture();

        fixture.service.createCard(
            1L, "word", "词", List.of("https://cdn.example/uploads/remote.jpg"), List.of());

        verify(fixture.cardMapper).insert(any(Card.class));
        verifyNoInteractions(fixture.userUploadMapper);
    }

    @Test
    void createRejectsDuplicateSideAInSameDeck() {
        Fixture fixture = new Fixture();
        when(fixture.cardMapper.findDeduplicationCandidates(1L, null)).thenReturn(List.of(existingCard(1L, " apple ", "苹果")));

        AppException ex = assertThrows(AppException.class,
            () -> fixture.service.createCard(1L, "Apple", "苹果", List.of(), List.of()));

        assertEquals(ErrorCode.CARD_ALREADY_EXISTS, ex.getErrorCode());
        verify(fixture.cardMapper, never()).insert(any(Card.class));
    }

    @Test
    void updateRejectsDuplicateSideAFromAnotherCard() {
        Fixture fixture = new Fixture();
        Card existing = new Card();
        existing.setId(10L);
        existing.setDeckId(1L);
        existing.setSideA("Orange");
        existing.setSideB("橙子");
        when(fixture.cardMapper.findById(10L)).thenReturn(existing);
        when(fixture.cardMapper.findDeduplicationCandidates(1L, 10L)).thenReturn(List.of(
            existing,
            existingCard(11L, "Ａｐｐｌｅ", "苹果")
        ));

        AppException ex = assertThrows(AppException.class,
            () -> fixture.service.updateCard(10L, "apple", "苹果", List.of(), List.of()));

        assertEquals(ErrorCode.CARD_ALREADY_EXISTS, ex.getErrorCode());
        verify(fixture.cardMapper, never()).updateCard(any(Card.class));
    }

    @Test
    void batchCountsCreatedDuplicateAndInvalidRows() {
        Fixture fixture = new Fixture();
        when(fixture.cardMapper.findDeduplicationCandidates(1L, null)).thenReturn(List.of(existingCard(1L, "apple", "苹果")));

        CardBatchCreateItem duplicateFromDb = item(" Apple ", "苹果");
        CardBatchCreateItem created = item("banana", "香蕉");
        CardBatchCreateItem duplicateFromBatch = item("BANANA", "香蕉");
        CardBatchCreateItem invalid = item("pear", "");

        CardBatchCreateResult result = fixture.service.createCardsBatch(1L,
            List.of(duplicateFromDb, created, duplicateFromBatch, invalid));

        assertEquals(1, result.getCreatedCount());
        assertEquals(2, result.getDuplicateCount());
        assertEquals(1, result.getInvalidCount());
        assertEquals(3, result.getFailures().size());
        assertEquals("卡片已存在", result.getFailures().get(0).reason());
        assertEquals("卡片已存在", result.getFailures().get(1).reason());
        assertEquals("A 面和 B 面都要填写", result.getFailures().get(2).reason());
        verify(fixture.cardMapper).insert(any(Card.class));
    }

    @Test
    void createRejectsDuplicateSideBWhenSideBDeduplicationEnabled() {
        Fixture fixture = new Fixture();
        fixture.settings.setDuplicateSideBEnabled(true);
        when(fixture.cardMapper.findDeduplicationCandidates(1L, null)).thenReturn(List.of(existingCard(1L, "orange", " 苹果 ")));

        AppException ex = assertThrows(AppException.class,
            () -> fixture.service.createCard(1L, "Apple", "苹果", List.of(), List.of()));

        assertEquals(ErrorCode.CARD_ALREADY_EXISTS, ex.getErrorCode());
        verify(fixture.cardMapper, never()).insert(any(Card.class));
    }

    @Test
    void createAllowsDuplicateSideAWhenSideADeduplicationDisabled() {
        Fixture fixture = new Fixture();
        fixture.settings.setDuplicateSideAEnabled(false);

        fixture.service.createCard(1L, "Apple", "苹果", List.of(), List.of());

        verify(fixture.cardMapper).insert(any(Card.class));
    }

    @Test
    void deduplicationDefaultsUseDeckSettingsServiceNormalizer() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/openflash_core/service/impl/CardServiceImpl.java"));

        assertTrue(source.contains("deckSettingsService.normalizeSettings"));
        assertFalse(source.contains("Objects.requireNonNullElse(settings.getDuplicateSideAEnabled(), true)"));
        assertFalse(source.contains("Objects.requireNonNullElse(settings.getDuplicateSideBEnabled(), false)"));
    }

    @Test
    void batchCountsDuplicateRowsFromBothEnabledSides() {
        Fixture fixture = new Fixture();
        fixture.settings.setDuplicateSideBEnabled(true);
        when(fixture.cardMapper.findDeduplicationCandidates(1L, null)).thenReturn(List.of(
            existingCard(1L, "apple", "苹果"),
            existingCard(2L, "orange", "香蕉")
        ));

        CardBatchCreateResult result = fixture.service.createCardsBatch(1L,
            List.of(item(" Apple ", "苹果"), item("banana", " 香蕉 "), item("pear", "梨")));

        assertEquals(1, result.getCreatedCount());
        assertEquals(2, result.getDuplicateCount());
        assertEquals(0, result.getInvalidCount());
    }

    @Test
    void deckStatsTodayCountMatchesTodayListCardScope() {
        Fixture fixture = new Fixture(new PracticeReviewSchedulerConfig(1, 10, 3, 120, 40));
        LocalDate today = LocalDate.now();
        Card scheduledReviewCard = existingCard(30L, "apple", "苹果");
        Card deferredReviewCard = existingCard(31L, "pear", "梨");
        Card scheduledNewCard = existingCard(32L, "grape", "葡萄");
        Card partialCard = existingCard(33L, "melon", "瓜");
        Card todayLearnedCard = existingCard(34L, "banana", "香蕉");
        Card todayReviewedCard = existingCard(35L, "orange", "橙子");
        List<Long> cardIds = List.of(30L, 31L, 32L, 33L, 34L, 35L);
        when(fixture.cardMapper.findByDeckId(1L, null))
            .thenReturn(List.of(
                scheduledReviewCard,
                deferredReviewCard,
                scheduledNewCard,
                partialCard,
                todayLearnedCard,
                todayReviewedCard
            ));
        when(fixture.cardMediaMapper.findByCardIds(cardIds)).thenReturn(List.of());
        when(fixture.cardProgressMapper.findByUserIdAndCardIds(7L, cardIds)).thenReturn(List.of(
            progress(30L, 7L, "A_TO_B", "learning", today.minusDays(4), today, today, 1, 5.0),
            progress(30L, 7L, "B_TO_A", "review", today.minusDays(4), today.minusDays(3), today.plusDays(3), 3, 80.0),
            progress(31L, 7L, "A_TO_B", "review", today.minusDays(4), today.minusDays(1), today, 3, 80.0),
            progress(31L, 7L, "B_TO_A", "review", today.minusDays(4), today.minusDays(3), today.plusDays(3), 3, 80.0),
            progress(32L, 7L, "A_TO_B", "new", null, null),
            progress(32L, 7L, "B_TO_A", "new", null, null),
            progress(33L, 7L, "A_TO_B", "review", today.minusDays(2), today.minusDays(1), today.plusDays(1)),
            progress(33L, 7L, "B_TO_A", "new", null, null),
            progress(34L, 7L, "A_TO_B", "review", today, null, today.plusDays(2)),
            progress(34L, 7L, "B_TO_A", "review", today, null, today.plusDays(2)),
            progress(35L, 7L, "A_TO_B", "review", today.minusDays(3), today, today.plusDays(1)),
            progress(35L, 7L, "B_TO_A", "review", today.minusDays(3), today.minusDays(2), today.plusDays(3))
        ));

        DeckCardStats stats = fixture.service.getDeckCardStats(1L, 10);
        PracticeServiceImpl practiceService = new PracticeServiceImpl(
            fixture.service,
            null,
            null,
            null,
            null,
            null,
            fixture.practiceReviewScheduler
        );
        List<Long> todayCardIds = practiceService.getTodayCardsByDeck(1L, 10).stream()
            .map(Card::getId)
            .toList();

        assertEquals(5, stats.getTodayCount());
        assertEquals(stats.getTodayCount(), todayCardIds.size());
        assertEquals(List.of(30L, 32L, 33L, 34L, 35L), todayCardIds);
    }

    @Test
    void todayCardsHidePureNewCardsWhenDailyTargetAlreadyUsed() {
        Fixture fixture = new Fixture(new PracticeReviewSchedulerConfig(2, 6, 3, 120, 40));
        LocalDate today = LocalDate.now();
        Card pureNewCard = existingCard(36L, "uart", "串口通信");
        Card anotherPureNewCard = existingCard(37L, "i2c", "双线外设通信");
        Card todayReviewedCard = existingCard(38L, "done", "今天已练");
        List<Card> cards = List.of(pureNewCard, anotherPureNewCard, todayReviewedCard);
        List<Long> cardIds = List.of(36L, 37L, 38L);
        when(fixture.cardMapper.findByDeckId(1L, null)).thenReturn(cards).thenReturn(cards);
        when(fixture.cardMediaMapper.findByCardIds(cardIds)).thenReturn(List.of()).thenReturn(List.of());
        when(fixture.cardProgressMapper.findByUserIdAndCardIds(7L, cardIds)).thenReturn(List.of(
            progress(36L, 7L, "A_TO_B", "new", null, null),
            progress(36L, 7L, "B_TO_A", "new", null, null),
            progress(37L, 7L, "A_TO_B", "new", null, null),
            progress(37L, 7L, "B_TO_A", "new", null, null),
            progress(38L, 7L, "A_TO_B", "review", today.minusDays(3), today, today.plusDays(1)),
            progress(38L, 7L, "B_TO_A", "review", today.minusDays(3), today.minusDays(1), today.plusDays(3))
        )).thenReturn(List.of(
            progress(36L, 7L, "A_TO_B", "new", null, null),
            progress(36L, 7L, "B_TO_A", "new", null, null),
            progress(37L, 7L, "A_TO_B", "new", null, null),
            progress(37L, 7L, "B_TO_A", "new", null, null),
            progress(38L, 7L, "A_TO_B", "review", today.minusDays(3), today, today.plusDays(1)),
            progress(38L, 7L, "B_TO_A", "review", today.minusDays(3), today.minusDays(1), today.plusDays(3))
        ));
        when(fixture.cardProgressMapper.countReviewedDirectionsToday(7L, today, 1L)).thenReturn(2);

        DeckCardStats stats = fixture.service.getDeckCardStats(1L, 10);
        PracticeServiceImpl practiceService = new PracticeServiceImpl(
            fixture.service,
            fixture.currentUserService,
            fixture.cardProgressMapper,
            null,
            null,
            null,
            fixture.practiceReviewScheduler,
            fixture.deckSettingsMapper
        );
        List<Long> todayCardIds = practiceService.getTodayCardsByDeck(1L, 10).stream()
            .map(Card::getId)
            .toList();

        assertEquals(1, stats.getTodayCount());
        assertEquals(List.of(38L), todayCardIds);
    }

    @Test
    void deckStatsTodayCountUsesPracticeTargetAfterFirstNewCardReview() {
        Fixture fixture = new Fixture(PracticeReviewSchedulerConfig.defaults());
        LocalDate today = LocalDate.now();
        List<Card> beforeCards = new ArrayList<>();
        List<CardProgress> beforeProgresses = new ArrayList<>();
        for (long i = 1; i <= 50; i++) {
            beforeCards.add(existingCard(i, "word " + i, "词 " + i));
            beforeProgresses.add(progress(i, 7L, "A_TO_B", "new", null, null));
            beforeProgresses.add(progress(i, 7L, "B_TO_A", "new", null, null));
        }

        List<Card> afterCards = new ArrayList<>(beforeCards);
        List<CardProgress> afterProgresses = new ArrayList<>();
        afterProgresses.add(progress(1L, 7L, "A_TO_B", "learning", today, today, today.plusDays(1)));
        afterProgresses.add(progress(1L, 7L, "B_TO_A", "new", null, null));
        for (long i = 2; i <= 50; i++) {
            afterProgresses.add(progress(i, 7L, "A_TO_B", "new", null, null));
            afterProgresses.add(progress(i, 7L, "B_TO_A", "new", null, null));
        }
        List<Long> cardIds = beforeCards.stream().map(Card::getId).toList();
        when(fixture.cardMapper.findByDeckId(1L, null)).thenReturn(beforeCards).thenReturn(afterCards);
        when(fixture.cardMediaMapper.findByCardIds(cardIds)).thenReturn(List.of()).thenReturn(List.of());
        when(fixture.cardProgressMapper.findByUserIdAndCardIds(7L, cardIds))
            .thenReturn(beforeProgresses)
            .thenReturn(afterProgresses);
        when(fixture.cardProgressMapper.countReviewedDirectionsToday(7L, today, 1L)).thenReturn(0, 1);

        DeckCardStats before = fixture.service.getDeckCardStats(1L, 50);
        DeckCardStats after = fixture.service.getDeckCardStats(1L, 50);
        PracticeServiceImpl practiceService = new PracticeServiceImpl(
            fixture.service,
            fixture.currentUserService,
            fixture.cardProgressMapper,
            null,
            null,
            null,
            fixture.practiceReviewScheduler,
            fixture.deckSettingsMapper
        );
        List<Card> todayCards = practiceService.getTodayCardsByDeck(1L, 50);

        assertEquals(35, before.getTodayCount());
        assertEquals(20, after.getTodayCount());
        assertEquals(after.getTodayCount(), todayCards.size());
    }

    @Test
    void learningStatsPendingCountsUseSchedulerWhenBacklogPausesNewCards() {
        Fixture fixture = new Fixture(new PracticeReviewSchedulerConfig(1, 1, 3, 1, 0));
        LocalDate today = LocalDate.now();
        Card selectedReviewCard = existingCard(40L, "apple", "苹果");
        Card deferredReviewCard = existingCard(41L, "pear", "梨");
        Card newCard = existingCard(42L, "grape", "葡萄");
        List<Long> cardIds = List.of(40L, 41L, 42L);
        when(fixture.cardMapper.findByDeckId(1L, null))
            .thenReturn(List.of(selectedReviewCard, deferredReviewCard, newCard));
        when(fixture.cardMediaMapper.findByCardIds(cardIds)).thenReturn(List.of());
        when(fixture.cardProgressMapper.findByUserIdAndCardIds(7L, cardIds)).thenReturn(List.of(
            progress(40L, 7L, "A_TO_B", "learning", today.minusDays(4), today.minusDays(1), today, 1, 5.0),
            progress(40L, 7L, "B_TO_A", "review", today.minusDays(4), today.minusDays(3), today.plusDays(3), 3, 80.0),
            progress(41L, 7L, "A_TO_B", "review", today.minusDays(4), today.minusDays(1), today, 3, 80.0),
            progress(41L, 7L, "B_TO_A", "review", today.minusDays(4), today.minusDays(3), today.plusDays(3), 3, 80.0),
            progress(42L, 7L, "A_TO_B", "new", null, null),
            progress(42L, 7L, "B_TO_A", "new", null, null)
        ));
        DeckLearningStats sqlStats = new DeckLearningStats();
        sqlStats.setTotal(3);
        sqlStats.setMastered(0);
        sqlStats.setPendingNew(1);
        sqlStats.setPendingReview(2);
        sqlStats.setPendingTotal(3);
        when(fixture.cardMapper.selectLearningStats(1L, 7L, today, 10)).thenReturn(sqlStats);
        when(fixture.cardMapper.selectTopReviewCards(1L, 7L, 5)).thenReturn(List.of());

        DeckLearningStats stats = fixture.service.getDeckLearningStats(1L, 10);

        assertEquals(0, stats.getPendingNew());
        assertEquals(1, stats.getPendingReview());
        assertEquals(1, stats.getPendingTotal());
        assertTrue(stats.getBacklogCount() > 0);
        assertEquals(true, stats.getNewCardsPaused());
        verify(fixture.cardMediaMapper, never()).findByCardIds(any());
    }

    /**
     * 验证统计页今日目标用完后，另一面首次练习不会绕过待复习统计门控。
     */
    @Test
    void learningStatsHidePendingNewDirectionWhenDailyTargetUsed() {
        Fixture fixture = new Fixture(new PracticeReviewSchedulerConfig(40, 70, 3, 120, 40));
        LocalDate today = LocalDate.now();
        Card partialCard = existingCard(50L, "melon", "瓜");
        List<Long> cardIds = List.of(50L);
        when(fixture.cardMapper.findByDeckId(1L, null)).thenReturn(List.of(partialCard));
        when(fixture.cardProgressMapper.findByUserIdAndCardIds(7L, cardIds)).thenReturn(List.of(
            progress(50L, 7L, "A_TO_B", "review", today.minusDays(2), today.minusDays(1), today.plusDays(1)),
            progress(50L, 7L, "B_TO_A", "new", null, null)
        ));
        when(fixture.cardProgressMapper.countReviewedDirectionsToday(7L, today, 1L)).thenReturn(40);
        DeckLearningStats sqlStats = new DeckLearningStats();
        sqlStats.setTotal(1);
        sqlStats.setMastered(0);
        sqlStats.setPendingNew(1);
        sqlStats.setPendingReview(1);
        sqlStats.setPendingTotal(2);
        when(fixture.cardMapper.selectLearningStats(1L, 7L, today, 10)).thenReturn(sqlStats);
        when(fixture.cardMapper.selectTopReviewCards(1L, 7L, 5)).thenReturn(List.of());

        DeckLearningStats stats = fixture.service.getDeckLearningStats(1L, 10);

        assertEquals(0, stats.getPendingReview());
        assertEquals(0, stats.getPendingNew());
        assertEquals(0, stats.getPendingTotal());
        verify(fixture.cardProgressMapper).countReviewedDirectionsToday(7L, today, 1L);
    }

    @Test
    void deckStatsUseDeckReviewLoadProfile() {
        Fixture fixture = new Fixture();
        fixture.settings.setReviewLoadProfile("relaxed");
        LocalDate today = LocalDate.now();
        List<Card> cards = new ArrayList<>();
        List<CardProgress> progresses = new ArrayList<>();
        for (long i = 1; i <= 80; i++) {
            cards.add(existingCard(i, "word " + i, "词 " + i));
            progresses.add(progress(i, 7L, "A_TO_B", "review", today.minusDays(2), today.minusDays(1), today, 3, 80.0));
            progresses.add(progress(i, 7L, "B_TO_A", "mastered", today.minusDays(2), today.minusDays(1), today.plusDays(10), 3, 80.0));
        }
        when(fixture.cardMapper.findByDeckId(1L, null)).thenReturn(cards);
        when(fixture.cardProgressMapper.findByUserIdAndCardIds(7L, cards.stream().map(Card::getId).toList()))
            .thenReturn(progresses);

        DeckCardStats stats = fixture.service.getDeckCardStats(1L, 10);

        assertEquals(true, stats.getNewCardsPaused());
        assertEquals(50, stats.getBacklogCount());
    }

    @Test
    void getCardShowsMasteredWhenBothDirectionsHaveMasteredAt() {
        Fixture fixture = new Fixture();
        LocalDate today = LocalDate.now();
        LocalDateTime masteredAt = LocalDateTime.now().minusDays(1);
        Card card = existingCard(60L, "done", "完成");
        CardProgress a2b = progress(60L, 7L, "A_TO_B", "review", today.minusDays(5), today.minusDays(1), today.plusDays(30));
        CardProgress b2a = progress(60L, 7L, "B_TO_A", "review", today.minusDays(5), today.minusDays(1), today.plusDays(40));
        a2b.setMasteredAt(masteredAt);
        b2a.setMasteredAt(masteredAt.plusHours(1));
        when(fixture.cardMapper.findById(60L)).thenReturn(card);
        when(fixture.cardMediaMapper.findByCardId(60L)).thenReturn(List.of());
        when(fixture.cardProgressStore.ensureDirectionalProgressRows(7L, 60L)).thenReturn(List.of(a2b, b2a));

        Card result = fixture.service.getCard(60L);

        assertEquals("mastered", result.getState());
        assertEquals(masteredAt.plusHours(1), result.getMasteredAt());
    }

    @Test
    void getCardIncludesOwnedDeckNameForMasteredCollectionDisplay() {
        Fixture fixture = new Fixture();
        Card card = existingCard(61L, "source", "来源");
        when(fixture.cardMapper.findById(61L)).thenReturn(card);
        when(fixture.cardMediaMapper.findByCardId(61L)).thenReturn(List.of());
        when(fixture.cardProgressStore.ensureDirectionalProgressRows(7L, 61L)).thenReturn(List.of(
            progress(61L, 7L, "A_TO_B", "new", null, null),
            progress(61L, 7L, "B_TO_A", "new", null, null)
        ));

        Card result = fixture.service.getCard(61L);

        assertEquals("Default", readDeckName(result));
    }

    @Test
    void moveCardsBatchMovesOnlyNonDuplicateCardsToTargetDeck() {
        Fixture fixture = new Fixture();
        Deck targetDeck = new Deck();
        targetDeck.setId(2L);
        targetDeck.setUserId(7L);
        targetDeck.setName("Target");
        when(fixture.deckMapper.findByIdAndUserId(2L, 7L)).thenReturn(targetDeck);
        DeckSettings targetSettings = new DeckSettings();
        targetSettings.setDuplicateSideAEnabled(true);
        targetSettings.setDuplicateSideBEnabled(false);
        when(fixture.deckSettingsMapper.findByDeckId(2L)).thenReturn(targetSettings);
        when(fixture.cardMapper.findByIds(List.of(10L, 11L, 12L))).thenReturn(List.of(
            cardInDeck(10L, 1L, "banana", "香蕉"),
            cardInDeck(11L, 1L, "apple", "苹果"),
            cardInDeck(12L, 99L, "pear", "梨")
        ));
        when(fixture.cardMapper.findDeduplicationCandidates(2L, null)).thenReturn(List.of(
            cardInDeck(21L, 2L, " Apple ", "苹果")
        ));

        CardBatchMoveResult result = fixture.service.moveCardsBatch(1L, 2L, List.of(10L, 11L, 12L, 10L));

        assertEquals(1, result.getMovedCount());
        assertEquals(List.of(10L), result.getMovedCardIds());
        assertEquals(1, result.getDuplicateCount());
        assertEquals(1, result.getInvalidCount());
        assertEquals(2, result.getFailures().size());
        assertEquals("DUPLICATE", result.getFailures().get(0).reasonCode());
        assertEquals("INVALID_CARD", result.getFailures().get(1).reasonCode());
        verify(fixture.cardMapper).updateDeckId(10L, 1L, 2L);
        verify(fixture.cardMapper, never()).updateDeckId(eq(11L), eq(1L), eq(2L));
        verify(fixture.cardMapper, never()).updateDeckId(eq(12L), eq(1L), eq(2L));
        verify(fixture.cardProgressMapper, never()).deleteByCardId(any());
        verify(fixture.cardMediaMapper, never()).deleteByCardId(any());
        verify(fixture.cardProgressStore, never()).createDefaultProgressRows(any(), any());
        verify(fixture.eventPublisher).publishEvent((Object) argThat(event -> {
            CardChangeEvent cardEvent = (CardChangeEvent) event;
            return cardEvent.userId().equals(7L)
                && cardEvent.kind() == CardChangeEvent.Kind.MOVED
                && cardEvent.cardIds().equals(List.of(10L))
                && cardEvent.sourceDeckId().equals(1L)
                && cardEvent.targetDeckId().equals(2L);
        }));
    }

    @Test
    void moveCardsBatchUsesTargetSideBDeduplicationAndBatchSeenValues() {
        Fixture fixture = new Fixture();
        Deck targetDeck = new Deck();
        targetDeck.setId(2L);
        targetDeck.setUserId(7L);
        targetDeck.setName("Target");
        when(fixture.deckMapper.findByIdAndUserId(2L, 7L)).thenReturn(targetDeck);
        DeckSettings targetSettings = new DeckSettings();
        targetSettings.setDuplicateSideAEnabled(false);
        targetSettings.setDuplicateSideBEnabled(true);
        when(fixture.deckSettingsMapper.findByDeckId(2L)).thenReturn(targetSettings);
        when(fixture.cardMapper.findByIds(List.of(10L, 11L, 12L))).thenReturn(List.of(
            cardInDeck(10L, 1L, "one", "same"),
            cardInDeck(11L, 1L, "two", " SAME "),
            cardInDeck(12L, 1L, "three", "unique")
        ));
        when(fixture.cardMapper.findDeduplicationCandidates(2L, null)).thenReturn(List.of());

        CardBatchMoveResult result = fixture.service.moveCardsBatch(1L, 2L, List.of(10L, 11L, 12L));

        assertEquals(2, result.getMovedCount());
        assertEquals(List.of(10L, 12L), result.getMovedCardIds());
        assertEquals(1, result.getDuplicateCount());
        verify(fixture.cardMapper).updateDeckId(10L, 1L, 2L);
        verify(fixture.cardMapper).updateDeckId(12L, 1L, 2L);
        verify(fixture.cardMapper, never()).updateDeckId(eq(11L), eq(1L), eq(2L));
    }

    @Test
    void moveCardsBatchRejectsSameTargetDeck() {
        Fixture fixture = new Fixture();

        AppException ex = assertThrows(AppException.class,
            () -> fixture.service.moveCardsBatch(1L, 1L, List.of(10L)));

        assertEquals(ErrorCode.DECK_MOVE_TARGET_INVALID, ex.getErrorCode());
        verify(fixture.cardMapper, never()).updateDeckId(any(), any(), any());
    }

    @Test
    void moveCardsBatchRejectsMissingTargetDeck() {
        Fixture fixture = new Fixture();
        when(fixture.deckMapper.findByIdAndUserId(2L, 7L)).thenReturn(null);

        AppException ex = assertThrows(AppException.class,
            () -> fixture.service.moveCardsBatch(1L, 2L, List.of(10L)));

        assertEquals(ErrorCode.DECK_NOT_FOUND, ex.getErrorCode());
        verify(fixture.cardMapper, never()).updateDeckId(any(), any(), any());
    }

    @Test
    void moveCardsBatchAllowsDuplicatesWhenTargetDeduplicationDisabled() {
        Fixture fixture = new Fixture();
        Deck targetDeck = new Deck();
        targetDeck.setId(2L);
        targetDeck.setUserId(7L);
        targetDeck.setName("Target");
        when(fixture.deckMapper.findByIdAndUserId(2L, 7L)).thenReturn(targetDeck);
        DeckSettings targetSettings = new DeckSettings();
        targetSettings.setDuplicateSideAEnabled(false);
        targetSettings.setDuplicateSideBEnabled(false);
        when(fixture.deckSettingsMapper.findByDeckId(2L)).thenReturn(targetSettings);
        when(fixture.cardMapper.findByIds(List.of(10L))).thenReturn(List.of(
            cardInDeck(10L, 1L, "apple", "苹果")
        ));

        CardBatchMoveResult result = fixture.service.moveCardsBatch(1L, 2L, List.of(10L));

        assertEquals(1, result.getMovedCount());
        assertEquals(0, result.getDuplicateCount());
        verify(fixture.cardMapper).updateDeckId(10L, 1L, 2L);
    }

    @Test
    void moveCardsBatchDoesNotTreatBlankDeduplicationKeysAsDuplicates() {
        Fixture fixture = new Fixture();
        Deck targetDeck = new Deck();
        targetDeck.setId(2L);
        targetDeck.setUserId(7L);
        targetDeck.setName("Target");
        when(fixture.deckMapper.findByIdAndUserId(2L, 7L)).thenReturn(targetDeck);
        DeckSettings targetSettings = new DeckSettings();
        targetSettings.setDuplicateSideAEnabled(true);
        targetSettings.setDuplicateSideBEnabled(false);
        when(fixture.deckSettingsMapper.findByDeckId(2L)).thenReturn(targetSettings);
        when(fixture.cardMapper.findByIds(List.of(10L, 11L))).thenReturn(List.of(
            cardInDeck(10L, 1L, "", "one"),
            cardInDeck(11L, 1L, " ", "two")
        ));
        when(fixture.cardMapper.findDeduplicationCandidates(2L, null)).thenReturn(List.of());
        when(fixture.cardMapper.updateDeckId(any(), any(), any())).thenReturn(1);

        CardBatchMoveResult result = fixture.service.moveCardsBatch(1L, 2L, List.of(10L, 11L));

        assertEquals(2, result.getMovedCount());
        assertEquals(0, result.getDuplicateCount());
        assertEquals(List.of(10L, 11L), result.getMovedCardIds());
    }

    @Test
    void moveCardsBatchCountsCardAsInvalidWhenSourceDeckChangedBeforeUpdate() {
        Fixture fixture = new Fixture();
        Deck targetDeck = new Deck();
        targetDeck.setId(2L);
        targetDeck.setUserId(7L);
        targetDeck.setName("Target");
        when(fixture.deckMapper.findByIdAndUserId(2L, 7L)).thenReturn(targetDeck);
        DeckSettings targetSettings = new DeckSettings();
        targetSettings.setDuplicateSideAEnabled(true);
        targetSettings.setDuplicateSideBEnabled(false);
        when(fixture.deckSettingsMapper.findByDeckId(2L)).thenReturn(targetSettings);
        when(fixture.cardMapper.findByIds(List.of(10L))).thenReturn(List.of(
            cardInDeck(10L, 1L, "apple", "苹果")
        ));
        when(fixture.cardMapper.findDeduplicationCandidates(2L, null)).thenReturn(List.of());
        when(fixture.cardMapper.updateDeckId(10L, 1L, 2L)).thenReturn(0);

        CardBatchMoveResult result = fixture.service.moveCardsBatch(1L, 2L, List.of(10L));

        assertEquals(0, result.getMovedCount());
        assertEquals(1, result.getInvalidCount());
        assertEquals(List.of(), result.getMovedCardIds());
        assertEquals("INVALID_CARD", result.getFailures().get(0).reasonCode());
    }

    private static CardBatchCreateItem item(String sideA, String sideB) {
        CardBatchCreateItem item = new CardBatchCreateItem();
        item.setSideA(sideA);
        item.setSideB(sideB);
        return item;
    }

    private static Card existingCard(Long id, String sideA, String sideB) {
        Card card = new Card();
        card.setId(id);
        card.setDeckId(1L);
        card.setSideA(sideA);
        card.setSideB(sideB);
        return card;
    }

    private static Card cardInDeck(Long id, Long deckId, String sideA, String sideB) {
        Card card = existingCard(id, sideA, sideB);
        card.setDeckId(deckId);
        return card;
    }

    /**
     * 通过反射读取新展示字段，让 RED 阶段能在字段缺失时表现为断言失败。
     */
    private static Object readDeckName(Card card) {
        try {
            return Card.class.getMethod("getDeckName").invoke(card);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static class Fixture {
        final CurrentUserService currentUserService = mock(CurrentUserService.class);
        final DeckMapper deckMapper = mock(DeckMapper.class);
        final CardMapper cardMapper = mock(CardMapper.class);
        final CardMediaMapper cardMediaMapper = mock(CardMediaMapper.class);
        final CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        final DeckSettingsMapper deckSettingsMapper = mock(DeckSettingsMapper.class);
        final PracticeReviewScheduler practiceReviewScheduler;
        final CardProgressStore cardProgressStore = mock(CardProgressStore.class);
        final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        final UserUploadMapper userUploadMapper = mock(UserUploadMapper.class);
        final UserUploadAccessGuard userUploadAccessGuard = new UserUploadAccessGuard(userUploadMapper);
        final DeckSettings settings = new DeckSettings();
        final CardServiceImpl service;

        Fixture() {
            this(PracticeReviewSchedulerConfig.defaults());
        }

        Fixture(PracticeReviewSchedulerConfig schedulerConfig) {
            practiceReviewScheduler = new PracticeReviewScheduler(schedulerConfig);
            service = new CardServiceImpl(
                currentUserService,
                deckMapper,
                cardMapper,
                cardMediaMapper,
                cardProgressMapper,
                deckSettingsMapper,
                practiceReviewScheduler,
                eventPublisher,
                cardProgressStore,
                new UploadFileDeleter(),
                userUploadAccessGuard
            );
            when(currentUserService.getCurrentUserId()).thenReturn(7L);
            Deck deck = new Deck();
            deck.setId(1L);
            deck.setUserId(7L);
            deck.setName("Default");
            when(deckMapper.findByIdAndUserId(1L, 7L)).thenReturn(deck);
            settings.setDuplicateSideAEnabled(true);
            settings.setDuplicateSideBEnabled(false);
            when(deckSettingsMapper.findByDeckId(1L)).thenReturn(settings);
            when(cardMapper.updateDeckId(any(), any(), any())).thenReturn(1);
            doAnswer(invocation -> {
                Card card = invocation.getArgument(0);
                card.setId(20L);
                when(cardMapper.findById(20L)).thenReturn(card);
                when(cardMediaMapper.findByCardId(20L)).thenReturn(List.of());
                when(cardProgressStore.ensureDirectionalProgressRows(7L, 20L)).thenReturn(List.of(
                    progress(20L, 7L, "A_TO_B"),
                    progress(20L, 7L, "B_TO_A")
                ));
                return 1;
            }).when(cardMapper).insert(any(Card.class));
        }

        private static CardProgress progress(Long cardId, Long userId, String direction) {
            return CardServiceDeduplicationTest.progress(cardId, userId, direction, "new", null, null);
        }
    }

    private static CardProgress progress(
            Long cardId,
            Long userId,
            String direction,
            String state,
            LocalDate firstLearnedDate,
            LocalDate nextReviewDate) {
        return progress(cardId, userId, direction, state, firstLearnedDate, firstLearnedDate, nextReviewDate);
    }

    private static CardProgress progress(
            Long cardId,
            Long userId,
            String direction,
            String state,
            LocalDate firstLearnedDate,
            LocalDate lastReviewDate,
            LocalDate nextReviewDate) {
        CardProgress progress = new CardProgress();
        progress.setCardId(cardId);
        progress.setUserId(userId);
        progress.setDirection(direction);
        progress.setState(state);
        progress.setStability(0.0);
        progress.setDifficulty(0.0);
        progress.setFirstLearnedDate(firstLearnedDate);
        progress.setLastReviewDate(lastReviewDate);
        progress.setNextReviewDate(nextReviewDate);
        progress.setLastRating(3);
        return progress;
    }

    private static CardProgress progress(
            Long cardId,
            Long userId,
            String direction,
            String state,
            LocalDate firstLearnedDate,
            LocalDate lastReviewDate,
            LocalDate nextReviewDate,
            int lastRating,
            double stability) {
        CardProgress progress = progress(cardId, userId, direction, state, firstLearnedDate, lastReviewDate,
            nextReviewDate);
        progress.setLastRating(lastRating);
        progress.setStability(stability);
        return progress;
    }
}
