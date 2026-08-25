INSERT INTO `pw_system_config`
(`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('import', 'import.zip.max-entry-bytes', '52428800', 'LONG',
 'Maximum decompressed bytes for one recognized ZIP import entry; changes take effect within 60 seconds'),
('import', 'import.zip.max-total-bytes', '104857600', 'LONG',
 'Maximum aggregate decompressed bytes for recognized ZIP import entries; changes take effect within 60 seconds'),
('import', 'import.zip.max-entries', '100', 'INT',
 'Maximum number of entries inspected in one ZIP import; changes take effect within 60 seconds'),
('tts', 'tts.capacity-wait-timeout-millis', '250', 'LONG',
 'Maximum wait for TTS upstream capacity in milliseconds; changes take effect within 60 seconds'),
('auth', 'auth.login.max-attempts', '5', 'INT',
 'Maximum failed login attempts per account or source during one window; changes take effect within 60 seconds'),
('auth', 'auth.login.window-millis', '900000', 'LONG',
 'Failed login attempt window in milliseconds; changes take effect within 60 seconds')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
