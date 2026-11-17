-- P0-B（2026-11-17）：新题强制归属课程，历史未归属题保留。
-- 背景：questions.course_id 可空，V1 的 FK 还是 ON DELETE SET NULL；历史库实测 228 题中
--       213 题 course_id 为空（旧演示/AI 生成遗留），因此不能直接把列改成 NOT NULL。
-- 裁定：新题必须有 courseId；历史空值题保留可读，不做静默删除或强行归属。
-- 实现：加 legacy_unassigned 标记（仅迁移回填为 1），再用 CHECK 约束保证所有非历史题 course_id 非空。
--       应用层 QuestionServiceImpl.createQuestion/batchImport/copyQuestion 同步拒绝空 courseId，形成双保险。
-- 影响：fk_questions_course 由 ON DELETE SET NULL 改为 RESTRICT——删除仍有题目的课程会被拒绝，
--       必须先归档/迁移题目，避免“删课程静默把新题变成无归属题”绕过 CHECK。
-- 回滚：恢复 FK 为 ON DELETE SET NULL（需先 DROP CHECK chk_questions_course_required），再 DROP COLUMN legacy_unassigned。
ALTER TABLE questions
    ADD COLUMN legacy_unassigned TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '历史未归属题标记：1=迁移前遗留且 course_id 为空；0=新题或已归属题';

UPDATE questions
SET legacy_unassigned = 1
WHERE course_id IS NULL;

-- MySQL 规定：参与外键 ON DELETE SET NULL 的列不能出现在 CHECK 约束中；
-- 改为 RESTRICT，既让 CHECK 可建立，也让删课程时明确失败而不是悄悄置空。
ALTER TABLE questions
    DROP FOREIGN KEY fk_questions_course;

ALTER TABLE questions
    ADD CONSTRAINT fk_questions_course
        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE RESTRICT;

ALTER TABLE questions
    ADD CONSTRAINT chk_questions_course_required
        CHECK (course_id IS NOT NULL OR legacy_unassigned = 1);
