ALTER TABLE `pw_tts_deck_settings`
    DROP CHECK `chk_tts_deck_speed`,
    DROP COLUMN `speed`;

UPDATE `pw_system_config`
SET `value` = '0.95',
    `value_type` = 'DECIMAL',
    `description` = 'Fixed CosyVoice3 synthesis speed; changes take effect within 60 seconds'
WHERE `config_key` = 'tts.speed';

INSERT INTO `pw_system_config`
(`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('tts', 'tts.piper-speed', '0.70', 'DECIMAL',
 'Fixed Piper synthesis speed; changes take effect within 60 seconds')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value` = VALUES(`value`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
