ALTER TABLE `pw_user`
  ADD COLUMN `role` varchar(16) NOT NULL DEFAULT 'USER' COMMENT '账号角色: ADMIN 或 USER' AFTER `nickname`,
  ADD CONSTRAINT `chk_pw_user_role` CHECK (`role` IN ('ADMIN', 'USER'));

UPDATE `pw_user`
SET `role` = 'ADMIN'
WHERE `username` = 'root' AND `deleted` = 0;

ALTER TABLE `pw_feature_flag`
  ADD COLUMN `rollout_type` varchar(20) NOT NULL DEFAULT 'GLOBAL'
  COMMENT 'GLOBAL=全局统一; USER_OVERRIDE=允许用户覆盖' AFTER `enabled`;

CREATE TABLE `pw_user_feature_flag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `feature_key` varchar(191) NOT NULL,
  `enabled` tinyint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_user_feature_flag` (`user_id`, `feature_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `rollout_type`, `description`)
VALUES ('feature.ai.codex-cli.user-access', 0, 'USER_OVERRIDE', 'Codex CLI per-user access')
ON DUPLICATE KEY UPDATE
  `enabled` = VALUES(`enabled`),
  `rollout_type` = VALUES(`rollout_type`),
  `description` = VALUES(`description`);

INSERT INTO `pw_user_feature_flag` (`user_id`, `feature_key`, `enabled`)
SELECT DISTINCT `user_id`, 'feature.ai.codex-cli.user-access', 1
FROM `pw_user_ai_config`
WHERE `provider` = 'codex-cli'
ON DUPLICATE KEY UPDATE `enabled` = VALUES(`enabled`);
