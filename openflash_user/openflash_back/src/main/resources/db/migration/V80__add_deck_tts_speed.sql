ALTER TABLE `pw_tts_deck_settings`
    ADD COLUMN `speed` DECIMAL(4,2) NOT NULL DEFAULT 1.00 AFTER `engine`,
    ADD CONSTRAINT `chk_tts_deck_speed`
        CHECK (`speed` BETWEEN 0.70 AND 1.20);
