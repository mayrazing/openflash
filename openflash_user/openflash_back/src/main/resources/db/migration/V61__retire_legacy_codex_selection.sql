UPDATE `pw_platform_ai_connection`
SET `enabled` = COALESCE((
  SELECT `legacy`.`enabled` FROM `pw_feature_flag` AS `legacy`
  WHERE `legacy`.`feature_key` = 'feature.ai.codex-cli' LIMIT 1
), `enabled`)
WHERE `connection_key` = 'platform-codex';

UPDATE `pw_platform_ai_offering`
SET `enabled` = COALESCE((
      SELECT `legacy`.`enabled` FROM `pw_feature_flag` AS `legacy`
      WHERE `legacy`.`feature_key` = 'feature.ai.codex-cli' LIMIT 1
    ), `enabled`),
    `default_access` = COALESCE((
      SELECT `legacy`.`enabled` FROM `pw_feature_flag` AS `legacy`
      WHERE `legacy`.`feature_key` = 'feature.ai.codex-cli.user-access' LIMIT 1
    ), `default_access`)
WHERE `offering_key` = 'platform-codex-cli';

INSERT INTO `pw_platform_ai_user_access` (`user_id`, `offering_id`, `enabled`)
SELECT `legacy`.`user_id`, `offering`.`id`, `legacy`.`enabled`
FROM `pw_user_feature_flag` AS `legacy`
JOIN `pw_platform_ai_offering` AS `offering`
  ON `offering`.`offering_key` = 'platform-codex-cli'
WHERE `legacy`.`feature_key` = 'feature.ai.codex-cli.user-access'
ON DUPLICATE KEY UPDATE `enabled` = VALUES(`enabled`);

INSERT INTO `pw_user_platform_ai_preference`
  (`user_id`, `offering_id`, `model`, `reasoning_effort`)
SELECT `config`.`user_id`, `offering`.`id`,
  CASE WHEN JSON_TYPE(JSON_EXTRACT(
    IF(JSON_VALID(`config`.`config`), `config`.`config`, '{}'), '$.model')) = 'STRING'
    THEN JSON_UNQUOTE(JSON_EXTRACT(`config`.`config`, '$.model')) ELSE NULL END,
  CASE WHEN JSON_TYPE(JSON_EXTRACT(
    IF(JSON_VALID(`config`.`config`), `config`.`config`, '{}'), '$.reasoningEffort')) = 'STRING'
    THEN JSON_UNQUOTE(JSON_EXTRACT(`config`.`config`, '$.reasoningEffort')) ELSE NULL END
FROM `pw_user_ai_config` AS `config`
JOIN `pw_platform_ai_offering` AS `offering`
  ON `offering`.`offering_key` = 'platform-codex-cli'
WHERE `config`.`provider` = 'codex-cli'
ON DUPLICATE KEY UPDATE
  `model` = VALUES(`model`), `reasoning_effort` = VALUES(`reasoning_effort`);

DELETE FROM `pw_user_active_ai_selection`;

INSERT INTO `pw_user_active_ai_selection`
  (`user_id`, `source`, `user_provider_key`, `offering_id`)
SELECT `ranked`.`user_id`, 'USER', `ranked`.`provider`, NULL
FROM (
  SELECT `config`.`user_id`, `config`.`provider`,
    ROW_NUMBER() OVER (
      PARTITION BY `config`.`user_id`
      ORDER BY `config`.`updated_at` DESC, `config`.`id` DESC
    ) AS `rn`
  FROM `pw_user_ai_config` AS `config`
  WHERE `config`.`is_active` = 1
) AS `ranked`
WHERE `ranked`.`rn` = 1 AND `ranked`.`provider` <> 'codex-cli';

INSERT INTO `pw_user_active_ai_selection`
  (`user_id`, `source`, `user_provider_key`, `offering_id`)
SELECT `ranked`.`user_id`, 'PLATFORM', NULL, `offering`.`id`
FROM (
  SELECT `config`.`user_id`, `config`.`provider`,
    ROW_NUMBER() OVER (
      PARTITION BY `config`.`user_id`
      ORDER BY `config`.`updated_at` DESC, `config`.`id` DESC
    ) AS `rn`
  FROM `pw_user_ai_config` AS `config`
  WHERE `config`.`is_active` = 1
) AS `ranked`
JOIN `pw_platform_ai_offering` AS `offering`
  ON `offering`.`offering_key` = 'platform-codex-cli'
WHERE `ranked`.`rn` = 1 AND `ranked`.`provider` = 'codex-cli';

DELETE FROM `pw_user_ai_config` WHERE `provider` = 'codex-cli';

DELETE FROM `pw_user_feature_flag`
WHERE `feature_key` IN (
  'feature.ai.codex-cli', 'feature.ai.codex-cli.user-access'
);

DELETE FROM `pw_type_registry`
WHERE `registry_type` = 'ai_provider_kind' AND `item_key` = 'codex-cli';

DELETE FROM `pw_feature_flag`
WHERE `feature_key` IN (
  'feature.ai.codex-cli', 'feature.ai.codex-cli.user-access'
);

ALTER TABLE `pw_user_ai_config` DROP COLUMN `is_active`;
