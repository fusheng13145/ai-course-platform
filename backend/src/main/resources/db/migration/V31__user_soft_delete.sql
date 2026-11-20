-- V31（2026-11-20，R2）：users 逻辑删除列。
-- 背景：管理员 DELETE /admin/users/{userId} 原先走 removeById 硬删，用户行消失后
-- 审计、成绩、提交等历史引用失去主体；改为 @TableLogic 软删除，保留行与全部外键关系。
-- 语义：0=正常，1=已删除；由 MyBatis-Plus 全局 logic-delete-field 与 User#deleted 共同接管。
-- 回滚：ALTER TABLE users DROP COLUMN deleted;
ALTER TABLE users
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常/1已删除' AFTER banned_by;
