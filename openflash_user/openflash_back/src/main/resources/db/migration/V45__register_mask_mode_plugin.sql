-- 遮蔽模式插件：按卡包记录题目面遮蔽模式（random=随机遮蔽 / full=完全遮蔽）。
CREATE TABLE `pw_mask_mode_deck_settings` (
  `deck_id`    BIGINT      NOT NULL COMMENT '卡包 ID',
  `mode`       VARCHAR(16) NOT NULL DEFAULT 'random' COMMENT '遮蔽模式：random / full',
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`deck_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='遮蔽模式按卡包设置；缺行时服务层回退 random';

-- 插件总开关。
INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `description`)
VALUES ('feature.mask-mode', 1, '遮蔽模式插件')
ON DUPLICATE KEY UPDATE `enabled` = `enabled`;

-- 插件目录行：config 只放语言无关数据，展示文案走前端 i18n。
INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('plugin', 'mask-mode', 'plugins.mask-mode.name', '{"descKey":"plugins.mask-mode.desc","icon":"🙈","categoryKey":"pluginCategories.studyAid"}', 3, 1)
ON DUPLICATE KEY UPDATE `item_key` = `item_key`;
