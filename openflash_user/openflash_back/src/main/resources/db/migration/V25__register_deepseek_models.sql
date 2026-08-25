INSERT INTO pw_type_registry (registry_type, item_key, item_name, config, sort_order, enabled) VALUES
('deepseek_model', 'deepseek-v4-flash', 'DeepSeek V4 Flash', '{}', 1, 1),
('deepseek_model', 'deepseek-v4-pro',   'DeepSeek V4 Pro',   '{}', 2, 1)
ON DUPLICATE KEY UPDATE
  item_name  = VALUES(item_name),
  config     = VALUES(config),
  sort_order = VALUES(sort_order),
  enabled    = VALUES(enabled);
