INSERT INTO `pw_system_config` (`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('practice', 'practice.review.target-daily-directions', '40', 'INT', '每日目标复习方向数'),
('practice', 'practice.review.absolute-daily-directions', '70', 'INT', '每日复习方向绝对上限'),
('practice', 'practice.review.max-deferral-days', '3', 'INT', '低风险复习方向最多平滑延期天数'),
('practice', 'practice.review.backlog-pause-new-threshold', '120', 'INT', '积压达到该方向数后暂停新卡'),
('practice', 'practice.review.backlog-resume-new-threshold', '40', 'INT', '积压低于该方向数后恢复新卡')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
