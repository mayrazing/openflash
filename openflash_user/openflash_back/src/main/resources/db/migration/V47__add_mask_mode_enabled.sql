-- 遮蔽模式插件：加卡包级总开关 enabled，关闭后所有遮蔽行为不生效。
ALTER TABLE `pw_mask_mode_deck_settings`
  ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '遮蔽模式总开关, 0=关闭则跳过所有遮蔽' AFTER `mode`;
