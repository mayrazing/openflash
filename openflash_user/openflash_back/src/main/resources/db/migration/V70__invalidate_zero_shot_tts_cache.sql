INSERT INTO `pw_system_config`
(`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('tts', 'tts.engine-version', 'tts-synthesis-v2', 'STRING',
 'TTS synthesis cache version; changes take effect within 60 seconds')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value` = VALUES(`value`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
