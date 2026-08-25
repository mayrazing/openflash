-- 删除 user_settings.think：Anthropic provider 全局禁用 thinking，用户级 think 字段失去意义。
ALTER TABLE pw_user_settings DROP COLUMN think;
