-- Defensive clamp: V17 seeded deck_settings from pw_user_settings which may have contained
-- out-of-range or unknown values (e.g. old profile keys before the enum was introduced).
-- The review_load_profile whitelist intentionally mirrors PracticeReviewLoadProfile;
-- if a new profile is added to that enum, update this list in a subsequent migration.
UPDATE pw_deck_settings
SET new_cards_per_day = LEAST(50, GREATEST(0, COALESCE(new_cards_per_day, 10))),
    target_retention = LEAST(0.9700, GREATEST(0.7000, COALESCE(target_retention, 0.9000))),
    review_load_profile = CASE
        WHEN review_load_profile IN ('relaxed', 'standard', 'intensive') THEN review_load_profile
        ELSE 'standard'
    END;
