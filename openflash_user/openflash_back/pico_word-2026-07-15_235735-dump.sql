-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: 127.0.0.1    Database: pick_word
-- ------------------------------------------------------
-- Server version	8.0.46-0ubuntu0.24.04.3

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `SPRING_SESSION`
--

DROP TABLE IF EXISTS `SPRING_SESSION`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SPRING_SESSION` (
  `PRIMARY_ID` char(36) NOT NULL COMMENT 'Spring Session 主键 ID',
  `SESSION_ID` char(36) NOT NULL COMMENT '会话 ID',
  `CREATION_TIME` bigint NOT NULL COMMENT '会话创建时间（Unix 毫秒）',
  `LAST_ACCESS_TIME` bigint NOT NULL COMMENT '最近访问时间（Unix 毫秒）',
  `MAX_INACTIVE_INTERVAL` int NOT NULL COMMENT '最大非活跃间隔（秒）',
  `EXPIRY_TIME` bigint NOT NULL COMMENT '会话过期时间（Unix 毫秒）',
  `PRINCIPAL_NAME` varchar(100) DEFAULT NULL COMMENT '关联主体名称，通常为用户名',
  PRIMARY KEY (`PRIMARY_ID`),
  UNIQUE KEY `SPRING_SESSION_IX1` (`SESSION_ID`),
  KEY `SPRING_SESSION_IX2` (`EXPIRY_TIME`),
  KEY `SPRING_SESSION_IX3` (`PRINCIPAL_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `SPRING_SESSION_ATTRIBUTES`
--

DROP TABLE IF EXISTS `SPRING_SESSION_ATTRIBUTES`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SPRING_SESSION_ATTRIBUTES` (
  `SESSION_PRIMARY_ID` char(36) NOT NULL COMMENT '所属 Spring Session 主键 ID',
  `ATTRIBUTE_NAME` varchar(200) NOT NULL COMMENT '会话属性名称',
  `ATTRIBUTE_BYTES` blob NOT NULL COMMENT '会话属性序列化内容',
  PRIMARY KEY (`SESSION_PRIMARY_ID`,`ATTRIBUTE_NAME`),
  CONSTRAINT `SPRING_SESSION_ATTRIBUTES_FK` FOREIGN KEY (`SESSION_PRIMARY_ID`) REFERENCES `SPRING_SESSION` (`PRIMARY_ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL COMMENT '迁移安装序号',
  `version` varchar(50) DEFAULT NULL COMMENT '迁移版本',
  `description` varchar(200) NOT NULL COMMENT '迁移描述',
  `type` varchar(20) NOT NULL COMMENT '迁移类型',
  `script` varchar(1000) NOT NULL COMMENT '迁移脚本名称',
  `checksum` int DEFAULT NULL COMMENT '迁移脚本校验和',
  `installed_by` varchar(100) NOT NULL COMMENT '迁移执行用户',
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '迁移安装时间',
  `execution_time` int NOT NULL COMMENT '迁移执行耗时（毫秒）',
  `success` tinyint(1) NOT NULL COMMENT '迁移是否成功：1=成功，0=失败',
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_async_task`
--

DROP TABLE IF EXISTS `pw_async_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=1578 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一异步任务编排表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_card`
--

DROP TABLE IF EXISTS `pw_card`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_card` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `deck_id` bigint NOT NULL COMMENT '所属词本 ID',
  `side_a` text COMMENT 'A 面内容',
  `side_b` text COMMENT 'B 面内容',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  KEY `idx_pw_card_deck_id` (`deck_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6580709400 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='词卡表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_card_ai_cache`
--

DROP TABLE IF EXISTS `pw_card_ai_cache`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_card_ai_cache` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `content_fingerprint` char(64) NOT NULL COMMENT '规范化 prompt 的 SHA-256 指纹，跨用户唯一',
  `prompt_fingerprint` char(64) NOT NULL COMMENT '规范化 prompt 的 SHA-256 指纹，同 prompt 只保留一条缓存',
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
) ENGINE=InnoDB AUTO_INCREMENT=175 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 缓存表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_card_media`
--

DROP TABLE IF EXISTS `pw_card_media`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_card_media` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `card_id` bigint NOT NULL COMMENT '所属词卡 ID',
  `card_side` varchar(10) NOT NULL COMMENT '图片所属面：A 或 B',
  `media_url` varchar(500) NOT NULL COMMENT '图片地址',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值，值越小越靠前',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_pw_card_media_card_id` (`card_id`)
) ENGINE=InnoDB AUTO_INCREMENT=117 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='词卡图片表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_card_progress`
--

DROP TABLE IF EXISTS `pw_card_progress`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_card_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `card_id` bigint NOT NULL COMMENT '词卡 ID',
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
  KEY `idx_pw_card_progress_user_direction_next_review_date` (`user_id`,`direction`,`next_review_date`),
  KEY `idx_pw_card_progress_user_last_review_card_direction` (`user_id`,`last_review_date`,`card_id`,`direction`)
) ENGINE=InnoDB AUTO_INCREMENT=5643 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='词卡学习进度表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_deck`
--

DROP TABLE IF EXISTS `pw_deck`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_deck` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `user_id` bigint NOT NULL COMMENT '所属用户 ID',
  `name` varchar(100) NOT NULL COMMENT '词本名称',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`),
  KEY `idx_pw_deck_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7580709400 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='词本表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_deck_ai_settings`
--

DROP TABLE IF EXISTS `pw_deck_ai_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_deck_ai_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `deck_id` bigint NOT NULL COMMENT '关联卡包 ID',
  `ai_explanation_prompt_a` text COMMENT '词卡解析 A 面提示词；NULL=无 system prompt',
  `ai_explanation_prompt_b` text COMMENT '词卡解析 B 面提示词；NULL=无 system prompt',
  `ai_completion_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '补全另一面开关：1=启用，0=关闭',
  `ai_completion_prompt` text COMMENT '补全另一面提示词；NULL=无 system prompt',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `ai_explanation_enabled_a` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'A 面解析开关：1=启用，0=关闭',
  `ai_explanation_enabled_b` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'B 面解析开关：1=启用，0=关闭',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_deck_ai_settings_deck_id` (`deck_id`)
) ENGINE=InnoDB AUTO_INCREMENT=226 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_deck_settings`
--

DROP TABLE IF EXISTS `pw_deck_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_deck_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `deck_id` bigint NOT NULL COMMENT '关联卡包 ID',
  `new_cards_per_day` int NOT NULL DEFAULT '10' COMMENT '每日新卡上限',
  `target_retention` decimal(5,4) NOT NULL DEFAULT '0.9000' COMMENT '目标记忆留存率',
  `review_load_profile` varchar(32) NOT NULL DEFAULT 'standard' COMMENT '复习负荷配置：standard / light / heavy',
  `duplicate_side_a_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'A 面重复检测开关：1=启用，0=关闭',
  `duplicate_side_b_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'B 面重复检测开关：1=启用，0=关闭',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  `tts_language_a` varchar(10) DEFAULT NULL COMMENT 'A面 TTS 语言代码，NULL 表示未设置',
  `tts_language_b` varchar(10) DEFAULT NULL COMMENT 'B面 TTS 语言代码，NULL 表示未设置',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_deck_settings_deck_id` (`deck_id`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_feature_flag`
--

DROP TABLE IF EXISTS `pw_feature_flag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_feature_flag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `feature_key` varchar(191) NOT NULL COMMENT '功能标识，如 feature.tts',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '全局默认：1=开，0=关',
  `description` varchar(500) DEFAULT NULL COMMENT '功能说明',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` varchar(50) DEFAULT NULL COMMENT '最后修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_feature_flag_key` (`feature_key`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='功能开关，支持全局开关和用户级覆盖';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_mask_mode_deck_settings`
--

DROP TABLE IF EXISTS `pw_mask_mode_deck_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_mask_mode_deck_settings` (
  `deck_id` bigint NOT NULL COMMENT '卡包 ID',
  `mode` varchar(16) NOT NULL DEFAULT 'random' COMMENT '遮蔽模式：random / full',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '遮蔽模式总开关, 0=关闭则跳过所有遮蔽',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`deck_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='遮蔽模式按卡包设置；缺行时服务层回退 random';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_plugin_install`
--

DROP TABLE IF EXISTS `pw_plugin_install`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_plugin_install` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `deck_id` bigint NOT NULL COMMENT '卡包 ID',
  `plugin_id` varchar(64) NOT NULL COMMENT '插件 ID，对应 PluginDescriptor.pluginId()',
  `installed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '安装时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plugin_install` (`user_id`,`deck_id`,`plugin_id`),
  KEY `idx_plugin_install_deck` (`deck_id`),
  KEY `idx_plugin_install_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=57 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='插件按卡包安装关系';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_practice_session_store`
--

DROP TABLE IF EXISTS `pw_practice_session_store`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_practice_session_store` (
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `deck_id` bigint NOT NULL COMMENT '词本 ID',
  `data` longtext NOT NULL COMMENT '会话快照 JSON，包含队列、重练状态、轮后加练、上一题历史等',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近一次写入时间',
  PRIMARY KEY (`user_id`,`deck_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='练习会话快照表（每个用户每个词本仅保留一条当前会话记录）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_system_config`
--

DROP TABLE IF EXISTS `pw_system_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `group_name` varchar(64) NOT NULL COMMENT '配置分组：tts / ai / async-task / cache',
  `config_key` varchar(191) NOT NULL COMMENT '唯一配置键，如 tts.voice',
  `value` varchar(2000) NOT NULL COMMENT '配置值（字符串存储，按 value_type 解析）',
  `value_type` varchar(20) NOT NULL COMMENT 'STRING / INT / BOOL / DECIMAL',
  `description` varchar(500) DEFAULT NULL COMMENT '说明，供后台管理界面展示',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` varchar(50) DEFAULT NULL COMMENT '最后修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_system_config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统标量配置，替代 YAML 里的运行时参数';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_tts_cache_meta`
--

DROP TABLE IF EXISTS `pw_tts_cache_meta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
  `language` varchar(10) DEFAULT NULL COMMENT 'TTS 语言代码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_tts_cache_meta_variant` (`variant_fingerprint`),
  KEY `idx_pw_tts_cache_meta_last_accessed_at` (`last_accessed_at`)
) ENGINE=InnoDB AUTO_INCREMENT=27220 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TTS 缓存元数据表（按变体指纹共享）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_tts_deck_settings`
--

DROP TABLE IF EXISTS `pw_tts_deck_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_tts_deck_settings` (
  `deck_id` bigint NOT NULL COMMENT '关联卡包 ID',
  `auto_speak_a` tinyint(1) NOT NULL DEFAULT '0' COMMENT '练习时自动朗读 A 面：1=启用，0=关闭',
  `auto_speak_b` tinyint(1) NOT NULL DEFAULT '0' COMMENT '练习时自动朗读 B 面：1=启用，0=关闭',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`deck_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_type_registry`
--

DROP TABLE IF EXISTS `pw_type_registry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_type_registry` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `registry_type` varchar(64) NOT NULL COMMENT '类型分类：ai_profile / ai_feature_mapping / practice_mode / card_category',
  `item_key` varchar(191) NOT NULL COMMENT '条目键',
  `item_name` varchar(200) DEFAULT NULL COMMENT '显示名，供后台管理界面展示',
  `config` longtext COMMENT 'JSON 扩展配置',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值，值越小越靠前',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1=是，0=否',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_type_registry` (`registry_type`,`item_key`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可扩展类型注册，admin 加一行 = 系统多一个类型';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_user`
--

DROP TABLE IF EXISTS `pw_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
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
) ENGINE=InnoDB AUTO_INCREMENT=8580709372 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_user_ai_config`
--

DROP TABLE IF EXISTS `pw_user_ai_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_user_ai_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `provider` varchar(20) NOT NULL COMMENT 'OLLAMA 或 DEEPSEEK',
  `config` varchar(2000) NOT NULL DEFAULT '{}' COMMENT 'provider 连接参数 JSON',
  `is_active` tinyint(1) NOT NULL DEFAULT '0' COMMENT '当前选用的 provider，每用户只有一行为 1',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_provider` (`user_id`,`provider`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户 AI provider 配置，每 provider 一行';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pw_user_settings`
--

DROP TABLE IF EXISTS `pw_user_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pw_user_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `theme` varchar(20) NOT NULL DEFAULT 'light' COMMENT '主题：light 或 dark',
  `last_exported_at` datetime DEFAULT NULL COMMENT '最近导出时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `sound_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '声音功能开关：1=启用，0=关闭',
  `language` varchar(10) NOT NULL DEFAULT 'en' COMMENT '界面语言代码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pw_user_settings_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=119 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户设置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-15 23:57:35
