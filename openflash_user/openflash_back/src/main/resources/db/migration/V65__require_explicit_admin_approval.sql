ALTER TABLE `pw_user`
  ADD COLUMN `admin_approved` tinyint NOT NULL DEFAULT 0
    COMMENT '管理员身份是否由可信操作员明确确认' AFTER `role`,
  ADD COLUMN `admin_approved_at` datetime DEFAULT NULL
    COMMENT '管理员身份确认时间' AFTER `admin_approved`,
  ADD COLUMN `admin_approval_source` varchar(32) DEFAULT NULL
    COMMENT '管理员身份确认来源' AFTER `admin_approved_at`,
  ADD CONSTRAINT `chk_pw_user_admin_approved`
    CHECK (`admin_approved` IN (0, 1));

-- 不按用户名自动回填. 升级后必须由可信操作员或已确认管理员明确授权.
