-- MySQL 的用户名唯一索引使用不区分大小写的排序规则.
-- PostgreSQL 默认区分大小写, 用表达式唯一索引保持原有注册和登录语义.
CREATE UNIQUE INDEX uk_pw_user_username_ci
    ON pw_user (lower(username));
