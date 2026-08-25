INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `rollout_type`, `description`)
VALUES ('feature.ai.collocations', 1, 'GLOBAL', 'AI 常见搭配一键放入卡包')
ON DUPLICATE KEY UPDATE `enabled` = `enabled`;
