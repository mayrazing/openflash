ALTER TABLE `pw_user`
  ADD COLUMN `banned` tinyint NOT NULL DEFAULT 0
    COMMENT '账号封禁状态: 0=可登录, 1=禁止登录' AFTER `role`,
  ADD INDEX `idx_pw_user_active_admin` (`deleted`, `banned`, `role`);
