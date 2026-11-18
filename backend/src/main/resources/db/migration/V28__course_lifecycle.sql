-- ====================================================================
-- V28：课程生命周期三层化（2026-11-17）
-- --------------------------------------------------------------------
-- 目标：
--   ① 新增 course_definitions（课程库/稳定定义层）：课程编号、名称、学分、
--      学时、课程性质、所属院系、先修要求、分类、难度、封面/大纲；
--      不绑定学期与教师。
--   ② courses 明确为「开课任务（offering）」：新增 definition_id / planned_sections；
--      课程编号唯一键由 (semester_id, code) 改为 (semester_id, definition_id)，
--      使同一课程定义可在不同学期重复开课。
--   ③ 存量 courses 每条按 code 生成/复用课程定义并回填 definition_id；
--      planned_sections 存量按 1 回填。
--
-- 兼容口径：
--   · courses.name/code/credits/description/category/difficulty/tags/college_id
--     继续保留，作为开课时的定义快照，避免存量查询与页面立即失效；
--   · 新开课任务的上述字段以 course_definitions 为准覆盖；
--   · course_enrollments 的 (student_id, course_id) 唯一键语义不变：
--     course_id 现在是开课任务，同一学生跨学期再次修读时 course_id 不同。
--
-- 可重复执行说明：加列/加表均先查 information_schema；回填以 IS NULL / NOT EXISTS 为界。
-- 回滚说明（Flyway 只前向，回退须手工补偿）：
--   ALTER TABLE courses DROP FOREIGN KEY fk_courses_definition;
--   ALTER TABLE courses DROP INDEX uk_courses_semester_definition;
--   ALTER TABLE courses ADD UNIQUE KEY uk_courses_semester_code (semester_id, code);
--   ALTER TABLE courses DROP COLUMN definition_id, DROP COLUMN planned_sections,
--                       DROP COLUMN completed_at;
--   DROP TABLE IF EXISTS course_definitions;
-- ====================================================================

-- --------------------------------------------------------------------
-- 1. 课程库（Course 层）
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_definitions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '课程定义ID，主键自增',
  code VARCHAR(50) NOT NULL COMMENT '课程编号，跨学期稳定且唯一',
  name VARCHAR(255) NOT NULL COMMENT '课程名称',
  description TEXT NULL COMMENT '课程简介',
  credits DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '学分',
  hours INT NULL COMMENT '学时',
  nature VARCHAR(20) NOT NULL DEFAULT '选修' COMMENT '课程性质：必修/选修/公选',
  college_id BIGINT NULL COMMENT '所属学院ID，关联colleges表',
  category VARCHAR(50) NULL COMMENT '课程分类',
  difficulty VARCHAR(20) NULL COMMENT '课程难度',
  prerequisite VARCHAR(500) NULL COMMENT '先修要求',
  cover_image VARCHAR(500) NULL COMMENT '封面图片URL',
  syllabus_url VARCHAR(500) NULL COMMENT '课程大纲URL',
  tags JSON NULL COMMENT '课程标签，JSON数组',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE-可用，INACTIVE-停用',
  created_by BIGINT NULL COMMENT '创建人ID，关联users表',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_definitions_code (code) COMMENT '课程编号全库唯一',
  KEY idx_course_definitions_college (college_id) COMMENT '按学院查询课程库',
  KEY idx_course_definitions_status (status) COMMENT '按状态查询课程库',
  CONSTRAINT fk_course_definitions_college FOREIGN KEY (college_id) REFERENCES colleges(id) ON DELETE SET NULL,
  CONSTRAINT fk_course_definitions_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程库：跨学期稳定的课程定义';

-- --------------------------------------------------------------------
-- 2. 存量课程回填课程定义（按 code 归并；同 code 取一条快照）
-- --------------------------------------------------------------------
INSERT INTO course_definitions
  (code, name, description, credits, nature, college_id, category, difficulty,
   status, created_by, created_at, updated_at)
SELECT c.code,
       MAX(c.name),
       MAX(c.description),
       MAX(c.credits),
       '选修',
       MAX(c.college_id),
       MAX(c.category),
       MAX(c.difficulty),
       CASE WHEN MAX(CASE WHEN c.status = 'ACTIVE' THEN 1 ELSE 0 END) = 1 THEN 'ACTIVE' ELSE 'INACTIVE' END,
       MIN(c.teacher_id),
       MIN(c.created_at),
       NOW()
FROM courses c
WHERE c.code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM course_definitions d WHERE d.code = c.code)
GROUP BY c.code;

-- --------------------------------------------------------------------
-- 3. courses 增加开课任务字段
-- --------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND COLUMN_NAME = 'definition_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE courses ADD COLUMN definition_id BIGINT NULL COMMENT ''课程库定义ID，关联course_definitions表（V28）''',
    'SELECT 1');
PREPARE v28_stmt FROM @ddl;
EXECUTE v28_stmt;
DEALLOCATE PREPARE v28_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND COLUMN_NAME = 'planned_sections');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE courses ADD COLUMN planned_sections INT NOT NULL DEFAULT 1 COMMENT ''计划教学班数；审批通过后按此生成教学班占位（V28）''',
    'SELECT 1');
PREPARE v28_stmt FROM @ddl;
EXECUTE v28_stmt;
DEALLOCATE PREPARE v28_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND COLUMN_NAME = 'completed_at');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE courses ADD COLUMN completed_at DATETIME NULL COMMENT ''结课/归档时间（V28）''',
    'SELECT 1');
PREPARE v28_stmt FROM @ddl;
EXECUTE v28_stmt;
DEALLOCATE PREPARE v28_stmt;

-- 回填 definition_id（按 code 对齐）
UPDATE courses c
JOIN course_definitions d ON d.code = c.code
SET c.definition_id = d.id
WHERE c.definition_id IS NULL;

-- --------------------------------------------------------------------
-- 4. 唯一键切换：学期内课程编号 → 学期内课程定义
--    前提：同 code 已归并为一条定义；存量 (semester_id, code) 唯一，
--    因此 (semester_id, definition_id) 也唯一。
-- --------------------------------------------------------------------
SET @old_key := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND INDEX_NAME = 'uk_courses_semester_code');
SET @ddl := IF(@old_key > 0,
    'ALTER TABLE courses DROP INDEX uk_courses_semester_code',
    'SELECT 1');
PREPARE v28_stmt FROM @ddl;
EXECUTE v28_stmt;
DEALLOCATE PREPARE v28_stmt;

SET @new_key := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND INDEX_NAME = 'uk_courses_semester_definition');
SET @ddl := IF(@new_key = 0,
    'ALTER TABLE courses ADD UNIQUE KEY uk_courses_semester_definition (semester_id, definition_id) COMMENT ''同一学期同一课程定义只允许一个开课任务（V28）''',
    'SELECT 1');
PREPARE v28_stmt FROM @ddl;
EXECUTE v28_stmt;
DEALLOCATE PREPARE v28_stmt;

-- 外键（definition_id 可空；删除课程定义前必须先处理开课任务）
SET @fk_exists := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses'
      AND CONSTRAINT_NAME = 'fk_courses_definition');
SET @ddl := IF(@fk_exists = 0,
    'ALTER TABLE courses ADD CONSTRAINT fk_courses_definition FOREIGN KEY (definition_id) REFERENCES course_definitions(id) ON DELETE RESTRICT',
    'SELECT 1');
PREPARE v28_stmt FROM @ddl;
EXECUTE v28_stmt;
DEALLOCATE PREPARE v28_stmt;

-- 便于按课程库查历史开课
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND INDEX_NAME = 'idx_courses_definition');
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE courses ADD INDEX idx_courses_definition (definition_id)',
    'SELECT 1');
PREPARE v28_stmt FROM @ddl;
EXECUTE v28_stmt;
DEALLOCATE PREPARE v28_stmt;

-- --------------------------------------------------------------------
-- 5. 回填自检（应全部为 0）
-- --------------------------------------------------------------------
-- SELECT COUNT(*) FROM courses WHERE definition_id IS NULL;
-- SELECT COUNT(*) FROM courses WHERE planned_sections < 1;
