INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('deck_ai_explanation_mode', 'shared',      '共用', '{}', 1, 1),
('deck_ai_explanation_mode', 'independent', 'A/B 独立', '{}', 2, 1)
ON DUPLICATE KEY UPDATE
  `item_name` = VALUES(`item_name`),
  `config` = VALUES(`config`),
  `sort_order` = VALUES(`sort_order`),
  `enabled` = VALUES(`enabled`);
