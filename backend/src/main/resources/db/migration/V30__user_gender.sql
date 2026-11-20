-- V30：users 增加 gender（性别），供个人中心展示与本人维护。
-- 取值 MALE / FEMALE / OTHER；允许 NULL，存量用户未填写时不阻塞。
ALTER TABLE users
    ADD COLUMN gender VARCHAR(16) NULL COMMENT '性别：MALE/FEMALE/OTHER' AFTER phone;

ALTER TABLE users
    ADD CONSTRAINT ck_users_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER'));
