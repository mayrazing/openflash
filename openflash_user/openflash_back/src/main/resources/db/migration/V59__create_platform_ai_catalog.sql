CREATE TABLE `pw_platform_ai_connection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `connection_key` varchar(64) NOT NULL,
  `kind` varchar(16) NOT NULL,
  `protocol` varchar(40) NOT NULL,
  `cli_key` varchar(64) DEFAULT NULL,
  `config` json NOT NULL,
  `credentials_configured` tinyint NOT NULL DEFAULT 0,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `sort_order` int NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_connection_key` (`connection_key`),
  UNIQUE KEY `uk_platform_ai_cli_key` (`cli_key`),
  CONSTRAINT `chk_platform_ai_connection_kind` CHECK (`kind` IN ('API','CLI')),
  CONSTRAINT `chk_platform_ai_connection_cli_key` CHECK (
    (`kind`='API' AND `cli_key` IS NULL) OR
    (`kind`='CLI' AND `cli_key` IS NOT NULL)
  )
);

CREATE TABLE `pw_platform_ai_secret` (
  `connection_id` bigint NOT NULL,
  `secret_enc` text NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`connection_id`),
  CONSTRAINT `fk_platform_ai_secret_connection` FOREIGN KEY (`connection_id`)
    REFERENCES `pw_platform_ai_connection` (`id`) ON DELETE CASCADE
);

CREATE TABLE `pw_platform_ai_offering` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `connection_id` bigint NOT NULL,
  `offering_key` varchar(64) NOT NULL,
  `model_key` varchar(191) DEFAULT NULL,
  `dynamic_connection_id` bigint GENERATED ALWAYS AS (
    CASE WHEN `model_key` IS NULL THEN `connection_id` ELSE NULL END
  ) VIRTUAL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `default_access` tinyint NOT NULL DEFAULT 0,
  `sort_order` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_ai_offering_key` (`offering_key`),
  UNIQUE KEY `uk_platform_ai_dynamic_connection` (`dynamic_connection_id`),
  CONSTRAINT `fk_platform_ai_offering_connection` FOREIGN KEY (`connection_id`)
    REFERENCES `pw_platform_ai_connection` (`id`) ON DELETE CASCADE
);

CREATE TABLE `pw_platform_ai_user_access` (
  `user_id` bigint NOT NULL,
  `offering_id` bigint NOT NULL,
  `enabled` tinyint NOT NULL,
  PRIMARY KEY (`user_id`, `offering_id`),
  CONSTRAINT `fk_platform_ai_access_user` FOREIGN KEY (`user_id`)
    REFERENCES `pw_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_platform_ai_access_offering` FOREIGN KEY (`offering_id`)
    REFERENCES `pw_platform_ai_offering` (`id`) ON DELETE CASCADE
);

INSERT INTO `pw_platform_ai_connection` (
  `connection_key`, `kind`, `protocol`, `cli_key`, `config`,
  `credentials_configured`, `enabled`, `sort_order`
)
SELECT
  'platform-codex', 'CLI', 'CODEX_APP_SERVER', 'codex', '{}',
  0,
  COALESCE((
    SELECT `enabled`
    FROM `pw_feature_flag`
    WHERE `feature_key` = 'feature.ai.codex-cli'
    LIMIT 1
  ), 1),
  0;

INSERT INTO `pw_platform_ai_offering` (
  `connection_id`, `offering_key`, `model_key`, `enabled`, `default_access`, `sort_order`
)
SELECT
  `connection`.`id`,
  'platform-codex-cli',
  NULL,
  `connection`.`enabled`,
  COALESCE((
    SELECT `enabled`
    FROM `pw_feature_flag`
    WHERE `feature_key` = 'feature.ai.codex-cli.user-access'
    LIMIT 1
  ), 0),
  0
FROM `pw_platform_ai_connection` AS `connection`
WHERE `connection`.`connection_key` = 'platform-codex';

INSERT INTO `pw_platform_ai_user_access` (`user_id`, `offering_id`, `enabled`)
SELECT `uff`.`user_id`, `offering`.`id`, `uff`.`enabled`
FROM `pw_user_feature_flag` AS `uff`
JOIN `pw_platform_ai_offering` AS `offering`
  ON `offering`.`offering_key` = 'platform-codex-cli'
WHERE `uff`.`feature_key` = 'feature.ai.codex-cli.user-access';
