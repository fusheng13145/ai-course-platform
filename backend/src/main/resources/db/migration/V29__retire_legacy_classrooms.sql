-- V29：退役旧 classrooms / users.class_id，行政班唯一来源收敛到 admin_classes。
-- 前置事实（2026-11-18 实测）：classrooms=22、admin_classes=24、users.class_id=138、
-- users.admin_class_id=153、class_id 未映射到 admin_class 的记录=0、admin_class 无效引用=0。

-- 1) 再回填一次，复用 V14 的 code 映射口径。
UPDATE users u
JOIN classrooms c ON c.id = u.class_id
JOIN admin_classes ac ON ac.code = c.code
SET u.admin_class_id = ac.id
WHERE u.class_id IS NOT NULL
  AND u.admin_class_id IS NULL;

-- 2) 拆除旧 FK 与硬门禁放在同一条 ALTER 里：MySQL 8 原子 DDL，
--    未映射记录会让 ADD CHECK 校验失败，整条语句回滚（FK 不会被单方面拆掉）。
--    不用临时表 / 普通守卫表：应用号没有 CREATE TEMPORARY TABLES 权限，
--    且测试库重建守卫会要求把瞬态表补进清理清单。
ALTER TABLE users
    DROP FOREIGN KEY fk_users_classroom,
    ADD CONSTRAINT ck_v29_class_map CHECK (class_id IS NULL OR admin_class_id IS NOT NULL);
ALTER TABLE users DROP CHECK ck_v29_class_map;

-- 3) 拆除旧关系与旧表。
ALTER TABLE users DROP INDEX idx_users_class_id;
ALTER TABLE users DROP COLUMN class_id;
DROP TABLE classrooms;

-- 4) 新列补上真实外键，避免 admin_classes 被删后留下悬空引用。
ALTER TABLE users
    ADD CONSTRAINT fk_users_admin_class
    FOREIGN KEY (admin_class_id) REFERENCES admin_classes(id) ON DELETE SET NULL;
