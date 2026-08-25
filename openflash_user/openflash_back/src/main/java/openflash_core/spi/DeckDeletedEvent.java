package openflash_core.spi;

import java.util.Objects;

/**
 * 卡包删除领域事件。核心删卡包成功后发布，插件通过 @EventListener 订阅以清理自己的卡包级数据。
 * 监听器必须只按 deckId 无条件清理自己的表，不读其他表，从而与执行顺序解耦。
 */
public record DeckDeletedEvent(Long userId, Long deckId) {
    public DeckDeletedEvent {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(deckId, "deckId must not be null");
    }
}
