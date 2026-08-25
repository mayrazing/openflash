DELETE FROM `pw_system_config`
WHERE `config_key` IN (
  'tts.cache.max-entries',
  'tts.cache.max-bytes',
  'tts.cache.max-entries-per-user',
  'tts.cache.max-bytes-per-user'
);

DROP TABLE IF EXISTS `pw_tts_cache_meta`;
