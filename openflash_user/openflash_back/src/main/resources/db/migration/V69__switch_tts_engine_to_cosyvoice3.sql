INSERT INTO `pw_system_config`
(`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('tts', 'tts.voice', 'default', 'STRING',
 'Compatibility voice identifier; CosyVoice3 currently uses one fixed reference voice'),
('tts', 'tts.engine-version', 'cosyvoice3-rl-fp16', 'STRING',
 'TTS engine cache fingerprint; changes take effect within 60 seconds')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value` = VALUES(`value`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
