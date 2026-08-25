DROP TABLE IF EXISTS pw_user_ai_config;

CREATE TABLE pw_user_ai_config (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT      NOT NULL                COMMENT '用户 ID',
    provider   VARCHAR(20) NOT NULL                COMMENT 'OLLAMA 或 DEEPSEEK',
    config     VARCHAR(2000) NOT NULL DEFAULT '{}'  COMMENT 'provider 连接参数 JSON',
    is_active  TINYINT(1)  NOT NULL DEFAULT 0      COMMENT '当前选用的 provider，每用户只有一行为 1',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_provider (user_id, provider)
) COMMENT='用户 AI provider 配置，每 provider 一行';
