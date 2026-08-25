ALTER TABLE `pw_card_ai_cache`
  ADD COLUMN `prompt_fingerprint` char(64) NULL
  COMMENT '规范化 prompt 的 SHA-256 指纹，同 prompt 只保留一条缓存'
  AFTER `content_fingerprint`;

UPDATE `pw_card_ai_cache`
SET `prompt_fingerprint` = SHA2(`prompt`, 256)
WHERE `prompt_fingerprint` IS NULL;

CREATE TEMPORARY TABLE `tmp_pw_card_ai_cache_keep` AS
SELECT `id`
FROM (
  SELECT
    `id`,
    ROW_NUMBER() OVER (
      PARTITION BY `prompt_fingerprint`
      ORDER BY COALESCE(`last_generated_at`, `updated_at`, `created_at`) DESC, `id` DESC
    ) AS `rn`
  FROM `pw_card_ai_cache`
) ranked
WHERE `rn` = 1;

-- delete older duplicate prompt rows before adding the unique prompt key.
DELETE older
FROM `pw_card_ai_cache` older
LEFT JOIN `tmp_pw_card_ai_cache_keep` keep_rows ON keep_rows.`id` = older.`id`
WHERE keep_rows.`id` IS NULL;

DROP TEMPORARY TABLE `tmp_pw_card_ai_cache_keep`;

ALTER TABLE `pw_card_ai_cache`
  MODIFY COLUMN `prompt_fingerprint` char(64) NOT NULL
  COMMENT '规范化 prompt 的 SHA-256 指纹，同 prompt 只保留一条缓存',
  ADD UNIQUE KEY `uk_pw_card_ai_cache_prompt_fingerprint` (`prompt_fingerprint`);
