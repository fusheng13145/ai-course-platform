-- ====================================================================
-- V13：课程审批与审批流水（B-12 / P-08 · WP3）
-- --------------------------------------------------------------------
-- 目标（拆包方案 WP3）：
--   ① courses 加 college_id（课程所属学院），由课程负责人 users.college_id 回填，
--      未回填时读路径回退教师学院；V16 才讨论收紧，本迁移保持可空。
--   ② 新建 course_approval_records：每次提交/通过/驳回/重新提交追加一行，只增不改。
--
-- expand–contract 口径：只做 expand，不改既有列、不删除、不加 NOT NULL。
--
-- 可重复执行说明：
--   MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，故加列/加索引用 information_schema
--   判存在 + PREPARE 动态 SQL；CREATE TABLE 用 IF NOT EXISTS；回填按「仅空值」更新，
--   二次执行计数不变。
--
-- 回滚说明（Flyway 只前向，回退须手工补偿）：
--   DROP TABLE IF EXISTS course_approval_records;
--   ALTER TABLE courses DROP INDEX idx_courses_college;
--   ALTER TABLE courses DROP COLUMN college_id;
--   注意：回滚会丢失审批流水，必须先备份。
-- ====================================================================

-- --------------------------------------------------------------------
-- 1. courses.college_id 与索引
-- --------------------------------------------------------------------

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND COLUMN_NAME = 'college_id');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE courses ADD COLUMN college_id BIGINT NULL COMMENT ''课程所属学院ID，V13 按负责人学院回填''', 'SELECT 1');
PREPARE v13_stmt FROM @ddl;
EXECUTE v13_stmt;
DEALLOCATE PREPARE v13_stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND INDEX_NAME = 'idx_courses_college');
SET @ddl := IF(@idx_exists = 0, 'ALTER TABLE courses ADD INDEX idx_courses_college (college_id)', 'SELECT 1');
PREPARE v13_stmt FROM @ddl;
EXECUTE v13_stmt;
DEALLOCATE PREPARE v13_stmt;

-- --------------------------------------------------------------------
-- 2. 存量课程按负责人学院回填（只填空值，可重跑）
-- --------------------------------------------------------------------

UPDATE courses c
JOIN users u ON u.id = c.teacher_id
SET c.college_id = u.college_id
WHERE c.college_id IS NULL AND u.college_id IS NOT NULL;

-- --------------------------------------------------------------------
-- 3. 审批流水表
-- --------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS course_approval_records (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流水ID，主键自增',
  course_id BIGINT NOT NULL COMMENT '课程ID，关联courses表',
  operator_id BIGINT NULL COMMENT '操作人ID，关联users表',
  action VARCHAR(16) NOT NULL COMMENT '动作：SUBMIT/APPROVE/REJECT/RESUBMIT',
  from_status VARCHAR(20) NULL COMMENT '变更前审批状态，首次提交为NULL',
  to_status VARCHAR(20) NOT NULL COMMENT '变更后审批状态',
  comment VARCHAR(500) NULL COMMENT '审批意见/驳回原因',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_course_approval_records_course (course_id, created_at) COMMENT '课程-时间索引，用于按课程查流水',
  KEY idx_course_approval_records_operator (operator_id) COMMENT '操作人索引',
  CONSTRAINT fk_course_approval_records_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_course_approval_records_operator FOREIGN KEY (operator_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程审批流水，只增不改';
