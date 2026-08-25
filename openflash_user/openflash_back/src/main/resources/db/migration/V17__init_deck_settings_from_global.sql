INSERT INTO pw_deck_settings (
    deck_id,
    new_cards_per_day,
    target_retention,
    review_load_profile,
    auto_speak_a,
    auto_speak_b,
    duplicate_side_a_enabled,
    duplicate_side_b_enabled,
    updated_at
)
SELECT
    d.id,
    COALESCE(s.new_cards_per_day,        10),
    COALESCE(s.target_retention,         0.9000),
    COALESCE(s.review_load_profile,      'standard'),
    COALESCE(s.auto_speak_a,             0),
    COALESCE(s.auto_speak_b,             0),
    COALESCE(s.duplicate_side_a_enabled, 1),
    COALESCE(s.duplicate_side_b_enabled, 0),
    NOW()
FROM pw_deck d
LEFT JOIN pw_user_settings s ON s.user_id = d.user_id
WHERE d.deleted = 0;
