-- ====================================================================
-- V10：教师 AI 出题限制名单（角色矩阵 ④ 的修订：由平台级开关改为"特定教师名单"）
-- --------------------------------------------------------------------
-- 背景：V8/前置批次已落地的 ④ 是**平台级**开关（system_configs 键
-- ai.question-generation.teacher-enabled），项目主 2026-10-13 明确要求改为
-- **只限制特定教师**，故新增本表承载名单；平台开关**保留**作紧急"一键全禁"覆写
-- （两者是"逐人名单 + 总闸"的关系，不是替代关系）。
--
-- ⚠️ 判定的单一事实来源在 AiGenerationPolicy：#isTeacherQuestionGenerationEnabled()
-- 读平台开关（缺省=允许）、#isUserRestricted(userId) 查本表；控制器对非 ADMIN 调用方
-- 先查总闸、再查名单，任一命中即 403。
-- ====================================================================

CREATE TABLE ai_generation_restrictions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '被限制的教师用户ID（users.id）',
    reason VARCHAR(255) NULL COMMENT '限制原因（管理员填写，展示给被限制者）',
    restricted_by BIGINT NULL COMMENT '操作管理员ID（留痕）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入名单时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_generation_restrictions_user (user_id) COMMENT '同一教师只保留一条名单记录',
    KEY idx_ai_generation_restrictions_created (created_at) COMMENT '按加入时间排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师 AI 出题限制名单（2026-10-13 新增，角色矩阵④修订）';
