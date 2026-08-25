UPDATE `pw_system_config`
SET `description` = 'AI 请求超时(ms)，重启后生效'
WHERE `config_key` = 'ai.timeout-millis';
