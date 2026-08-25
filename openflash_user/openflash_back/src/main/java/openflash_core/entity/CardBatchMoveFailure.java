package openflash_core.entity;

/**
 * 表示批量迁移卡片时单张失败的原因。
 */
public record CardBatchMoveFailure(
    Long cardId,
    String sideA,
    String sideB,
    String reasonCode,
    String reason
) {
}
