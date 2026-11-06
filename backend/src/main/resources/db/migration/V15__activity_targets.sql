-- ====================================================================
-- V15：任务/试卷教学目标（B-12 / P-08 · WP6）
-- --------------------------------------------------------------------
-- 目标（拆包方案 WP6，决策 5A）：
--   ① tasks / exam_papers 新增 visibility：
--        COURSE  = 课程级（默认），该课程全部教学班可见/可做；
--        CLASSES = 教学班级，仅目标教学班可见/可做，必须至少 1 条目标行；
--   ② 新增 task_teaching_classes / exam_teaching_classes 目标表：
--        (任务|试卷, 教学班) 唯一，随活动删除级联清理；
--   ③ 存量 160 条任务、175 份试卷统一 visibility='COURSE'，
--      且**不为存量批量插入目标行**（COURSE 语义即全部教学班，无需目标行）。
--
-- expand–contract 口径：
--   只做 expand。新列 NOT NULL + 默认 COURSE，不收紧既有列；
--   存量不与教学班逐行绑定，避免产生不可解释的目标行。
--
-- 可重复执行说明：
--   MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，加列用 information_schema 判存在 + PREPARE；
--   CREATE TABLE 用 IF NOT EXISTS；本迁移不写业务数据，二次执行不改变任何计数。
--
-- 回滚说明（Flyway 只前向，回退须手工补偿）：
--   DROP TABLE IF EXISTS exam_teaching_classes;
--   DROP TABLE IF EXISTS task_teaching_classes;
--   ALTER TABLE exam_papers DROP COLUMN visibility;
--   ALTER TABLE tasks DROP COLUMN visibility;
--   回滚前必须先备份。
-- ====================================================================

-- --------------------------------------------------------------------
-- 1. tasks.visibility（默认 COURSE，存量自动取默认值）
-- --------------------------------------------------------------------

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'visibility');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE tasks ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT ''COURSE'' COMMENT ''可见范围：COURSE-课程全部教学班，CLASSES-仅目标教学班''',
    'SELECT 1');
PREPARE v15_stmt FROM @ddl;
EXECUTE v15_stmt;
DEALLOCATE PREPARE v15_stmt;

-- --------------------------------------------------------------------
-- 2. exam_papers.visibility（默认 COURSE，存量自动取默认值）
-- --------------------------------------------------------------------

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exam_papers' AND COLUMN_NAME = 'visibility');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE exam_papers ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT ''COURSE'' COMMENT ''可见范围：COURSE-课程全部教学班，CLASSES-仅目标教学班''',
    'SELECT 1');
PREPARE v15_stmt FROM @ddl;
EXECUTE v15_stmt;
DEALLOCATE PREPARE v15_stmt;

-- --------------------------------------------------------------------
-- 3. 任务目标教学班表
-- --------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS task_teaching_classes (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_id BIGINT NOT NULL COMMENT '任务ID，关联tasks表',
  teaching_class_id BIGINT NOT NULL COMMENT '目标教学班ID，关联teaching_classes表',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_teaching_class (task_id, teaching_class_id) COMMENT '任务-教学班唯一索引',
  KEY idx_task_teaching_class_tc (teaching_class_id) COMMENT '教学班索引',
  CONSTRAINT fk_task_teaching_class_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
  CONSTRAINT fk_task_teaching_class_tc FOREIGN KEY (teaching_class_id) REFERENCES teaching_classes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务目标教学班：visibility=CLASSES 时的可见/可做范围';

-- --------------------------------------------------------------------
-- 4. 试卷目标教学班表
-- --------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS exam_teaching_classes (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  exam_paper_id BIGINT NOT NULL COMMENT '试卷ID，关联exam_papers表',
  teaching_class_id BIGINT NOT NULL COMMENT '目标教学班ID，关联teaching_classes表',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_exam_teaching_class (exam_paper_id, teaching_class_id) COMMENT '试卷-教学班唯一索引',
  KEY idx_exam_teaching_class_tc (teaching_class_id) COMMENT '教学班索引',
  CONSTRAINT fk_exam_teaching_class_paper FOREIGN KEY (exam_paper_id) REFERENCES exam_papers(id) ON DELETE CASCADE,
  CONSTRAINT fk_exam_teaching_class_tc FOREIGN KEY (teaching_class_id) REFERENCES teaching_classes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷目标教学班：visibility=CLASSES 时的可见/可做范围';
