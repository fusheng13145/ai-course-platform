-- ====================================================================
-- V11：内容审核与违规处理（角色矩阵 ③ 的实现落点）
-- --------------------------------------------------------------------
-- 背景（手册 §D.12.4）：③ 内容审核（讨论区评论 / 平台全部资源审核 + 违规处理）此前无任何落点——
-- `discussion_posts` 与 `resources` 都没有审核/状态列，也没有"隐藏、删除、封禁作者"的处理流。
--
-- 本迁移补三处结构：
--   ① discussion_posts：审核状态 + 原因 + 操作留痕（隐藏/恢复用，删除为物理删）
--   ② resources：同上（平台全部资源）
--   ③ users：封禁留痕三列（ban_reason / banned_at / banned_by）
--
-- ⚠️ 两条口径（与页面、手册一致，勿误读）：
--   (a) **封禁作者 = 复用既有 users.is_active=0**（与"账号停用"同一开关，不另造第二套封禁语义，
--       避免"停用但未封禁""封禁但仍可用"这类自相矛盾状态）；本迁移只补**留痕**三列，
--       并以 `banned_at IS NOT NULL` 把"经审核封禁"与"其它原因停用"区分开
--       （生产上演示种子账号也会被加固器停用，不该出现在审核页的封禁名单里）。
--   (b) 违规处理走既有 `OperationLogAspect`（它拦截全部 controller 方法、记录 POST/PUT/DELETE），
--       故不另建审核动作日志表——删除（物理删）的事实与原因在该切面留痕。
-- ====================================================================

ALTER TABLE discussion_posts
    ADD COLUMN moderation_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL'
        COMMENT '审核状态：NORMAL-正常；HIDDEN-已隐藏（读路径过滤，可恢复）' AFTER mentions,
    ADD COLUMN moderation_reason VARCHAR(255) NULL COMMENT '审核原因（管理员填写）' AFTER moderation_status,
    ADD COLUMN moderated_at DATETIME NULL COMMENT '最近一次审核时间' AFTER moderation_reason,
    ADD COLUMN moderated_by BIGINT NULL COMMENT '最近一次审核管理员ID' AFTER moderated_at,
    ADD INDEX idx_discussion_posts_moderation (moderation_status);

ALTER TABLE resources
    ADD COLUMN moderation_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL'
        COMMENT '审核状态：NORMAL-正常；HIDDEN-已隐藏（读路径过滤，可恢复）' AFTER visible_to_all,
    ADD COLUMN moderation_reason VARCHAR(255) NULL COMMENT '审核原因（管理员填写）' AFTER moderation_status,
    ADD COLUMN moderated_at DATETIME NULL COMMENT '最近一次审核时间' AFTER moderation_reason,
    ADD COLUMN moderated_by BIGINT NULL COMMENT '最近一次审核管理员ID' AFTER moderated_at,
    ADD INDEX idx_resources_moderation (moderation_status);

ALTER TABLE users
    ADD COLUMN ban_reason VARCHAR(255) NULL COMMENT '封禁原因（经内容审核封禁时写入）' AFTER locked_until,
    ADD COLUMN banned_at DATETIME NULL COMMENT '封禁时间' AFTER ban_reason,
    ADD COLUMN banned_by BIGINT NULL COMMENT '执行封禁的管理员ID' AFTER banned_at,
    ADD INDEX idx_users_banned (banned_at);
