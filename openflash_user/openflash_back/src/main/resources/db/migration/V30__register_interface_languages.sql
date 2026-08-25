INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('interface_language', 'zh', '中文', '{}', 1, 1),
('interface_language', 'en', 'English', '{}', 2, 1),
('interface_language', 'fi', 'Suomi', '{}', 3, 1),
('interface_language', 'de', 'Deutsch', '{}', 4, 1)
ON DUPLICATE KEY UPDATE
  `item_name` = VALUES(`item_name`),
  `config` = VALUES(`config`),
  `sort_order` = VALUES(`sort_order`),
  `enabled` = VALUES(`enabled`);
