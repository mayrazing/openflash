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
