ALTER TABLE `pw_card_ai_cache`
  DROP INDEX `uk_pw_card_ai_cache_prompt_fingerprint`;

UPDATE `pw_card_ai_cache`
SET `prompt_fingerprint` = SHA2(LOWER(REGEXP_REPLACE(TRIM(`prompt`), '[[:space:]]+', ' ')), 256);

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

-- delete older normalized prompt duplicate rows before restoring the unique prompt key.
DELETE older
FROM `pw_card_ai_cache` older
LEFT JOIN `tmp_pw_card_ai_cache_keep` keep_rows ON keep_rows.`id` = older.`id`
WHERE keep_rows.`id` IS NULL;

DROP TEMPORARY TABLE `tmp_pw_card_ai_cache_keep`;

ALTER TABLE `pw_card_ai_cache`
  ADD UNIQUE KEY `uk_pw_card_ai_cache_prompt_fingerprint` (`prompt_fingerprint`);
