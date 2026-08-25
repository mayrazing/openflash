INSERT INTO `pw_system_config`
(`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('tts', 'tts.request-timeout-millis', '30000', 'INT',
 'TTS request timeout for multi-candidate synthesis; changes take effect within 60 seconds')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value` = VALUES(`value`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
