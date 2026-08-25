ALTER TABLE `pw_tts_cache_meta`
  ADD COLUMN `voice` varchar(64) NOT NULL COMMENT 'TTS 声音' AFTER `normalized_text`,
  ADD COLUMN `speed` double NOT NULL COMMENT 'TTS 语速' AFTER `voice`;
