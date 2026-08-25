package openflash_core.service;

import java.util.List;
import openflash_core.entity.Deck;

/**
 * 负责卡包相关的业务处理。
 */
public interface DeckService {

    /**
     * 查询当前用户的卡包列表。
     */
    List<Deck> listDecks();

    /**
     * 查询单个卡包详情。
     */
    Deck getDeck(Long deckId);

    /**
     * 创建卡包。
     */
    Deck createDeck(String name);

    /**
     * 重命名卡包。
     */
    Deck renameDeck(Long deckId, String name);

    /**
     * 删除卡包。
     */
    void deleteDeck(Long deckId);
}
