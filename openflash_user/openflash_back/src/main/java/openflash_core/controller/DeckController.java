package openflash_core.controller;

import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import openflash_core.dto.ApiResponse;
import openflash_core.entity.Deck;
import openflash_core.service.DeckService;

/**
 * 处理卡包相关接口。
 */
@RestController
@RequestMapping("/api/decks")
public class DeckController {

    private final DeckService deckService;

    public DeckController(DeckService deckService) {
        this.deckService = deckService;
    }

    /**
     * 查询当前用户的卡包列表。
     */
    @GetMapping
    public ApiResponse<List<Deck>> listDecks() {
        return ApiResponse.success(deckService.listDecks());
    }

    /**
     * 查询单个卡包详情。
     */
    @GetMapping("/{deckId}")
    public ApiResponse<Deck> getDeck(@PathVariable Long deckId) {
        return ApiResponse.success(deckService.getDeck(deckId));
    }

    /**
     * 创建卡包。
     */
    @PostMapping
    public ApiResponse<Deck> createDeck(@RequestBody DeckRequest request) {
        return ApiResponse.success(deckService.createDeck(request.name()));
    }

    /**
     * 重命名卡包。
     */
    @PutMapping("/{deckId}")
    public ApiResponse<Deck> renameDeck(@PathVariable Long deckId, @RequestBody DeckRequest request) {
        return ApiResponse.success(deckService.renameDeck(deckId, request.name()));
    }

    /**
     * 删除卡包。
     */
    @DeleteMapping("/{deckId}")
    public ApiResponse<Void> deleteDeck(@PathVariable Long deckId) {
        deckService.deleteDeck(deckId);
        return ApiResponse.success(null);
    }

    public record DeckRequest(String name) {
    }
}
