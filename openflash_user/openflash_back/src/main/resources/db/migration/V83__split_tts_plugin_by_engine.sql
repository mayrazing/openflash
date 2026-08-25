-- 把单一 TTS 插件按合成引擎拆成两个独立插件 tts-cosyvoice3 / tts-piper。
-- 老卡包按原来选中的 engine 列决定自动安装哪个新插件，老结构随后清掉。

-- 1. 注册两个新插件的全局功能开关。
INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `rollout_type`, `description`) VALUES
('feature.tts-cosyvoice3', 1, 'GLOBAL', 'TTS 英语（CosyVoice3 引擎）'),
('feature.tts-piper', 1, 'GLOBAL', 'TTS 英语（Piper 引擎）')
ON DUPLICATE KEY UPDATE
  `enabled` = VALUES(`enabled`),
  `rollout_type` = VALUES(`rollout_type`),
  `description` = VALUES(`description`);

-- 2. 注册两个新插件的市场目录条目，并移除旧的单一 TTS 条目。
INSERT INTO `pw_type_registry`
(`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('plugin', 'tts-cosyvoice3', 'TTS 英语_cosyvoice3',
 '{"desc":"自动朗读卡片正反面（CosyVoice3 引擎）","icon":"🔊","category":"学习辅助"}', 1, 1),
('plugin', 'tts-piper', 'TTS 英语_piper',
 '{"desc":"自动朗读卡片正反面（Piper 引擎）","icon":"🔊","category":"学习辅助"}', 2, 1)
ON DUPLICATE KEY UPDATE
  `item_name` = VALUES(`item_name`),
  `config` = VALUES(`config`),
  `sort_order` = VALUES(`sort_order`),
  `enabled` = VALUES(`enabled`);

DELETE FROM `pw_type_registry` WHERE `registry_type` = 'plugin' AND `item_key` = 'tts';

-- 3. 按老卡包当前 engine 列翻译成新插件的安装记录，然后删除旧的 tts 安装行。
INSERT INTO `pw_plugin_install` (`user_id`, `deck_id`, `plugin_id`, `installed_at`)
SELECT
    pi.`user_id`,
    pi.`deck_id`,
    CASE WHEN tds.`engine` = 'piper' THEN 'tts-piper' ELSE 'tts-cosyvoice3' END,
    pi.`installed_at`
FROM `pw_plugin_install` pi
LEFT JOIN `pw_tts_deck_settings` tds ON tds.`deck_id` = pi.`deck_id`
WHERE pi.`plugin_id` = 'tts'
ON DUPLICATE KEY UPDATE `installed_at` = VALUES(`installed_at`);

DELETE FROM `pw_plugin_install` WHERE `plugin_id` = 'tts';

-- 4. 卡包设置表增加 plugin_id 维度，按老 engine 列回填，随后删除 engine 列。
ALTER TABLE `pw_tts_deck_settings`
    ADD COLUMN `plugin_id` VARCHAR(32) NOT NULL DEFAULT 'tts-cosyvoice3' AFTER `deck_id`;

UPDATE `pw_tts_deck_settings`
SET `plugin_id` = CASE WHEN `engine` = 'piper' THEN 'tts-piper' ELSE 'tts-cosyvoice3' END;

ALTER TABLE `pw_tts_deck_settings`
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (`deck_id`, `plugin_id`),
    DROP COLUMN `engine`;

-- 5. 移除不再需要的旧配置：单一 TTS 开关与引擎下拉注册项。
DELETE FROM `pw_feature_flag` WHERE `feature_key` = 'feature.tts';
DELETE FROM `pw_type_registry` WHERE `registry_type` = 'tts_engine';
