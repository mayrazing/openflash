package openflash_core.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录批量迁移卡片的成功、重复、无效统计。
 */
public class CardBatchMoveResult {

    private int movedCount;
    private int duplicateCount;
    private int invalidCount;
    private final List<Long> movedCardIds = new ArrayList<>();
    private final List<CardBatchMoveFailure> failures = new ArrayList<>();

    public int getMovedCount() {
        return movedCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public int getInvalidCount() {
        return invalidCount;
    }

    public List<Long> getMovedCardIds() {
        return movedCardIds;
    }

    public List<CardBatchMoveFailure> getFailures() {
        return failures;
    }

    /** 记录一张成功迁移的卡片。 */
    public void addMovedCardId(Long cardId) {
        movedCardIds.add(cardId);
        movedCount++;
    }

    /** 记录一张被目标卡包去重规则跳过的卡片。 */
    public void addDuplicateFailure(Card card) {
        duplicateCount++;
        failures.add(new CardBatchMoveFailure(
            card.getId(),
            card.getSideA(),
            card.getSideB(),
            "DUPLICATE",
            "卡片已存在"
        ));
    }

    /** 记录一个无效卡片 ID。 */
    public void addInvalidFailure(Long cardId) {
        invalidCount++;
        failures.add(new CardBatchMoveFailure(cardId, null, null, "INVALID_CARD", "卡片不存在"));
    }
}
