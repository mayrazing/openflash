package openflash_core.service.impl;

import static openflash_core.entity.PracticeDirection.A_TO_B;
import static openflash_core.entity.PracticeDirection.B_TO_A;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import openflash_core.entity.Card;
import openflash_core.entity.CardBatchCreateItem;
import openflash_core.entity.CardBatchCreateResult;
import openflash_core.entity.CardBatchMoveResult;
import openflash_core.entity.CardDirectionProgresses;
import openflash_core.entity.CardFsrs;
import openflash_core.entity.CardMedia;
import openflash_core.entity.CardPage;
import openflash_core.entity.CardProgress;
import openflash_core.entity.DirectionProgressSnapshot;
import openflash_core.entity.Deck;
import openflash_core.entity.DeckCardStats;
import openflash_core.entity.DeckLearningStats;
import openflash_core.entity.PracticeReviewSchedule;
import openflash_core.entity.DeckSettings;
import openflash_core.mapper.CardMapper;
import openflash_core.mapper.CardMediaMapper;
import openflash_core.mapper.CardProgressMapper;
import openflash_core.mapper.DeckMapper;
import openflash_core.mapper.DeckSettingsMapper;
import openflash_core.common.AppException;
import openflash_core.common.ErrorCode;
import openflash_core.spi.CardChangeEvent;
import openflash_core.service.CardService;
import openflash_core.service.CurrentUserService;
import openflash_core.service.DeckSettingsService;
import openflash_core.service.PracticeService;
import openflash_core.service.UserUploadAccessGuard;

/**
 * 负责卡片内容、图片和初始学习进度的维护。
 */
@Service
public class CardServiceImpl implements CardService {


    private final CurrentUserService currentUserService;
    private final DeckMapper deckMapper;
    private final CardMapper cardMapper;
    private final CardMediaMapper cardMediaMapper;
    private final CardProgressMapper cardProgressMapper;
    private final DeckSettingsMapper deckSettingsMapper;
    private final DeckSettingsService deckSettingsService;
    private final CardProgressStore cardProgressStore;
    private final PracticeReviewScheduleBuilder reviewScheduleBuilder;
    private final ApplicationEventPublisher eventPublisher;
    private final UploadFileDeleter uploadFileDeleter;
    private final UserUploadAccessGuard userUploadAccessGuard;

    @Autowired
    public CardServiceImpl(
            CurrentUserService currentUserService,
            DeckMapper deckMapper,
            CardMapper cardMapper,
            CardMediaMapper cardMediaMapper,
            CardProgressMapper cardProgressMapper,
            DeckSettingsMapper deckSettingsMapper,
            DeckSettingsService deckSettingsService,
            PracticeReviewScheduler practiceReviewScheduler,
            ApplicationEventPublisher eventPublisher,
            CardProgressStore cardProgressStore,
            UploadFileDeleter uploadFileDeleter,
            UserUploadAccessGuard userUploadAccessGuard) {
        this.currentUserService = currentUserService;
        this.deckMapper = deckMapper;
        this.cardMapper = cardMapper;
        this.cardMediaMapper = cardMediaMapper;
        this.cardProgressMapper = cardProgressMapper;
        this.deckSettingsMapper = deckSettingsMapper;
        this.deckSettingsService = deckSettingsService;
        this.cardProgressStore = cardProgressStore;
        this.reviewScheduleBuilder = new PracticeReviewScheduleBuilder(
                practiceReviewScheduler,
                currentUserService,
                deckSettingsMapper,
                cardProgressMapper);
        this.eventPublisher = eventPublisher;
        this.uploadFileDeleter = uploadFileDeleter;
        this.userUploadAccessGuard = userUploadAccessGuard;
    }

    CardServiceImpl(
            CurrentUserService currentUserService,
            DeckMapper deckMapper,
            CardMapper cardMapper,
            CardMediaMapper cardMediaMapper,
            CardProgressMapper cardProgressMapper,
            DeckSettingsMapper deckSettingsMapper,
            PracticeReviewScheduler practiceReviewScheduler,
            ApplicationEventPublisher eventPublisher,
            CardProgressStore cardProgressStore,
            UploadFileDeleter uploadFileDeleter,
            UserUploadAccessGuard userUploadAccessGuard) {
        this(currentUserService, deckMapper, cardMapper, cardMediaMapper,
            cardProgressMapper, deckSettingsMapper,
            new DeckSettingsServiceImpl(null, null, null), practiceReviewScheduler,
            eventPublisher, cardProgressStore, uploadFileDeleter, userUploadAccessGuard);
    }

    /**
     * 查询单张卡片，并补齐图片和学习状态。
     */
    @Override
    @Transactional
    public Card getCard(Long cardId) {
        return getCardForCurrentUser(cardId);
    }

    @Override
    @Transactional
    public Card getBasicCard(Long cardId) {
        Long userId = currentUserService.getCurrentUserId();
        return getOwnedCard(cardId, userId);
    }

    /**
     * 查询某个卡包下的卡片列表，并补齐图片和学习状态。
     */
    @Override
    @Transactional
    public List<Card> listCards(Long deckId, String keyword) {
        Long userId = currentUserService.getCurrentUserId();
        ensureDeckExists(deckId, userId);

        List<Card> cards = cardMapper.findByDeckId(deckId, normalizeKeyword(keyword));
        hydrateCards(cards, userId);
        return cards;
    }

    /**
     * 分页查询某个卡包下的卡片列表，优先给前端首屏用。
     */
    @Override
    @Transactional
    public CardPage listCardsPage(Long deckId, String keyword, String state, String sort, Integer offset, Integer limit) {
        Long userId = currentUserService.getCurrentUserId();
        ensureDeckExists(deckId, userId);

        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedState = normalizeState(state);
        String normalizedSort = normalizeSort(sort);
        int safeOffset = normalizeOffset(offset);
        int safeLimit = normalizePageLimit(limit);
        long total = cardMapper.countByDeckId(deckId, normalizedKeyword, normalizedState, userId);
        List<Card> cards = total == 0
                ? new ArrayList<>()
                : cardMapper.findPageByDeckId(deckId, normalizedKeyword, normalizedState, userId, safeOffset,
                        safeLimit, normalizedSort);
        hydrateCards(cards, userId);

        CardPage page = new CardPage();
        page.setItems(cards);
        page.setTotal(total);
        page.setOffset(safeOffset);
        page.setLimit(safeLimit);
        page.setHasMore((long) safeOffset + cards.size() < total);
        return page;
    }

    /**
     * 查询卡包详情页顶部统计，避免前端再拿全量卡片自己算。
     */
    @Override
    @Transactional
    public DeckCardStats getDeckCardStats(Long deckId, Integer newCardsLimit) {
        LocalDate today = LocalDate.now();
        Long userId = currentUserService.getCurrentUserId();
        ensureDeckExists(deckId, userId);
        List<Card> allCards = listCardsForReviewSchedule(deckId, userId);
        int newCount = 0;
        int learningCount = 0;
        int masteredCount = 0;

        for (Card card : allCards) {
            if ("new".equals(card.getState())) {
                newCount++;
            } else if ("graduated".equals(card.getState()) || card.getMasteredAt() != null) {
                masteredCount++;
            } else {
                learningCount++;
            }
        }

        DeckCardStats stats = new DeckCardStats();
        stats.setTotal(allCards.size());
        stats.setNewCount(newCount);
        stats.setLearningCount(learningCount);
        stats.setMasteredCount(masteredCount);
        stats.setTomorrowCount(countTomorrowCards(allCards, today));
        PracticeReviewSchedule schedule = reviewScheduleBuilder.schedule(allCards, normalizeNewCardsLimit(newCardsLimit),
                today, PracticeService.DEFAULT_MODE, deckId);
        stats.setTodayCount(reviewScheduleBuilder.countTodayCards(allCards, schedule, today));
        stats.setBacklogCount(schedule.load().backlogCardCount());
        stats.setNewCardsPaused(schedule.load().newCardsPaused());
        return stats;
    }

    /**
     * 查询学习统计页概览，使用数据库聚合避免拉取并补齐全量卡片。
     */
    @Override
    @Transactional
    public DeckLearningStats getDeckLearningStats(Long deckId, Integer newCardsLimit) {
        Long userId = currentUserService.getCurrentUserId();
        ensureDeckExists(deckId, userId);

        DeckLearningStats stats = cardMapper.selectLearningStats(
                deckId,
                userId,
                LocalDate.now(),
                normalizeNewCardsLimit(newCardsLimit));
        if (stats == null) {
            stats = new DeckLearningStats();
            stats.setTotal(0);
            stats.setMastered(0);
            stats.setPendingNew(0);
            stats.setPendingReview(0);
            stats.setPendingTotal(0);
            stats.setTodayCompletedNew(0);
            stats.setTodayCompletedReview(0);
            stats.setBacklogCount(0);
            stats.setNewCardsPaused(false);
        } else {
            List<Card> allCards = listCardsForReviewSchedule(deckId, userId);
            PracticeReviewSchedule schedule = reviewScheduleBuilder.schedule(
                    allCards,
                    normalizeNewCardsLimit(newCardsLimit),
                    LocalDate.now(),
                    PracticeService.DEFAULT_MODE,
                    deckId);
            int pendingNew = schedule.newCards().size();
            int pendingReview = reviewScheduleBuilder.countPendingReviewCards(allCards, schedule);
            stats.setPendingNew(pendingNew);
            stats.setPendingReview(pendingReview);
            stats.setPendingTotal(pendingNew + pendingReview);
            stats.setBacklogCount(schedule.load().backlogCardCount());
            stats.setNewCardsPaused(schedule.load().newCardsPaused());
        }
        stats.setTopCards(cardMapper.selectTopReviewCards(deckId, userId, 5));
        return stats;
    }

    /**
     * 读取统计和调度所需的轻量卡片列表，只补学习进度，不加载图片。
     */
    private List<Card> listCardsForReviewSchedule(Long deckId, Long userId) {
        List<Card> cards = cardMapper.findByDeckId(deckId, null);
        hydrateCardsForReviewSchedule(cards, userId);
        return cards;
    }

    /**
     * 给一批卡片补齐图片和学习状态。
     */
    private void hydrateCards(List<Card> cards, Long userId) {
        if (cards.isEmpty()) {
            return;
        }

        List<Long> cardIds = cards.stream().map(Card::getId).collect(Collectors.toList());

        Map<Long, List<CardMedia>> mediaByCardId = cardMediaMapper.findByCardIds(cardIds)
                .stream().collect(Collectors.groupingBy(CardMedia::getCardId));

        for (Long cardId : cardIds) {
            cardProgressStore.ensureDirectionalProgressRows(userId, cardId);
        }

        Map<Long, List<CardProgress>> progressByCardId = cardProgressMapper.findByUserIdAndCardIds(userId, cardIds)
                .stream().collect(Collectors.groupingBy(CardProgress::getCardId));

        for (Card card : cards) {
            hydrateCardFromMaps(card, userId, mediaByCardId, progressByCardId);
        }
    }

    /**
     * 给调度统计补齐双方向学习状态，跳过图片加载以降低统计页开销。
     */
    private void hydrateCardsForReviewSchedule(List<Card> cards, Long userId) {
        if (cards.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now();
        List<Long> cardIds = cards.stream().map(Card::getId).collect(Collectors.toList());
        Map<Long, List<CardProgress>> progressByCardId = cardProgressMapper.findByUserIdAndCardIds(userId, cardIds)
                .stream().collect(Collectors.groupingBy(CardProgress::getCardId));

        for (Card card : cards) {
            List<CardProgress> progresses = progressByCardId.get(card.getId());
            if (progresses == null || progresses.size() < 2) {
                progresses = cardProgressStore.ensureDirectionalProgressRows(userId, card.getId());
            }
            hydrateCardFromProgressRows(card, progresses, userId, today);
        }
    }

    /**
     * 创建卡片，并写入默认学习进度。
     */
    @Override
    @Transactional
    public Card createCard(Long deckId, String sideA, String sideB, List<String> sideAImage, List<String> sideBImage) {
        Long userId = currentUserService.getCurrentUserId();
        ensureDeckExists(deckId, userId);
        String trimmedSideA = trimText(sideA);
        String trimmedSideB = trimText(sideB);
        CardDeduplicationSettings deduplicationSettings = resolveDeduplicationSettings(deckId);
        assertNoDuplicateCard(deckId, trimmedSideA, trimmedSideB, null, deduplicationSettings);
        requireOwnedMedia(userId, sideAImage, sideBImage);

        Card card = new Card();
        card.setDeckId(deckId);
        card.setSideA(trimmedSideA);
        card.setSideB(trimmedSideB);
        cardMapper.insert(card);

        replaceMedia(card.getId(), sideAImage, sideBImage);
        cardProgressStore.createDefaultProgressRows(card.getId(), userId);
        publishCardChange(List.of(card.getId()), userId, CardChangeEvent.Kind.CREATED);
        return getCardForCurrentUser(card.getId());
    }

    /**
     * 批量创建卡片，重复和无效行会跳过并返回统计。
     */
    @Override
    @Transactional
    public CardBatchCreateResult createCardsBatch(Long deckId, List<CardBatchCreateItem> cards) {
        Long userId = currentUserService.getCurrentUserId();
        ensureDeckExists(deckId, userId);

        CardBatchCreateResult result = new CardBatchCreateResult();
        if (cards == null || cards.isEmpty()) {
            return result;
        }

        CardDeduplicationSettings deduplicationSettings = resolveDeduplicationSettings(deckId);
        Set<String> seenSideAKeys = new HashSet<>();
        Set<String> seenSideBKeys = new HashSet<>();
        for (Card existingCard : findDeduplicationCandidates(deckId, null, deduplicationSettings)) {
            if (deduplicationSettings.sideAEnabled()) {
                addNormalizedText(seenSideAKeys, existingCard.getSideA());
            }
            if (deduplicationSettings.sideBEnabled()) {
                addNormalizedText(seenSideBKeys, existingCard.getSideB());
            }
        }

        List<Long> createdCardIds = new ArrayList<>();
        for (CardBatchCreateItem item : cards) {
            String sideA = trimText(item == null ? null : item.getSideA());
            String sideB = trimText(item == null ? null : item.getSideB());
            if (sideA.isEmpty() || sideB.isEmpty()) {
                result.incrementInvalidCount();
                result.addFailure(sideA, sideB, "A 面和 B 面都要填写");
                continue;
            }

            String sideAKey = CardSideANormalizer.normalize(sideA);
            String sideBKey = CardSideANormalizer.normalize(sideB);
            if ((deduplicationSettings.sideAEnabled() && seenSideAKeys.contains(sideAKey))
                    || (deduplicationSettings.sideBEnabled() && seenSideBKeys.contains(sideBKey))) {
                result.incrementDuplicateCount();
                result.addFailure(sideA, sideB, "卡片已存在");
                continue;
            }

            Card card = new Card();
            card.setDeckId(deckId);
            card.setSideA(sideA);
            card.setSideB(sideB);
            cardMapper.insert(card);
            cardProgressStore.createDefaultProgressRows(card.getId(), userId);
            createdCardIds.add(card.getId());
            if (deduplicationSettings.sideAEnabled()) {
                seenSideAKeys.add(sideAKey);
            }
            if (deduplicationSettings.sideBEnabled()) {
                seenSideBKeys.add(sideBKey);
            }
            result.incrementCreatedCount();
        }

        publishCardChange(createdCardIds, userId, CardChangeEvent.Kind.CREATED);
        return result;
    }

    /**
     * 批量把源卡包卡片迁移到目标卡包；目标重复项跳过，其他卡片继续迁移。
     */
    @Override
    @Transactional
    public CardBatchMoveResult moveCardsBatch(Long sourceDeckId, Long targetDeckId, List<Long> cardIds) {
        Long userId = currentUserService.getCurrentUserId();
        ensureDeckExists(sourceDeckId, userId);
        ensureDeckExists(targetDeckId, userId);
        if (sourceDeckId.equals(targetDeckId)) {
            throw new AppException(ErrorCode.DECK_MOVE_TARGET_INVALID);
        }

        CardBatchMoveResult result = new CardBatchMoveResult();
        List<Long> uniqueCardIds = uniqueCardIds(cardIds);
        if (uniqueCardIds.isEmpty()) {
            return result;
        }

        Map<Long, Card> sourceCardsById = new LinkedHashMap<>();
        for (Card card : cardMapper.findByIds(uniqueCardIds)) {
            if (card != null && sourceDeckId.equals(card.getDeckId())) {
                sourceCardsById.put(card.getId(), card);
            }
        }

        CardDeduplicationSettings deduplicationSettings = resolveDeduplicationSettings(targetDeckId);
        Set<String> seenSideAKeys = new HashSet<>();
        Set<String> seenSideBKeys = new HashSet<>();
        for (Card existingCard : findDeduplicationCandidates(targetDeckId, null, deduplicationSettings)) {
            if (deduplicationSettings.sideAEnabled()) {
                addNormalizedText(seenSideAKeys, existingCard.getSideA());
            }
            if (deduplicationSettings.sideBEnabled()) {
                addNormalizedText(seenSideBKeys, existingCard.getSideB());
            }
        }

        for (Long cardId : uniqueCardIds) {
            Card card = sourceCardsById.get(cardId);
            if (card == null) {
                result.addInvalidFailure(cardId);
                continue;
            }

            String sideAKey = CardSideANormalizer.normalize(card.getSideA());
            String sideBKey = CardSideANormalizer.normalize(card.getSideB());
            if ((deduplicationSettings.sideAEnabled() && !sideAKey.isEmpty() && seenSideAKeys.contains(sideAKey))
                    || (deduplicationSettings.sideBEnabled() && !sideBKey.isEmpty() && seenSideBKeys.contains(sideBKey))) {
                result.addDuplicateFailure(card);
                continue;
            }

            int updatedRows = cardMapper.updateDeckId(cardId, sourceDeckId, targetDeckId);
            if (updatedRows == 0) {
                result.addInvalidFailure(cardId);
                continue;
            }
            result.addMovedCardId(cardId);
            if (deduplicationSettings.sideAEnabled()) {
                addNormalizedText(seenSideAKeys, card.getSideA());
            }
            if (deduplicationSettings.sideBEnabled()) {
                addNormalizedText(seenSideBKeys, card.getSideB());
            }
        }

        publishCardMoved(result.getMovedCardIds(), userId, sourceDeckId, targetDeckId);
        return result;
    }

    /**
     * 更新卡片内容和图片。
     */
    @Override
    @Transactional
    public Card updateCard(Long cardId, String sideA, String sideB, List<String> sideAImage, List<String> sideBImage) {
        Long userId = currentUserService.getCurrentUserId();
        Card existing = getOwnedCard(cardId, userId);
        String trimmedSideA = trimText(sideA);
        String trimmedSideB = trimText(sideB);
        CardDeduplicationSettings deduplicationSettings = resolveDeduplicationSettings(existing.getDeckId());
        assertNoDuplicateCard(existing.getDeckId(), trimmedSideA, trimmedSideB, cardId, deduplicationSettings);
        requireOwnedMedia(userId, sideAImage, sideBImage);

        existing.setSideA(trimmedSideA);
        existing.setSideB(trimmedSideB);
        cardMapper.updateCard(existing);
        replaceMedia(cardId, sideAImage, sideBImage);
        publishCardChange(List.of(cardId), userId, CardChangeEvent.Kind.UPDATED);
        return getCardForCurrentUser(cardId);
    }

    /**
     * 重置卡片的 FSRS 学习进度为全新状态。
     */
    @Override
    @Transactional
    public Card resetCard(Long cardId) {
        Long userId = currentUserService.getCurrentUserId();
        getOwnedCard(cardId, userId);

        resetProgressRows(cardId, userId);
        return getCardForCurrentUser(cardId);
    }

    /**
     * 删除卡片以及它对应的图片和学习进度。
     */
    @Override
    @Transactional
    public void deleteCard(Long cardId) {
        Long userId = currentUserService.getCurrentUserId();
        getOwnedCard(cardId, userId);

        List<CardMedia> oldMedia = cardMediaMapper.findByCardId(cardId);
        cardProgressMapper.deleteByCardId(cardId);
        cardMediaMapper.deleteByCardId(cardId);
        cardMapper.deleteById(cardId);
        deleteUploadFiles(oldMedia);
    }

    /**
     * 读取当前用户视角下的一张完整卡片。
     */
    private Card getCardForCurrentUser(Long cardId) {
        Long userId = currentUserService.getCurrentUserId();
        Card card = getOwnedCard(cardId, userId);
        hydrateCard(card, userId);
        return card;
    }

    /**
     * 校验卡片存在，并且属于当前用户的卡包。
     */
    private Card getOwnedCard(Long cardId, Long userId) {
        Card card = cardMapper.findById(cardId);
        if (card == null) {
            throw new AppException(ErrorCode.CARD_NOT_FOUND);
        }

        Deck deck = ensureDeckExists(card.getDeckId(), userId);
        card.setDeckName(deck.getName());
        return card;
    }

    /**
     * 校验卡包是否属于当前用户。
     */
    private Deck ensureDeckExists(Long deckId, Long userId) {
        Deck deck = deckMapper.findByIdAndUserId(deckId, userId);
        if (deck == null) {
            throw new AppException(ErrorCode.DECK_NOT_FOUND);
        }
        return deck;
    }

    /**
     * 按用户设置校验 A 面和 B 面是否与同卡包已有卡片重复。
     */
    private void assertNoDuplicateCard(
            Long deckId,
            String sideA,
            String sideB,
            Long excludingCardId,
            CardDeduplicationSettings deduplicationSettings) {
        List<Card> candidates = findDeduplicationCandidates(deckId, excludingCardId, deduplicationSettings);
        String sideAKey = CardSideANormalizer.normalize(sideA);
        String sideBKey = CardSideANormalizer.normalize(sideB);
        boolean checkSideA = deduplicationSettings.sideAEnabled() && !sideAKey.isEmpty();
        boolean checkSideB = deduplicationSettings.sideBEnabled() && !sideBKey.isEmpty();
        if (!checkSideA && !checkSideB) {
            return;
        }

        for (Card candidate : candidates) {
            if (checkSideA && sideAKey.equals(CardSideANormalizer.normalize(candidate.getSideA()))) {
                throw new AppException(ErrorCode.CARD_ALREADY_EXISTS);
            }
            if (checkSideB && sideBKey.equals(CardSideANormalizer.normalize(candidate.getSideB()))) {
                throw new AppException(ErrorCode.CARD_ALREADY_EXISTS);
            }
        }
    }

    /**
     * 读取用户去重设置，缺失字段按旧体验回退为 A 面开、B 面关。
     */
    private CardDeduplicationSettings resolveDeduplicationSettings(Long deckId) {
        DeckSettings settings = deckSettingsService.normalizeSettings(deckSettingsMapper.findByDeckId(deckId));
        return new CardDeduplicationSettings(
                settings.getDuplicateSideAEnabled(),
                settings.getDuplicateSideBEnabled());
    }

    /**
     * 读取同卡包内参与去重判断的卡片。
     */
    private List<Card> findDeduplicationCandidates(
            Long deckId,
            Long excludingCardId,
            CardDeduplicationSettings deduplicationSettings) {
        if (!deduplicationSettings.sideAEnabled() && !deduplicationSettings.sideBEnabled()) {
            return List.of();
        }
        return cardMapper.findDeduplicationCandidates(deckId, excludingCardId);
    }

    /**
     * 把已有卡片文本加入去重集合。
     */
    private void addNormalizedText(Set<String> normalizedValues, String value) {
        String key = CardSideANormalizer.normalize(value);
        if (!key.isEmpty()) {
            normalizedValues.add(key);
        }
    }

    /**
     * 按请求顺序去重卡片 ID；重复 ID 不进入统计。
     */
    private List<Long> uniqueCardIds(List<Long> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return List.of();
        }
        return cardIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    /**
     * 表示当前用户新增、编辑卡片时开启哪些去重面。
     */
    private record CardDeduplicationSettings(boolean sideAEnabled, boolean sideBEnabled) {
    }

    /**
     * 计算卡包详情页“明天复习”标签的数量。
     */
    private int countTomorrowCards(List<Card> allCards, LocalDate today) {
        int tomorrowCount = 0;
        for (Card card : allCards) {
            if (shouldRepeatTomorrow(card, today)) {
                tomorrowCount++;
            }
        }
        return tomorrowCount;
    }

    /**
     * 与前端标签语义保持一致，判断卡片是否属于“明天复习”集合。
     */
    private boolean shouldRepeatTomorrow(Card card, LocalDate today) {
        if (card == null || "new".equals(card.getState()) || "mastered".equals(card.getState()) || "graduated".equals(card.getState())) {
            return false;
        }
        CardFsrs fsrs = card.getFsrs();
        if (fsrs == null) {
            return false;
        }

        LocalDate nextReviewDate = fsrs.getNextReviewDate();
        LocalDate tomorrow = today.plusDays(1);
        if (tomorrow.equals(nextReviewDate)) {
            return true;
        }

        return today.equals(nextReviewDate)
                && today.equals(fsrs.getLastReviewDate())
                && !shouldRepeatToday(card, today);
    }

    /**
     * 与前端今天标签同口径：只读取后端已计算字段，不在统计里重复风险规则。
     */
    private boolean shouldRepeatToday(Card card, LocalDate today) {
        if (card == null || "new".equals(card.getState()) || "mastered".equals(card.getState()) || "graduated".equals(card.getState())) {
            return false;
        }
        CardFsrs fsrs = card.getFsrs();
        if (fsrs == null) {
            return false;
        }
        return Boolean.TRUE.equals(card.getTodayCalculated())
                && today.equals(fsrs.getNextReviewDate())
                && today.equals(fsrs.getLastReviewDate());
    }

    /**
     * 保护分页起始值，避免传负数。
     */
    private int normalizeOffset(Integer offset) {
        if (offset == null) {
            return 0;
        }
        return Math.max(0, offset);
    }

    /**
     * 控制每次分页大小，避免一把又查回太多。
     */
    private int normalizePageLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        return Math.min(200, Math.max(1, limit));
    }

    /**
     * 把分页排序值限制为创建时间白名单，非法值回到最新在上。
     */
    private String normalizeSort(String sort) {
        if ("created_asc".equals(sort)) {
            return "created_asc";
        }
        return "created_desc";
    }

    /**
     * 卡包详情页“今天”统计默认也沿用每日新卡上限。
     */
    private int normalizeNewCardsLimit(Integer newCardsLimit) {
        if (newCardsLimit == null) {
            return 10;
        }
        return Math.max(0, newCardsLimit);
    }

    /**
     * 给卡片补齐图片和学习状态，返回给前端直接使用。
     */
    private void hydrateCard(Card card, Long userId) {
        LocalDate today = LocalDate.now();
        List<CardMedia> mediaList = cardMediaMapper.findByCardId(card.getId());
        List<String> sideAImages = new ArrayList<>();
        List<String> sideBImages = new ArrayList<>();
        for (CardMedia media : mediaList) {
            if ("A".equalsIgnoreCase(media.getCardSide())) {
                sideAImages.add(media.getMediaUrl());
            } else if ("B".equalsIgnoreCase(media.getCardSide())) {
                sideBImages.add(media.getMediaUrl());
            }
        }

        card.setSideAImage(sideAImages);
        card.setSideBImage(sideBImages);
        List<CardProgress> progresses = cardProgressStore.ensureDirectionalProgressRows(userId, card.getId());
        hydrateCardFromProgressRows(card, progresses, userId, today);
    }

    /**
     * 从批量查询结果中给卡片补齐图片和学习状态。
     */
    private void hydrateCardFromMaps(Card card, Long userId,
            Map<Long, List<CardMedia>> mediaByCardId,
            Map<Long, List<CardProgress>> progressByCardId) {
        LocalDate today = LocalDate.now();
        List<CardMedia> mediaList = mediaByCardId.getOrDefault(card.getId(), List.of());
        List<String> sideAImages = new ArrayList<>();
        List<String> sideBImages = new ArrayList<>();
        for (CardMedia media : mediaList) {
            if ("A".equalsIgnoreCase(media.getCardSide())) {
                sideAImages.add(media.getMediaUrl());
            } else if ("B".equalsIgnoreCase(media.getCardSide())) {
                sideBImages.add(media.getMediaUrl());
            }
        }

        card.setSideAImage(sideAImages);
        card.setSideBImage(sideBImages);
        List<CardProgress> progresses = progressByCardId.get(card.getId());
        if (progresses == null || progresses.size() < 2) {
            progresses = cardProgressStore.ensureDirectionalProgressRows(userId, card.getId());
        }
        hydrateCardFromProgressRows(card, progresses, userId, today);
    }

    /**
     * 用双方向进度填充卡片的派生展示字段。
     */
    private void hydrateCardFromProgressRows(Card card, List<CardProgress> progresses, Long userId, LocalDate today) {
        CardProgress a2bProgress = findDirectionProgress(progresses, A_TO_B, card.getId(), userId);
        CardProgress b2aProgress = findDirectionProgress(progresses, B_TO_A, card.getId(), userId);

        DirectionProgressSnapshot a2bSnapshot = toDirectionSnapshot(a2bProgress);
        DirectionProgressSnapshot b2aSnapshot = toDirectionSnapshot(b2aProgress);

        CardDirectionProgresses directionProgresses = new CardDirectionProgresses();
        directionProgresses.setA2b(a2bSnapshot);
        directionProgresses.setB2a(b2aSnapshot);

        card.setDirectionProgresses(directionProgresses);
        card.setState(resolveCardState(a2bProgress, b2aProgress));
        card.setFirstLearnedDate(resolveFirstLearnedDate(a2bProgress, b2aProgress));
        card.setMasteredAt(resolveMasteredAt(a2bProgress, b2aProgress));
        card.setTodayCalculated(isTodayCalculated(a2bProgress, b2aProgress, today));
        card.setFsrs(buildDisplayFsrs(a2bProgress, b2aProgress, card.getState()));
    }

    /**
     * 统一把一张卡的两条方向进度重置成新卡。
     */
    private void resetProgressRows(Long cardId, Long userId) {
        List<CardProgress> progresses = cardProgressStore.ensureDirectionalProgressRows(userId, cardId);
        for (CardProgress progress : progresses) {
            CardProgress reset = CardProgressSupport.newProgress(cardId, userId, progress.getDirection());
            if (progress.getId() == null) {
                cardProgressMapper.insert(reset);
            } else {
                reset.setId(progress.getId());
                cardProgressMapper.updateByUserIdAndCardIdAndDirection(reset);
            }
        }
    }

    /**
     * 取某方向进度行；缺该方向时用入参 cardId/userId 建未保存的兜底行。
     * cardId/userId 由调用方传入而非从 progresses.get(0) 取，防止空列表（并发首访竞态）时下标越界崩溃。
     */
    private CardProgress findDirectionProgress(List<CardProgress> progresses, String direction,
            Long cardId, Long userId) {
        return progresses.stream()
                .filter(progress -> Objects.equals(direction, progress.getDirection()))
                .findFirst()
                .orElseGet(() -> CardProgressSupport.newProgress(cardId, userId, direction));
    }

    private DirectionProgressSnapshot toDirectionSnapshot(CardProgress progress) {
        DirectionProgressSnapshot snapshot = new DirectionProgressSnapshot();
        snapshot.setState(progress.getState());
        snapshot.setFsrs(toCardFsrs(progress));
        snapshot.setFirstLearnedDate(progress.getFirstLearnedDate());
        snapshot.setMasteredAt(progress.getMasteredAt());
        return snapshot;
    }

    private CardFsrs toCardFsrs(CardProgress progress) {
        CardFsrs fsrs = new CardFsrs();
        fsrs.setStability(progress.getStability());
        fsrs.setDifficulty(progress.getDifficulty());
        fsrs.setState(progress.getState());
        fsrs.setStep(progress.getStep());
        fsrs.setNextReviewDate(progress.getNextReviewDate());
        fsrs.setReps(progress.getReps());
        fsrs.setLapses(progress.getLapses());
        fsrs.setLastRating(progress.getLastRating());
        fsrs.setLastReviewDate(progress.getLastReviewDate());
        return fsrs;
    }

    private String resolveCardState(CardProgress a2bProgress, CardProgress b2aProgress) {
        if (isState(a2bProgress, "graduated") && isState(b2aProgress, "graduated")) {
            return "graduated";
        }
        if (hasMasteredAt(a2bProgress) && hasMasteredAt(b2aProgress)) {
            return "mastered";
        }
        if (isState(a2bProgress, "new") && isState(b2aProgress, "new")) {
            return "new";
        }
        return "learning";
    }

    /**
     * 判断方向是否已经进入已掌握卡包。
     */
    private boolean hasMasteredAt(CardProgress progress) {
        return progress != null && progress.getMasteredAt() != null;
    }

    private LocalDate resolveFirstLearnedDate(CardProgress a2bProgress, CardProgress b2aProgress) {
        LocalDate aDate = a2bProgress.getFirstLearnedDate();
        LocalDate bDate = b2aProgress.getFirstLearnedDate();
        if (aDate == null) {
            return bDate;
        }
        if (bDate == null) {
            return aDate;
        }
        return aDate.isBefore(bDate) ? aDate : bDate;
    }

    private LocalDateTime resolveMasteredAt(CardProgress a2bProgress, CardProgress b2aProgress) {
        LocalDateTime aTime = a2bProgress.getMasteredAt();
        LocalDateTime bTime = b2aProgress.getMasteredAt();
        if (aTime == null && bTime == null) {
            return null;
        }
        if (aTime == null) return bTime;
        if (bTime == null) return aTime;
        return aTime.isAfter(bTime) ? aTime : bTime;
    }

    private boolean isTodayCalculated(CardProgress a2bProgress, CardProgress b2aProgress, LocalDate today) {
        return today != null
                && (today.equals(a2bProgress.getLastReviewDate()) || today.equals(b2aProgress.getLastReviewDate()));
    }

    private CardFsrs buildDisplayFsrs(CardProgress a2bProgress, CardProgress b2aProgress, String cardState) {
        CardFsrs fsrs = new CardFsrs();
        fsrs.setState(cardState);
        fsrs.setStep(resolveDisplayStep(a2bProgress, b2aProgress));
        fsrs.setStability(
                Math.min(defaultDouble(a2bProgress.getStability()), defaultDouble(b2aProgress.getStability())));
        fsrs.setDifficulty(
                Math.max(defaultDouble(a2bProgress.getDifficulty()), defaultDouble(b2aProgress.getDifficulty())));
        fsrs.setNextReviewDate(resolveEarlierDate(a2bProgress.getNextReviewDate(), b2aProgress.getNextReviewDate()));
        fsrs.setLastReviewDate(resolveLaterDate(a2bProgress.getLastReviewDate(), b2aProgress.getLastReviewDate()));
        fsrs.setReps(safeInt(a2bProgress.getReps()) + safeInt(b2aProgress.getReps()));
        fsrs.setLapses(safeInt(a2bProgress.getLapses()) + safeInt(b2aProgress.getLapses()));
        fsrs.setLastRating(resolveLastRating(a2bProgress, b2aProgress));
        return fsrs;
    }

    private Integer resolveDisplayStep(CardProgress a2bProgress, CardProgress b2aProgress) {
        if (isWeaker(a2bProgress, b2aProgress)) {
            return a2bProgress.getStep();
        }
        return b2aProgress.getStep();
    }

    private Integer resolveLastRating(CardProgress a2bProgress, CardProgress b2aProgress) {
        LocalDate aLast = a2bProgress.getLastReviewDate();
        LocalDate bLast = b2aProgress.getLastReviewDate();
        if (aLast == null) {
            return b2aProgress.getLastRating();
        }
        if (bLast == null) {
            return a2bProgress.getLastRating();
        }
        return aLast.isAfter(bLast) ? a2bProgress.getLastRating() : b2aProgress.getLastRating();
    }

    private boolean isWeaker(CardProgress left, CardProgress right) {
        double leftStability = defaultDouble(left.getStability());
        double rightStability = defaultDouble(right.getStability());
        if (leftStability != rightStability) {
            return leftStability < rightStability;
        }
        return safeInt(left.getReps()) <= safeInt(right.getReps());
    }

    private LocalDate resolveEarlierDate(LocalDate first, LocalDate second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isBefore(second) ? first : second;
    }

    private LocalDate resolveLaterDate(LocalDate first, LocalDate second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private boolean isState(CardProgress progress, String expected) {
        return progress != null && expected.equals(progress.getState());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    /**
     * 替换卡片图片，只删除新列表里不再使用的旧文件。
     */
    private void replaceMedia(Long cardId, List<String> sideAImage, List<String> sideBImage) {
        List<CardMedia> oldMedia = cardMediaMapper.findByCardId(cardId);
        cardMediaMapper.deleteByCardId(cardId);
        insertMedia(cardId, "A", sideAImage);
        insertMedia(cardId, "B", sideBImage);

        Set<String> newUrls = new HashSet<>();
        if (sideAImage != null) newUrls.addAll(sideAImage);
        if (sideBImage != null) newUrls.addAll(sideBImage);
        List<CardMedia> orphaned = oldMedia.stream()
                .filter(m -> !newUrls.contains(m.getMediaUrl()))
                .collect(Collectors.toList());
        deleteUploadFiles(orphaned);
    }

    private void requireOwnedMedia(Long userId, List<String> sideAImage, List<String> sideBImage) {
        List<String> urls = new ArrayList<>();
        if (sideAImage != null) {
            urls.addAll(sideAImage);
        }
        if (sideBImage != null) {
            urls.addAll(sideBImage);
        }
        userUploadAccessGuard.requireMediaUrlsOwnedBy(userId, urls);
    }

    /**
     * 按顺序写入某一面的图片。
     */
    private void insertMedia(Long cardId, String side, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        int sortOrder = 0;
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) {
                continue;
            }
            CardMedia media = new CardMedia();
            media.setCardId(cardId);
            media.setCardSide(side);
            media.setMediaUrl(url.trim());
            media.setSortOrder(sortOrder++);
            cardMediaMapper.insert(media);
        }
    }

    private void deleteUploadFiles(List<CardMedia> mediaList) {
        uploadFileDeleter.delete(mediaList);
    }

    /** 通知插件卡片发生变化，插件自行决定触发哪些后台任务。 */
    private void publishCardChange(List<Long> cardIds, Long userId, CardChangeEvent.Kind kind) {
        if (cardIds == null || cardIds.isEmpty()) {
            return;
        }
        CardChangeEvent event = CardChangeEvent.of(userId, cardIds, kind);
        eventPublisher.publishEvent(event);
    }

    /** 通知插件卡片迁移完成，cardIds 只包含成功迁移的卡片。 */
    private void publishCardMoved(List<Long> cardIds, Long userId, Long sourceDeckId, Long targetDeckId) {
        if (cardIds == null || cardIds.isEmpty()) {
            return;
        }
        CardChangeEvent event = CardChangeEvent.moved(userId, cardIds, sourceDeckId, targetDeckId);
        eventPublisher.publishEvent(event);
    }

    /**
     * 文本入库前做基础清理。
     */
    private String trimText(String text) {
        return text == null ? "" : text.trim();
    }

    /**
     * 搜索关键字为空时返回 null，方便 mapper 直接判断。
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return keyword.trim();
    }

    /**
     * state 只允许 new/learning/mastered/graduated，其他值一律当 null（不过滤）。
     */
    private String normalizeState(String state) {
        if ("new".equals(state) || "learning".equals(state) || "mastered".equals(state) || "graduated".equals(state)) {
            return state;
        }
        return null;
    }
}
