package openflash_core.service.impl;

import static openflash_core.entity.PracticeDirection.A_TO_B;
import static openflash_core.entity.PracticeDirection.API_A_TO_B;
import static openflash_core.entity.PracticeDirection.API_B_TO_A;
import static openflash_core.entity.PracticeDirection.B_TO_A;
import static openflash_core.entity.PracticeDirection.normalizeStorageDirection;
import static openflash_core.entity.PracticeDirection.toApiDirection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import openflash_core.entity.Card;
import openflash_core.entity.CardFsrs;
import openflash_core.entity.CardProgress;
import openflash_core.entity.CardProgressSnapshot;
import openflash_core.entity.DeckSettings;
import openflash_core.entity.DirectionProgressSnapshot;
import openflash_core.entity.PendingPracticeSummary;
import openflash_core.entity.PracticeItem;
import openflash_core.entity.PracticeModeOption;
import openflash_core.entity.PracticeQueue;
import openflash_core.entity.PracticeReviewSchedule;
import openflash_core.entity.ProgressUpdateResult;
import openflash_core.dto.ReviewRequest;
import openflash_core.mapper.CardProgressMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.service.CardService;
import openflash_core.service.CurrentUserService;
import openflash_core.service.DeckSettingsService;
import openflash_core.service.FsrsService;
import openflash_core.service.PracticeService;
import openflash_core.service.TypeRegistryService;

/**
 * 负责双方向学习进度、掌握状态和练习题目队列。
 */
@Service
public class PracticeServiceImpl implements PracticeService {

    private static final double GRADUATION_STABILITY_DAYS = 180.0;

    private final CardService cardService;
    private final CurrentUserService currentUserService;
    private final CardProgressMapper cardProgressMapper;
    private final FsrsService fsrsService;
    private final CardProgressStore cardProgressStore;
    private final TypeRegistryService typeRegistryService;
    private final DeckSettingsMapper deckSettingsMapper;
    private final DeckSettingsService deckSettingsService;
    private final PracticeReviewScheduleBuilder reviewScheduleBuilder;

    @Autowired
    public PracticeServiceImpl(
        CardService cardService,
        CurrentUserService currentUserService,
        CardProgressMapper cardProgressMapper,
        FsrsService fsrsService,
        CardProgressStore cardProgressStore,
        TypeRegistryService typeRegistryService,
        PracticeReviewScheduler practiceReviewScheduler,
        DeckSettingsMapper deckSettingsMapper,
        DeckSettingsService deckSettingsService
    ) {
        this.cardService = cardService;
        this.currentUserService = currentUserService;
        this.cardProgressMapper = cardProgressMapper;
        this.fsrsService = fsrsService;
        this.cardProgressStore = cardProgressStore;
        this.typeRegistryService = typeRegistryService;
        this.deckSettingsMapper = deckSettingsMapper;
        this.deckSettingsService = deckSettingsService;
        this.reviewScheduleBuilder = new PracticeReviewScheduleBuilder(
            practiceReviewScheduler,
            currentUserService,
            deckSettingsMapper,
            cardProgressMapper
        );
    }

    /**
     * 保留测试使用的旧构造入口，未提供用户设置 mapper 时复习强度回退标准档。
     */
    PracticeServiceImpl(
        CardService cardService,
        CurrentUserService currentUserService,
        CardProgressMapper cardProgressMapper,
        FsrsService fsrsService,
        CardProgressStore cardProgressStore,
        TypeRegistryService typeRegistryService,
        PracticeReviewScheduler practiceReviewScheduler,
        DeckSettingsMapper deckSettingsMapper
    ) {
        this(cardService, currentUserService, cardProgressMapper, fsrsService,
            cardProgressStore, typeRegistryService, practiceReviewScheduler,
            deckSettingsMapper, new DeckSettingsServiceImpl(null, null, null));
    }

    PracticeServiceImpl(
        CardService cardService,
        CurrentUserService currentUserService,
        CardProgressMapper cardProgressMapper,
        FsrsService fsrsService,
        CardProgressStore cardProgressStore,
        TypeRegistryService typeRegistryService,
        PracticeReviewScheduler practiceReviewScheduler
    ) {
        this(
            cardService,
            currentUserService,
            cardProgressMapper,
            fsrsService,
            cardProgressStore,
            typeRegistryService,
            practiceReviewScheduler,
            null,
            new DeckSettingsServiceImpl(null, null, null)
        );
    }

    /**
     * 按所选模式返回后端权威 PracticeItem 队列。
     */
    @Override
    public PracticeQueue buildDailyQueue(Long deckId, Integer newCardsLimit, String mode) {
        LocalDate today = LocalDate.now();
        int safeLimit = normalizeNewCardsLimit(newCardsLimit);
        String safeMode = normalizeMode(mode);
        List<Card> allCards = cardService.listCards(deckId, null);
        PracticeReviewSchedule schedule = scheduleDeckReviewLoad(allCards, safeLimit, today, safeMode, deckId);
        List<PracticeItem> reviewItems = buildBaseItems(schedule.reviewCards(), false, safeMode, today);
        reviewItems.addAll(buildPendingNewDirectionItems(allCards, safeMode, schedule));
        List<PracticeItem> newItems = buildBaseItems(schedule.newCards(), true, safeMode, today);
        List<PracticeItem> items = new ArrayList<>();
        items.addAll(reviewItems);
        items.addAll(newItems);
        Collections.shuffle(items, ThreadLocalRandom.current());

        PracticeQueue queue = new PracticeQueue(
            items,
            reviewItems.size(),
            newItems.size(),
            distinctCardCount(reviewItems),
            schedule.newCards().size(),
            distinctCardCount(reviewItems) + schedule.newCards().size()
        );
        applyScheduleLoad(queue, schedule);
        return queue;
    }

    /**
     * 读取页面可选择的练习模式。
     */
    @Override
    public List<PracticeModeOption> listPracticeModes() {
        return typeRegistryService.getEnabledPracticeModes();
    }

    /**
     * 用双方向快照恢复整张卡的学习进度。
     */
    @Override
    @Transactional
    public ProgressUpdateResult updateCardProgress(Long cardId, CardProgressSnapshot snapshot) {
        if (snapshot == null || snapshot.getDirectionProgresses() == null) {
            throw new AppException(ErrorCode.PRACTICE_STATE_INVALID);
        }

        cardService.getCard(cardId);
        Long userId = currentUserService.getCurrentUserId();
        List<CardProgress> progresses = cardProgressStore.ensureDirectionalProgressRows(userId, cardId);
        applySnapshot(progresses, snapshot);
        Card updatedCard = cardService.getCard(cardId);
        return new ProgressUpdateResult(updatedCard, false);
    }

    /**
     * 只更新当前题目的方向进度，不污染另一方向。
     */
    @Override
    @Transactional
    public ProgressUpdateResult reviewCard(Long cardId, ReviewRequest request) {
        if (request == null || request.getDirection() == null || request.getDirection().isBlank()) {
            throw new AppException(ErrorCode.PRACTICE_RATING_INVALID);
        }

        Card reviewedCard = cardService.getCard(cardId);
        Long userId = currentUserService.getCurrentUserId();
        String direction = normalizeStorageDirection(request.getDirection());
        CardProgress currentProgress = cardProgressStore.getOrCreateProgress(userId, cardId, direction);
        CardProgress scheduled = fsrsService.schedule(
            currentProgress,
            request.getRating(),
            resolveTargetRetention(reviewedCard.getDeckId())
        );
        scheduled.setDirection(direction);

        if (currentProgress.getId() == null) {
            cardProgressMapper.insert(scheduled);
        } else {
            scheduled.setId(currentProgress.getId());
            cardProgressMapper.updateByUserIdAndCardIdAndDirection(scheduled);
        }

        Card updatedCard = cardService.getCard(cardId);
        int userRating = safeInt(request.getRating());
        boolean graduated = shouldMarkGraduated(updatedCard, userRating);
        if (graduated) {
            Card graduatedCard = graduateValidatedCard(cardId, userId);
            return new ProgressUpdateResult(graduatedCard, false, true);
        }
        return new ProgressUpdateResult(updatedCard, false);
    }

    /**
     * 正式评分只认卡包已保存的目标留存率，避免页面缓存或请求参数改变调度结果。
     */
    private double resolveTargetRetention(Long deckId) {
        DeckSettings settings = deckSettingsMapper == null
            ? deckSettingsService.createDefaultSettings(deckId)
            : deckSettingsMapper.findByDeckId(deckId);
        if (settings == null) {
            settings = deckSettingsService.createDefaultSettings(deckId);
        }
        return deckSettingsService.normalizeSettings(settings).getTargetRetention().doubleValue();
    }

    /**
     * 不传模式时保持卡级摘要；传模式时返回该模式的题级摘要。
     */
    @Override
    public PendingPracticeSummary getPendingPracticeSummary(Long deckId, Integer newCardsLimit, String mode) {
        if (mode == null || mode.isBlank()) {
            LocalDate today = LocalDate.now();
            int safeLimit = normalizeNewCardsLimit(newCardsLimit);
            List<Card> allCards = cardService.listCards(deckId, null);
            PracticeReviewSchedule schedule = scheduleDeckReviewLoad(allCards, safeLimit, today, deckId);
            List<PracticeItem> reviewItems = buildBaseItems(schedule.reviewCards(), false, PracticeService.DEFAULT_MODE, today);
            reviewItems.addAll(buildPendingNewDirectionItems(allCards, PracticeService.DEFAULT_MODE, schedule));
            int pendingReview = distinctCardCount(reviewItems);
            int pendingNew = schedule.newCards().size();
            PendingPracticeSummary summary = new PendingPracticeSummary(pendingReview + pendingNew, pendingNew, pendingReview);
            applyScheduleLoad(summary, schedule);
            return summary;
        }

        PracticeQueue queue = buildDailyQueue(deckId, newCardsLimit, mode);
        int pendingReview = safeInt(queue.getReviewCardCount());
        int pendingNew = safeInt(queue.getNewCardCount());
        PendingPracticeSummary summary = new PendingPracticeSummary(pendingReview + pendingNew, pendingNew, pendingReview);
        applyQueueLoad(summary, queue);
        return summary;
    }

    /**
     * 今天相关的卡片仍保持卡级语义。
     */
    @Override
    public List<Card> getTodayCardsByDeck(Long deckId, Integer newCardsLimit) {
        LocalDate today = LocalDate.now();
        List<Card> allCards = cardService.listCards(deckId, null);
        PracticeReviewSchedule schedule = reviewScheduleBuilder.schedule(
            allCards,
            normalizeNewCardsLimit(newCardsLimit),
            today,
            PracticeService.DEFAULT_MODE,
            deckId
        );
        Map<Long, Card> todayCards = new LinkedHashMap<>();
        reviewScheduleBuilder.addCardsById(todayCards, schedule.reviewCards());
        reviewScheduleBuilder.addCardsById(todayCards, schedule.newCards());

        for (Card card : allCards) {
            if (reviewScheduleBuilder.hasPendingNewDirectionAfterOtherStarted(card)
                || today.equals(card.getFirstLearnedDate())
                || reviewScheduleBuilder.wasReviewedToday(card, today)) {
                todayCards.put(card.getId(), card);
            }
        }

        return new ArrayList<>(todayCards.values());
    }

    /**
     * 只有双方向都已掌握的卡才能进入已掌握列表。
     */
    @Override
    public List<Card> listMasteredCards(String keyword) {
        Long userId = currentUserService.getCurrentUserId();
        List<Long> cardIds = cardProgressMapper.findMasteredCardIds(userId, normalizeKeyword(keyword));
        List<Card> cards = new ArrayList<>();
        for (Long cardId : cardIds) {
            cards.add(cardService.getCard(cardId));
        }
        return cards;
    }

    /**
     * 手动标记掌握时同时写两条方向进度。
     */
    @Override
    @Transactional
    public Card moveToMastered(Long cardId) {
        Card card = cardService.getCard(cardId);
        Long userId = currentUserService.getCurrentUserId();
        LocalDate today = LocalDate.now();
        LocalDateTime masteredAt = LocalDateTime.now();

        for (CardProgress progress : cardProgressStore.ensureDirectionalProgressRows(userId, cardId)) {
            if (progress.getFirstLearnedDate() == null && "new".equals(card.getState())) {
                progress.setFirstLearnedDate(today);
            }
            progress.setMasteredAt(masteredAt);
            saveProgress(progress);
        }
        return cardService.getCard(cardId);
    }

    /**
     * 将两个方向进度标记为已毕业，让用户后续练习队列不再展示这张卡。
     */
    @Override
    @Transactional
    public Card moveToGraduated(Long cardId) {
        cardService.getCard(cardId);
        Long userId = currentUserService.getCurrentUserId();
        return graduateValidatedCard(cardId, userId);
    }

    /**
     * 在调用方已校验卡片归属后，写入两个方向的毕业状态。
     */
    private Card graduateValidatedCard(Long cardId, Long userId) {
        LocalDateTime graduatedAt = LocalDateTime.now();

        for (CardProgress progress : cardProgressStore.ensureDirectionalProgressRows(userId, cardId)) {
            progress.setState("graduated");
            if (progress.getMasteredAt() == null) {
                progress.setMasteredAt(graduatedAt);
            }
            saveProgress(progress);
        }
        return cardService.getCard(cardId);
    }

    /**
     * 从已掌握卡包移回时同时重置两条方向进度。
     */
    @Override
    @Transactional
    public Card removeFromMastered(Long cardId) {
        cardService.getCard(cardId);
        Long userId = currentUserService.getCurrentUserId();
        for (CardProgress progress : cardProgressStore.ensureDirectionalProgressRows(userId, cardId)) {
            CardProgressSupport.resetToNew(progress, cardId, userId, progress.getDirection());
            if (progress.getId() == null) {
                cardProgressMapper.insert(progress);
            } else {
                cardProgressMapper.updateByUserIdAndCardIdAndDirection(progress);
            }
        }
        return cardService.getCard(cardId);
    }

    private List<PracticeItem> buildBaseItems(List<Card> cards, boolean isNew, String mode, LocalDate today) {
        List<PracticeItem> items = new ArrayList<>();
        for (Card card : cards) {
            if (PracticeService.DEFAULT_MODE.equals(mode)) {
                if (isNew || reviewScheduleBuilder.shouldIncludeSpecificDirection(card, A_TO_B, today)) {
                    items.add(buildPracticeItem(card, isNew, A_TO_B, "base", 0));
                }
                if (isNew || reviewScheduleBuilder.shouldIncludeSpecificDirection(card, B_TO_A, today)) {
                    items.add(buildPracticeItem(card, isNew, B_TO_A, "base", 0));
                }
            } else if ("a2b".equals(mode)) {
                if (!isNew && !reviewScheduleBuilder.shouldIncludeSpecificDirection(card, A_TO_B, today)) {
                    continue;
                }
                items.add(buildPracticeItem(card, isNew, A_TO_B, "base", 0));
            } else {
                if (!isNew && !reviewScheduleBuilder.shouldIncludeSpecificDirection(card, B_TO_A, today)) {
                    continue;
                }
                items.add(buildPracticeItem(card, isNew, B_TO_A, "base", 0));
            }
        }
        return items;
    }

    private PracticeItem buildPracticeItem(Card card, boolean isNew, String direction, String kind, int ordinal) {
        PracticeItem item = new PracticeItem();
        item.setItemKey(card.getId() + ":" + toApiDirection(direction) + ":" + kind + ":" + ordinal);
        item.setCardId(card.getId());
        item.setDirection(toApiDirection(direction));
        item.setKind(kind);
        item.setOrdinal(ordinal);
        item.setIsNew(isNew);
        item.setIsReview(!isNew);
        item.setCard(card);
        return item;
    }

    /**
     * 用同一个调度器计算当天复习和新卡片，保证队列、摘要和今天列表同源。
     */
    private PracticeReviewSchedule scheduleDeckReviewLoad(List<Card> allCards, int newCardsLimit, LocalDate today, Long deckId) {
        return reviewScheduleBuilder.schedule(allCards, newCardsLimit, today, PracticeService.DEFAULT_MODE, deckId);
    }

    /**
     * 按练习模式裁剪复习方向后调度，避免非当前模式方向占用当天名额。
     */
    private PracticeReviewSchedule scheduleDeckReviewLoad(
        List<Card> allCards,
        int newCardsLimit,
        LocalDate today,
        String mode,
        Long deckId
    ) {
        return reviewScheduleBuilder.schedule(allCards, newCardsLimit, today, mode, deckId);
    }

    /**
     * 为已开始学习的卡补充另一方向的首次练习题，不让它被复习平滑策略吞掉。
     */
    private List<PracticeItem> buildPendingNewDirectionItems(List<Card> cards, String mode) {
        List<PracticeItem> items = new ArrayList<>();
        for (Card card : cards) {
            if (card == null || card.getDirectionProgresses() == null) {
                continue;
            }
            if ("new".equals(card.getState()) || "mastered".equals(card.getState()) || "graduated".equals(card.getState())) {
                continue;
            }
            if ((PracticeService.DEFAULT_MODE.equals(mode) || API_A_TO_B.equals(mode))
                && isDirectionNew(card.getDirectionProgresses().getA2b())
                && hasStartedOtherDirection(card, A_TO_B)) {
                items.add(buildPracticeItem(card, false, A_TO_B, "base", 0));
            }
            if ((PracticeService.DEFAULT_MODE.equals(mode) || API_B_TO_A.equals(mode))
                && isDirectionNew(card.getDirectionProgresses().getB2a())
                && hasStartedOtherDirection(card, B_TO_A)) {
                items.add(buildPracticeItem(card, false, B_TO_A, "base", 0));
            }
        }
        return items;
    }

    /**
     * 按今日剩余额度追加另一面首次练习题，额度耗尽时不再露出题目。
     */
    private List<PracticeItem> buildPendingNewDirectionItems(
        List<Card> cards,
        String mode,
        PracticeReviewSchedule schedule
    ) {
        if (schedule.load().targetReviewItemCount() <= 0) {
            return List.of();
        }
        return buildPendingNewDirectionItems(cards, mode);
    }

    /**
     * 按题目里的卡片 id 计算用户当天实际会看到的复习卡数量。
     */
    private int distinctCardCount(List<PracticeItem> items) {
        return (int) items.stream()
            .map(PracticeItem::getCardId)
            .distinct()
            .count();
    }

    /**
     * 把调度器给出的队列压力写到练习队列，供页面展示积压和新卡暂停状态。
     */
    private void applyScheduleLoad(PracticeQueue queue, PracticeReviewSchedule schedule) {
        queue.setReviewBacklogCount(schedule.load().backlogCardCount());
        queue.setNewCardsPaused(schedule.load().newCardsPaused());
        queue.setTargetReviewItemCount(schedule.load().targetReviewItemCount());
        queue.setMaxReviewItemCount(schedule.load().maxReviewItemCount());
    }

    /**
     * 把调度器给出的队列压力写到摘要，保证开始前数字和开始练习后队列一致。
     */
    private void applyScheduleLoad(PendingPracticeSummary summary, PracticeReviewSchedule schedule) {
        summary.setPendingBacklog(schedule.load().backlogCardCount());
        summary.setNewCardsPaused(schedule.load().newCardsPaused());
        summary.setTargetReviewItemCount(schedule.load().targetReviewItemCount());
        summary.setMaxReviewItemCount(schedule.load().maxReviewItemCount());
    }

    /**
     * 把已构建队列的压力元数据复制到摘要，避免同一模式重复计算出不同结果。
     */
    private void applyQueueLoad(PendingPracticeSummary summary, PracticeQueue queue) {
        summary.setPendingBacklog(queue.getReviewBacklogCount());
        summary.setNewCardsPaused(queue.getNewCardsPaused());
        summary.setTargetReviewItemCount(queue.getTargetReviewItemCount());
        summary.setMaxReviewItemCount(queue.getMaxReviewItemCount());
    }

    private boolean hasStartedOtherDirection(Card card, String direction) {
        DirectionProgressSnapshot otherSnapshot = A_TO_B.equals(direction)
            ? card.getDirectionProgresses().getB2a()
            : card.getDirectionProgresses().getA2b();
        return !isDirectionNew(otherSnapshot);
    }

    private boolean isDirectionNew(DirectionProgressSnapshot snapshot) {
        return snapshot != null && "new".equals(snapshot.getState());
    }

    /**
     * 判断用户本次评分后，卡片是否已达到长期记忆毕业标准。
     */
    private boolean shouldMarkGraduated(Card card, int userRating) {
        if (card == null || card.getDirectionProgresses() == null) {
            return false;
        }
        if (userRating != 3) {
            return false;
        }
        return qualifiesForGraduation(card.getDirectionProgresses().getA2b())
            && qualifiesForGraduation(card.getDirectionProgresses().getB2a());
    }

    /**
     * 判断单个方向的稳定度是否达到长期记忆毕业线。
     */
    private boolean qualifiesForGraduation(DirectionProgressSnapshot snapshot) {
        if (snapshot == null || snapshot.getFsrs() == null) {
            return false;
        }
        return defaultDouble(snapshot.getFsrs().getStability()) >= GRADUATION_STABILITY_DAYS;
    }

    private void applySnapshot(List<CardProgress> progresses, CardProgressSnapshot snapshot) {
        if (snapshot.getDirectionProgresses().getA2b() != null) {
            applyDirectionSnapshot(findDirectionProgress(progresses, A_TO_B), snapshot.getDirectionProgresses().getA2b());
        }
        if (snapshot.getDirectionProgresses().getB2a() != null) {
            applyDirectionSnapshot(findDirectionProgress(progresses, B_TO_A), snapshot.getDirectionProgresses().getB2a());
        }
        for (CardProgress progress : progresses) {
            saveProgress(progress);
        }
    }

    /**
     * 按进度是否已入库选择新增或更新，统一所有方向进度保存出口。
     */
    private void saveProgress(CardProgress progress) {
        if (progress.getId() == null) {
            cardProgressMapper.insert(progress);
        } else {
            cardProgressMapper.updateByUserIdAndCardIdAndDirection(progress);
        }
    }

    private CardProgress findDirectionProgress(List<CardProgress> progresses, String direction) {
        return progresses.stream()
            .filter(progress -> Objects.equals(direction, progress.getDirection()))
            .findFirst()
            .orElseGet(() -> cardProgressStore.getOrCreateProgress(progresses.get(0).getUserId(), progresses.get(0).getCardId(), direction));
    }

    private void applyDirectionSnapshot(CardProgress progress, DirectionProgressSnapshot snapshot) {
        if (progress == null || snapshot == null || snapshot.getFsrs() == null) {
            throw new AppException(ErrorCode.PRACTICE_STATE_INVALID);
        }
        CardFsrs fsrs = snapshot.getFsrs();
        String state = snapshot.getState();
        if (state == null || state.isBlank()) {
            state = fsrs.getState();
        }
        if (state == null || state.isBlank()) {
            throw new AppException(ErrorCode.PRACTICE_STATE_INVALID);
        }

        progress.setState(state);
        progress.setStep(fsrs.getStep());
        progress.setStability(defaultDouble(fsrs.getStability()));
        progress.setDifficulty(defaultDouble(fsrs.getDifficulty()));
        progress.setNextReviewDate(fsrs.getNextReviewDate());
        progress.setLastReviewDate(fsrs.getLastReviewDate());
        progress.setReps(safeInt(fsrs.getReps()));
        progress.setLapses(safeInt(fsrs.getLapses()));
        progress.setLastRating(safeInt(fsrs.getLastRating()));
        progress.setFirstLearnedDate(snapshot.getFirstLearnedDate());
        progress.setMasteredAt(snapshot.getMasteredAt());
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return keyword.trim();
    }

    private int normalizeNewCardsLimit(Integer newCardsLimit) {
        if (newCardsLimit == null) {
            return 10;
        }
        return Math.max(0, newCardsLimit);
    }

    private String normalizeMode(String mode) {
        String requestedMode = (mode == null || mode.isBlank()) ? PracticeService.DEFAULT_MODE : mode;
        List<String> dbModes = typeRegistryService.getEnabledPracticeModeKeys();
        List<String> validModes = dbModes.isEmpty()
            ? List.of(API_A_TO_B, API_B_TO_A, PracticeService.DEFAULT_MODE)
            : dbModes;
        if (validModes.contains(requestedMode)) {
            return requestedMode;
        }
        throw new AppException(ErrorCode.PRACTICE_MODE_INVALID);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }
}
