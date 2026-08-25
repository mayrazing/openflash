package openflash_plugin.mask_mode.dto;

/**
 * 承载遮蔽模式插件设置页一次保存的总开关与遮蔽模式。
 * enabled 用对象 Boolean：null 触发 DECK_SETTINGS_INVALID，与 mode 校验一致。
 */
public record MaskModeDeckSettingsUpdateCommand(String mode, Boolean enabled) {
}
