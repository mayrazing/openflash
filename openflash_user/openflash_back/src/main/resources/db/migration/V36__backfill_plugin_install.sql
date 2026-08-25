-- 纳入 TTS/AI：给所有现有卡包补默认安装记录，保证老用户功能不丢。
-- 与全局开关无关；最终可见性仍由「已装 ∩ 全局启用」决定（见 PluginInstallService）。
INSERT INTO `pw_plugin_install` (`user_id`, `deck_id`, `plugin_id`, `installed_at`)
SELECT d.`user_id`, d.`id`, 'tts', NOW()
FROM `pw_deck` d
ON DUPLICATE KEY UPDATE `installed_at` = `pw_plugin_install`.`installed_at`;

INSERT INTO `pw_plugin_install` (`user_id`, `deck_id`, `plugin_id`, `installed_at`)
SELECT d.`user_id`, d.`id`, 'ai-card', NOW()
FROM `pw_deck` d
ON DUPLICATE KEY UPDATE `installed_at` = `pw_plugin_install`.`installed_at`;
