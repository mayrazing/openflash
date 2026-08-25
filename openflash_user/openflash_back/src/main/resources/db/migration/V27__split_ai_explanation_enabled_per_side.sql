ALTER TABLE pw_deck_ai_settings
    ADD COLUMN ai_explanation_enabled_a TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'A 面解析开关：1=启用，0=关闭',
    ADD COLUMN ai_explanation_enabled_b TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'B 面解析开关：1=启用，0=关闭';

UPDATE pw_deck_ai_settings
SET ai_explanation_enabled_a = ai_explanation_enabled,
    ai_explanation_enabled_b = ai_explanation_enabled;

ALTER TABLE pw_deck_ai_settings
    DROP COLUMN ai_explanation_enabled;
