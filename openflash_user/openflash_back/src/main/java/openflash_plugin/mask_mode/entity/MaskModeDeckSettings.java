package openflash_plugin.mask_mode.entity;

/**
 * 返回遮蔽模式插件在某卡包的总开关与整面遮蔽模式。
 * enabled 用原始 boolean：DB 列 NOT NULL DEFAULT 1，MyBatis 拆箱安全。
 */
public record MaskModeDeckSettings(Long deckId, String mode, boolean enabled) {
}
