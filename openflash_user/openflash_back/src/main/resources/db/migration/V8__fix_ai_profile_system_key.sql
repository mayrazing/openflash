-- 将 V7 已插入的 ai_profile JSON key 从 system_prompt 迁移为代码读取的 system。
UPDATE `pw_type_registry`
SET `config` = JSON_REMOVE(
    JSON_SET(
        `config`,
        '$.system',
        JSON_UNQUOTE(JSON_EXTRACT(`config`, '$.system_prompt'))
    ),
    '$.system_prompt'
)
WHERE `registry_type` = 'ai_profile'
  AND JSON_EXTRACT(`config`, '$.system_prompt') IS NOT NULL;
