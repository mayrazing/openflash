CREATE TABLE pw_tts_deck_settings (
    deck_id      BIGINT     NOT NULL,
    auto_speak_a TINYINT(1) NOT NULL DEFAULT 0,
    auto_speak_b TINYINT(1) NOT NULL DEFAULT 0,
    updated_at   DATETIME   NOT NULL,
    PRIMARY KEY (deck_id)
);

INSERT INTO pw_tts_deck_settings (
    deck_id,
    auto_speak_a,
    auto_speak_b,
    updated_at
)
SELECT
    deck_id,
    auto_speak_a,
    auto_speak_b,
    updated_at
FROM pw_deck_settings;

ALTER TABLE pw_deck_settings
    DROP COLUMN auto_speak_a,
    DROP COLUMN auto_speak_b;
