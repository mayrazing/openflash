INSERT INTO `pw_system_config` (`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('sse', 'sse.heartbeat-interval-millis', '25000', 'LONG', 'SSE 连接心跳间隔（毫秒，修改后重启生效）')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
