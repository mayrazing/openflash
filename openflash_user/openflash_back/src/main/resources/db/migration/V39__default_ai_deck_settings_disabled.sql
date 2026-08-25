-- 新卡包默认安装 ai-card，但卡包内 AI 解析和补全功能默认关闭。
ALTER TABLE pw_deck_ai_settings
    MODIFY COLUMN ai_explanation_enabled_a TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'A 面解析开关：1=启用，0=关闭',
    MODIFY COLUMN ai_explanation_enabled_b TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'B 面解析开关：1=启用，0=关闭',
    MODIFY COLUMN ai_completion_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '补全另一面开关：1=启用，0=关闭';
