INSERT INTO `pw_system_config`
(`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('tts', 'tts.request-queue-capacity', '1', 'INT',
 'Maximum queued TTS cache misses; changes require an application restart'),
('tts', 'tts.max-concurrent-requests-per-user', '2', 'INT',
 'Maximum TTS requests held by one user; changes require an application restart')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
