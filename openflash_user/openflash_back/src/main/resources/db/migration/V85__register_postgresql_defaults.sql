-- Restore PostgreSQL schema semantics lost during pgloader conversion, then register
-- version 84 system defaults without overwriting migrated administrator choices.
SET search_path TO ${flyway:defaultSchema}, public;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'pw_platform_ai_offering'
          AND column_name = 'dynamic_connection_id'
          AND is_generated = 'NEVER'
    ) THEN
        DROP INDEX IF EXISTS idx_16504_uk_platform_ai_dynamic_connection;
        ALTER TABLE pw_platform_ai_offering DROP COLUMN dynamic_connection_id;
        ALTER TABLE pw_platform_ai_offering
            ADD COLUMN dynamic_connection_id bigint GENERATED ALWAYS AS (
                CASE WHEN model_key IS NULL THEN connection_id ELSE NULL::bigint END
            ) STORED;
        CREATE UNIQUE INDEX idx_16504_uk_platform_ai_dynamic_connection
            ON pw_platform_ai_offering (dynamic_connection_id);
    END IF;
END
$$;

DO $$
DECLARE
    target_table text;
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        'pw_async_task',
        'pw_card',
        'pw_card_ai_cache',
        'pw_card_progress',
        'pw_deck',
        'pw_deck_ai_settings',
        'pw_feature_flag',
        'pw_mask_mode_deck_settings',
        'pw_platform_ai_connection',
        'pw_platform_ai_secret',
        'pw_practice_session_store',
        'pw_system_config',
        'pw_type_registry',
        'pw_user',
        'pw_user_ai_config',
        'pw_user_feature_flag',
        'pw_user_platform_ai_preference',
        'pw_user_settings'
    ]
    LOOP
        EXECUTE format(
            'UPDATE %I SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL',
            target_table
        );
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP',
            target_table
        );
        EXECUTE format(
            'ALTER TABLE %I ALTER COLUMN updated_at SET NOT NULL',
            target_table
        );
    END LOOP;
END
$$;

INSERT INTO pw_feature_flag (id, feature_key, enabled, rollout_type, description, updated_at, updated_by) VALUES (2, 'feature.ai.card-markdown', 1, 'GLOBAL', 'AI 词卡解析', '2026-05-11 20:34:07+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_feature_flag (id, feature_key, enabled, rollout_type, description, updated_at, updated_by) VALUES (3, 'feature.ai.side-completion', 1, 'GLOBAL', 'AI 补全另一面', '2026-05-11 20:34:07+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_feature_flag (id, feature_key, enabled, rollout_type, description, updated_at, updated_by) VALUES (4, 'feature.card.export', 1, 'GLOBAL', '卡片导出（可按用户单独控制）', '2026-05-11 20:34:07+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_feature_flag (id, feature_key, enabled, rollout_type, description, updated_at, updated_by) VALUES (5, 'feature.ai.collocations', 1, 'GLOBAL', 'AI 常见搭配一键放入卡包', '2026-06-08 16:55:05+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_feature_flag (id, feature_key, enabled, rollout_type, description, updated_at, updated_by) VALUES (6, 'feature.plugin-marketplace', 1, 'GLOBAL', '插件市场入口', '2026-06-10 20:46:32+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_feature_flag (id, feature_key, enabled, rollout_type, description, updated_at, updated_by) VALUES (7, 'feature.browser-import', 1, 'GLOBAL', '浏览器插件导入', '2026-06-16 02:04:37+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_feature_flag (id, feature_key, enabled, rollout_type, description, updated_at, updated_by) VALUES (8, 'feature.mask-mode', 1, 'GLOBAL', '遮蔽模式插件', '2026-06-21 20:20:05+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_feature_flag (id, feature_key, enabled, rollout_type, description, updated_at, updated_by) VALUES (14, 'feature.tts', 1, 'GLOBAL', 'English TTS', '2026-08-15 18:42:55+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_platform_ai_connection (id, connection_key, kind, protocol, cli_key, config, credentials_configured, enabled, sort_order, updated_at) VALUES (1, 'platform-codex', 'CLI', 'CODEX_APP_SERVER', 'codex', '{}', 0, 1, 0, '2026-07-30 16:18:12+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_platform_ai_offering (id, connection_id, offering_key, model_key, reasoning_effort, enabled, default_access, sort_order) VALUES (1, 1, 'platform-codex-cli', NULL, NULL, 1, 0, 0) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (1, 'tts', 'tts.voice', 'default', 'STRING', 'Compatibility voice identifier; CosyVoice3 currently uses one fixed reference voice', '2026-08-09 21:36:50+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (2, 'tts', 'tts.speed', '0.95', 'DECIMAL', 'Fixed CosyVoice3 synthesis speed; changes take effect within 60 seconds', '2026-08-10 14:18:49+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (3, 'tts', 'tts.max-concurrent-requests', '1', 'INT', 'TTS 最大并发数', '2026-05-11 20:34:07+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (4, 'tts', 'tts.connect-timeout-millis', '5000', 'INT', 'TTS 连接超时(ms)', '2026-05-11 20:34:07+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (5, 'tts', 'tts.request-timeout-millis', '30000', 'INT', 'TTS request timeout for multi-candidate synthesis; changes take effect within 60 seconds', '2026-08-10 02:13:45+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (6, 'ai', 'ai.think', 'false', 'BOOL', 'AI 默认思考模式', '2026-05-11 20:34:07+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (7, 'ai', 'ai.timeout-millis', '180000', 'INT', 'AI 请求超时(ms)，重启后生效', '2026-06-01 20:20:58+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (8, 'async-task', 'async-task.process-batch-size', '20', 'INT', '每轮消费任务数', '2026-05-11 20:34:07+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (9, 'async-task', 'async-task.fixed-delay-millis', '1000', 'INT', '消费间隔(ms)', '2026-07-28 21:16:41+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (10, 'async-task', 'async-task.lease-millis', '120000', 'INT', '任务租约时长(ms)', '2026-05-11 20:34:07+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (11, 'cache', 'cache.ttl-days', '100', 'INT', 'AI/TTS 缓存保留天数', '2026-05-11 20:34:07+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (12, 'cache', 'cache.touch-min-interval-hours', '6', 'INT', '缓存访问节流窗口(h)', '2026-05-11 20:34:07+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (13, 'practice', 'practice.review.target-daily-directions', '40', 'INT', '每日目标复习方向数', '2026-05-14 03:18:52+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (14, 'practice', 'practice.review.absolute-daily-directions', '70', 'INT', '每日复习方向绝对上限', '2026-05-14 03:18:52+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (15, 'practice', 'practice.review.max-deferral-days', '3', 'INT', '低风险复习方向最多平滑延期天数', '2026-05-13 20:51:53+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (16, 'practice', 'practice.review.backlog-pause-new-threshold', '120', 'INT', '积压达到该方向数后暂停新词', '2026-05-14 03:18:52+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (17, 'practice', 'practice.review.backlog-resume-new-threshold', '40', 'INT', '积压低于该方向数后恢复新词', '2026-05-14 03:18:52+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (18, 'practice', 'practice.response-time.timeout-seconds', '60', 'INT', '翻牌后超时作废阈值（秒），超过则卡片重回队列不计分', '2026-05-24 00:04:43+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (19, 'practice', 'practice.response-time.grade3-slow-threshold-seconds', '8', 'INT', '3分（记得很清楚）降档阈值（秒），超过则降为2分', '2026-05-24 01:04:27+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (20, 'practice', 'practice.response-time.grade2-slow-threshold-seconds', '15', 'INT', '2分（想起来了）降档阈值（秒），超过则降为1分', '2026-05-24 01:04:27+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (21, 'sse', 'sse.heartbeat-interval-millis', '25000', 'LONG', 'SSE 连接心跳间隔（毫秒，修改后重启生效）', '2026-07-11 22:40:09+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (26, 'ai', 'ai.codex-timeout-millis', '90000', 'INT', 'Codex CLI request timeout in milliseconds', '2026-07-27 08:55:57+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (27, 'ai', 'ai.codex-status-timeout-millis', '5000', 'INT', 'Codex CLI status timeout in milliseconds', '2026-07-27 08:55:57+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (28, 'ai', 'ai.codex-home', '~/.local/share/openflash/codex-home', 'STRING', 'OpenFlash-owned CODEX_HOME; restart OpenFlash after changing', '2026-07-28 13:43:44+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (29, 'ai', 'ai.codex-login-timeout-millis', '600000', 'INT', 'Shared Codex device login timeout in milliseconds', '2026-07-27 20:47:10+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (30, 'import', 'import.zip.max-entry-bytes', '52428800', 'LONG', 'Maximum decompressed bytes for one recognized ZIP import entry; changes take effect within 60 seconds', '2026-07-31 11:02:21+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (31, 'import', 'import.zip.max-total-bytes', '104857600', 'LONG', 'Maximum aggregate decompressed bytes for recognized ZIP import entries; changes take effect within 60 seconds', '2026-07-31 11:02:21+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (32, 'import', 'import.zip.max-entries', '100', 'INT', 'Maximum number of entries inspected in one ZIP import; changes take effect within 60 seconds', '2026-07-31 11:02:21+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (33, 'tts', 'tts.capacity-wait-timeout-millis', '250', 'LONG', 'Maximum wait for TTS upstream capacity in milliseconds; changes take effect within 60 seconds', '2026-07-31 11:02:21+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (34, 'auth', 'auth.login.max-attempts', '5', 'INT', 'Maximum failed login attempts per account or source during one window; changes take effect within 60 seconds', '2026-07-31 11:02:21+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (35, 'auth', 'auth.login.window-millis', '900000', 'LONG', 'Failed login attempt window in milliseconds; changes take effect within 60 seconds', '2026-07-31 11:02:21+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (40, 'tts', 'tts.request-queue-capacity', '1', 'INT', 'Maximum queued TTS cache misses; changes require an application restart', '2026-07-31 11:27:55+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (41, 'tts', 'tts.max-concurrent-requests-per-user', '2', 'INT', 'Maximum TTS requests held by one user; changes require an application restart', '2026-07-31 11:27:55+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (42, 'tts', 'tts.engine-version', 'tts-synthesis-v5', 'STRING', 'TTS synthesis cache version; changes take effect within 60 seconds', '2026-08-10 02:11:33+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (45, 'tts', 'tts.accent', 'american', 'STRING', 'TTS accent instruction; changes take effect within 60 seconds', '2026-08-10 00:40:27+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (50, 'tts', 'tts.piper-engine-version', 'piper-1.6.0-libritts-r-medium-speaker-0', 'STRING', 'Piper synthesis cache version; changes take effect within 60 seconds', '2026-08-10 12:58:30+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_system_config (id, group_name, config_key, value, value_type, description, updated_at, updated_by) VALUES (52, 'tts', 'tts.piper-speed', '0.70', 'DECIMAL', 'Fixed Piper synthesis speed; changes take effect within 60 seconds', '2026-08-10 14:18:49+03', NULL) ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (1, 'ai_profile', 'ai_cache', 'AI词卡解析Profile', '{"model": "qwen3.5:9b", "system": "回答规则：\n  - 回答内容只围绕输入的单词/短语/表达，禁止添加任何无关内容\n  - 回答内容必须符合以下格式模板\n  - 请用中文详细解释只解释作为英文母语者在日常口语和听力中第一反应的意思、语感、用在什么场合\n  - 例句只列出英文原句+中文翻译，日常高频口语和高频听力场景\n  - 常见搭配只列出口语和听力中高频常见的\n  - 含义只列口语和听力中高频的词性和含义，严格按此格式输出，例如：n. 酒精；v. 使兴奋，没有的词性不写\n  - 回答第一行必须是：📌 单词：xxx 或 📌 短语：xxx 或 📌 对应表达：xxx，根据输入内容三选一，禁止其他写法\n  - 禁止在格式模板之外自行添加任何额外内容\n\n请按以下格式回答:\n📌 单词：xxx\n🔤 音标:/xxx/\n📚 含义:xxx\n\n📖 详细解释:\nxxx\n\n✏️ 例句:\n1. xxx（中文翻译）\n2. xxx（中文翻译）\n3. xxx（中文翻译）\n\n💡 常见搭配:\n- xxx\n- xxx\n- xxx", "temperature": 0.1}', 1, 1, '2026-05-11 20:34:07+03', '2026-05-12 09:21:46+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (2, 'ai_profile', 'ai_side_completion', 'AI补全另一面Profile', '{"model": "qwen3.5:9b", "system": "回答必须遵循以下规则：\n  - 当输入为中文时，无论是单词、短语还是句子，仅输出在口语和听力场景中最高频、最自然的英文对应表达，不附加任何解释、标点、引号或格式符号。\n  - 当输入为英文单词时，仅输出紧凑词性释义，格式为：有哪个词性就写哪个词性，每个词性后跟中文释义，词性之间用分号分隔，只取口语和听力中第一反应的最高频含义，不换行。当输入为英文短语或句子时，直接输出最自然的中文对应表达。\n  - 不输出任何前缀、后缀或说明性文字。\n  - 示例：\n  - 输入:\"放弃\" → 输出: give up\n  - 输入:\"你说得对\" → 输出: you''re right\n    输入\"abandon\" → 输出: v.放弃;抛弃\n    输入\"give it a shot\" → 输出: 试试看", "temperature": 0.1}', 2, 1, '2026-05-11 20:34:07+03', '2026-05-11 22:43:11+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (3, 'ai_feature_mapping', 'card-ai-markdown', 'AI词卡解析功能映射', '{"profile_name":"ai_cache"}', 1, 1, '2026-05-11 20:34:07+03', '2026-05-11 20:34:07+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (4, 'ai_feature_mapping', 'card-side-completion', 'AI补全另一面功能映射', '{"profile_name":"ai_side_completion"}', 2, 1, '2026-05-11 20:34:07+03', '2026-05-11 20:34:07+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (5, 'practice_mode', 'a2b', NULL, '{}', 1, 1, '2026-05-11 20:34:07+03', '2026-06-10 16:17:25+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (6, 'practice_mode', 'b2a', NULL, '{}', 2, 1, '2026-05-11 20:34:07+03', '2026-06-10 16:17:25+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (7, 'practice_mode', 'random', NULL, '{}', 3, 1, '2026-05-11 20:34:07+03', '2026-06-10 16:17:25+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (8, 'review_load_profile', 'relaxed', '轻松', '{}', 1, 1, '2026-05-14 03:17:12+03', '2026-05-14 03:17:12+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (9, 'review_load_profile', 'standard', '标准', '{}', 2, 1, '2026-05-14 03:17:12+03', '2026-05-14 03:17:12+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (10, 'review_load_profile', 'intensive', '强化', '{}', 3, 1, '2026-05-14 03:17:12+03', '2026-05-14 03:17:12+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (11, 'deck_ai_explanation_mode', 'shared', '共用', '{}', 1, 1, '2026-06-02 14:37:00+03', '2026-06-02 14:37:00+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (12, 'deck_ai_explanation_mode', 'independent', 'A/B 独立', '{}', 2, 1, '2026-06-02 14:37:00+03', '2026-06-02 14:37:00+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (13, 'deepseek_model', 'deepseek-v4-flash', 'DeepSeek V4 Flash', '{}', 1, 1, '2026-06-03 19:51:49+03', '2026-06-03 19:51:49+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (14, 'deepseek_model', 'deepseek-v4-pro', 'DeepSeek V4 Pro', '{}', 2, 1, '2026-06-03 19:51:49+03', '2026-06-03 19:51:49+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (15, 'interface_language', 'zh', '中文', '{}', 1, 1, '2026-06-07 19:10:06+03', '2026-06-07 19:10:06+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (16, 'interface_language', 'en', 'English', '{}', 2, 1, '2026-06-07 19:10:06+03', '2026-06-07 19:10:06+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (17, 'interface_language', 'fi', 'Suomi', '{}', 3, 1, '2026-06-07 19:10:06+03', '2026-06-07 19:10:06+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (18, 'interface_language', 'de', 'Deutsch', '{}', 4, 1, '2026-06-07 19:10:06+03', '2026-06-07 19:10:06+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (20, 'plugin', 'ai-card', 'AI 卡片解析', '{"desc":"AI 生成释义、例句与常见搭配","icon":"🤖","category":"AI"}', 2, 1, '2026-06-10 20:46:32+03', '2026-06-10 20:46:32+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (21, 'plugin', 'mask-mode', 'plugins.mask-mode.name', '{"descKey":"plugins.mask-mode.desc","icon":"🙈","categoryKey":"pluginCategories.studyAid"}', 3, 1, '2026-06-21 20:20:05+03', '2026-06-22 02:11:02+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (28, 'plugin', 'tts', 'tts', '{"icon":"🔊","category":"study"}', 1, 1, '2026-08-15 18:42:55+03', '2026-08-15 18:42:55+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (29, 'tts_engine', 'cosyvoice3', 'cosyvoice3', '{}', 1, 1, '2026-08-15 18:42:55+03', '2026-08-15 18:42:55+03') ON CONFLICT DO NOTHING;

INSERT INTO pw_type_registry (id, registry_type, item_key, item_name, config, sort_order, enabled, created_at, updated_at) VALUES (30, 'tts_engine', 'piper', 'piper', '{}', 2, 1, '2026-08-15 18:42:55+03', '2026-08-15 18:42:55+03') ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('pw_feature_flag', 'id'), GREATEST(COALESCE(MAX(id), 1), (SELECT last_value FROM pw_feature_flag_id_seq)), true) FROM pw_feature_flag;
SELECT setval(pg_get_serial_sequence('pw_system_config', 'id'), GREATEST(COALESCE(MAX(id), 1), (SELECT last_value FROM pw_system_config_id_seq)), true) FROM pw_system_config;
SELECT setval(pg_get_serial_sequence('pw_type_registry', 'id'), GREATEST(COALESCE(MAX(id), 1), (SELECT last_value FROM pw_type_registry_id_seq)), true) FROM pw_type_registry;
SELECT setval(pg_get_serial_sequence('pw_platform_ai_connection', 'id'), GREATEST(COALESCE(MAX(id), 1), (SELECT last_value FROM pw_platform_ai_connection_id_seq)), true) FROM pw_platform_ai_connection;
SELECT setval(pg_get_serial_sequence('pw_platform_ai_offering', 'id'), GREATEST(COALESCE(MAX(id), 1), (SELECT last_value FROM pw_platform_ai_offering_id_seq)), true) FROM pw_platform_ai_offering;
