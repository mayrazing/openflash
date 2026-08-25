DELETE FROM `pw_card_ai_cache`
WHERE `owner_user_id` IS NULL;

ALTER TABLE `pw_card_ai_cache`
  MODIFY COLUMN `owner_user_id` BIGINT NOT NULL;
