CREATE TABLE `pw_user_platform_ai_preference` (
  `user_id` bigint NOT NULL,
  `offering_id` bigint NOT NULL,
  `model` varchar(191) DEFAULT NULL,
  `reasoning_effort` varchar(32) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_platform_preference` (`user_id`, `offering_id`),
  CONSTRAINT `fk_user_platform_preference_user` FOREIGN KEY (`user_id`)
    REFERENCES `pw_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_platform_preference_offering` FOREIGN KEY (`offering_id`)
    REFERENCES `pw_platform_ai_offering` (`id`) ON DELETE CASCADE
);

CREATE TABLE `pw_user_active_ai_selection` (
  `user_id` bigint NOT NULL,
  `source` varchar(16) NOT NULL,
  `user_provider_key` varchar(64) DEFAULT NULL,
  `offering_id` bigint DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `chk_user_active_ai_source` CHECK (
    (`source`='USER' AND `user_provider_key` IS NOT NULL AND `offering_id` IS NULL) OR
    (`source`='PLATFORM' AND `user_provider_key` IS NULL AND `offering_id` IS NOT NULL)
  ),
  CONSTRAINT `fk_user_active_ai_user` FOREIGN KEY (`user_id`)
    REFERENCES `pw_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_active_ai_offering` FOREIGN KEY (`offering_id`)
    REFERENCES `pw_platform_ai_offering` (`id`) ON DELETE CASCADE
);

ALTER TABLE `pw_user`
  ADD COLUMN `auth_version` bigint NOT NULL DEFAULT 0 AFTER `banned`;

INSERT INTO `pw_user_platform_ai_preference` (
  `user_id`, `offering_id`, `model`, `reasoning_effort`
)
SELECT
  `ai_config`.`user_id`,
  `offering`.`id`,
  CASE
    WHEN JSON_TYPE(JSON_EXTRACT(
      IF(JSON_VALID(`ai_config`.`config`), `ai_config`.`config`, '{}'),
      '$.model'
    )) = 'STRING'
    THEN JSON_UNQUOTE(JSON_EXTRACT(
      `ai_config`.`config`,
      '$.model'
    ))
    ELSE NULL
  END,
  CASE
    WHEN JSON_TYPE(JSON_EXTRACT(
      IF(JSON_VALID(`ai_config`.`config`), `ai_config`.`config`, '{}'),
      '$.reasoningEffort'
    )) = 'STRING'
    THEN JSON_UNQUOTE(JSON_EXTRACT(
      `ai_config`.`config`,
      '$.reasoningEffort'
    ))
    ELSE NULL
  END
FROM `pw_user_ai_config` AS `ai_config`
JOIN `pw_platform_ai_offering` AS `offering`
  ON `offering`.`offering_key` = 'platform-codex-cli'
WHERE `provider` = 'codex-cli';

INSERT INTO `pw_user_active_ai_selection` (
  `user_id`, `source`, `user_provider_key`, `offering_id`
)
SELECT
  `ranked`.`user_id`,
  CASE
    WHEN `ranked`.`provider` = 'codex-cli' THEN 'PLATFORM'
    ELSE 'USER'
  END,
  CASE
    WHEN `ranked`.`provider` = 'codex-cli' THEN NULL
    ELSE `ranked`.`provider`
  END,
  CASE
    WHEN `ranked`.`provider` = 'codex-cli' THEN `offering`.`id`
    ELSE NULL
  END
FROM (
  SELECT
    `ai_config`.`user_id`,
    `ai_config`.`provider`,
    ROW_NUMBER() OVER (
      PARTITION BY `ai_config`.`user_id`
      ORDER BY `ai_config`.`updated_at` DESC, `ai_config`.`id` DESC
    ) AS `rn`
  FROM `pw_user_ai_config` AS `ai_config`
  WHERE `ai_config`.`is_active` = 1
) AS `ranked`
JOIN `pw_platform_ai_offering` AS `offering`
  ON `offering`.`offering_key` = 'platform-codex-cli'
WHERE `ranked`.`rn` = 1;
