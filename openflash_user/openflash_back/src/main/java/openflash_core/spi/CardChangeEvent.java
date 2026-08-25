package openflash_core.spi;

import java.util.List;
import java.util.Objects;

/**
 * 核心卡片变更事件，提供插件响应后台任务所需的上下文。
 */
public record CardChangeEvent(
    Long userId,
    List<Long> cardIds,
    Kind kind,
    Long sourceDeckId,
    Long targetDeckId
) {

    public enum Kind {
        CREATED,
        IMPORTED,
        UPDATED,
        MOVED
    }

    public CardChangeEvent {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(cardIds, "cardIds must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        if (kind == Kind.MOVED) {
            Objects.requireNonNull(sourceDeckId, "sourceDeckId must not be null");
            Objects.requireNonNull(targetDeckId, "targetDeckId must not be null");
        }
        cardIds = List.copyOf(cardIds);
    }

    /** 创建普通卡片变化事件，并复制 cardIds 防止调用方后续修改。 */
    public static CardChangeEvent of(Long userId, List<Long> cardIds, Kind kind) {
        return new CardChangeEvent(userId, cardIds, kind, null, null);
    }

    /** 创建卡片迁移事件，cardIds 只包含成功迁移的卡片。 */
    public static CardChangeEvent moved(Long userId, List<Long> cardIds, Long sourceDeckId, Long targetDeckId) {
        return new CardChangeEvent(userId, cardIds, Kind.MOVED, sourceDeckId, targetDeckId);
    }
}
