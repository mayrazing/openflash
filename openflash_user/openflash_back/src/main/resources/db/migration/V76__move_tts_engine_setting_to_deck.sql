ALTER TABLE pw_tts_deck_settings
    ADD COLUMN cosyvoice_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER auto_speak_b;

DROP TABLE IF EXISTS pw_tts_user_settings;
