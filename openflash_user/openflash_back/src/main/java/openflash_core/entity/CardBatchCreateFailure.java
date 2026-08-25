package openflash_core.entity;

/**
 * 批量创建卡片里某一张失败时返回给前端展示的失败明细。
 */
public record CardBatchCreateFailure(String sideA, String sideB, String reason) {
}
