ALTER TABLE `pw_tts_deck_settings`
    ADD COLUMN `engine` VARCHAR(32) NOT NULL DEFAULT 'cosyvoice3' AFTER `auto_speak_b`;

INSERT INTO `pw_type_registry`
(`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('tts_engine', 'cosyvoice3', 'cosyvoice3', '{}', 1, 1),
('tts_engine', 'piper',      'piper',      '{}', 2, 1)
ON DUPLICATE KEY UPDATE
  `item_name` = VALUES(`item_name`),
  `config` = VALUES(`config`),
  `sort_order` = VALUES(`sort_order`),
  `enabled` = VALUES(`enabled`);

INSERT INTO `pw_system_config`
(`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('tts', 'tts.piper-engine-version', 'piper-1.6.0-lessac-medium', 'STRING',
 'Piper synthesis cache version; changes take effect within 60 seconds')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
