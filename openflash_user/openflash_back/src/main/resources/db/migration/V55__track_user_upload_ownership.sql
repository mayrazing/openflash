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
