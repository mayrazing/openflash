ALTER TABLE `pw_user_settings`
  ADD COLUMN `review_load_profile` varchar(20) NOT NULL DEFAULT 'standard'
  COMMENT '学习强度档位：relaxed / standard / intensive' AFTER `target_retention`;

INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('review_load_profile', 'relaxed',   '轻松', '{}', 1, 1),
('review_load_profile', 'standard',  '标准', '{}', 2, 1),
('review_load_profile', 'intensive', '强化', '{}', 3, 1)
ON DUPLICATE KEY UPDATE
  `item_name` = VALUES(`item_name`),
  `config` = VALUES(`config`),
  `sort_order` = VALUES(`sort_order`),
  `enabled` = VALUES(`enabled`);
