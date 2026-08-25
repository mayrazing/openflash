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
