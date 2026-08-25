-- 插件目录元数据：市场「全部」列表的数据源（名称/简介/图标/分类）。
INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('plugin', 'tts',     'TTS 朗读',    '{"desc":"自动朗读卡片正反面，支持语速与音色","icon":"🔊","category":"学习辅助"}', 1, 1),
('plugin', 'ai-card', 'AI 卡片解析', '{"desc":"AI 生成释义、例句与常见搭配","icon":"🤖","category":"AI"}',          2, 1);

-- 市场入口总开关。
INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `rollout_type`, `description`)
VALUES ('feature.plugin-marketplace', 1, 'GLOBAL', '插件市场入口')
ON DUPLICATE KEY UPDATE `enabled` = `enabled`;
