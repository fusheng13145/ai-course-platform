-- ====================================================================
-- V16：公告与活动快照（B-12 / P-08 · WP7 + WP8）
-- --------------------------------------------------------------------
-- 目标（拆包方案 WP7 / WP8）：
--   ① WP7 快照：submissions / task_completions / exam_sessions / grade_items
--      新增可空 teaching_class_id，存量按学生**当前** APPROVED 选课回填；
--      新写入由 TeachingClassSnapshotResolver 在落库时解析（不从请求体信任），
--      此后不随成员关系变化改写（转班不漂移，验收 3）。
--   ② WP7 讨论：discussion_topics 新增可空 teaching_class_id；存量保持 NULL
--      （课程级），不发回填。
--   ③ WP8 公告：新增 announcements 表（课程/教学班/行政班三目标互斥），
--      发布后按受众扇出到 notifications（category=ANNOUNCEMENT）。
--
-- expand–contract 口径：
--   只做 expand。快照列一律可空（未选课的历史记录合法存在），不在本版收紧；
--   V17（WP10）统一收紧。回填只写 teaching_class_id，不触碰任何业务列。
--
-- 可重复执行说明：
--   MySQL 8 无 ADD COLUMN IF NOT EXISTS，加列用 information_schema 判存在 + PREPARE；
--   CREATE TABLE 用 IF NOT EXISTS；回填 UPDATE 以 IS NULL 为界，二次执行零改动。
--
-- 回滚说明（Flyway 只前向，回退须手工补偿）：
--   DROP TABLE IF EXISTS announcements;
--   ALTER TABLE submissions DROP COLUMN teaching_class_id;
--   ALTER TABLE task_completions DROP COLUMN teaching_class_id;
--   ALTER TABLE exam_sessions DROP COLUMN teaching_class_id;
--   ALTER TABLE grade_items DROP COLUMN teaching_class_id;
--   ALTER TABLE discussion_topics DROP COLUMN teaching_class_id;
--   回滚前必须先备份。
-- ====================================================================

-- --------------------------------------------------------------------
-- 1. 四类活动记录的教学班快照列（可空）
-- --------------------------------------------------------------------

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'teaching_class_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE submissions ADD COLUMN teaching_class_id BIGINT NULL COMMENT ''提交时学生所在教学班快照（WP7）：从成员关系解析，转班不漂移''',
    'SELECT 1');
PREPARE v16_stmt FROM @ddl;
EXECUTE v16_stmt;
DEALLOCATE PREPARE v16_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'task_completions' AND COLUMN_NAME = 'teaching_class_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE task_completions ADD COLUMN teaching_class_id BIGINT NULL COMMENT ''完成记录落库时学生所在教学班快照（WP7）''',
    'SELECT 1');
PREPARE v16_stmt FROM @ddl;
EXECUTE v16_stmt;
DEALLOCATE PREPARE v16_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exam_sessions' AND COLUMN_NAME = 'teaching_class_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE exam_sessions ADD COLUMN teaching_class_id BIGINT NULL COMMENT ''开考时学生所在教学班快照（WP7）''',
    'SELECT 1');
PREPARE v16_stmt FROM @ddl;
EXECUTE v16_stmt;
DEALLOCATE PREPARE v16_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grade_items' AND COLUMN_NAME = 'teaching_class_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE grade_items ADD COLUMN teaching_class_id BIGINT NULL COMMENT ''成绩明细首次落库时学生所在教学班快照（WP7）''',
    'SELECT 1');
PREPARE v16_stmt FROM @ddl;
EXECUTE v16_stmt;
DEALLOCATE PREPARE v16_stmt;

-- --------------------------------------------------------------------
-- 2. 存量回填（验收 1：按学生当前 APPROVED 选课，四类记录快照空值数 0）
--    注：多选课记录下 (student_id, course_id) 受 uk_enrollment 唯一约束保护，
--    JOIN 不会产生一对多放大。
-- --------------------------------------------------------------------

UPDATE submissions s
JOIN tasks t ON t.id = s.task_id
JOIN course_enrollments ce ON ce.student_id = s.student_id AND ce.course_id = t.course_id
    AND ce.approval_status = 'APPROVED' AND ce.is_active = 1
SET s.teaching_class_id = ce.teaching_class_id
WHERE s.teaching_class_id IS NULL;

UPDATE task_completions tc
JOIN course_enrollments ce ON ce.student_id = tc.student_id AND ce.course_id = tc.course_id
    AND ce.approval_status = 'APPROVED' AND ce.is_active = 1
SET tc.teaching_class_id = ce.teaching_class_id
WHERE tc.teaching_class_id IS NULL;

UPDATE exam_sessions es
JOIN exam_papers ep ON ep.id = es.exam_paper_id
JOIN course_enrollments ce ON ce.student_id = es.student_id AND ce.course_id = ep.course_id
    AND ce.approval_status = 'APPROVED' AND ce.is_active = 1
SET es.teaching_class_id = ce.teaching_class_id
WHERE es.teaching_class_id IS NULL;

UPDATE grade_items gi
JOIN grade_policies gp ON gp.id = gi.policy_id
JOIN course_enrollments ce ON ce.student_id = gi.student_id AND ce.course_id = gp.course_id
    AND ce.approval_status = 'APPROVED' AND ce.is_active = 1
SET gi.teaching_class_id = ce.teaching_class_id
WHERE gi.teaching_class_id IS NULL;

-- --------------------------------------------------------------------
-- 3. discussion_topics.teaching_class_id（可空，存量保持 NULL）
-- --------------------------------------------------------------------

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'discussion_topics' AND COLUMN_NAME = 'teaching_class_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE discussion_topics ADD COLUMN teaching_class_id BIGINT NULL COMMENT ''话题归属教学班（WP7，可空；存量课程级为 NULL）''',
    'SELECT 1');
PREPARE v16_stmt FROM @ddl;
EXECUTE v16_stmt;
DEALLOCATE PREPARE v16_stmt;

-- --------------------------------------------------------------------
-- 4. announcements（WP8）
-- --------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS announcements (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '公告ID，主键自增',
  course_id BIGINT NULL COMMENT '课程目标（与教学班/行政班目标互斥）',
  teaching_class_id BIGINT NULL COMMENT '教学班目标（与课程/行政班目标互斥）',
  admin_class_id BIGINT NULL COMMENT '行政班目标（与课程/教学班目标互斥，班主任发布）',
  title VARCHAR(200) NOT NULL COMMENT '公告标题',
  content TEXT NOT NULL COMMENT '公告正文',
  publisher_id BIGINT NOT NULL COMMENT '发布人ID',
  publisher_role VARCHAR(16) NOT NULL COMMENT '发布人角色：TEACHER/ADVISOR/ADMIN',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_ann_course (course_id) COMMENT '课程维度列表',
  KEY idx_ann_tc (teaching_class_id) COMMENT '教学班维度列表',
  KEY idx_ann_ac (admin_class_id) COMMENT '行政班维度列表'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表（WP8，扇出到 notifications）';

-- --------------------------------------------------------------------
-- 5. 回填自检（WP10 的 OrgMigrationBackfillTest 以同一口径断言 0；此处留读数 SQL）
-- --------------------------------------------------------------------
-- SELECT COUNT(*) FROM submissions      WHERE teaching_class_id IS NULL;
-- SELECT COUNT(*) FROM task_completions WHERE teaching_class_id IS NULL AND course_id IS NOT NULL;
-- SELECT COUNT(*) FROM exam_sessions    WHERE teaching_class_id IS NULL;
-- SELECT COUNT(*) FROM grade_items      WHERE teaching_class_id IS NULL;
