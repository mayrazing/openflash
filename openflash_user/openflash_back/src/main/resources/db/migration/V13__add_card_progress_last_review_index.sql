CREATE INDEX idx_pw_card_progress_user_last_review_card_direction
    ON pw_card_progress (user_id, last_review_date, card_id, direction);
