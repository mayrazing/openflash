-- V14__add_response_time_config.sql
-- 注册练习反应时间阈值配置，代码读取时以此为准，缺失时回退默认值。

INSERT INTO
    `pw_system_config` (
        `group_name`,
        `config_key`,
        `value`,
        `value_type`,
        `description`
    )
VALUES (
        'practice',
        'practice.response-time.timeout-seconds',
        '60',
        'INT',
        '翻牌后超时作废阈值（秒），超过则卡片重回队列不计分'
    ),
    (
        'practice',
        'practice.response-time.grade3-slow-threshold-seconds',
        '8',
        'INT',
        '3分（记得很清楚）降档阈值（秒），超过则降为2分'
    ),
    (
        'practice',
        'practice.response-time.grade2-slow-threshold-seconds',
        '15',
        'INT',
        '2分（想起来了）降档阈值（秒），超过则降为1分'
    );