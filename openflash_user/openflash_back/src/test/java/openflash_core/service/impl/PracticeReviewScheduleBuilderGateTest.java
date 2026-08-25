package openflash_core.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import openflash_core.entity.Card;
import openflash_core.entity.CardDirectionProgresses;
import openflash_core.entity.CardFsrs;
import openflash_core.entity.DeckSettings;
import openflash_core.entity.DirectionProgressSnapshot;
import openflash_core.entity.PracticeReviewLoad;
import openflash_core.entity.PracticeReviewSchedule;
import openflash_core.mapper.CardProgressMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.service.CurrentUserService;

class PracticeReviewScheduleBuilderGateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 13);
    private static final long USER_ID = 7L;

    /**
     * 今日已做数达到目标时，低风险到期复习不再出现在页面负载里。
     */
    @Test
    void mapperReviewedCountGatesLowRiskDueReviews() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(2, 5, 3, 120, 40));
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        when(currentUserService.getCurrentUserId()).thenReturn(USER_ID);
        when(cardProgressMapper.countReviewedDirectionsToday(eq(USER_ID), eq(TODAY), any(Long.class))).thenReturn(2);
        PracticeReviewScheduleBuilder builder = new PracticeReviewScheduleBuilder(
            scheduler, currentUserService, null, cardProgressMapper
        );

        PracticeReviewSchedule schedule = builder.schedule(lowRiskDueCards(3), 0, TODAY, "random", 1L);

        assertEquals(0, schedule.reviewCards().size());
        assertEquals(0, schedule.load().targetReviewItemCount());
    }

    /**
     * 旧构造未注入 mapper 时沿用今日已做数为 0，低风险到期复习正常展示。
     */
    @Test
    void oldConstructorFallsBackToZeroReviewedToday() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(2, 5, 3, 120, 40));
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        PracticeReviewScheduleBuilder builder = new PracticeReviewScheduleBuilder(
            scheduler, currentUserService, null
        );

        PracticeReviewSchedule schedule = builder.schedule(lowRiskDueCards(3), 0, TODAY, "random");

        assertEquals(2, schedule.reviewCards().size());
        assertEquals(2, schedule.load().targetReviewItemCount());
    }

    /**
     * 不同卡包的学习强度会改变当天复习负载。
     */
    @Test
    void deckLoadProfileControlsReviewLoad() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(PracticeReviewSchedulerConfig.defaults());
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        DeckSettingsMapper deckSettingsMapper = mock(DeckSettingsMapper.class);
        when(currentUserService.getCurrentUserId()).thenReturn(USER_ID);
        when(deckSettingsMapper.findByDeckId(1L)).thenReturn(deckSettings("relaxed"));
        when(deckSettingsMapper.findByDeckId(2L)).thenReturn(deckSettings("intensive"));
        PracticeReviewScheduleBuilder builder = new PracticeReviewScheduleBuilder(
            scheduler, currentUserService, deckSettingsMapper
        );

        PracticeReviewSchedule relaxed = builder.schedule(lowRiskDueCards(60), 0, TODAY, "random", 1L);
        PracticeReviewSchedule intensive = builder.schedule(lowRiskDueCards(60), 0, TODAY, "random", 2L);

        assertEquals(30, relaxed.load().targetReviewItemCount());
        assertEquals(60, intensive.load().targetReviewItemCount());
    }

    /**
     * A 卡包今日额度耗尽后，B 卡包的新卡不应受影响——额度统计须按卡包隔离。
     */
    @Test
    void dailyTargetExhaustedOnDeckADoesNotBlockNewCardsOnDeckB() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(2, 5, 3, 120, 40));
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        CardProgressMapper cardProgressMapper = mock(CardProgressMapper.class);
        when(currentUserService.getCurrentUserId()).thenReturn(USER_ID);
        when(cardProgressMapper.countReviewedDirectionsToday(USER_ID, TODAY, 1L)).thenReturn(2);
        when(cardProgressMapper.countReviewedDirectionsToday(USER_ID, TODAY, 2L)).thenReturn(0);
        PracticeReviewScheduleBuilder builder = new PracticeReviewScheduleBuilder(
            scheduler, currentUserService, null, cardProgressMapper
        );

        PracticeReviewSchedule deck2Schedule = builder.schedule(newStateCards(3), 2, TODAY, "random", 2L);

        assertEquals(2, deck2Schedule.newCards().size(), "B 卡包未消耗额度，新卡应正常出现");
    }

    /**
     * 已毕业卡不再通过另一面首次练习规则出现在页面待练习数字里。
     */
    @Test
    void graduatedCardDoesNotCountAsPendingNewDirection() {
        PracticeReviewScheduler scheduler = new PracticeReviewScheduler(config(2, 5, 3, 120, 40));
        PracticeReviewScheduleBuilder builder = new PracticeReviewScheduleBuilder(
            scheduler, null, null
        );
        Card graduated = graduatedCardWithPendingNewDirection();
        PracticeReviewSchedule schedule = new PracticeReviewSchedule(
            List.of(),
            List.of(),
            new PracticeReviewLoad(0, 0, 0, false, 1, 5)
        );

        assertEquals(0, builder.countPendingReviewCards(List.of(graduated), schedule));
    }

    /**
     * 创建调度器配置，便于测试用小额度观察页面负载变化。
     */
    private static PracticeReviewSchedulerConfig config(
        int target,
        int max,
        int deferralDays,
        int pause,
        int resume
    ) {
        return new PracticeReviewSchedulerConfig(target, max, deferralDays, pause, resume);
    }

    /**
     * 创建多张低风险到期卡，用来验证额度门控只影响展示数量。
     */
    private static List<Card> lowRiskDueCards(int count) {
        return java.util.stream.LongStream.rangeClosed(1, count)
            .mapToObj(PracticeReviewScheduleBuilderGateTest::lowRiskDueCard)
            .toList();
    }

    private static DeckSettings deckSettings(String profile) {
        DeckSettings settings = new DeckSettings();
        settings.setReviewLoadProfile(profile);
        return settings;
    }

    /**
     * 创建一张低风险到期卡，只让 A 到 B 方向进入复习队列。
     */
    private static Card lowRiskDueCard(long id) {
        Card card = new Card();
        card.setId(id);
        card.setState("learning");
        CardDirectionProgresses progresses = new CardDirectionProgresses();
        progresses.setA2b(directionSnapshot("review", 3, 80.0, TODAY.minusDays(1), TODAY));
        progresses.setB2a(directionSnapshot("review", 3, 80.0, TODAY.minusDays(10), TODAY.plusDays(30)));
        card.setDirectionProgresses(progresses);
        return card;
    }

    /**
     * 创建多张全新状态卡片，用于验证新卡包不受其他卡包已用额度影响。
     */
    private static List<Card> newStateCards(int count) {
        return java.util.stream.LongStream.rangeClosed(1, count)
            .mapToObj(id -> {
                Card card = new Card();
                card.setId(id);
                card.setState("new");
                CardDirectionProgresses progresses = new CardDirectionProgresses();
                DirectionProgressSnapshot newSnap = new DirectionProgressSnapshot();
                newSnap.setState("new");
                progresses.setA2b(newSnap);
                progresses.setB2a(newSnap);
                card.setDirectionProgresses(progresses);
                return card;
            })
            .toList();
    }

    /**
     * 创建已毕业但仍带一面 new 的异常数据，验证页面不会露出无效练习入口。
     */
    private static Card graduatedCardWithPendingNewDirection() {
        Card card = new Card();
        card.setId(99L);
        card.setState("graduated");
        CardDirectionProgresses progresses = new CardDirectionProgresses();
        progresses.setA2b(directionSnapshot("graduated", 3, 200.0, TODAY.minusDays(180), TODAY.plusDays(200)));
        progresses.setB2a(directionSnapshot("new", 0, 0.0, null, null));
        card.setDirectionProgresses(progresses);
        return card;
    }

    /**
     * 创建方向进度快照，保证 Builder 与 Scheduler 都能按已学方向处理。
     */
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
