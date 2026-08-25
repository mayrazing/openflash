CREATE TABLE pw_deck_ai_settings (
    id                      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    deck_id                 BIGINT      NOT NULL COMMENT '关联卡包 ID',
    ai_explanation_enabled  TINYINT(1)  NOT NULL DEFAULT 1  COMMENT '卡片解析开关：1=启用，0=关闭',
    ai_explanation_mode     VARCHAR(20) NOT NULL DEFAULT 'shared'
                            COMMENT '卡片解析提示词模式：shared=共用，independent=A/B独立',
    ai_explanation_prompt_a TEXT        DEFAULT NULL COMMENT '卡片解析 A 面提示词；NULL=无 system prompt',
    ai_explanation_prompt_b TEXT        DEFAULT NULL COMMENT '卡片解析 B 面提示词；NULL=无 system prompt',
    ai_completion_enabled   TINYINT(1)  NOT NULL DEFAULT 1  COMMENT '补全另一面开关：1=启用，0=关闭',
    ai_completion_prompt    TEXT        DEFAULT NULL COMMENT '补全另一面提示词；NULL=无 system prompt',
    updated_at              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_deck_ai_settings_deck_id (deck_id)
);
