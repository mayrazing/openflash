-- Cumulative schema and seed data for new empty installations at version 61.
-- Flyway applies this baseline migration only when schema history is empty.
-- Historical installations continue validating and executing the original V migrations.
-- V15 is omitted because V1, V6, and V10 already define its user-setting columns.
-- V31 is omitted because V1 already defines pw_tts_cache_meta.voice and speed.
CREATE TABLE `SPRING_SESSION` (
  `PRIMARY_ID` char(36) NOT NULL,
  `SESSION_ID` char(36) NOT NULL,
  `CREATION_TIME` bigint NOT NULL,
  `LAST_ACCESS_TIME` bigint NOT NULL,
  `MAX_INACTIVE_INTERVAL` int NOT NULL,
  `EXPIRY_TIME` bigint NOT NULL,
  `PRINCIPAL_NAME` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`PRIMARY_ID`),
  UNIQUE KEY `SPRING_SESSION_IX1` (`SESSION_ID`),
  KEY `SPRING_SESSION_IX2` (`EXPIRY_TIME`),
  KEY `SPRING_SESSION_IX3` (`PRINCIPAL_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `SPRING_SESSION_ATTRIBUTES` (
  `SESSION_PRIMARY_ID` char(36) NOT NULL,
  `ATTRIBUTE_NAME` varchar(200) NOT NULL,
  `ATTRIBUTE_BYTES` blob NOT NULL,
  PRIMARY KEY (`SESSION_PRIMARY_ID`,`ATTRIBUTE_NAME`),
  CONSTRAINT `SPRING_SESSION_ATTRIBUTES_FK` FOREIGN KEY (`SESSION_PRIMARY_ID`) REFERENCES `SPRING_SESSION` (`PRIMARY_ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE `pw_async_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `biz_key` varchar(191) NOT NULL COMMENT '业务幂等键',
  `task_type` varchar(64) NOT NULL COMMENT '任务类型',
  `payload` longtext NOT NULL COMMENT '任务负载 JSON',
  `status` varchar(20) NOT NULL COMMENT '任务状态：PENDING、PROCESSING、COMPLETED、FAILED',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已重试次数',
  `max_retry_count` int NOT NULL DEFAULT '3' COMMENT '最大重试次数',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下一次允许重试时间',
  `lease_until` datetime DEFAULT NULL COMMENT '租约到期时间',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最近一次错误',
  `priority` int NOT NULL DEFAULT '0' COMMENT '任务优先级',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_async_task_biz_key` (`biz_key`),
  KEY `idx_pw_async_task_claim` (`status`,`next_retry_at`,`lease_until`,`priority`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一异步任务编排表';

CREATE TABLE `pw_card` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `deck_id` bigint NOT NULL COMMENT '所属卡包 ID',
  `side_a` text COMMENT 'A 面内容',
  `side_b` text COMMENT 'B 面内容',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  KEY `idx_pw_card_deck_id` (`deck_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡片表';

CREATE TABLE `pw_card_ai_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `content_fingerprint` char(64) NOT NULL COMMENT '规范化 prompt 的 SHA-256 指纹，跨用户唯一',
  `prompt` text NOT NULL COMMENT '生成该结果时使用的 prompt',
  `content` longtext COMMENT '缓存的 AI 结果内容',
  `think_used` tinyint(1) DEFAULT NULL COMMENT '生成该缓存时实际使用的 think 值',
  `last_accessed_at` datetime DEFAULT NULL COMMENT '最近一次被用户服务链路命中的时间',
  `last_generated_at` datetime DEFAULT NULL COMMENT '最近一次成功生成时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_card_ai_cache_fingerprint` (`content_fingerprint`),
  KEY `idx_pw_card_ai_cache_last_accessed_at` (`last_accessed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 缓存表（按内容指纹共享，跨用户复用）';

CREATE TABLE `pw_card_media` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `card_id` bigint NOT NULL COMMENT '所属卡片 ID',
  `card_side` varchar(10) NOT NULL COMMENT '图片所属面：A 或 B',
  `media_url` varchar(500) NOT NULL COMMENT '图片地址',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值，值越小越靠前',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_pw_card_media_card_id` (`card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡片图片表';

CREATE TABLE `pw_card_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `card_id` bigint NOT NULL COMMENT '卡片 ID',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `direction` varchar(20) NOT NULL COMMENT '练习方向：A_TO_B 或 B_TO_A',
  `state` varchar(20) NOT NULL DEFAULT 'new' COMMENT '学习状态：new、learning、review、relearning、mastered',
  `step` int DEFAULT NULL COMMENT 'FSRS 学习步进，仅 learning/relearning 阶段使用',
  `stability` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT 'FSRS 稳定度',
  `difficulty` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT 'FSRS 难度',
  `next_review_date` date DEFAULT NULL COMMENT '下次复习日期',
  `last_review_date` date DEFAULT NULL COMMENT '最近复习日期',
  `reps` int NOT NULL DEFAULT '0' COMMENT '复习次数',
  `lapses` int NOT NULL DEFAULT '0' COMMENT '遗忘次数',
  `last_rating` int NOT NULL DEFAULT '0' COMMENT '最近一次评分',
  `first_learned_date` date DEFAULT NULL COMMENT '第一次学习日期',
  `mastered_at` datetime DEFAULT NULL COMMENT '标记为已掌握的时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_card_progress_user_card_direction` (`user_id`,`card_id`,`direction`),
  KEY `idx_pw_card_progress_card_id` (`card_id`),
  KEY `idx_pw_card_progress_user_id` (`user_id`),
  KEY `idx_pw_card_progress_next_review_date` (`next_review_date`),
  KEY `idx_pw_card_progress_user_card` (`user_id`,`card_id`),
  KEY `idx_pw_card_progress_user_direction_next_review_date` (`user_id`,`direction`,`next_review_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡片学习进度表';

CREATE TABLE `pw_deck` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `user_id` bigint NOT NULL COMMENT '所属用户 ID',
  `name` varchar(100) NOT NULL COMMENT '卡包名称',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  KEY `idx_pw_deck_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='卡包表';

CREATE TABLE `pw_practice_session_store` (
  `user_id` bigint NOT NULL,
  `deck_id` bigint NOT NULL,
  `session_date` date NOT NULL,
  `type` varchar(32) NOT NULL,
  `data` longtext NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`deck_id`,`session_date`,`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `pw_tts_cache_meta` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `variant_fingerprint` char(64) NOT NULL COMMENT '规范化文本 + voice + speed + engine_version 的 SHA-256 指纹',
  `normalized_text` text NOT NULL COMMENT '规范化后的发音文本',
  `voice` varchar(64) NOT NULL COMMENT 'TTS 声音',
  `speed` double NOT NULL COMMENT 'TTS 语速',
  `engine_version` varchar(64) NOT NULL COMMENT 'TTS 引擎版本标识',
  `relative_path` varchar(255) NOT NULL COMMENT '相对缓存目录的音频文件路径',
  `last_accessed_at` datetime DEFAULT NULL COMMENT '最近一次被用户服务链路命中的时间',
  `last_generated_at` datetime DEFAULT NULL COMMENT '最近一次成功生成时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_tts_cache_meta_variant` (`variant_fingerprint`),
  KEY `idx_pw_tts_cache_meta_last_accessed_at` (`last_accessed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TTS 缓存元数据表（按变体指纹共享）';

CREATE TABLE `pw_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `username` varchar(50) NOT NULL COMMENT '登录用户名',
  `password_hash` varchar(255) NOT NULL COMMENT '密码哈希值',
  `nickname` varchar(50) DEFAULT NULL COMMENT '用户昵称',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE `pw_user_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `new_cards_per_day` int NOT NULL DEFAULT '10' COMMENT '每日新卡上限',
  `target_retention` decimal(5,4) NOT NULL DEFAULT '0.9000' COMMENT '目标记忆留存率',
  `theme` varchar(20) NOT NULL DEFAULT 'light' COMMENT '主题：light 或 dark',
  `auto_speak_a` tinyint NOT NULL DEFAULT '0' COMMENT '是否自动朗读 A 面：0 否，1 是',
  `auto_speak_b` tinyint NOT NULL DEFAULT '0' COMMENT '是否自动朗读 B 面：0 否，1 是',
  `think` tinyint DEFAULT NULL COMMENT 'AI 思考模式，NULL 表示使用系统默认值',
  `last_exported_at` datetime DEFAULT NULL COMMENT '最近导出时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_user_settings_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户设置表';
ALTER TABLE pw_practice_session_store
    RENAME TO pw_practice_session_store_legacy_v1;

CREATE TABLE pw_practice_session_store (
  user_id bigint NOT NULL,
  deck_id bigint NOT NULL,
  data longtext NOT NULL,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, deck_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO pw_practice_session_store (user_id, deck_id, data, updated_at)
WITH ranked_sessions AS (
    SELECT user_id,
           deck_id,
           session_date,
           data,
           updated_at,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, deck_id
               ORDER BY updated_at DESC, session_date DESC
           ) AS rn
    FROM pw_practice_session_store_legacy_v1
    WHERE type = 'session'
),
ranked_orphan_aux AS (
    SELECT t.user_id,
           t.deck_id,
           t.session_date,
           MAX(t.updated_at) AS updated_at,
           ROW_NUMBER() OVER (
               PARTITION BY t.user_id, t.deck_id
               ORDER BY MAX(t.updated_at) DESC, t.session_date DESC
           ) AS rn
    FROM pw_practice_session_store_legacy_v1 t
    WHERE t.type IN ('retry_queue', 'post_round_retry')
      AND NOT EXISTS (
          SELECT 1
          FROM pw_practice_session_store_legacy_v1 s_any
          WHERE s_any.user_id = t.user_id
            AND s_any.deck_id = t.deck_id
            AND s_any.type = 'session'
      )
      AND NOT EXISTS (
          SELECT 1
          FROM pw_practice_session_store_legacy_v1 s
          WHERE s.user_id = t.user_id
            AND s.deck_id = t.deck_id
            AND s.session_date = t.session_date
            AND s.type = 'session'
      )
    GROUP BY t.user_id, t.deck_id, t.session_date
)
SELECT s.user_id,
       s.deck_id,
       CAST(
           JSON_SET(
               CAST(s.data AS JSON),
               '$.retryQueueItems',
               COALESCE(JSON_EXTRACT(r.data, '$.items'), JSON_ARRAY()),
               '$.postRoundRetryCards',
               COALESCE(JSON_EXTRACT(p.data, '$.cards'), JSON_ARRAY()),
               '$.history',
               JSON_ARRAY()
           ) AS CHAR CHARACTER SET utf8mb4
       ) AS data,
       GREATEST(
           s.updated_at,
           COALESCE(r.updated_at, s.updated_at),
           COALESCE(p.updated_at, s.updated_at)
       ) AS updated_at
FROM ranked_sessions s
LEFT JOIN pw_practice_session_store_legacy_v1 r
    ON r.user_id = s.user_id
   AND r.deck_id = s.deck_id
   AND r.session_date = s.session_date
   AND r.type = 'retry_queue'
LEFT JOIN pw_practice_session_store_legacy_v1 p
    ON p.user_id = s.user_id
   AND p.deck_id = s.deck_id
   AND p.session_date = s.session_date
   AND p.type = 'post_round_retry'
WHERE s.rn = 1

UNION ALL

SELECT a.user_id,
       a.deck_id,
       CAST(
           JSON_OBJECT(
               'mode',
               COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.data, '$.mode')), 'random'),
               'queueItems',
               COALESCE(JSON_EXTRACT(r.data, '$.items'), JSON_ARRAY()),
               'current',
               0,
               'revealed',
               FALSE,
               'practiceFinished',
               JSON_LENGTH(COALESCE(JSON_EXTRACT(p.data, '$.cards'), JSON_ARRAY())) > 0,
               'masteredQueue',
               JSON_ARRAY(),
               'postRoundRetryActive',
               FALSE,
               'retryQueueItems',
               COALESCE(JSON_EXTRACT(r.data, '$.items'), JSON_ARRAY()),
               'postRoundRetryCards',
               COALESCE(JSON_EXTRACT(p.data, '$.cards'), JSON_ARRAY()),
               'history',
               JSON_ARRAY(),
               'stats',
               JSON_OBJECT('again', 0, 'hard', 0, 'good', 0, 'easy', 0, 'newCount', 0, 'reviewCountStat', 0, 'masteredCount', 0),
               'firstRatedIds',
               JSON_ARRAY(),
               'cardProgressState',
               JSON_OBJECT('requiredDirectionsByCard', JSON_OBJECT(), 'completedDirectionsByCard', JSON_OBJECT()),
               'sessionSchemaVersion',
               2,
               'settingsNewCardsPerDay',
               10,
               'savedAt',
               UNIX_TIMESTAMP(a.updated_at) * 1000
           ) AS CHAR CHARACTER SET utf8mb4
       ) AS data,
       a.updated_at
FROM ranked_orphan_aux a
LEFT JOIN pw_practice_session_store_legacy_v1 r
    ON r.user_id = a.user_id
   AND r.deck_id = a.deck_id
   AND r.session_date = a.session_date
   AND r.type = 'retry_queue'
LEFT JOIN pw_practice_session_store_legacy_v1 p
    ON p.user_id = a.user_id
   AND p.deck_id = a.deck_id
   AND p.session_date = a.session_date
   AND p.type = 'post_round_retry'
WHERE a.rn = 1;
DROP TABLE IF EXISTS pw_practice_session_store_legacy_v1;
ALTER TABLE pw_practice_session_store
    COMMENT = '练习会话快照表（每个用户每个卡包仅保留一条当前会话记录）';

ALTER TABLE pw_practice_session_store
    MODIFY COLUMN user_id bigint NOT NULL COMMENT '用户 ID',
    MODIFY COLUMN deck_id bigint NOT NULL COMMENT '卡包 ID',
    MODIFY COLUMN data longtext NOT NULL COMMENT '会话快照 JSON，包含队列、重练状态、轮后加练、上一题历史等',
    MODIFY COLUMN updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近一次写入时间';
alter table pw_user_settings
    add column sound_enabled boolean not null default true;
alter table pw_user_settings
    add column duplicate_side_a_enabled tinyint not null default 1 comment '新增和编辑卡片时是否按 A 面去重',
    add column duplicate_side_b_enabled tinyint not null default 0 comment '新增和编辑卡片时是否按 B 面去重';
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
INSERT INTO `pw_system_config` (`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('practice', 'practice.review.target-daily-directions', '40', 'INT', '每日目标复习方向数'),
('practice', 'practice.review.absolute-daily-directions', '70', 'INT', '每日复习方向绝对上限'),
('practice', 'practice.review.max-deferral-days', '3', 'INT', '低风险复习方向最多平滑延期天数'),
('practice', 'practice.review.backlog-pause-new-threshold', '120', 'INT', '积压达到该方向数后暂停新卡'),
('practice', 'practice.review.backlog-resume-new-threshold', '40', 'INT', '积压低于该方向数后恢复新卡')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
ALTER TABLE `pw_user_settings`
  ADD COLUMN `review_load_profile` varchar(20) NOT NULL DEFAULT 'standard'
  COMMENT '学习强度档位：relaxed / standard / intensive' AFTER `target_retention`;

INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('review_load_profile', 'relaxed',   '轻松', '{}', 1, 1),
('review_load_profile', 'standard',  '标准', '{}', 2, 1),
('review_load_profile', 'intensive', '强化', '{}', 3, 1)
ON DUPLICATE KEY UPDATE
  `item_name` = VALUES(`item_name`),
  `config` = VALUES(`config`),
  `sort_order` = VALUES(`sort_order`),
  `enabled` = VALUES(`enabled`);
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
CREATE INDEX idx_pw_card_progress_user_last_review_card_direction
    ON pw_card_progress (user_id, last_review_date, card_id, direction);
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
    );CREATE TABLE pw_deck_settings (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    deck_id                  BIGINT       NOT NULL,
    new_cards_per_day        INT          NOT NULL DEFAULT 10,
    target_retention         DECIMAL(5,4) NOT NULL DEFAULT 0.9000,
    review_load_profile      VARCHAR(32)  NOT NULL DEFAULT 'standard',
    auto_speak_a             TINYINT(1)   NOT NULL DEFAULT 0,
    auto_speak_b             TINYINT(1)   NOT NULL DEFAULT 0,
    duplicate_side_a_enabled TINYINT(1)   NOT NULL DEFAULT 1,
    duplicate_side_b_enabled TINYINT(1)   NOT NULL DEFAULT 0,
    updated_at               DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_deck_settings_deck_id (deck_id)
);
INSERT INTO pw_deck_settings (
    deck_id,
    new_cards_per_day,
    target_retention,
    review_load_profile,
    auto_speak_a,
    auto_speak_b,
    duplicate_side_a_enabled,
    duplicate_side_b_enabled,
    updated_at
)
SELECT
    d.id,
    COALESCE(s.new_cards_per_day,        10),
    COALESCE(s.target_retention,         0.9000),
    COALESCE(s.review_load_profile,      'standard'),
    COALESCE(s.auto_speak_a,             0),
    COALESCE(s.auto_speak_b,             0),
    COALESCE(s.duplicate_side_a_enabled, 1),
    COALESCE(s.duplicate_side_b_enabled, 0),
    NOW()
FROM pw_deck d
LEFT JOIN pw_user_settings s ON s.user_id = d.user_id
WHERE d.deleted = 0;
ALTER TABLE pw_user_settings
    DROP COLUMN new_cards_per_day,
    DROP COLUMN target_retention,
    DROP COLUMN review_load_profile,
    DROP COLUMN auto_speak_a,
    DROP COLUMN auto_speak_b,
    DROP COLUMN duplicate_side_a_enabled,
    DROP COLUMN duplicate_side_b_enabled;
-- Defensive clamp: V17 seeded deck_settings from pw_user_settings which may have contained
-- out-of-range or unknown values (e.g. old profile keys before the enum was introduced).
-- The review_load_profile whitelist intentionally mirrors PracticeReviewLoadProfile;
-- if a new profile is added to that enum, update this list in a subsequent migration.
UPDATE pw_deck_settings
SET new_cards_per_day = LEAST(50, GREATEST(0, COALESCE(new_cards_per_day, 10))),
    target_retention = LEAST(0.9700, GREATEST(0.7000, COALESCE(target_retention, 0.9000))),
    review_load_profile = CASE
        WHEN review_load_profile IN ('relaxed', 'standard', 'intensive') THEN review_load_profile
        ELSE 'standard'
    END;
UPDATE `pw_system_config`
SET `description` = 'AI 请求超时(ms)，重启后生效'
WHERE `config_key` = 'ai.timeout-millis';
CREATE TABLE pw_deck_ai_settings (
    id                      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    deck_id                 BIGINT      NOT NULL COMMENT '关联卡包 ID',
    ai_explanation_enabled  TINYINT(1)  NOT NULL DEFAULT 1  COMMENT '卡片解析开关：1=启用，0=关闭',
    ai_explanation_mode     VARCHAR(20) NOT NULL DEFAULT 'shared'
                            COMMENT '卡片解析提示词模式：shared=共用，independent=A/B独立',
    ai_explanation_prompt_a TEXT        DEFAULT NULL COMMENT '卡片解析 A 面提示词；NULL=无 system prompt',
    ai_explanation_prompt_b TEXT        DEFAULT NULL COMMENT '卡片解析 B 面提示词；NULL=无 system prompt',
    ai_completion_enabled   TINYINT(1)  NOT NULL DEFAULT 1  COMMENT '补全另一面开关：1=启用，0=关闭',
    ai_completion_prompt    TEXT        DEFAULT NULL COMMENT '补全另一面提示词；NULL=无 system prompt',
    updated_at              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uq_deck_ai_settings_deck_id (deck_id)
);
ALTER TABLE `pw_card_ai_cache`
  DROP INDEX `uk_pw_card_ai_cache_prompt_fingerprint`;
INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('deck_ai_explanation_mode', 'shared',      '共用', '{}', 1, 1),
('deck_ai_explanation_mode', 'independent', 'A/B 独立', '{}', 2, 1)
ON DUPLICATE KEY UPDATE
  `item_name` = VALUES(`item_name`),
  `config` = VALUES(`config`),
  `sort_order` = VALUES(`sort_order`),
  `enabled` = VALUES(`enabled`);
CREATE TABLE pw_user_ai_config (
    id                    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id               BIGINT      NOT NULL                COMMENT '用户 ID，一用户一行',
    provider              VARCHAR(20) NOT NULL                COMMENT 'OLLAMA 或 DEEPSEEK',
    ollama_base_url       VARCHAR(255)         DEFAULT NULL   COMMENT 'Ollama 连接地址',
    ollama_model          VARCHAR(100)         DEFAULT NULL   COMMENT 'Ollama 模型名',
    deepseek_api_key_enc  TEXT                 DEFAULT NULL   COMMENT 'DeepSeek API Key，AES-GCM 加密，格式 Base64(IV||ciphertext)',
    deepseek_model        VARCHAR(100)         DEFAULT NULL   COMMENT 'DeepSeek 模型名',
    updated_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_ai_config_user_id (user_id)
) COMMENT='用户 AI provider 配置';
INSERT INTO pw_type_registry (registry_type, item_key, item_name, config, sort_order, enabled) VALUES
('deepseek_model', 'deepseek-v4-flash', 'DeepSeek V4 Flash', '{}', 1, 1),
('deepseek_model', 'deepseek-v4-pro',   'DeepSeek V4 Pro',   '{}', 2, 1)
ON DUPLICATE KEY UPDATE
  item_name  = VALUES(item_name),
  config     = VALUES(config),
  sort_order = VALUES(sort_order),
  enabled    = VALUES(enabled);
DROP TABLE IF EXISTS pw_user_ai_config;

CREATE TABLE pw_user_ai_config (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT      NOT NULL                COMMENT '用户 ID',
    provider   VARCHAR(20) NOT NULL                COMMENT 'OLLAMA 或 DEEPSEEK',
    config     VARCHAR(2000) NOT NULL DEFAULT '{}'  COMMENT 'provider 连接参数 JSON',
    is_active  TINYINT(1)  NOT NULL DEFAULT 0      COMMENT '当前选用的 provider，每用户只有一行为 1',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_provider (user_id, provider)
) COMMENT='用户 AI provider 配置，每 provider 一行';
ALTER TABLE pw_deck_ai_settings
    ADD COLUMN ai_explanation_enabled_a TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'A 面解析开关：1=启用，0=关闭',
    ADD COLUMN ai_explanation_enabled_b TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'B 面解析开关：1=启用，0=关闭';

UPDATE pw_deck_ai_settings
SET ai_explanation_enabled_a = ai_explanation_enabled,
    ai_explanation_enabled_b = ai_explanation_enabled;

ALTER TABLE pw_deck_ai_settings
    DROP COLUMN ai_explanation_enabled;
ALTER TABLE pw_deck_ai_settings DROP COLUMN ai_explanation_mode;
ALTER TABLE pw_user_settings
  ADD COLUMN language VARCHAR(10) NOT NULL DEFAULT 'en'
  COMMENT '界面语言：zh/en/fi/de';
INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('interface_language', 'zh', '中文', '{}', 1, 1),
('interface_language', 'en', 'English', '{}', 2, 1),
('interface_language', 'fi', 'Suomi', '{}', 3, 1),
('interface_language', 'de', 'Deutsch', '{}', 4, 1)
ON DUPLICATE KEY UPDATE
  `item_name` = VALUES(`item_name`),
  `config` = VALUES(`config`),
  `sort_order` = VALUES(`sort_order`),
  `enabled` = VALUES(`enabled`);
INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `rollout_type`, `description`)
VALUES ('feature.ai.collocations', 1, 'GLOBAL', 'AI 常见搭配一键放入卡包')
ON DUPLICATE KEY UPDATE `enabled` = `enabled`;
CREATE TABLE pw_tts_deck_settings (
    deck_id      BIGINT     NOT NULL,
    auto_speak_a TINYINT(1) NOT NULL DEFAULT 0,
    auto_speak_b TINYINT(1) NOT NULL DEFAULT 0,
    updated_at   DATETIME   NOT NULL,
    PRIMARY KEY (deck_id)
);

INSERT INTO pw_tts_deck_settings (
    deck_id,
    auto_speak_a,
    auto_speak_b,
    updated_at
)
SELECT
    deck_id,
    auto_speak_a,
    auto_speak_b,
    updated_at
FROM pw_deck_settings;

ALTER TABLE pw_deck_settings
    DROP COLUMN auto_speak_a,
    DROP COLUMN auto_speak_b;
-- 插件安装关系：记录某用户的某卡包安装了某插件。装=插入，卸=删除。
CREATE TABLE `pw_plugin_install` (
  `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `user_id`      bigint       NOT NULL COMMENT '用户 ID',
  `deck_id`      bigint       NOT NULL COMMENT '卡包 ID',
  `plugin_id`    varchar(64)  NOT NULL COMMENT '插件 ID，对应 PluginDescriptor.pluginId()',
  `installed_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '安装时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_install` (`user_id`, `deck_id`, `plugin_id`),
  KEY `idx_plugin_install_deck` (`deck_id`),
  KEY `idx_plugin_install_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='插件按卡包安装关系';
-- 插件目录元数据：市场「全部」列表的数据源（名称/简介/图标/分类）。
INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('plugin', 'tts',     'TTS 朗读',    '{"desc":"自动朗读卡片正反面，支持语速与音色","icon":"🔊","category":"学习辅助"}', 1, 1),
('plugin', 'ai-card', 'AI 卡片解析', '{"desc":"AI 生成释义、例句与常见搭配","icon":"🤖","category":"AI"}',          2, 1);

-- 市场入口总开关。
INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `rollout_type`, `description`)
VALUES ('feature.plugin-marketplace', 1, 'GLOBAL', '插件市场入口')
ON DUPLICATE KEY UPDATE `enabled` = `enabled`;
-- 纳入 TTS/AI：给所有现有卡包补默认安装记录，保证老用户功能不丢。
-- 与全局开关无关；最终可见性仍由「已装 ∩ 全局启用」决定（见 PluginInstallService）。
INSERT INTO `pw_plugin_install` (`user_id`, `deck_id`, `plugin_id`, `installed_at`)
SELECT d.`user_id`, d.`id`, 'tts', NOW()
FROM `pw_deck` d
ON DUPLICATE KEY UPDATE `installed_at` = `pw_plugin_install`.`installed_at`;

INSERT INTO `pw_plugin_install` (`user_id`, `deck_id`, `plugin_id`, `installed_at`)
SELECT d.`user_id`, d.`id`, 'ai-card', NOW()
FROM `pw_deck` d
ON DUPLICATE KEY UPDATE `installed_at` = `pw_plugin_install`.`installed_at`;
-- 删除用户级功能开关覆盖表 pw_user_feature_flag。
-- 该表自 V7 建立后全代码库零调用（isEnabledForUser 仅被测试引用），属投机性死代码，移除。
-- pw_feature_flag.rollout_type 列保留：仍是全局开关表上的语义元数据，与本表解耦。
DROP TABLE IF EXISTS `pw_user_feature_flag`;
-- 删除 pw_feature_flag.rollout_type 列。
-- 该列原用于标记"哪些 flag 允许用户级覆盖"，配合 pw_user_feature_flag 使用。
-- 用户级覆盖表与代码已在 V37 移除，本列再无任何消费者（全代码库零引用），属悬空死数据，一并清除。
ALTER TABLE `pw_feature_flag` DROP COLUMN `rollout_type`;
-- 新卡包默认安装 ai-card，但卡包内 AI 解析和补全功能默认关闭。
ALTER TABLE pw_deck_ai_settings
    MODIFY COLUMN ai_explanation_enabled_a TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'A 面解析开关：1=启用，0=关闭',
    MODIFY COLUMN ai_explanation_enabled_b TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'B 面解析开关：1=启用，0=关闭',
    MODIFY COLUMN ai_completion_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '补全另一面开关：1=启用，0=关闭';
-- 浏览器插件导入入口总开关。
INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `description`)
VALUES ('feature.browser-import', 1, '浏览器插件导入')
ON DUPLICATE KEY UPDATE `enabled` = `enabled`;
UPDATE `pw_feature_flag`
SET `description` = 'TTS 英语功能'
WHERE `feature_key` = 'feature.tts';

UPDATE `pw_type_registry`
SET `item_name` = 'TTS 英语'
WHERE `registry_type` = 'plugin'
  AND `item_key` = 'tts';
UPDATE pw_user_ai_config
SET provider = 'deepseek',
    config = JSON_OBJECT(
        'protocol', 'ANTHROPIC',
        'displayName', 'DeepSeek',
        'website', 'https://www.deepseek.com',
        'note', '',
        'baseUrl', 'https://api.deepseek.com/anthropic',
        'apiKeyEnc', JSON_UNQUOTE(JSON_EXTRACT(config, '$.apiKeyEnc')),
        'model', JSON_UNQUOTE(JSON_EXTRACT(config, '$.model'))
    )
WHERE provider = 'DEEPSEEK';

UPDATE pw_user_ai_config
SET provider = 'ollama',
    config = JSON_OBJECT(
        'protocol', 'ANTHROPIC',
        'displayName', 'Ollama',
        'website', 'https://ollama.com',
        'note', '',
        'baseUrl', COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(config, '$.baseUrl')), ''), 'http://localhost:11434'),
        'model', COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(config, '$.model')), ''), 'qwen3.5:9b')
    )
WHERE provider = 'OLLAMA';
-- 移除 Ollama provider: 不再支持 Anthropic 协议下的 Ollama,删除全部 ollama 行,
-- 若有用户当前 active 是 ollama,先将该用户其它 provider 行任选一行置 active,避免该用户失去 active 配置。

-- Step 1: 把 active=ollama 用户的 active 切换到同用户最早的其它 provider 行。
UPDATE pw_user_ai_config target
JOIN (
    SELECT MIN(other.id) AS pick_id
    FROM pw_user_ai_config other
    JOIN (
        SELECT user_id
        FROM pw_user_ai_config
        WHERE provider = 'ollama'
          AND is_active = 1
    ) ollama_active ON other.user_id = ollama_active.user_id
    WHERE other.provider <> 'ollama'
    GROUP BY other.user_id
) replacement ON target.id = replacement.pick_id
SET target.is_active = 1;

-- Step 2: 删除所有 ollama 行。
DELETE FROM pw_user_ai_config WHERE provider = 'ollama';
-- 删除 user_settings.think：Anthropic provider 全局禁用 thinking，用户级 think 字段失去意义。
ALTER TABLE pw_user_settings DROP COLUMN think;
-- 遮蔽模式插件：按卡包记录题目面遮蔽模式（random=随机遮蔽 / full=完全遮蔽）。
CREATE TABLE `pw_mask_mode_deck_settings` (
  `deck_id`    BIGINT      NOT NULL COMMENT '卡包 ID',
  `mode`       VARCHAR(16) NOT NULL DEFAULT 'random' COMMENT '遮蔽模式：random / full',
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`deck_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='遮蔽模式按卡包设置；缺行时服务层回退 random';

-- 插件总开关。
INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `description`)
VALUES ('feature.mask-mode', 1, '遮蔽模式插件')
ON DUPLICATE KEY UPDATE `enabled` = `enabled`;

-- 插件目录行：config 只放语言无关数据，展示文案走前端 i18n。
INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('plugin', 'mask-mode', 'plugins.mask-mode.name', '{"descKey":"plugins.mask-mode.desc","icon":"🙈","categoryKey":"pluginCategories.studyAid"}', 3, 1)
ON DUPLICATE KEY UPDATE `item_key` = `item_key`;
-- 把 mask-mode 类型注册行从中文展示文案修正为 i18n key，DB 只存语言无关数据。
UPDATE `pw_type_registry`
SET `item_name` = 'plugins.mask-mode.name',
    `config`    = '{"descKey":"plugins.mask-mode.desc","icon":"🙈","categoryKey":"pluginCategories.studyAid"}'
WHERE `registry_type` = 'plugin'
  AND `item_key`      = 'mask-mode';
-- 遮蔽模式插件：加卡包级总开关 enabled，关闭后所有遮蔽行为不生效。
ALTER TABLE `pw_mask_mode_deck_settings`
  ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '遮蔽模式总开关, 0=关闭则跳过所有遮蔽' AFTER `mode`;
INSERT INTO `pw_system_config` (`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('sse', 'sse.heartbeat-interval-millis', '25000', 'LONG', 'SSE 连接心跳间隔（毫秒，修改后重启生效）')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
ALTER TABLE pw_card_progress
    MODIFY COLUMN card_id INT NOT NULL COMMENT '卡片 ID';

ALTER TABLE pw_card_media
    MODIFY COLUMN card_id INT NOT NULL COMMENT '所属卡片 ID';

ALTER TABLE pw_card
    MODIFY COLUMN id INT NOT NULL AUTO_INCREMENT COMMENT '主键 ID';
INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `description`)
VALUES ('feature.ai.codex-cli', 1, 'Codex CLI AI provider')
ON DUPLICATE KEY UPDATE `enabled` = `enabled`;

INSERT INTO `pw_system_config` (`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('ai', 'ai.codex-timeout-millis', '90000', 'INT', 'Codex CLI request timeout in milliseconds'),
('ai', 'ai.codex-status-timeout-millis', '5000', 'INT', 'Codex CLI status timeout in milliseconds')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);

INSERT INTO `pw_type_registry` (`registry_type`, `item_key`, `item_name`, `config`, `sort_order`, `enabled`) VALUES
('ai_provider_kind', 'codex-cli', 'settings.aiCodexCliName', '{"protocol":"CODEX_APP_SERVER","builtIn":true,"nameKey":"settings.aiCodexCliName","descriptionKey":"settings.aiCodexCliSharedLocalAccountDescription"}', 1, 1)
ON DUPLICATE KEY UPDATE `item_key` = `item_key`;
-- `codex-cli` becomes a reserved built-in key in this release. Move any historical
-- API provider using that key into an application-recognized migration namespace.
-- `_codex_` is never generated by the normal provider slugger; base36 BIGINT ids
-- keep the replacement unique and within provider VARCHAR(20). Only the key changes.
UPDATE `pw_user_ai_config`
SET `provider` = CONCAT('_codex_', LOWER(CONV(`id`, 10, 36)))
WHERE `provider` = 'codex-cli'
  AND COALESCE(
        JSON_UNQUOTE(JSON_EXTRACT(
          IF(JSON_VALID(`config`), `config`, '{}'),
          '$.protocol'
        )),
        ''
      ) <> 'CODEX_APP_SERVER';
INSERT INTO `pw_system_config`
(`group_name`, `config_key`, `value`, `value_type`, `description`) VALUES
('ai', 'ai.codex-home', '~/.local/share/openflash/codex-home', 'STRING',
 'OpenFlash-owned CODEX_HOME; restart OpenFlash after changing'),
('ai', 'ai.codex-login-timeout-millis', '600000', 'INT',
 'Shared Codex device login timeout in milliseconds')
ON DUPLICATE KEY UPDATE
  `group_name` = VALUES(`group_name`),
  `value_type` = VALUES(`value_type`),
  `description` = VALUES(`description`);
ALTER TABLE `pw_user`
  ADD COLUMN `role` varchar(16) NOT NULL DEFAULT 'USER' COMMENT '账号角色: ADMIN 或 USER' AFTER `nickname`,
  ADD CONSTRAINT `chk_pw_user_role` CHECK (`role` IN ('ADMIN', 'USER'));

UPDATE `pw_user`
SET `role` = 'ADMIN'
WHERE `username` = 'root' AND `deleted` = 0;

ALTER TABLE `pw_feature_flag`
  ADD COLUMN `rollout_type` varchar(20) NOT NULL DEFAULT 'GLOBAL'
  COMMENT 'GLOBAL=全局统一; USER_OVERRIDE=允许用户覆盖' AFTER `enabled`;

CREATE TABLE `pw_user_feature_flag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `feature_key` varchar(191) NOT NULL,
  `enabled` tinyint NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_user_feature_flag` (`user_id`, `feature_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `rollout_type`, `description`)
VALUES ('feature.ai.codex-cli.user-access', 0, 'USER_OVERRIDE', 'Codex CLI per-user access')
ON DUPLICATE KEY UPDATE
  `enabled` = VALUES(`enabled`),
  `rollout_type` = VALUES(`rollout_type`),
  `description` = VALUES(`description`);

INSERT INTO `pw_user_feature_flag` (`user_id`, `feature_key`, `enabled`)
SELECT DISTINCT `user_id`, 'feature.ai.codex-cli.user-access', 1
FROM `pw_user_ai_config`
WHERE `provider` = 'codex-cli'
ON DUPLICATE KEY UPDATE `enabled` = VALUES(`enabled`);
ALTER TABLE `pw_user`
  ADD COLUMN `banned` tinyint NOT NULL DEFAULT 0
    COMMENT '账号封禁状态: 0=可登录, 1=禁止登录' AFTER `role`,
  ADD INDEX `idx_pw_user_active_admin` (`deleted`, `banned`, `role`);
CREATE TABLE `pw_user_upload` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `relative_path` varchar(255) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_user_upload_path` (`relative_path`),
  KEY `idx_pw_user_upload_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `pw_user_upload` (`user_id`, `relative_path`)
SELECT MIN(d.`user_id`), cm.`media_url`
FROM `pw_card_media` cm
JOIN `pw_card` c ON c.`id` = cm.`card_id`
JOIN `pw_deck` d ON d.`id` = c.`deck_id`
WHERE REGEXP_LIKE(cm.`media_url`, '^/uploads/[A-Za-z0-9._-]+\\z', 'c')
  AND CHAR_LENGTH(cm.`media_url`) <= 255
  AND SUBSTRING(cm.`media_url`, 10) NOT IN ('.', '..')
GROUP BY cm.`media_url`
HAVING COUNT(DISTINCT d.`user_id`) = 1;
DELETE cm FROM pw_card_media cm
LEFT JOIN pw_card c ON c.id = cm.card_id
LEFT JOIN pw_deck d ON d.id = c.deck_id
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE c.id IS NULL OR d.id IS NULL OR u.id IS NULL;

DELETE cp FROM pw_card_progress cp
LEFT JOIN pw_user direct_user ON direct_user.id = cp.user_id
LEFT JOIN pw_card c ON c.id = cp.card_id
LEFT JOIN pw_deck d ON d.id = c.deck_id
LEFT JOIN pw_user deck_user ON deck_user.id = d.user_id
WHERE direct_user.id IS NULL OR c.id IS NULL OR d.id IS NULL OR deck_user.id IS NULL;

DELETE ds FROM pw_deck_settings ds
LEFT JOIN pw_deck d ON d.id = ds.deck_id
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE d.id IS NULL OR u.id IS NULL;

DELETE das FROM pw_deck_ai_settings das
LEFT JOIN pw_deck d ON d.id = das.deck_id
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE d.id IS NULL OR u.id IS NULL;

DELETE tds FROM pw_tts_deck_settings tds
LEFT JOIN pw_deck d ON d.id = tds.deck_id
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE d.id IS NULL OR u.id IS NULL;

DELETE mds FROM pw_mask_mode_deck_settings mds
LEFT JOIN pw_deck d ON d.id = mds.deck_id
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE d.id IS NULL OR u.id IS NULL;

DELETE ps FROM pw_practice_session_store ps
LEFT JOIN pw_user u ON u.id = ps.user_id
LEFT JOIN pw_deck d ON d.id = ps.deck_id
WHERE u.id IS NULL OR d.id IS NULL OR d.user_id <> ps.user_id;

DELETE pi FROM pw_plugin_install pi
LEFT JOIN pw_user u ON u.id = pi.user_id
LEFT JOIN pw_deck d ON d.id = pi.deck_id
WHERE u.id IS NULL OR d.id IS NULL OR d.user_id <> pi.user_id;

DELETE c FROM pw_card c
LEFT JOIN pw_deck d ON d.id = c.deck_id
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE d.id IS NULL OR u.id IS NULL;

DELETE d FROM pw_deck d
LEFT JOIN pw_user u ON u.id = d.user_id
WHERE u.id IS NULL;

DELETE s FROM pw_user_settings s
LEFT JOIN pw_user u ON u.id = s.user_id
WHERE u.id IS NULL;

DELETE a FROM pw_user_ai_config a
LEFT JOIN pw_user u ON u.id = a.user_id
WHERE u.id IS NULL;

DELETE f FROM pw_user_feature_flag f
LEFT JOIN pw_user u ON u.id = f.user_id
WHERE u.id IS NULL;

DELETE up FROM pw_user_upload up
LEFT JOIN pw_user u ON u.id = up.user_id
WHERE u.id IS NULL;

ALTER TABLE pw_async_task
  ADD COLUMN owner_user_id BIGINT NULL AFTER id;

UPDATE pw_async_task t
JOIN pw_user u ON u.id = CAST(COALESCE(
  JSON_UNQUOTE(JSON_EXTRACT(t.payload, '$.userId')),
  JSON_UNQUOTE(JSON_EXTRACT(t.payload, '$.build.userId')),
  JSON_UNQUOTE(JSON_EXTRACT(t.payload, '$.notificationTarget.userId'))
) AS UNSIGNED)
SET t.owner_user_id = u.id
WHERE t.task_type IN ('AI_CACHE_BUILD', 'CARD_SIDE_COMPLETION')
  AND JSON_VALID(t.payload);

ALTER TABLE pw_deck ADD CONSTRAINT fk_deck_user
  FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;
ALTER TABLE pw_card ADD CONSTRAINT fk_card_deck
  FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_card_media ADD CONSTRAINT fk_card_media_card
  FOREIGN KEY (card_id) REFERENCES pw_card(id) ON DELETE CASCADE;
ALTER TABLE pw_card_progress
  ADD CONSTRAINT fk_card_progress_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_card_progress_card FOREIGN KEY (card_id) REFERENCES pw_card(id) ON DELETE CASCADE;
ALTER TABLE pw_user_settings ADD CONSTRAINT fk_user_settings_user
  FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;
ALTER TABLE pw_user_ai_config ADD CONSTRAINT fk_user_ai_config_user
  FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;
ALTER TABLE pw_user_feature_flag ADD CONSTRAINT fk_user_feature_flag_user
  FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;
ALTER TABLE pw_user_upload ADD CONSTRAINT fk_user_upload_user
  FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE;
ALTER TABLE pw_practice_session_store
  ADD CONSTRAINT fk_practice_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_practice_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_plugin_install
  ADD CONSTRAINT fk_plugin_install_user FOREIGN KEY (user_id) REFERENCES pw_user(id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_plugin_install_deck FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_deck_settings ADD CONSTRAINT fk_deck_settings_deck
  FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_deck_ai_settings ADD CONSTRAINT fk_deck_ai_settings_deck
  FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_tts_deck_settings ADD CONSTRAINT fk_tts_deck_settings_deck
  FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_mask_mode_deck_settings ADD CONSTRAINT fk_mask_mode_deck_settings_deck
  FOREIGN KEY (deck_id) REFERENCES pw_deck(id) ON DELETE CASCADE;
ALTER TABLE pw_async_task ADD CONSTRAINT fk_async_task_owner_user
  FOREIGN KEY (owner_user_id) REFERENCES pw_user(id) ON DELETE CASCADE;
UPDATE `pw_system_config`
SET `value` = '1000'
WHERE `config_key` = 'async-task.fixed-delay-millis';
ALTER TABLE `pw_card_media`
  ADD KEY `idx_pw_card_media_media_url` (`media_url`(255));
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
