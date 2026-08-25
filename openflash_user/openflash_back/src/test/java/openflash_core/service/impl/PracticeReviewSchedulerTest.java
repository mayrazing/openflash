package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import openflash_core.entity.Card;
import openflash_core.entity.CardDirectionProgresses;
import openflash_core.entity.CardFsrs;
import openflash_core.entity.DirectionProgressSnapshot;
import openflash_core.entity.PracticeReviewSchedule;

class PracticeReviewSchedulerTest {

    private final LocalDate today = LocalDate.of(2026, 5, 13);

    @Test
    void keepsAllDueDirectionsWhenUnderTarget() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(40, 70, 3, 120, 40));
        PracticeReviewSchedule schedule = scheduler.schedule(List.of(
            dueCard(1L, "A_TO_B", "review", 2, 10.0, today.minusDays(1), today),
            dueCard(2L, "A_TO_B", "review", 3, 30.0, today.minusDays(3), today)
        ), List.of(), 10, today);

        assertEquals(2, schedule.reviewCards().size());
        assertEquals(0, schedule.backlogDirectionCount());
        assertFalse(schedule.newCardsPaused());
    }

    @Test
    void relaxedProfileOverridesDailyTargetAndLimit() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(40, 70, 3, 120, 40));
        List<Card> cards = java.util.stream.LongStream.rangeClosed(1, 50)
            .mapToObj(id -> dueCard(id, "A_TO_B", "review", 3, 80.0, today.minusDays(1), today))
            .toList();

        PracticeReviewSchedule schedule = scheduler.schedule(cards, List.of(), 10, today, "relaxed");

        assertEquals(30, schedule.load().selectedReviewDirectionCount());
        assertEquals(20, schedule.backlogDirectionCount());
        assertEquals(30, schedule.load().targetReviewItemCount());
        assertEquals(45, schedule.load().maxReviewItemCount());
    }

    /**
     * 验证今日已做数达到目标后，低风险复习和新词都不进入队列。
     */
    @Test
    void alreadyReviewedTodayEqualToTargetResultsInEmptyQueue() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(40, 70, 3, 120, 40));
        List<Card> dueCards = java.util.stream.LongStream.rangeClosed(1, 10)
            .mapToObj(id -> dueCard(id, "A_TO_B", "review", 3, 30.0, today.minusDays(1), today))
            .toList();
        List<Card> newCards = List.of(newCard(100L));

        PracticeReviewSchedule schedule = scheduler.schedule(
            dueCards, newCards, 5, today, null, 1, 40
        );

        assertEquals(0, schedule.reviewCards().size());
        assertEquals(0, schedule.newCards().size());
    }

    /**
     * 验证今日已做数为 0 时，7 参调度保持 6 参调度表现。
     */
    @Test
    void alreadyReviewedZeroPreservesOriginalBehavior() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(40, 70, 3, 120, 40));
        List<Card> dueCards = java.util.stream.LongStream.rangeClosed(1, 50)
            .mapToObj(id -> dueCard(id, "A_TO_B", "review", 3, 30.0, today.minusDays(1), today))
            .toList();
        List<Card> newCards = java.util.stream.LongStream.rangeClosed(100, 109)
            .mapToObj(PracticeReviewSchedulerTest::newCard)
            .toList();

        PracticeReviewSchedule old6 = scheduler.schedule(dueCards, newCards, 5, today, null, 1);
        PracticeReviewSchedule new7 = scheduler.schedule(dueCards, newCards, 5, today, null, 1, 0);

        assertEquals(old6.reviewCards().size(), new7.reviewCards().size());
        assertEquals(old6.newCards().size(), new7.newCards().size());
        assertEquals(old6.load().backlogDirectionCount(), new7.load().backlogDirectionCount());
        assertEquals(old6.load().targetReviewItemCount(), new7.load().targetReviewItemCount());
        assertEquals(old6.load().maxReviewItemCount(), new7.load().maxReviewItemCount());
    }

    /**
     * 验证今日已做数超过目标但未达上限时，只保留高风险复习。
     */
    @Test
    void alreadyReviewedBetweenTargetAndAbsoluteAllowsHighRiskOnly() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(40, 70, 3, 120, 40));

        List<Card> highRisk = java.util.stream.LongStream.rangeClosed(1, 10)
            .mapToObj(id -> dueCard(id, "A_TO_B", "review", 1, 5.0, today.minusDays(1), today))
            .toList();
        List<Card> lowRisk = java.util.stream.LongStream.rangeClosed(11, 20)
            .mapToObj(id -> dueCard(id, "A_TO_B", "review", 3, 80.0, today.minusDays(1), today))
            .toList();
        List<Card> all = new java.util.ArrayList<>();
        all.addAll(highRisk);
        all.addAll(lowRisk);
        List<Card> newCards = List.of(newCard(100L));

        PracticeReviewSchedule schedule = scheduler.schedule(all, newCards, 5, today, null, 1, 45);

        assertEquals(10, schedule.reviewCards().size());
        assertEquals(0, schedule.newCards().size());
        assertTrue(schedule.reviewCards().stream()
            .allMatch(c -> c.getId() >= 1 && c.getId() <= 10),
            "只有高风险卡（id 1-10）应在队列中");
    }

    /**
     * 验证今日已做数达到绝对上限后，复习和新词都不进入队列。
     */
    @Test
    void alreadyReviewedAbsoluteOrMoreResultsInCompletelyEmptyQueue() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(40, 70, 3, 120, 40));
        List<Card> highRisk = java.util.stream.LongStream.rangeClosed(1, 5)
            .mapToObj(id -> dueCard(id, "A_TO_B", "relearning", 0, 3.0, today.minusDays(5), today))
            .toList();

        PracticeReviewSchedule schedule = scheduler.schedule(highRisk, List.of(newCard(99L)), 5, today, null, 1, 70);

        assertEquals(0, schedule.reviewCards().size());
        assertEquals(0, schedule.newCards().size());
    }

    /**
     * 验证积压展示按卡片去重，即同一张卡两面都没排上时页面只显示 1 张积压卡。
     */
    @Test
    void backlogCardCountDeduplicatesDeferredDirectionsByCard() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(40, 70, 3, 120, 40));

        PracticeReviewSchedule schedule = scheduler.schedule(
            List.of(dualDueCard(1L)), List.of(), 10, today, null, 1, 70
        );

        assertEquals(2, schedule.backlogDirectionCount());
        assertEquals(1, schedule.backlogCardCount());
        assertEquals(1, schedule.load().backlogCardCount());
    }

    @Test
    void highRiskDirectionsStayAheadOfLowRiskDirections() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(1, 70, 3, 120, 40));
        PracticeReviewSchedule schedule = scheduler.schedule(List.of(
            dueCard(1L, "A_TO_B", "review", 3, 80.0, today.minusDays(1), today),
            dueCard(2L, "A_TO_B", "review", 1, 8.0, today.minusDays(1), today)
        ), List.of(), 10, today);

        assertEquals(List.of(2L), schedule.reviewCards().stream().map(Card::getId).toList());
        assertEquals(1, schedule.backlogDirectionCount());
    }

    @Test
    void pausesNewCardsWhenBacklogReachesPauseThreshold() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(1, 70, 3, 2, 1));
        PracticeReviewSchedule schedule = scheduler.schedule(List.of(
            dueCard(1L, "A_TO_B", "review", 3, 80.0, today.minusDays(1), today),
            dueCard(2L, "A_TO_B", "review", 3, 80.0, today.minusDays(1), today),
            dueCard(3L, "A_TO_B", "review", 3, 80.0, today.minusDays(1), today)
        ), List.of(newCard(10L)), 10, today);

        assertTrue(schedule.newCardsPaused());
        assertEquals(0, schedule.newCards().size());
    }

    @Test
    void keepsNewCardsActiveWhenBacklogIsBelowPauseThresholdEvenAboveResume() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(1, 70, 3, 5, 2));
        PracticeReviewSchedule schedule = scheduler.schedule(List.of(
            dueCard(1L, "A_TO_B", "review", 3, 80.0, today.minusDays(1), today),
            dueCard(2L, "A_TO_B", "review", 3, 80.0, today.minusDays(1), today),
            dueCard(3L, "A_TO_B", "review", 3, 80.0, today.minusDays(1), today)
        ), List.of(newCard(10L)), 10, today);

        assertEquals(2, schedule.backlogDirectionCount());
        assertFalse(schedule.newCardsPaused());
        assertEquals(1, schedule.newCards().size());
    }

    @Test
    void keepsNewCardsActiveWhenBacklogIsWellBelowPauseThreshold() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(2, 70, 3, 5, 2));
        PracticeReviewSchedule schedule = scheduler.schedule(List.of(
            dueCard(1L, "A_TO_B", "review", 3, 80.0, today.minusDays(1), today),
            dueCard(2L, "A_TO_B", "review", 3, 80.0, today.minusDays(1), today),
            dueCard(3L, "A_TO_B", "review", 3, 80.0, today.minusDays(1), today)
        ), List.of(newCard(10L)), 10, today);

        assertEquals(1, schedule.backlogDirectionCount());
        assertFalse(schedule.newCardsPaused());
        assertEquals(1, schedule.newCards().size());
    }

    @Test
    void skipsCardsWhenCardStateIsNewOrMastered() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(40, 70, 3, 120, 40));
        Card newStateCard = dueCard(1L, "A_TO_B", "review", 1, 8.0, today.minusDays(1), today);
        newStateCard.setState("new");
        Card masteredStateCard = dueCard(2L, "A_TO_B", "review", 1, 8.0, today.minusDays(1), today);
        masteredStateCard.setState("mastered");

        PracticeReviewSchedule schedule = scheduler.schedule(List.of(newStateCard, masteredStateCard), List.of(), 10, today);

        assertEquals(0, schedule.reviewCards().size());
        assertEquals(0, schedule.backlogDirectionCount());
    }

    @Test
    void selectedCardCopyKeepsOnlySelectedDueDirection() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(1, 70, 3, 120, 40));
        Card sourceCard = dualDueCard(1L);

        PracticeReviewSchedule schedule = scheduler.schedule(List.of(sourceCard), List.of(), 10, today);

        assertEquals(1, schedule.reviewCards().size());
        assertEquals(1, schedule.backlogDirectionCount());
        Card scheduledCard = schedule.reviewCards().get(0);
        assertEquals(1L, scheduledCard.getId());
        assertEquals(sourceCard.getSideA(), scheduledCard.getSideA());
        assertEquals(sourceCard.getSideB(), scheduledCard.getSideB());
        assertEquals("review", scheduledCard.getDirectionProgresses().getA2b().getState());
        assertNull(scheduledCard.getDirectionProgresses().getB2a());
        assertEquals("review", sourceCard.getDirectionProgresses().getB2a().getState());
    }

    @Test
    void selectedCardCopyKeepsBothDirectionsWhenBothAreSelected() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(40, 70, 3, 120, 40));
        Card sourceCard = dualDueCard(1L);
        DirectionProgressSnapshot originalA2b = sourceCard.getDirectionProgresses().getA2b();
        DirectionProgressSnapshot originalB2a = sourceCard.getDirectionProgresses().getB2a();
        String originalA2bState = originalA2b.getState();
        String originalB2aState = originalB2a.getState();

        PracticeReviewSchedule schedule = scheduler.schedule(List.of(sourceCard), List.of(), 10, today);

        assertEquals(1, schedule.reviewCards().size());
        assertEquals(0, schedule.backlogDirectionCount());
        Card scheduledCard = schedule.reviewCards().get(0);
        assertNotSame(sourceCard, scheduledCard);
        assertNotNull(scheduledCard.getDirectionProgresses().getA2b());
        assertNotNull(scheduledCard.getDirectionProgresses().getB2a());
        assertSame(originalA2b, sourceCard.getDirectionProgresses().getA2b());
        assertSame(originalB2a, sourceCard.getDirectionProgresses().getB2a());
        assertEquals(originalA2bState, sourceCard.getDirectionProgresses().getA2b().getState());
        assertEquals(originalB2aState, sourceCard.getDirectionProgresses().getB2a().getState());
    }

    @Test
    void excludesGraduatedCardsFromReviewQueue() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(40, 70, 3, 120, 40));
        Card graduated = graduatedCard(1L);
        Card active = dueCard(2L, "A_TO_B", "review", 3, 30.0, today.minusDays(1), today);

        PracticeReviewSchedule schedule = scheduler.schedule(
            List.of(graduated, active), List.of(), 10, today
        );

        assertEquals(1, schedule.reviewCards().size());
        assertEquals(2L, schedule.reviewCards().get(0).getId());
    }

    private static PracticeReviewSchedulerConfig config(
        int target,
        int max,
        int deferralDays,
        int pause,
        int resume
    ) {
        return new PracticeReviewSchedulerConfig(target, max, deferralDays, pause, resume);
    }

    private Card dueCard(
        Long id,
        String direction,
        String state,
        int lastRating,
        double stability,
        LocalDate lastReviewDate,
        LocalDate nextReviewDate
    ) {
        DirectionProgressSnapshot snapshot = directionSnapshot(state, lastRating, stability, lastReviewDate, nextReviewDate);
        DirectionProgressSnapshot quiet = directionSnapshot("review", 3, 80.0, today.minusDays(10), today.plusDays(30));
        Card card = new Card();
        card.setId(id);
        card.setState("learning");
        CardDirectionProgresses progresses = new CardDirectionProgresses();
        if ("A_TO_B".equals(direction)) {
            progresses.setA2b(snapshot);
            progresses.setB2a(quiet);
        } else {
            progresses.setA2b(quiet);
            progresses.setB2a(snapshot);
        }
        card.setDirectionProgresses(progresses);
        return card;
    }

    private static Card newCard(Long id) {
        Card card = new Card();
        card.setId(id);
        card.setState("new");
        CardDirectionProgresses progresses = new CardDirectionProgresses();
        progresses.setA2b(directionSnapshot("new", 0, 0.0, null, LocalDate.of(2026, 5, 13)));
        progresses.setB2a(directionSnapshot("new", 0, 0.0, null, LocalDate.of(2026, 5, 13)));
        card.setDirectionProgresses(progresses);
        return card;
    }

    private Card dualDueCard(Long id) {
        Card card = new Card();
        card.setId(id);
        card.setDeckId(100L);
        card.setSideA("apple");
        card.setSideB("苹果");
        card.setState("learning");
        CardDirectionProgresses progresses = new CardDirectionProgresses();
        progresses.setA2b(directionSnapshot("review", 1, 8.0, today.minusDays(1), today));
        progresses.setB2a(directionSnapshot("review", 3, 80.0, today.minusDays(1), today));
        card.setDirectionProgresses(progresses);
        return card;
    }

    private Card graduatedCard(Long id) {
        Card card = new Card();
        card.setId(id);
        card.setState("graduated");
        CardDirectionProgresses progresses = new CardDirectionProgresses();
        // nextReviewDate = today，让 isDue 通过，确保是 state 排除逻辑在生效
        progresses.setA2b(directionSnapshot("graduated", 3, 200.0, today.minusDays(180), today));
        progresses.setB2a(directionSnapshot("graduated", 3, 190.0, today.minusDays(180), today));
        card.setDirectionProgresses(progresses);
        return card;
    }

    private static DirectionProgressSnapshot directionSnapshot(
        String state,
        int lastRating,
        double stability,
        LocalDate lastReviewDate,
        LocalDate nextReviewDate
    ) {
        CardFsrs fsrs = new CardFsrs();
        fsrs.setState(state);
        fsrs.setLastRating(lastRating);
        fsrs.setStability(stability);
        fsrs.setLastReviewDate(lastReviewDate);
        fsrs.setNextReviewDate(nextReviewDate);
        DirectionProgressSnapshot snapshot = new DirectionProgressSnapshot();
        snapshot.setState(state);
        snapshot.setFsrs(fsrs);
        snapshot.setFirstLearnedDate(lastReviewDate);
        return snapshot;
    }
}
