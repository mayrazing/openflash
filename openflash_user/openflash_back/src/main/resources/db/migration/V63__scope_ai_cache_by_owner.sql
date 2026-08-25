ALTER TABLE `pw_card_ai_cache`
  ADD COLUMN `owner_user_id` BIGINT NULL AFTER `id`,
  DROP INDEX `uk_pw_card_ai_cache_fingerprint`,
  ADD UNIQUE KEY `uk_pw_card_ai_cache_owner_fingerprint` (`owner_user_id`, `content_fingerprint`),
  ADD CONSTRAINT `fk_pw_card_ai_cache_owner`
    FOREIGN KEY (`owner_user_id`) REFERENCES `pw_user` (`id`) ON DELETE CASCADE;
