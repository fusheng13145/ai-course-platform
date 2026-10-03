-- ====================================================================
-- V3：统一课程状态取值，修复历史数据与运行时状态大小写不一致
-- ====================================================================

UPDATE courses
SET status = CASE LOWER(status)
    WHEN 'active' THEN 'ACTIVE'
    WHEN 'inactive' THEN 'INACTIVE'
    ELSE UPPER(status)
END
WHERE status IS NOT NULL;

ALTER TABLE courses
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    COMMENT '课程状态（大写）：ACTIVE-启用，INACTIVE-停用';
