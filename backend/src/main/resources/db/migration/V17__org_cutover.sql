-- ====================================================================
-- V17：组织改造收口（B-12 / P-08 · WP10）
-- --------------------------------------------------------------------
-- 目标（拆包方案 WP10 验收）：
--   ① 4A 学期内唯一课程编号：courses 新增 semester_id（回填后收紧 NOT NULL），
--      唯一键由 uk_courses_code（全库唯一）切换为 uk_courses_semester_code
--      (semester_id, code)。回填规则：
--        a. course.created_at 落在某个学期的 [start_date, end_date] 内 → 该学期；
--        b. 否则 → 当前 ACTIVE 学期中开始日期最早的一个；
--        c. 仍无（库中无任何学期）→ 保持 NULL，此时唯一键对其不约束（MySQL
--           唯一索引不约束 NULL），待首个学期建立后由应用侧补归属。
--      收紧前校验：回填 UPDATE 只对能解析到学期的行生效，故 NOT NULL 收紧
--      与回填同条件执行（information_schema 判残留 NULL，为 0 才收紧）。
--   ② 成员收紧：course_enrollments.teaching_class_id → NOT NULL。依据：
--      V14 已回填存量、WP5 起选课链路在无班可归属时直接 409（CourseDefaultTeachingClassSync
--      保证每课恰有 1 个默认班），写路径不存在 NULL 形态。
--   ③ 刻意不收紧（留待运行时验证后再收，见 docs/后端待办项.md）：
--      users.admin_class_id（自助注册学生尚无行政班）、courses.college_id
--      （历史教师可能无学院归属，建课链路未强制）。
--
-- 可重复执行说明：加列/改键均以 information_schema 判存在；回填以 IS NULL 为界。
-- 回滚说明（Flyway 只前向，回退须手工补偿）：
--   ALTER TABLE courses DROP INDEX uk_courses_semester_code;
--   ALTER TABLE courses ADD UNIQUE KEY uk_courses_code (code);
--   ALTER TABLE courses MODIFY semester_id BIGINT NULL;
--   ALTER TABLE course_enrollments MODIFY teaching_class_id BIGINT NULL;
-- ====================================================================

-- --------------------------------------------------------------------
-- 1. courses.semester_id：加列 → 回填 → 收紧
-- --------------------------------------------------------------------

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND COLUMN_NAME = 'semester_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE courses ADD COLUMN semester_id BIGINT NULL COMMENT ''所属学期（4A）：创建时解析 ACTIVE 学期，编号学期内唯一''',
    'SELECT 1');
PREPARE v17_stmt FROM @ddl;
EXECUTE v17_stmt;
DEALLOCATE PREPARE v17_stmt;

-- 回填 a：按创建日期落进学期区间
UPDATE courses c
JOIN semesters s ON c.created_at >= CONCAT(s.start_date, ' 00:00:00')
                AND c.created_at <= CONCAT(s.end_date, ' 23:59:59')
SET c.semester_id = s.id
WHERE c.semester_id IS NULL;

-- 回填 b：其余归入最早的 ACTIVE 学期
UPDATE courses c
JOIN (SELECT MIN(id) AS id FROM semesters WHERE status = 'ACTIVE') s ON TRUE
SET c.semester_id = s.id
WHERE c.semester_id IS NULL;

-- 收紧：仅当残留 NULL 为 0（回填 c 的空库形态除外）
SET @null_left := (SELECT COUNT(*) FROM courses WHERE semester_id IS NULL);
SET @ddl := IF(@null_left = 0,
    'ALTER TABLE courses MODIFY semester_id BIGINT NOT NULL COMMENT ''所属学期（4A）：创建时解析 ACTIVE 学期，编号学期内唯一''',
    'SELECT 1');
PREPARE v17_stmt FROM @ddl;
EXECUTE v17_stmt;
DEALLOCATE PREPARE v17_stmt;

-- --------------------------------------------------------------------
-- 2. 4A 唯一键切换：uk_courses_code → (semester_id, code)
--    切换前提：回填后同 code 必属同学期（存量 code 全库唯一，故学期内必然唯一）
-- --------------------------------------------------------------------

SET @old_key := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND INDEX_NAME = 'uk_courses_code');
SET @ddl := IF(@old_key > 0,
    'ALTER TABLE courses DROP INDEX uk_courses_code',
    'SELECT 1');
PREPARE v17_stmt FROM @ddl;
EXECUTE v17_stmt;
DEALLOCATE PREPARE v17_stmt;

SET @new_key := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND INDEX_NAME = 'uk_courses_semester_code');
SET @ddl := IF(@new_key = 0,
    'ALTER TABLE courses ADD UNIQUE KEY uk_courses_semester_code (semester_id, code) COMMENT ''学期内课程编号唯一（4A，V17）''',
    'SELECT 1');
PREPARE v17_stmt FROM @ddl;
EXECUTE v17_stmt;
DEALLOCATE PREPARE v17_stmt;

-- --------------------------------------------------------------------
-- 3. 成员收紧：course_enrollments.teaching_class_id → NOT NULL
-- --------------------------------------------------------------------

SET @null_left := (SELECT COUNT(*) FROM course_enrollments WHERE teaching_class_id IS NULL);
SET @col_nullable := (SELECT IS_NULLABLE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_enrollments' AND COLUMN_NAME = 'teaching_class_id');
SET @ddl := IF(@null_left = 0 AND @col_nullable = 'YES',
    'ALTER TABLE course_enrollments MODIFY teaching_class_id BIGINT NOT NULL COMMENT ''选课归属教学班（WP5 起选课链路必解析；V17 收紧）''',
    'SELECT 1');
PREPARE v17_stmt FROM @ddl;
EXECUTE v17_stmt;
DEALLOCATE PREPARE v17_stmt;

-- --------------------------------------------------------------------
-- 4. 回填自检（OrgMigrationBackfillTest 以同一口径断言 0）
-- --------------------------------------------------------------------
-- SELECT COUNT(*) FROM users.users              WHERE admin_class_id IS NULL;   -- 口径：见拆包方案（自助注册除外）
-- SELECT COUNT(*) FROM courses                  WHERE semester_id IS NULL;
-- SELECT COUNT(*) FROM courses                  WHERE college_id IS NULL;
-- SELECT COUNT(*) FROM course_enrollments       WHERE teaching_class_id IS NULL;
-- SELECT COUNT(*) FROM submissions              WHERE teaching_class_id IS NULL;
-- SELECT COUNT(*) FROM exam_sessions            WHERE teaching_class_id IS NULL;
-- SELECT COUNT(*) FROM task_completions         WHERE teaching_class_id IS NULL;
-- SELECT COUNT(*) FROM grade_items              WHERE teaching_class_id IS NULL;
