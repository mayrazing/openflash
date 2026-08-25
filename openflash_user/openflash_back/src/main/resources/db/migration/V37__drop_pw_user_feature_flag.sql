-- 删除用户级功能开关覆盖表 pw_user_feature_flag。
-- 该表自 V7 建立后全代码库零调用（isEnabledForUser 仅被测试引用），属投机性死代码，移除。
-- pw_feature_flag.rollout_type 列保留：仍是全局开关表上的语义元数据，与本表解耦。
DROP TABLE IF EXISTS `pw_user_feature_flag`;
