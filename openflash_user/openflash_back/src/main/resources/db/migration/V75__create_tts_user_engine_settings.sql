CREATE TABLE `pw_tts_user_settings` (
  `user_id` BIGINT NOT NULL,
  `cosyvoice_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_pw_tts_user_settings_user`
    FOREIGN KEY (`user_id`) REFERENCES `pw_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='TTS plugin per-user synthesis engine setting';
