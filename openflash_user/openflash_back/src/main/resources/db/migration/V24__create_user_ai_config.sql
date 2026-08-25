CREATE TABLE pw_user_ai_config (
    id                    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id               BIGINT      NOT NULL                COMMENT '用户 ID，一用户一行',
    provider              VARCHAR(20) NOT NULL                COMMENT 'OLLAMA 或 DEEPSEEK',
    ollama_base_url       VARCHAR(255)         DEFAULT NULL   COMMENT 'Ollama 连接地址',
    ollama_model          VARCHAR(100)         DEFAULT NULL   COMMENT 'Ollama 模型名',
    deepseek_api_key_enc  TEXT                 DEFAULT NULL   COMMENT 'DeepSeek API Key，AES-GCM 加密，格式 Base64(IV||ciphertext)',
    deepseek_model        VARCHAR(100)         DEFAULT NULL   COMMENT 'DeepSeek 模型名',
    updated_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_ai_config_user_id (user_id)
) COMMENT='用户 AI provider 配置';
