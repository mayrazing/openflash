package openflash_core.service;

import java.util.List;
import openflash_core.entity.Card;
import openflash_core.entity.CardBatchCreateItem;
import openflash_core.entity.CardBatchCreateResult;
import openflash_core.entity.CardBatchMoveResult;
import openflash_core.entity.CardPage;
import openflash_core.entity.DeckCardStats;
import openflash_core.entity.DeckLearningStats;

/**
 * 负责卡片相关的业务处理。
 */
public interface CardService {

    /**
     * 查询单张卡片详情。
     */
    Card getCard(Long cardId);

    /**
     * 查询当前用户拥有的卡片基础字段，不补齐媒体和学习状态。
     */
    Card getBasicCard(Long cardId);

    /**
     * 查询某个卡包下的卡片列表。
     */
    List<Card> listCards(Long deckId, String keyword);

    /**
     * 分页查询某个卡包下的卡片列表。
     */
    CardPage listCardsPage(Long deckId, String keyword, String state, String sort, Integer offset, Integer limit);

    /**
     * 查询卡包详情页顶部统计。
     */
    DeckCardStats getDeckCardStats(Long deckId, Integer newCardsLimit);

    /**
     * 查询卡包学习统计页概览。
     */
    DeckLearningStats getDeckLearningStats(Long deckId, Integer newCardsLimit);

    /**
     * 创建卡片。
     */
    Card createCard(Long deckId, String sideA, String sideB, List<String> sideAImage, List<String> sideBImage);

    /**
     * 批量创建卡片，重复和无效行会跳过并返回统计。
     */
    CardBatchCreateResult createCardsBatch(Long deckId, List<CardBatchCreateItem> cards);

    /**
     * 批量把卡片迁移到另一个卡包，目标重复项跳过。
     */
    CardBatchMoveResult moveCardsBatch(Long sourceDeckId, Long targetDeckId, List<Long> cardIds);

    /**
     * 更新卡片内容。
     */
    Card updateCard(Long cardId, String sideA, String sideB, List<String> sideAImage, List<String> sideBImage);

    /**
     * 删除卡片。
     */
    void deleteCard(Long cardId);

    /**
     * 重置卡片的 FSRS 学习进度为全新状态。
     */
    Card resetCard(Long cardId);
}
