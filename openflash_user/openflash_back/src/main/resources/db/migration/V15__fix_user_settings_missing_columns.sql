ALTER TABLE pw_user_settings
    ADD COLUMN new_cards_per_day        int            NOT NULL DEFAULT 10,
    ADD COLUMN target_retention         decimal(5,4)   NOT NULL DEFAULT 0.9000,
    ADD COLUMN review_load_profile      varchar(20)    NOT NULL DEFAULT 'standard',
    ADD COLUMN auto_speak_a             tinyint        NOT NULL DEFAULT 0,
    ADD COLUMN auto_speak_b             tinyint        NOT NULL DEFAULT 0,
    ADD COLUMN duplicate_side_a_enabled tinyint        NOT NULL DEFAULT 1,
    ADD COLUMN duplicate_side_b_enabled tinyint        NOT NULL DEFAULT 0;
