-- ====================================================================
-- V2：列注释与运行时实现对齐（仅修改 COMMENT，不改类型/默认值/数据）
-- ====================================================================
-- 背景（2026-09-14 端到端验证发现）：V1__init_schema.sql 中若干列的 COMMENT
-- 与实际实现写入的取值域不一致，导致按「注释即契约」开发时反复踩坑：
--   1) resources.file_type  —— 注释写枚举 video/document/image/other，
--      实现写入的是 MIME 类型（如 video/mp4、application/pdf）；
--   2) questions.type       —— 注释写小写 single/multiple/true_false/...，
--      实现（含判分 switch）使用大写 SINGLE_CHOICE/TRUE_FALSE/...；
--   3) exam_sessions.status —— 注释仅列 3 态，实际还有 AUTO_SUBMITTED /
--      FORCE_SUBMITTED / TIMEOUT（见 ExamSessionStatus）；
--   4) exam_papers.status / tasks.status —— 实现写入大写 PUBLISHED，
--      注释写小写 published。
-- 本迁移只刷新注释，使其成为可靠契约；不改动任何列类型、默认值与既有数据。
-- 注：V1 已被基线化（flyway_schema_history baseline v1），不得修改其内容，
-- 故以增量迁移方式对齐。
-- ====================================================================

ALTER TABLE resources
    MODIFY COLUMN file_type VARCHAR(50) NULL
    COMMENT '文件类型：实际存储 MIME 类型（如 video/mp4、application/pdf、image/png），非 video/document 枚举';

ALTER TABLE questions
    MODIFY COLUMN type VARCHAR(20) NOT NULL
    COMMENT '题目类型（大写）：SINGLE_CHOICE-单选，MULTIPLE_CHOICE-多选，TRUE_FALSE-判断，FILL_BLANK-填空，SHORT_ANSWER-简答';

ALTER TABLE exam_sessions
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'STARTED'
    COMMENT '考试状态：STARTED-进行中，SUBMITTED-已提交，AUTO_SUBMITTED-超时自动收卷，FORCE_SUBMITTED-强制收卷，GRADED-已批改，TIMEOUT-历史遗留超时标记';

ALTER TABLE exam_papers
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'draft'
    COMMENT '试卷状态：draft-草稿，PUBLISHED-已发布（实现写入大写），closed-已关闭';

ALTER TABLE tasks
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'draft'
    COMMENT '任务状态：draft-草稿，PUBLISHED-已发布（实现写入大写），closed-已关闭';
