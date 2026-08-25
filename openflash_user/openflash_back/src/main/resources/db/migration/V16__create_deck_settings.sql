CREATE TABLE pw_deck_settings (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    deck_id                  BIGINT       NOT NULL,
    new_cards_per_day        INT          NOT NULL DEFAULT 10,
    target_retention         DECIMAL(5,4) NOT NULL DEFAULT 0.9000,
    review_load_profile      VARCHAR(32)  NOT NULL DEFAULT 'standard',
    auto_speak_a             TINYINT(1)   NOT NULL DEFAULT 0,
    auto_speak_b             TINYINT(1)   NOT NULL DEFAULT 0,
    duplicate_side_a_enabled TINYINT(1)   NOT NULL DEFAULT 1,
    duplicate_side_b_enabled TINYINT(1)   NOT NULL DEFAULT 0,
    updated_at               DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_deck_settings_deck_id (deck_id)
);
