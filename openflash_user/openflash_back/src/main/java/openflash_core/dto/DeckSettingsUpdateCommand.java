package openflash_core.dto;

import java.math.BigDecimal;

/**
 * 承载卡包设置页一次保存的全部字段，避免多个布尔开关靠参数顺序传递。
 */
public record DeckSettingsUpdateCommand(
    Integer newCardsPerDay,
    BigDecimal targetRetention,
    String reviewLoadProfile,
    Boolean duplicateSideAEnabled,
    Boolean duplicateSideBEnabled
) {
}
