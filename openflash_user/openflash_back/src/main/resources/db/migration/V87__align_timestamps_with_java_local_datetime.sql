-- 项目实体统一使用 LocalDateTime. PostgreSQL JDBC 不能把 timestamptz 直接读成 LocalDateTime.
-- 转换时保留当前数据库时区中看到的墙上时间, 与迁移前 MySQL DATETIME/TIMESTAMP 的应用表现一致.
ALTER TABLE pw_async_task
    ALTER COLUMN next_retry_at TYPE timestamp without time zone
        USING next_retry_at AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN lease_until TYPE timestamp without time zone
        USING lease_until AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN created_at TYPE timestamp without time zone
        USING created_at AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_card
    ALTER COLUMN created_at TYPE timestamp without time zone
        USING created_at AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_card_ai_cache
    ALTER COLUMN last_accessed_at TYPE timestamp without time zone
        USING last_accessed_at AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN last_generated_at TYPE timestamp without time zone
        USING last_generated_at AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN created_at TYPE timestamp without time zone
        USING created_at AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_card_media
    ALTER COLUMN created_at TYPE timestamp without time zone
        USING created_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_card_progress
    ALTER COLUMN mastered_at TYPE timestamp without time zone
        USING mastered_at AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_deck
    ALTER COLUMN created_at TYPE timestamp without time zone
        USING created_at AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_deck_ai_settings
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_deck_settings
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_feature_flag
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_mask_mode_deck_settings
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_platform_ai_connection
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_platform_ai_secret
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_plugin_install
    ALTER COLUMN installed_at TYPE timestamp without time zone
        USING installed_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_practice_session_store
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_system_config
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_tts_deck_settings
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_type_registry
    ALTER COLUMN created_at TYPE timestamp without time zone
        USING created_at AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_user
    ALTER COLUMN admin_approved_at TYPE timestamp without time zone
        USING admin_approved_at AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN created_at TYPE timestamp without time zone
        USING created_at AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_user_ai_config
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_user_feature_flag
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_user_platform_ai_preference
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_user_settings
    ALTER COLUMN last_exported_at TYPE timestamp without time zone
        USING last_exported_at AT TIME ZONE current_setting('TimeZone'),
    ALTER COLUMN updated_at TYPE timestamp without time zone
        USING updated_at AT TIME ZONE current_setting('TimeZone');

ALTER TABLE pw_user_upload
    ALTER COLUMN created_at TYPE timestamp without time zone
        USING created_at AT TIME ZONE current_setting('TimeZone');
