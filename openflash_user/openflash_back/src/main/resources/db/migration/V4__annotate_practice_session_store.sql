ALTER TABLE pw_practice_session_store
    COMMENT = '练习会话快照表（每个用户每个卡包仅保留一条当前会话记录）';

ALTER TABLE pw_practice_session_store
    MODIFY COLUMN user_id bigint NOT NULL COMMENT '用户 ID',
    MODIFY COLUMN deck_id bigint NOT NULL COMMENT '卡包 ID',
    MODIFY COLUMN data longtext NOT NULL COMMENT '会话快照 JSON，包含队列、重练状态、轮后加练、上一题历史等',
    MODIFY COLUMN updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近一次写入时间';
