-- 删除 pw_feature_flag.rollout_type 列。
-- 该列原用于标记"哪些 flag 允许用户级覆盖"，配合 pw_user_feature_flag 使用。
-- 用户级覆盖表与代码已在 V37 移除，本列再无任何消费者（全代码库零引用），属悬空死数据，一并清除。
ALTER TABLE `pw_feature_flag` DROP COLUMN `rollout_type`;
