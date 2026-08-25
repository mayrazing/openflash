package openflash_core.service.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import openflash_core.entity.Card;
import openflash_core.entity.CardDirectionProgresses;
import openflash_core.entity.CardFsrs;
import openflash_core.entity.DirectionProgressSnapshot;
import openflash_core.entity.PracticeReviewLoad;
import openflash_core.entity.PracticeReviewSchedule;

@Component
public class PracticeReviewScheduler {

    private static final double HIGH_RISK_STABILITY_DAYS = 21.0;
    private final PracticeReviewSchedulerConfig config;

    /**
     * 使用内存中的调度配置创建纯调度器，调度过程不触发数据库读取。
     */
    public PracticeReviewScheduler(PracticeReviewSchedulerConfig config) {
        this.config = config;
    }

    /**
     * 根据已加载方向进度的卡片生成当天复习和新卡队列，不访问数据库也不修改进度。
     */
    public PracticeReviewSchedule schedule(
        List<Card> reviewSourceCards,
        List<Card> newSourceCards,
        int requestedNewCardCount,
        LocalDate today
    ) {
        return schedule(reviewSourceCards, newSourceCards, requestedNewCardCount, today, null);
    }

    /**
     * 根据用户学习强度生成当天复习和新卡队列，不访问数据库也不修改进度。
     */
    public PracticeReviewSchedule schedule(
        List<Card> reviewSourceCards,
        List<Card> newSourceCards,
        int requestedNewCardCount,
        LocalDate today,
        String reviewLoadProfile
    ) {
        return schedule(reviewSourceCards, newSourceCards, requestedNewCardCount, today, reviewLoadProfile, 1);
    }

    /**
     * 根据用户学习强度和新卡方向成本生成当天队列，不访问数据库也不修改进度。
     */
    public PracticeReviewSchedule schedule(
        List<Card> reviewSourceCards,
        List<Card> newSourceCards,
        int requestedNewCardCount,
        LocalDate today,
        String reviewLoadProfile,
        int newCardDirectionCost
    ) {
        PracticeReviewSchedulerConfig effectiveConfig = config.withLoadProfile(reviewLoadProfile);
        List<DirectionCandidate> dueCandidates = dueCandidates(reviewSourceCards, today, effectiveConfig);
        List<DirectionCandidate> selectedDirections = selectReviewDirections(dueCandidates, effectiveConfig, today);
        int backlogDirectionCount = dueCandidates.size() - selectedDirections.size();
        int backlogCardCount = countBacklogCards(dueCandidates, selectedDirections);
        boolean newCardsPaused = shouldPauseNewCards(backlogDirectionCount, effectiveConfig);
        List<Card> reviewCards = reviewCards(selectedDirections);
        List<Card> newCards = selectNewCards(
            newSourceCards,
            requestedNewCardCount,
            effectiveConfig.absoluteDailyDirections() - selectedDirections.size(),
            newCardsPaused,
            newCardDirectionCost
        );
        PracticeReviewLoad load = new PracticeReviewLoad(
            selectedDirections.size(),
            backlogDirectionCount,
            backlogCardCount,
            newCardsPaused,
            effectiveConfig.targetDailyDirections(),
            effectiveConfig.absoluteDailyDirections()
        );
        return new PracticeReviewSchedule(reviewCards, newCards, load);
    }

    /**
     * 扣除今日已做方向数后再调度；target 用完后普通复习和新卡不再出现，高风险仍可在 absolute 剩余额度内追加。
     * 注：不用 PracticeReviewSchedulerConfig 持有门控值，绕开 compact constructor 的 target>=1 clamp。
     */
    public PracticeReviewSchedule schedule(
        List<Card> reviewSourceCards,
        List<Card> newSourceCards,
        int requestedNewCardCount,
        LocalDate today,
        String reviewLoadProfile,
        int newCardDirectionCost,
        int alreadyReviewedToday
    ) {
        if (alreadyReviewedToday <= 0) {
            return schedule(
                reviewSourceCards,
                newSourceCards,
                requestedNewCardCount,
                today,
                reviewLoadProfile,
                newCardDirectionCost
            );
        }
        return scheduleWithReviewedGate(
            reviewSourceCards,
            newSourceCards,
            requestedNewCardCount,
            today,
            reviewLoadProfile,
            newCardDirectionCost,
            alreadyReviewedToday,
            true
        );
    }

    private PracticeReviewSchedule scheduleWithReviewedGate(
        List<Card> reviewSourceCards,
        List<Card> newSourceCards,
        int requestedNewCardCount,
        LocalDate today,
        String reviewLoadProfile,
        int newCardDirectionCost,
        int alreadyReviewedToday,
        boolean gateNewCardsByTarget
    ) {
        PracticeReviewSchedulerConfig effectiveConfig = config.withLoadProfile(reviewLoadProfile);
        int gatedTarget = Math.max(0, effectiveConfig.targetDailyDirections() - alreadyReviewedToday);
        int gatedAbsolute = Math.max(0, effectiveConfig.absoluteDailyDirections() - alreadyReviewedToday);
        int newCardCapacity = gateNewCardsByTarget ? gatedTarget : gatedAbsolute;

        List<DirectionCandidate> dueCandidates = dueCandidates(reviewSourceCards, today, effectiveConfig);
        List<DirectionCandidate> selectedDirections = selectReviewDirectionsWithLimits(
            dueCandidates, effectiveConfig, today, gatedTarget, gatedAbsolute
        );
        int backlogDirectionCount = dueCandidates.size() - selectedDirections.size();
        int backlogCardCount = countBacklogCards(dueCandidates, selectedDirections);
        boolean newCardsPaused = shouldPauseNewCards(backlogDirectionCount, effectiveConfig);
        List<Card> reviewCards = reviewCards(selectedDirections);
        List<Card> newCards = selectNewCards(
            newSourceCards,
            requestedNewCardCount,
            newCardCapacity - selectedDirections.size(),
            newCardsPaused,
            newCardDirectionCost
        );
        PracticeReviewLoad load = new PracticeReviewLoad(
            selectedDirections.size(),
            backlogDirectionCount,
            backlogCardCount,
            newCardsPaused,
            gatedTarget,
            gatedAbsolute
        );
        return new PracticeReviewSchedule(reviewCards, newCards, load);
    }

    /**
     * 提取所有到期方向，过滤未学和已掌握方向，避免它们挤占复习队列。
     */
    private List<DirectionCandidate> dueCandidates(List<Card> cards, LocalDate today, PracticeReviewSchedulerConfig config) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        return cards.stream()
            .filter(Objects::nonNull)
            .filter(this::isReviewableCard)
            .flatMap(card -> directionCandidates(card).stream())
            .filter(candidate -> isReviewable(candidate.progress()))
            .filter(candidate -> isDue(candidate.progress(), today))
            .sorted(directionPriority(today, config))
            .toList();
    }

    /**
     * 判断卡片本身是否可复习，新卡和已掌握卡整张排除，避免方向进度误入队列。
     */
    private boolean isReviewableCard(Card card) {
        String state = card.getState();
        return !"new".equals(state) && !"mastered".equals(state) && !"graduated".equals(state);
    }

    /**
     * 从卡片双方向进度生成方向候选，让 A->B 与 B->A 可以独立排序。
     */
    private List<DirectionCandidate> directionCandidates(Card card) {
        CardDirectionProgresses progresses = card.getDirectionProgresses();
        if (progresses == null) {
            return List.of();
        }
        List<DirectionCandidate> candidates = new ArrayList<>(2);
        if (progresses.getA2b() != null) {
            candidates.add(new DirectionCandidate(card, "A_TO_B", progresses.getA2b()));
        }
        if (progresses.getB2a() != null) {
            candidates.add(new DirectionCandidate(card, "B_TO_A", progresses.getB2a()));
        }
        return candidates;
    }

    /**
     * 判断方向是否可以进入复习队列，新方向和已掌握方向不参与当天复习排序。
     */
    private boolean isReviewable(DirectionProgressSnapshot progress) {
        String state = state(progress);
        // defensive: card-level isReviewableCard already excludes graduated cards, but guard here too in case direction state diverges
        return !"new".equals(state) && !"mastered".equals(state) && !"graduated".equals(state);
    }

    /**
     * 判断方向是否已经到期，未设置下次复习日期的方向不强行纳入。
     */
    private boolean isDue(DirectionProgressSnapshot progress, LocalDate today) {
        CardFsrs fsrs = fsrs(progress);
        LocalDate nextReviewDate = fsrs == null ? null : fsrs.getNextReviewDate();
        return nextReviewDate != null && !nextReviewDate.isAfter(today);
    }

    /**
     * 按用户当天看到的优先级排序：高风险先做，越拖越久越靠前，稳定度低优先。
     */
    private Comparator<DirectionCandidate> directionPriority(LocalDate today, PracticeReviewSchedulerConfig config) {
        return Comparator
            .comparing((DirectionCandidate candidate) -> !isHighRisk(candidate, today, config))
            .thenComparing(Comparator.comparingLong((DirectionCandidate candidate) -> overdueDays(candidate, today)).reversed())
            .thenComparingDouble(this::stability)
            .thenComparingInt(this::lastRating)
            .thenComparing(candidate -> cardId(candidate.card()), Comparator.nullsLast(Long::compareTo))
            .thenComparing(DirectionCandidate::direction);
    }

    /**
     * 先选到目标复习量，再只追加高风险方向直到绝对上限。
     */
    private List<DirectionCandidate> selectReviewDirections(
        List<DirectionCandidate> dueCandidates,
        PracticeReviewSchedulerConfig config,
        LocalDate today
    ) {
        return selectReviewDirectionsWithLimits(
            dueCandidates,
            config,
            today,
            config.targetDailyDirections(),
            config.absoluteDailyDirections()
        );
    }

    /**
     * 与 selectReviewDirections() 逻辑相同，但 target/absolute 以参数形式传入，允许为 0。
     */
    private List<DirectionCandidate> selectReviewDirectionsWithLimits(
        List<DirectionCandidate> dueCandidates,
        PracticeReviewSchedulerConfig config,
        LocalDate today,
        int target,
        int absolute
    ) {
        List<DirectionCandidate> selected = new ArrayList<>();
        for (DirectionCandidate candidate : dueCandidates) {
            if (selected.size() < target) {
                selected.add(candidate);
                continue;
            }
            if (selected.size() < absolute && isHighRisk(candidate, today, config)) {
                selected.add(candidate);
            }
        }
        return selected;
    }

    /**
     * 判断方向是否当天必须复习，避免低稳定度或已答错内容被平滑延期。
     */
    private boolean isHighRisk(DirectionCandidate candidate, LocalDate today, PracticeReviewSchedulerConfig config) {
        String state = state(candidate.progress());
        return "learning".equals(state)
            || "relearning".equals(state)
            || lastRating(candidate) <= 1
            || stability(candidate) < HIGH_RISK_STABILITY_DAYS
            || overdueDays(candidate, today) > config.maxDeferralDays();
    }

    /**
     * 计算方向已过期天数，排序时让拖延更久的方向优先出现。
     */
    private long overdueDays(DirectionCandidate candidate, LocalDate today) {
        CardFsrs fsrs = fsrs(candidate.progress());
        LocalDate nextReviewDate = fsrs == null ? null : fsrs.getNextReviewDate();
        if (nextReviewDate == null || nextReviewDate.isAfter(today)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(nextReviewDate, today);
    }

    /**
     * 读取方向稳定度，缺失时按最大稳定处理，避免空值排到最前。
     */
    private double stability(DirectionCandidate candidate) {
        CardFsrs fsrs = fsrs(candidate.progress());
        Double stability = fsrs == null ? null : fsrs.getStability();
        return stability == null ? Double.MAX_VALUE : stability;
    }

    /**
     * 读取方向上次评分，缺失时按已通过处理，避免空值被误判成高风险。
     */
    private int lastRating(DirectionCandidate candidate) {
        CardFsrs fsrs = fsrs(candidate.progress());
        Integer lastRating = fsrs == null ? null : fsrs.getLastRating();
        return lastRating == null ? Integer.MAX_VALUE : lastRating;
    }

    /**
     * 判断新卡是否因当天积压达到暂停线而暂停。当前调度器不保存昨天是否已暂停，只做无状态日内计算；
     * 恢复线仍保留在配置中并参与读取和 clamp，供未来持久化暂停状态使用，不让 40-119 积压直接判暂停。
     */
    private boolean shouldPauseNewCards(int backlogDirectionCount, PracticeReviewSchedulerConfig config) {
        return backlogDirectionCount >= config.backlogPauseNewThreshold();
    }

    /**
     * 计算还有到期方向没排进今天的卡片数量，保证页面积压数字和其他统计一样按卡去重。
     */
    private int countBacklogCards(List<DirectionCandidate> dueCandidates, List<DirectionCandidate> selectedDirections) {
        Set<DirectionKey> selectedKeys = new HashSet<>();
        for (DirectionCandidate candidate : selectedDirections) {
            Long id = cardId(candidate.card());
            if (id != null) {
                selectedKeys.add(new DirectionKey(id, candidate.direction()));
            }
        }

        Set<Long> backlogCardIds = new HashSet<>();
        for (DirectionCandidate candidate : dueCandidates) {
            Long id = cardId(candidate.card());
            if (id != null && !selectedKeys.contains(new DirectionKey(id, candidate.direction()))) {
                backlogCardIds.add(id);
            }
        }
        return backlogCardIds.size();
    }

    /**
     * 把已选方向合并成卡片副本，只保留真正入队的方向，避免页面重新带回未选方向。
     */
    private List<Card> reviewCards(List<DirectionCandidate> selectedDirections) {
        Map<Long, Card> copiesById = new LinkedHashMap<>();
        for (DirectionCandidate candidate : selectedDirections) {
            Long id = cardId(candidate.card());
            if (id != null) {
                Card copy = copiesById.computeIfAbsent(id, ignored -> copyCardBase(candidate.card()));
                keepSelectedDirection(copy, candidate);
            }
        }
        return List.copyOf(copiesById.values());
    }

    /**
     * 复制页面展示需要的卡片基础字段，不复用输入卡片对象。
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
        copy.setSideAImage(copyStringList(source.getSideAImage()));
        copy.setSideBImage(copyStringList(source.getSideBImage()));
        copy.setState(source.getState());
        copy.setFsrs(source.getFsrs());
        copy.setDirectionProgresses(new CardDirectionProgresses());
        copy.setFirstLearnedDate(source.getFirstLearnedDate());
        copy.setMasteredAt(source.getMasteredAt());
        copy.setTodayCalculated(source.getTodayCalculated());
        return copy;
    }

    /**
     * 复制图片列表，避免返回副本和输入卡片共享可变列表容器。
     */
    private List<String> copyStringList(List<String> values) {
        return values == null ? null : new ArrayList<>(values);
    }

    /**
     * 按已选方向写入副本方向进度，未选方向保持为空。
     */
    private void keepSelectedDirection(Card copy, DirectionCandidate candidate) {
        CardDirectionProgresses progresses = copy.getDirectionProgresses();
        if ("A_TO_B".equals(candidate.direction())) {
            progresses.setA2b(candidate.progress());
            return;
        }
        if ("B_TO_A".equals(candidate.direction())) {
            progresses.setB2a(candidate.progress());
        }
    }

    /**
     * 按剩余容量截取新卡，复习积压暂停时直接返回空列表。
     */
    private List<Card> selectNewCards(
        List<Card> newSourceCards,
        int requestedNewCardCount,
        int remainingCapacity,
        boolean newCardsPaused,
        int newCardDirectionCost
    ) {
        if (newCardsPaused || newSourceCards == null || requestedNewCardCount <= 0 || remainingCapacity <= 0) {
            return List.of();
        }
        int safeDirectionCost = Math.max(1, newCardDirectionCost);
        int capacityByDirectionCost = remainingCapacity / safeDirectionCost;
        if (capacityByDirectionCost <= 0) {
            return List.of();
        }
        int limit = Math.min(Math.min(requestedNewCardCount, capacityByDirectionCost), newSourceCards.size());
        return List.copyOf(newSourceCards.subList(0, limit));
    }

    /**
     * 返回方向状态，优先使用方向快照，缺失时使用 FSRS 状态。
     */
    private String state(DirectionProgressSnapshot progress) {
        if (progress == null) {
            return null;
        }
        if (progress.getState() != null) {
            return progress.getState();
        }
        CardFsrs fsrs = progress.getFsrs();
        return fsrs == null ? null : fsrs.getState();
    }

    /**
     * 返回方向 FSRS 数据，集中处理空进度，避免每条规则重复判空。
     */
    private CardFsrs fsrs(DirectionProgressSnapshot progress) {
        return progress == null ? null : progress.getFsrs();
    }

    /**
     * 返回卡片 id，集中处理空卡片，保证排序和去重逻辑一致。
     */
    private Long cardId(Card card) {
        return card == null ? null : card.getId();
    }

    private record DirectionCandidate(Card card, String direction, DirectionProgressSnapshot progress) {
    }

    private record DirectionKey(Long cardId, String direction) {
    }
}
