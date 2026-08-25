-- ① 系统标量配置表
CREATE TABLE `pw_system_config` (
  `id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `group_name`  varchar(64)   NOT NULL COMMENT '配置分组：tts / ai / async-task / cache',
  `config_key`  varchar(191)  NOT NULL COMMENT '唯一配置键，如 tts.voice',
  `value`       varchar(2000) NOT NULL COMMENT '配置值（字符串存储，按 value_type 解析）',
  `value_type`  varchar(20)   NOT NULL COMMENT 'STRING / INT / BOOL / DECIMAL',
  `description` varchar(500)  DEFAULT NULL COMMENT '说明，供后台管理界面展示',
  `updated_at`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by`  varchar(50)   DEFAULT NULL COMMENT '最后修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_system_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统标量配置，替代 YAML 里的运行时参数';

INSERT INTO `pw_system_config` (`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('tts',        'tts.voice',                        'af_heart',  'STRING',  'TTS 声音'),
('tts',        'tts.speed',                        '1.0',       'DECIMAL', 'TTS 语速'),
('tts',        'tts.max-concurrent-requests',      '1',         'INT',     'TTS 最大并发数'),
('tts',        'tts.connect-timeout-millis',       '5000',      'INT',     'TTS 连接超时(ms)'),
('tts',        'tts.request-timeout-millis',       '10000',     'INT',     'TTS 请求超时(ms)'),
('ai',         'ai.think',                         'false',     'BOOL',    'AI 默认思考模式'),
('ai',         'ai.timeout-millis',                '180000',    'INT',     'AI 请求超时(ms)'),
('async-task', 'async-task.process-batch-size',    '20',        'INT',     '每轮消费任务数'),
('async-task', 'async-task.fixed-delay-millis',    '5000',      'INT',     '消费间隔(ms)'),
('async-task', 'async-task.lease-millis',          '120000',    'INT',     '任务租约时长(ms)'),
('cache',      'cache.ttl-days',                   '100',       'INT',     'AI/TTS 缓存保留天数'),
('cache',      'cache.touch-min-interval-hours',   '6',         'INT',     '缓存访问节流窗口(h)');

-- ② 功能开关主表
CREATE TABLE `pw_feature_flag` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `feature_key`  varchar(191) NOT NULL COMMENT '功能标识，如 feature.tts',
  `enabled`      tinyint      NOT NULL DEFAULT 1 COMMENT '全局默认：1=开，0=关',
  `rollout_type` varchar(20)  NOT NULL DEFAULT 'GLOBAL'
                              COMMENT 'GLOBAL=全局统一；USER_OVERRIDE=允许用户级覆盖',
  `description`  varchar(500) DEFAULT NULL COMMENT '功能说明',
  `updated_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by`   varchar(50)  DEFAULT NULL COMMENT '最后修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_feature_flag_key` (`feature_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='功能开关，支持全局开关和用户级覆盖';

INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `rollout_type`, `description`) VALUES
('feature.tts',                 1, 'GLOBAL',        'TTS 朗读功能'),
('feature.ai.card-markdown',    1, 'GLOBAL',        'AI 卡片解析'),
('feature.ai.side-completion',  1, 'GLOBAL',        'AI 补全另一面'),
('feature.card.export',         1, 'USER_OVERRIDE', '卡片导出（可按用户单独控制）');

-- ③ 用户级功能开关覆盖表
CREATE TABLE `pw_user_feature_flag` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `user_id`     bigint       NOT NULL COMMENT '用户 ID',
  `feature_key` varchar(191) NOT NULL COMMENT '对应 pw_feature_flag.feature_key',
  `enabled`     tinyint      NOT NULL COMMENT '覆盖值：1=强制开，0=强制关',
  `updated_at`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_user_feature_flag` (`user_id`, `feature_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户级功能开关覆盖，仅对 USER_OVERRIDE 类型的 flag 生效';

-- ④ 可扩展类型注册表
CREATE TABLE `pw_type_registry` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `registry_type` varchar(64)  NOT NULL
                               COMMENT '类型分类：ai_profile / ai_feature_mapping / practice_mode / card_category',
  `item_key`      varchar(191) NOT NULL COMMENT '条目键',
  `item_name`     varchar(200) DEFAULT NULL COMMENT '显示名，供后台管理界面展示',
  `config`        longtext     DEFAULT NULL COMMENT 'JSON 扩展配置',
  `sort_order`    int          NOT NULL DEFAULT 0 COMMENT '排序值，值越小越靠前',
  `enabled`       tinyint      NOT NULL DEFAULT 1 COMMENT '是否启用：1=是，0=否',
  `created_at`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_type_registry` (`registry_type`, `item_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可扩展类型注册，admin 加一行 = 系统多一个类型';

-- ai_profile：AI 模型 + prompt 配置（从 application-ai.yaml 迁移）
INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`) VALUES
('ai_profile', 'ai_cache', 'AI卡片解析Profile',
 '{"model":"qwen3.5:9b","temperature":0.1,"system_prompt":"回答规则：\\n  - 回答内容只围绕输入的单词/短语/表达，禁止添加任何无关内容\\n  - 回答内容必须符合以下格式模板\\n  - 请用中文详细解释只解释作为英文母语者在日常口语和听力中第一反应的意思、语感、用在什么场合\\n  - 例句只列出英文原句+中文翻译，日常高频口语和高频听力场景\\n  - 常见搭配只列出口语和听力中高频常见的\\n  - 含义只列口语和听力中高频的词性和含义，严格按此格式输出，例如：n. 酒精；v. 使兴奋，没有的词性不写\\n  - 回答第一行必须是：📌 单词：xxx 或 📌 短语：xxx 或 📌 对应表达：xxx，根据输入内容三选一，禁止其他写法\\n  - 禁止在格式模板之外自行添加任何额外内容\\n\\n请按以下格式回答:\\n📌 单词：xxx\\n🔤 音标:/xxx/\\n📚 含义:xxx\\n\\n📖 详细解释:\\nxxx\\n\\n✏️ 例句:\\n1. xxx（中文翻译）\\n2. xxx（中文翻译）\\n3. xxx（中文翻译）\\n\\n💡 常见搭配:\\n- xxx\\n- xxx\\n- xxx"}',
 1),
('ai_profile', 'ai_side_completion', 'AI补全另一面Profile',
 '{"model":"qwen3.5:9b","temperature":0.1,"system_prompt":"回答必须遵循以下规则：\\n  - 当输入为中文时，无论是单词、短语还是句子，仅输出在口语和听力场景中最高频、最自然的英文对应表达，不附加任何解释、标点、引号或格式符号。\\n  - 当输入为英文单词时，仅输出紧凑词性释义，格式为：有哪个词性就写哪个词性，每个词性后跟中文释义，词性之间用分号分隔，只取口语和听力中第一反应的最高频含义，不换行。当输入为英文短语或句子时，直接输出最自然的中文对应表达。\\n  - 不输出任何前缀、后缀或说明性文字。\\n  - 示例：\\n  - 输入:\\"放弃\\" → 输出: give up\\n  - 输入:\\"你说得对\\" → 输出: you''re right\\n    输入\\"abandon\\" → 输出: v.放弃;抛弃\\n    输入\\"give it a shot\\" → 输出: 试试看"}',
 2);

-- ai_feature_mapping：AI 功能 key → profile 映射（从 application-ai.yaml feature-profiles 迁移）
INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`) VALUES
('ai_feature_mapping', 'card-ai-markdown',    'AI卡片解析功能映射',  '{"profile_name":"ai_cache"}',          1),
('ai_feature_mapping', 'card-side-completion', 'AI补全另一面功能映射', '{"profile_name":"ai_side_completion"}', 2);

-- practice_mode：练习模式（从 PracticeServiceImpl 硬编码迁移）
INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`) VALUES
('practice_mode', 'a2b',    'A面→B面',  '{}', 1),
('practice_mode', 'b2a',    'B面→A面',  '{}', 2),
('practice_mode', 'random', '随机双向', '{}', 3);
