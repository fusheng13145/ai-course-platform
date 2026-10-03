-- ====================================================================
-- V4：统一作业和试卷状态取值，修复历史数据与运行时状态大小写不一致
-- ====================================================================

UPDATE tasks
SET status = CASE LOWER(status)
    WHEN 'draft' THEN 'DRAFT'
    WHEN 'published' THEN 'PUBLISHED'
    WHEN 'closed' THEN 'CLOSED'
    ELSE UPPER(status)
END
WHERE status IS NOT NULL;

UPDATE exam_papers
SET status = CASE LOWER(status)
    WHEN 'draft' THEN 'DRAFT'
    WHEN 'published' THEN 'PUBLISHED'
    WHEN 'closed' THEN 'ARCHIVED'
    ELSE UPPER(status)
END
WHERE status IS NOT NULL;

ALTER TABLE tasks
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
    COMMENT '任务状态（大写）：DRAFT-草稿，PUBLISHED-已发布，CLOSED-已关闭';

ALTER TABLE exam_papers
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
    COMMENT '试卷状态（大写）：DRAFT-草稿，PUBLISHED-已发布，ARCHIVED-已归档';
