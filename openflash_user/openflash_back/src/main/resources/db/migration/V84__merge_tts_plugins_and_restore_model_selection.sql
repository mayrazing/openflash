-- 把按引擎拆分的 TTS 插件重新合并为一个 tts 插件.
-- 模型变成卡包设置, 发音入口和自动朗读只保留一套.

-- 1. 恢复统一功能开关和插件目录, 模型选项继续由 DB 注册.
INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `rollout_type`, `description`)
SELECT
    'feature.tts',
    COALESCE(MAX(`enabled`), 1),
    'GLOBAL',
    'English TTS'
FROM `pw_feature_flag`
WHERE `feature_key` IN ('feature.tts-cosyvoice3', 'feature.tts-piper')
ON DUPLICATE KEY UPDATE
    `enabled` = VALUES(`enabled`),
    `rollout_type` = VALUES(`rollout_type`),
    `description` = VALUES(`description`);

INSERT INTO `pw_type_registry`
(`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`)
VALUES ('plugin', 'tts', 'tts', '{"icon":"🔊","category":"study"}', 1, 1)
ON DUPLICATE KEY UPDATE
    `item_name` = VALUES(`item_name`),
    `config` = VALUES(`config`),
    `sort_order` = VALUES(`sort_order`),
    `enabled` = VALUES(`enabled`);

-- 原拆分插件的开关现在变成模型可用性, 不能把管理员关掉的模型重新打开.
INSERT INTO `pw_type_registry`
(`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`)
SELECT
    'tts_engine',
    'cosyvoice3',
    'cosyvoice3',
    '{}',
    1,
    COALESCE((
        SELECT MAX(`enabled`)
        FROM `pw_feature_flag`
        WHERE `feature_key` = 'feature.tts-cosyvoice3'
    ), 1)
UNION ALL
SELECT
    'tts_engine',
    'piper',
    'piper',
    '{}',
    2,
    COALESCE((
        SELECT MAX(`enabled`)
        FROM `pw_feature_flag`
        WHERE `feature_key` = 'feature.tts-piper'
    ), 1)
ON DUPLICATE KEY UPDATE
    `item_name` = VALUES(`item_name`),
    `config` = VALUES(`config`),
    `sort_order` = VALUES(`sort_order`),
    `enabled` = VALUES(`enabled`);

-- 2. 先根据安装记录选出卡包默认模型. 两个都装时取最后安装的一项.
CREATE TEMPORARY TABLE `tmp_tts_v84_selected_engine` (
    `deck_id` BIGINT NOT NULL,
    `engine` VARCHAR(32) NOT NULL,
    PRIMARY KEY (`deck_id`)
);

INSERT INTO `tmp_tts_v84_selected_engine` (`deck_id`, `engine`)
SELECT
    `deck_id`,
    SUBSTRING_INDEX(
        GROUP_CONCAT(
            CASE
                WHEN `plugin_id` = 'tts-piper' THEN 'piper'
                ELSE 'cosyvoice3'
            END
            ORDER BY `installed_at` DESC, `id` DESC
            SEPARATOR ','
        ),
        ',',
        1
    )
FROM `pw_plugin_install`
WHERE `plugin_id` IN ('tts-cosyvoice3', 'tts-piper')
GROUP BY `deck_id`;

-- 3. 合并同一卡包的两份设置. 自动朗读取 OR, 避免迁移时静默关闭已开启的一面.
ALTER TABLE `pw_tts_deck_settings`
    ADD COLUMN `engine` VARCHAR(32) NOT NULL DEFAULT 'cosyvoice3' AFTER `auto_speak_b`;

CREATE TEMPORARY TABLE `tmp_tts_v84_merged_settings` (
    `deck_id` BIGINT NOT NULL,
    `auto_speak_a` TINYINT(1) NOT NULL,
    `auto_speak_b` TINYINT(1) NOT NULL,
    `engine` VARCHAR(32) NOT NULL,
    `updated_at` DATETIME NOT NULL,
    PRIMARY KEY (`deck_id`)
);

INSERT INTO `tmp_tts_v84_merged_settings`
(`deck_id`, `auto_speak_a`, `auto_speak_b`, `engine`, `updated_at`)
SELECT
    settings.`deck_id`,
    MAX(settings.`auto_speak_a`),
    MAX(settings.`auto_speak_b`),
    COALESCE(
        selected.`engine`,
        CASE
            WHEN SUM(settings.`plugin_id` = 'tts-piper') > 0
                 AND SUM(settings.`plugin_id` = 'tts-cosyvoice3') = 0
                THEN 'piper'
            ELSE 'cosyvoice3'
        END
    ),
    MAX(settings.`updated_at`)
FROM `pw_tts_deck_settings` settings
LEFT JOIN `tmp_tts_v84_selected_engine` selected
    ON selected.`deck_id` = settings.`deck_id`
GROUP BY settings.`deck_id`, selected.`engine`;

UPDATE `pw_tts_deck_settings` settings
JOIN `tmp_tts_v84_merged_settings` merged
    ON merged.`deck_id` = settings.`deck_id`
SET settings.`auto_speak_a` = merged.`auto_speak_a`,
    settings.`auto_speak_b` = merged.`auto_speak_b`,
    settings.`engine` = merged.`engine`,
    settings.`updated_at` = merged.`updated_at`;

DELETE duplicate_settings
FROM `pw_tts_deck_settings` duplicate_settings
JOIN `pw_tts_deck_settings` keeper
    ON keeper.`deck_id` = duplicate_settings.`deck_id`
   AND keeper.`plugin_id` < duplicate_settings.`plugin_id`;

ALTER TABLE `pw_tts_deck_settings`
    DROP PRIMARY KEY,
    DROP COLUMN `plugin_id`,
    ADD PRIMARY KEY (`deck_id`);

-- 只安装过某个引擎但从未保存设置的卡包也要保留该引擎选择.
INSERT INTO `pw_tts_deck_settings`
(`deck_id`, `auto_speak_a`, `auto_speak_b`, `engine`, `updated_at`)
SELECT
    selected.`deck_id`,
    0,
    0,
    selected.`engine`,
    NOW()
FROM `tmp_tts_v84_selected_engine` selected
LEFT JOIN `pw_tts_deck_settings` settings
    ON settings.`deck_id` = selected.`deck_id`
WHERE settings.`deck_id` IS NULL;

DROP TEMPORARY TABLE `tmp_tts_v84_merged_settings`;
DROP TEMPORARY TABLE `tmp_tts_v84_selected_engine`;

-- 4. 任一拆分插件已安装的卡包统一改装 tts, 然后删除拆分记录.
INSERT INTO `pw_plugin_install` (`user_id`, `deck_id`, `plugin_id`, `installed_at`)
SELECT
    `user_id`,
    `deck_id`,
    'tts',
    MIN(`installed_at`)
FROM `pw_plugin_install`
WHERE `plugin_id` IN ('tts-cosyvoice3', 'tts-piper')
GROUP BY `user_id`, `deck_id`
ON DUPLICATE KEY UPDATE `installed_at` = LEAST(`installed_at`, VALUES(`installed_at`));

DELETE FROM `pw_plugin_install`
WHERE `plugin_id` IN ('tts-cosyvoice3', 'tts-piper');

-- 5. 删除拆分插件的注册项. 统一项和模型注册已经在上方完成.
DELETE FROM `pw_type_registry`
WHERE `registry_type` = 'plugin'
  AND `item_key` IN ('tts-cosyvoice3', 'tts-piper');

DELETE FROM `pw_feature_flag`
WHERE `feature_key` IN ('feature.tts-cosyvoice3', 'feature.tts-piper');
