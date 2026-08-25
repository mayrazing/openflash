package openflash_core.service.impl;

import static openflash_core.entity.PracticeDirection.A_TO_B;
import static openflash_core.entity.PracticeDirection.API_A_TO_B;
import static openflash_core.entity.PracticeDirection.API_B_TO_A;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import openflash_core.entity.Card;
import openflash_core.entity.CardDirectionProgresses;
import openflash_core.entity.CardFsrs;
import openflash_core.entity.DirectionProgressSnapshot;
import openflash_core.entity.PracticeReviewSchedule;
import openflash_core.entity.DeckSettings;
import openflash_core.mapper.CardProgressMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.service.CurrentUserService;
import openflash_core.service.PracticeService;

/**
 * 统一把已加载卡片转换成当天复习调度输入，保证队列、详情和统计页使用同一套规则。
 */
public class PracticeReviewScheduleBuilder {

    private final PracticeReviewScheduler practiceReviewScheduler;
    private final CurrentUserService currentUserService;
    private final DeckSettingsMapper deckSettingsMapper;
    private final CardProgressMapper cardProgressMapper;

    /**
     * 创建当天调度构建器，用户设置缺失时学习强度交给调度器回退默认档。
     */
    public PracticeReviewScheduleBuilder(
        PracticeReviewScheduler practiceReviewScheduler,
        CurrentUserService currentUserService,
        DeckSettingsMapper deckSettingsMapper
    ) {
        this(practiceReviewScheduler, currentUserService, deckSettingsMapper, null);
    }

    /**
     * 创建当天调度构建器，并注入今日已做方向数查询能力。
     */
    public PracticeReviewScheduleBuilder(
        PracticeReviewScheduler practiceReviewScheduler,
        CurrentUserService currentUserService,
        DeckSettingsMapper deckSettingsMapper,
        CardProgressMapper cardProgressMapper
    ) {
        this.practiceReviewScheduler = practiceReviewScheduler;
        this.currentUserService = currentUserService;
        this.deckSettingsMapper = deckSettingsMapper;
        this.cardProgressMapper = cardProgressMapper;
    }

    /**
     * 按默认随机模式生成当天复习负载，供详情和统计页保持卡级口径。
     */
    public PracticeReviewSchedule schedule(List<Card> allCards, int newCardsLimit, LocalDate today) {
        return schedule(allCards, newCardsLimit, today, PracticeService.DEFAULT_MODE, null);
    }

    public PracticeReviewSchedule schedule(List<Card> allCards, int newCardsLimit, LocalDate today, String mode) {
        return schedule(allCards, newCardsLimit, today, mode, null);
    }

    /**
     * 按练习模式和卡包 ID 生成当天复习负载，从卡包设置读取学习强度。
     */
    public PracticeReviewSchedule schedule(List<Card> allCards, int newCardsLimit, LocalDate today, String mode, Long deckId) {
        return scheduleForDeck(allCards, newCardsLimit, today, mode, deckId);
    }

    private PracticeReviewSchedule scheduleForDeck(List<Card> allCards, int newCardsLimit, LocalDate today, String mode, Long deckId) {
        List<Card> reviewCards = new ArrayList<>();
        List<Card> newCards = new ArrayList<>();
        int todayLearnedNewCount = 0;
        for (Card card : allCards) {
            if (today.equals(card.getFirstLearnedDate())) {
                todayLearnedNewCount++;
            }
            if (shouldIncludeInReview(card, today)) {
                Card reviewCard = cardForMode(card, mode);
                if (hasAnyDirection(reviewCard)) {
                    reviewCards.add(reviewCard);
                }
            }
            if ("new".equals(card.getState())) {
                newCards.add(card);
            }
        }

        int remainingNewCards = Math.max(0, newCardsLimit - todayLearnedNewCount);
        Long userId = currentUserId();
        int alreadyReviewedToday = queryAlreadyReviewedToday(userId, today, deckId);
        return practiceReviewScheduler.schedule(
            reviewCards,
            newCards,
            remainingNewCards,
            today,
            currentReviewLoadProfile(deckId),
            newCardDirectionCost(mode),
            alreadyReviewedToday
        );
    }

    /**
     * 计算今天页面会展示的卡片数量，包含已安排、今天已学和另一面首次练习。
     */
    public int countTodayCards(List<Card> allCards, PracticeReviewSchedule schedule, LocalDate today) {
        Map<Long, Card> todayCards = new LinkedHashMap<>();
        addCardsById(todayCards, schedule.reviewCards());
        addCardsById(todayCards, schedule.newCards());
        for (Card card : allCards) {
            if (hasPendingNewDirectionAfterOtherStarted(card)
                || today.equals(card.getFirstLearnedDate())
                || wasReviewedToday(card, today)) {
                todayCards.put(card.getId(), card);
            }
        }
        return todayCards.size();
    }

    /**
     * 计算学习统计页待复习卡数；target 用完时只保留已安排复习卡，未用完时补入另一面首次练习卡。
     */
    public int countPendingReviewCards(List<Card> allCards, PracticeReviewSchedule schedule) {
        Map<Long, Card> pendingCards = new LinkedHashMap<>();
        addCardsById(pendingCards, schedule.reviewCards());
        if (schedule.load().targetReviewItemCount() <= 0) {
            return pendingCards.size();
        }
        for (Card card : allCards) {
            if (hasPendingNewDirectionAfterOtherStarted(card)) {
                pendingCards.put(card.getId(), card);
            }
        }
        return pendingCards.size();
    }

    /**
     * 判断卡片是否有已学一面后的另一面首次练习，保证它不被复习平滑吞掉。
     */
    public boolean hasPendingNewDirectionAfterOtherStarted(Card card) {
        if (card == null || card.getDirectionProgresses() == null) {
            return false;
        }
        if ("new".equals(card.getState()) || "mastered".equals(card.getState()) || "graduated".equals(card.getState())) {
            return false;
        }
        DirectionProgressSnapshot a2b = card.getDirectionProgresses().getA2b();
        DirectionProgressSnapshot b2a = card.getDirectionProgresses().getB2a();
        return (isDirectionNew(a2b) && isDirectionStarted(b2a))
            || (isDirectionNew(b2a) && isDirectionStarted(a2b));
    }

    /**
     * 判断指定方向是否应进入当前练习题，已开始的另一面首次练习直接纳入。
     */
    public boolean shouldIncludeSpecificDirection(Card card, String direction, LocalDate today) {
        if (card == null || card.getDirectionProgresses() == null
            || "new".equals(card.getState()) || "mastered".equals(card.getState()) || "graduated".equals(card.getState())) {
            return false;
        }
        DirectionProgressSnapshot snapshot = A_TO_B.equals(direction)
            ? card.getDirectionProgresses().getA2b()
            : card.getDirectionProgresses().getB2a();
        if (isDirectionNew(snapshot)) {
            return hasStartedOtherDirection(card, direction);
        }
        return shouldIncludeDirectionInReview(snapshot, today);
    }

    /**
     * 把卡片列表按 id 合并进目标集合，保持页面统计按卡数去重。
     */
    public void addCardsById(Map<Long, Card> cardsById, List<Card> cards) {
        for (Card card : cards) {
            if (card != null && card.getId() != null) {
                cardsById.put(card.getId(), card);
            }
        }
    }

    /**
     * 判断卡片是否今天已有任一方向复习记录。
     */
    public boolean wasReviewedToday(Card card, LocalDate today) {
        if (card == null || card.getDirectionProgresses() == null) {
            return false;
        }
        return isDirectionReviewedToday(card.getDirectionProgresses().getA2b(), today)
            || isDirectionReviewedToday(card.getDirectionProgresses().getB2a(), today);
    }

    /**
     * 读取当前用户学习强度，缺失时让调度器使用标准档。
     */
    private String currentReviewLoadProfile(Long deckId) {
        if (deckId == null || deckSettingsMapper == null) {
            return null;
        }
        DeckSettings settings = deckSettingsMapper.findByDeckId(deckId);
        return settings == null ? null : settings.getReviewLoadProfile();
    }

    /**
     * 读取当前用户 id，缺少用户服务时保持无用户调度。
     */
    private Long currentUserId() {
        return currentUserService == null ? null : currentUserService.getCurrentUserId();
    }

    /**
     * 查询当前用户今天在指定卡包内已完成的复习方向数，缺少依赖时保持旧行为。
     */
    private int queryAlreadyReviewedToday(Long userId, LocalDate today, Long deckId) {
        if (cardProgressMapper == null || userId == null || deckId == null) {
            return 0;
        }
        return cardProgressMapper.countReviewedDirectionsToday(userId, today, deckId);
    }

    /**
     * 随机模式下每张新卡会生成双方向题，单方向模式只生成一题。
     */
    private int newCardDirectionCost(String mode) {
        return PracticeService.DEFAULT_MODE.equals(mode) ? 2 : 1;
    }

    /**
     * 返回只包含当前练习模式方向的卡片副本，避免非当前方向占用当天名额。
     */
    private Card cardForMode(Card source, String mode) {
        if (PracticeService.DEFAULT_MODE.equals(mode)) {
            return source;
        }
        Card copy = copyCardBase(source);
        CardDirectionProgresses cropped = new CardDirectionProgresses();
        CardDirectionProgresses sourceProgresses = source.getDirectionProgresses();
        if (sourceProgresses != null && API_A_TO_B.equals(mode)) {
            cropped.setA2b(sourceProgresses.getA2b());
        }
        if (sourceProgresses != null && API_B_TO_A.equals(mode)) {
            cropped.setB2a(sourceProgresses.getB2a());
        }
        copy.setDirectionProgresses(cropped);
        return copy;
    }

    /**
     * 复制调度需要的卡片基础字段，方向数据由调用方按模式写入。
     */
    private Card copyCardBase(Card source) {
        Card copy = new Card();
        copy.setId(source.getId());
        copy.setDeckId(source.getDeckId());
        copy.setSideA(source.getSideA());
        copy.setSideB(source.getSideB());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setDeleted(source.getDeleted());
        copy.setSideAImage(source.getSideAImage());
        copy.setSideBImage(source.getSideBImage());
        copy.setState(source.getState());
        copy.setFsrs(source.getFsrs());
        copy.setFirstLearnedDate(source.getFirstLearnedDate());
        copy.setMasteredAt(source.getMasteredAt());
        copy.setTodayCalculated(source.getTodayCalculated());
        return copy;
    }

    /**
     * 判断卡片是否保留了任一方向进度。
     */
    private boolean hasAnyDirection(Card card) {
        return card.getDirectionProgresses() != null
            && (card.getDirectionProgresses().getA2b() != null || card.getDirectionProgresses().getB2a() != null);
    }

    /**
     * 复用练习队列规则，判断卡片是否有方向需要进入复习候选。
     */
    private boolean shouldIncludeInReview(Card card, LocalDate today) {
        if (card == null || card.getDirectionProgresses() == null) {
            return false;
        }
        if ("new".equals(card.getState()) || "mastered".equals(card.getState()) || "graduated".equals(card.getState())) {
            return false;
        }
        return shouldIncludeDirectionInReview(card.getDirectionProgresses().getA2b(), today)
            || shouldIncludeDirectionInReview(card.getDirectionProgresses().getB2a(), today)
            || hasPendingNewDirection(card);
    }

    /**
     * 判断方向是否已到今天该复习，FSRS 安排当天重做时刷新后仍进入队列。
     */
    private boolean shouldIncludeDirectionInReview(DirectionProgressSnapshot snapshot, LocalDate today) {
        if (snapshot == null || snapshot.getFsrs() == null) {
            return false;
        }
        if ("new".equals(snapshot.getState()) || "mastered".equals(snapshot.getState()) || "graduated".equals(snapshot.getState())) {
            return false;
        }
        LocalDate lastReviewDate = snapshot.getFsrs().getLastReviewDate();
        LocalDate nextReviewDate = snapshot.getFsrs().getNextReviewDate();
        return lastReviewDate != null
            && nextReviewDate != null
            && !nextReviewDate.isAfter(today);
    }

    /**
     * 判断卡片是否仍有全新方向，供候选阶段保留另一面首次练习卡。
     */
    private boolean hasPendingNewDirection(Card card) {
        return isDirectionNew(card.getDirectionProgresses().getA2b())
            || isDirectionNew(card.getDirectionProgresses().getB2a());
    }

    /**
     * 判断另一个方向是否已经开始学习。
     */
    private boolean hasStartedOtherDirection(Card card, String direction) {
        DirectionProgressSnapshot otherSnapshot = A_TO_B.equals(direction)
            ? card.getDirectionProgresses().getB2a()
            : card.getDirectionProgresses().getA2b();
        return !isDirectionNew(otherSnapshot);
    }

    /**
     * 判断方向是否仍处于新方向状态。
     */
    private boolean isDirectionNew(DirectionProgressSnapshot snapshot) {
        return snapshot != null && "new".equals(snapshot.getState());
    }

    /**
     * 判断方向是否已经开始学习。
     */
    private boolean isDirectionStarted(DirectionProgressSnapshot snapshot) {
        return snapshot != null && !isDirectionNew(snapshot);
    }

    /**
     * 判断方向最后复习日期是否为今天。
     */
    private boolean isDirectionReviewedToday(DirectionProgressSnapshot snapshot, LocalDate today) {
        CardFsrs fsrs = snapshot == null ? null : snapshot.getFsrs();
        return fsrs != null && today.equals(fsrs.getLastReviewDate());
    }
}
