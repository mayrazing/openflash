package openflash_plugin.ai_card.entity;

/**
 * AI 解释缓存就绪通知的 SSE payload 契约。
 */
public class AiCacheReadyNotification {

    public static final String EVENT_NAME = "ai-cache-ready";

    private final Long cardId;
    private final Long deckId;
    private final String cardTitle;
    private final String side;

    public AiCacheReadyNotification(Long cardId, Long deckId, String cardTitle, String side) {
        this.cardId = cardId;
        this.deckId = deckId;
        this.cardTitle = cardTitle;
        this.side = side;
    }

    public Long getCardId() {
        return cardId;
    }

    public Long getDeckId() {
        return deckId;
    }

    public String getCardTitle() {
        return cardTitle;
    }

    public String getSide() {
        return side;
    }
}
