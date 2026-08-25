alter table pw_user_settings
    add column duplicate_side_a_enabled tinyint not null default 1 comment '新增和编辑卡片时是否按 A 面去重',
    add column duplicate_side_b_enabled tinyint not null default 0 comment '新增和编辑卡片时是否按 B 面去重';
