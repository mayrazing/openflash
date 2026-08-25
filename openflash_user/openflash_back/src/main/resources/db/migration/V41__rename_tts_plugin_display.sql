UPDATE `pw_feature_flag`
SET `description` = 'TTS 英语功能'
WHERE `feature_key` = 'feature.tts';

UPDATE `pw_type_registry`
SET `item_name` = 'TTS 英语'
WHERE `registry_type` = 'plugin'
  AND `item_key` = 'tts';
