INSERT INTO `pw_system_config`
(`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('tts', 'tts.piper-engine-version', 'piper-1.6.0-libritts-r-medium-speaker-0', 'STRING',
 'Piper synthesis cache version; changes take effect within 60 seconds')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);

UPDATE `pw_system_config`
SET `value` = 'piper-1.6.0-libritts-r-medium-speaker-0'
WHERE `config_key` = 'tts.piper-engine-version'
  AND `value` = 'piper-1.6.0-lessac-medium';
