package openflash_core.controller;

import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.dto.ApiResponse;
import openflash_core.entity.Card;
import openflash_core.entity.CardBatchCreateItem;
import openflash_core.entity.CardBatchCreateResult;
import openflash_core.entity.CardBatchMoveResult;
import openflash_core.entity.CardPage;
import openflash_core.entity.DeckCardStats;
import openflash_core.entity.DeckLearningStats;
import openflash_core.service.CardService;

/**
 * 处理卡片相关接口。
 */
@RestController
@RequestMapping("/api")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    /**
     * 查询单张卡片详情。
     */
    @GetMapping("/cards/{cardId}")
    public ApiResponse<Card> getCard(@PathVariable Long cardId) {
        return ApiResponse.success(cardService.getCard(cardId));
    }

    /**
     * 查询某个卡包下的卡片列表。
     */
    @GetMapping("/decks/{deckId}/cards")
    public ApiResponse<List<Card>> listCards(@PathVariable Long deckId, @RequestParam(required = false) String keyword) {
        return ApiResponse.success(cardService.listCards(deckId, keyword));
    }

    /**
     * 分页查询某个卡包下的卡片列表。
     */
    @GetMapping("/decks/{deckId}/cards/page")
    public ApiResponse<CardPage> listCardsPage(
        @PathVariable Long deckId,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) Integer offset,
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(cardService.listCardsPage(deckId, keyword, state, sort, offset, limit));
    }

    /**
     * 查询卡包详情页顶部统计。
     */
    @GetMapping("/decks/{deckId}/cards/stats")
    public ApiResponse<DeckCardStats> getDeckCardStats(
        @PathVariable Long deckId,
        @RequestParam(required = false) Integer newCardsLimit
    ) {
        return ApiResponse.success(cardService.getDeckCardStats(deckId, newCardsLimit));
    }

    /**
     * 查询卡包学习统计页概览。
     */
    @GetMapping("/decks/{deckId}/learning-stats")
    public ApiResponse<DeckLearningStats> getDeckLearningStats(
        @PathVariable Long deckId,
        @RequestParam(required = false) Integer newCardsLimit
    ) {
        return ApiResponse.success(cardService.getDeckLearningStats(deckId, newCardsLimit));
    }

    /**
     * 在卡包下创建卡片。
     */
    @PostMapping("/decks/{deckId}/cards")
    public ApiResponse<Card> createCard(@PathVariable Long deckId, @RequestBody CardRequest request) {
        return ApiResponse.success(
            cardService.createCard(deckId, request.sideA(), request.sideB(), request.sideAImage(), request.sideBImage())
        );
    }

    /**
     * 批量创建卡片，重复和无效行会跳过并返回统计。
     */
    @PostMapping("/decks/{deckId}/cards/batch")
    public ApiResponse<CardBatchCreateResult> createCardsBatch(
        @PathVariable Long deckId,
        @RequestBody CardBatchRequest request
    ) {
        List<CardBatchCreateItem> cards = request == null ? null : request.cards();
        return ApiResponse.success(cardService.createCardsBatch(deckId, cards));
    }

    /**
     * 批量迁移卡片到另一个卡包，目标重复项会跳过。
     */
    @PostMapping("/decks/{sourceDeckId}/cards/move")
    public ApiResponse<CardBatchMoveResult> moveCardsBatch(
        @PathVariable Long sourceDeckId,
        @RequestBody CardBatchMoveRequest request
    ) {
        Long targetDeckId = request == null ? null : request.targetDeckId();
        List<Long> cardIds = request == null ? null : request.cardIds();
        return ApiResponse.success(cardService.moveCardsBatch(sourceDeckId, targetDeckId, cardIds));
    }

    /**
     * 更新卡片内容。
     */
    @PutMapping("/cards/{cardId}")
    public ApiResponse<Card> updateCard(@PathVariable Long cardId, @RequestBody CardRequest request) {
        return ApiResponse.success(
            cardService.updateCard(cardId, request.sideA(), request.sideB(), request.sideAImage(), request.sideBImage())
        );
    }

    /**
     * 删除卡片。
     */
    @DeleteMapping("/cards/{cardId}")
    public ApiResponse<Void> deleteCard(@PathVariable Long cardId) {
        cardService.deleteCard(cardId);
        return ApiResponse.success(null);
    }

    /**
     * 重置卡片的 FSRS 学习进度为全新状态。
     */
    @PutMapping("/cards/{cardId}/reset")
    public ApiResponse<Card> resetCard(@PathVariable Long cardId) {
        return ApiResponse.success(cardService.resetCard(cardId));
    }

    public record CardRequest(
        String sideA,
        String sideB,
        List<String> sideAImage,
        List<String> sideBImage
    ) {
    }

    public record CardBatchRequest(
        List<CardBatchCreateItem> cards
    ) {
    }

    public record CardBatchMoveRequest(
        Long targetDeckId,
        List<Long> cardIds
    ) {
    }

}
