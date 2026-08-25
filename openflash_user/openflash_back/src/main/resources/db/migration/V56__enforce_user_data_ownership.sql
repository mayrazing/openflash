DELETE cm FROM pw_card_media cm
LEFT JOIN pw_card c ON c.id = cm.card_id
LEFT JOIN pw_deck d ON d.id = c.deck_id
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE c.id IS NULL OR d.id IS NULL OR u.id IS NULL;

DELETE cp FROM pw_card_progress cp
LEFT JOIN pw_user direct_user ON direct_user.id = cp.user_id
LEFT JOIN pw_card c ON c.id = cp.card_id
LEFT JOIN pw_deck d ON d.id = c.deck_id
LEFT JOIN pw_user deck_user ON deck_user.id = d.user_id
WHERE direct_user.id IS NULL OR c.id IS NULL OR d.id IS NULL OR deck_user.id IS NULL;

DELETE ds FROM pw_deck_settings ds
LEFT JOIN pw_deck d ON d.id = ds.deck_id
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE d.id IS NULL OR u.id IS NULL;

DELETE das FROM pw_deck_ai_settings das
LEFT JOIN pw_deck d ON d.id = das.deck_id
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE d.id IS NULL OR u.id IS NULL;

DELETE tds FROM pw_tts_deck_settings tds
LEFT JOIN pw_deck d ON d.id = tds.deck_id
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE d.id IS NULL OR u.id IS NULL;

DELETE mds FROM pw_mask_mode_deck_settings mds
LEFT JOIN pw_deck d ON d.id = mds.deck_id
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE d.id IS NULL OR u.id IS NULL;

DELETE ps FROM pw_practice_session_store ps
LEFT JOIN pw_user u ON u.id = ps.user_id
LEFT JOIN pw_deck d ON d.id = ps.deck_id
WHERE u.id IS NULL OR d.id IS NULL OR d.user_id <> ps.user_id;

DELETE pi FROM pw_plugin_install pi
LEFT JOIN pw_user u ON u.id = pi.user_id
LEFT JOIN pw_deck d ON d.id = pi.deck_id
WHERE u.id IS NULL OR d.id IS NULL OR d.user_id <> pi.user_id;

DELETE c FROM pw_card c
LEFT JOIN pw_deck d ON d.id = c.deck_id
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE d.id IS NULL OR u.id IS NULL;

DELETE d FROM pw_deck d
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE u.id IS NULL;

DELETE s FROM pw_user_settings s
LEFT JOIN pw_user u ON u.id = s.user_id
WHERE u.id IS NULL;

DELETE a FROM pw_user_ai_config a
LEFT JOIN pw_user u ON u.id = a.user_id
WHERE u.id IS NULL;

DELETE f FROM pw_user_feature_flag f
LEFT JOIN pw_user u ON u.id = f.user_id
WHERE u.id IS NULL;

DELETE up FROM pw_user_upload up
LEFT JOIN pw_user u ON u.id = up.user_id
WHERE u.id IS NULL;

ALTER TABLE pw_async_task
  ADD COLUMN owner_user_id BIGINT NULL AFTER id;

UPDATE pw_async_task t
JOIN pw_user u ON u.id = CAST(COALESCE(
  JSON_UNQUOTE(JSON_EXTRACT(t.payload, '$.userId')),
  JSON_UNQUOTE(JSON_EXTRACT(t.payload, '$.build.userId')),
  JSON_UNQUOTE(JSON_EXTRACT(t.payload, '$.notificationTarget.userId'))
) AS UNSIGNED)
SET t.owner_user_id = u.id
WHERE t.task_type IN ('AI_CACHE_BUILD', 'CARD_SIDE_COMPLETION')
  AND JSON_VALID(t.payload);

ALTER TABLE pw_deck ADD CONSTRAINT fk_deck_user
  FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;
ALTER TABLE pw_card ADD CONSTRAINT fk_card_deck
  FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_card_media ADD CONSTRAINT fk_card_media_card
  FOREIGN KEY (card_id) REFERENCES pw_card(id) ON DELETE CASCADE;
ALTER TABLE pw_card_progress
  ADD CONSTRAINT fk_card_progress_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_card_progress_card FOREIGN KEY (card_id) REFERENCES pw_card(id) ON DELETE CASCADE;
ALTER TABLE pw_user_settings ADD CONSTRAINT fk_user_settings_user
  FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;
ALTER TABLE pw_user_ai_config ADD CONSTRAINT fk_user_ai_config_user
  FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;
ALTER TABLE pw_user_feature_flag ADD CONSTRAINT fk_user_feature_flag_user
  FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;
ALTER TABLE pw_user_upload ADD CONSTRAINT fk_user_upload_user
  FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;
ALTER TABLE pw_practice_session_store
  ADD CONSTRAINT fk_practice_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_practice_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_plugin_install
  ADD CONSTRAINT fk_plugin_install_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_plugin_install_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_deck_settings ADD CONSTRAINT fk_deck_settings_deck
  FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_deck_ai_settings ADD CONSTRAINT fk_deck_ai_settings_deck
  FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_tts_deck_settings ADD CONSTRAINT fk_tts_deck_settings_deck
  FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_mask_mode_deck_settings ADD CONSTRAINT fk_mask_mode_deck_settings_deck
  FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_async_task ADD CONSTRAINT fk_async_task_owner_user
  FOREIGN KEY (owner_user_id) REFERENCES pw_user(id) ON DELETE CASCADE;
