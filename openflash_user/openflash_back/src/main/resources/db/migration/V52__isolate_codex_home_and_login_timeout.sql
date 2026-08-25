INSERT INTO `pw_system_config`
(`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('ai', 'ai.codex-home', '~/.local/share/openflash/codex-home', 'STRING',
 'OpenFlash-owned CODEX_HOME; restart OpenFlash after changing'),
('ai', 'ai.codex-login-timeout-millis', '600000', 'INT',
 'Shared Codex device login timeout in milliseconds')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
