INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `description`)
VALUES ('feature.ai.codex-cli', 1, 'Codex CLI AI provider')
ON DUPLICATE KEY UPDATE `enabled` = `enabled`;

INSERT INTO `pw_system_config` (`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('ai', 'ai.codex-timeout-millis', '90000', 'INT', 'Codex CLI request timeout in milliseconds'),
('ai', 'ai.codex-status-timeout-millis', '5000', 'INT', 'Codex CLI status timeout in milliseconds')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);

INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('ai_provider_kind', 'codex-cli', 'settings.aiCodexCliName', '{"protocol":"CODEX_APP_SERVER","builtIn":true,"nameKey":"settings.aiCodexCliName","descriptionKey":"settings.aiCodexCliSharedLocalAccountDescription"}', 1, 1)
ON DUPLICATE KEY UPDATE `item_key` = `item_key`;
