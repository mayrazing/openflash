-- 浏览器插件导入入口总开关。
INSERT INTO `pw_feature_flag` (`feature_key`, `enabled`, `description`)
VALUES ('feature.browser-import', 1, '浏览器插件导入')
ON DUPLICATE KEY UPDATE `enabled` = `enabled`;
