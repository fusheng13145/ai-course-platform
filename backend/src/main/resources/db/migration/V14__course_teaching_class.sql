-- ====================================================================
-- V14：教学班与主讲归属（B-12 / P-08 · WP4）
-- --------------------------------------------------------------------
-- 目标（拆包方案 WP4）：
--   ① 按 P0-1 决策（docs/组织映射清单-2026-11-06.md §12）建立真实组织数据：
--      colleges 3（CS 计算机学院 / EE 电子信息学院 / MATH 数学学院）、majors 8、
--      admin_classes 24、semesters 2；回填 users.college_id / major_id / admin_class_id；
--   ② courses.college_id 按课程 category 判定（D3，category 优先于教师学院）；
--      courses.semester 按 D1 规范化 30 门（空学期 7 门保持 NULL，D2 豁免）；
--   ③ 建 teaching_classes / teaching_class_admin_classes（一个教学班一个主讲）；
--      每门存量课程建 1 个默认教学班：ACTIVE 课程 → ACTIVE，INACTIVE 课程 → DRAFT（D8）；
--   ④ course_enrollments 加 teaching_class_id / source / source_admin_class_id，
--      存量 512 条全部回填默认教学班，source='LEGACY'（D9，不过滤审批状态）。
--
-- expand–contract 口径：
--   只做 expand。新列可空；NOT NULL 收紧、旧列清理、课程编号按学期唯一留 V17（WP10）。
--   不修改 classrooms / users.class_id 旧列。
--
-- 不得静默填默认值（P0-1 §12.3）：
--   · courses.semester 为空的 7 门（id 1–6、4060）不在 UPDATE 白名单内，保持 NULL；
--   · 学生 2151（major 为空）不进入 major 回填，major_id 保持 NULL；
--   · courses.category 为空时 CASE 返回 NULL，迁移末尾在验收 SQL 中单独核对；
--   · 班主任不做改派，admin_classes.advisor_teacher_id 原样保留（跨院 7 班仅在文档留痕）。
--
-- 可重复执行说明：
--   MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，加列/加索引均用 information_schema
--   判存在 + PREPARE 动态 SQL；CREATE TABLE 用 IF NOT EXISTS；INSERT 用 NOT EXISTS 守卫；
--   回填 UPDATE 只处理「目标列为空」或「白名单源值」，二次执行不改变计数。
--
-- 回滚说明（Flyway 只前向，回退须手工补偿）：
--   ALTER TABLE course_enrollments DROP INDEX idx_enrollments_class_status;
--   ALTER TABLE course_enrollments DROP COLUMN source_admin_class_id, DROP COLUMN source,
--                                  DROP COLUMN teaching_class_id;
--   DROP TABLE IF EXISTS teaching_class_admin_classes;
--   DROP TABLE IF EXISTS teaching_classes;
--   组织数据回填（colleges/majors/admin_classes/semesters 与 users/courses 列）按需逐项补偿；
--   回滚前必须先备份。
-- ====================================================================

-- --------------------------------------------------------------------
-- 1. 真实学院 / 专业 / 行政班 / 学期（P0-1 D5、D10）
--    V12.1 的两个演示学院按 D10 原地改名复用，避免 (school_id, code) 唯一键冲突。
-- --------------------------------------------------------------------

UPDATE colleges SET name = '计算机学院'
WHERE code = 'CS' AND name <> '计算机学院';

UPDATE colleges SET name = '电子信息学院'
WHERE code = 'EE' AND name <> '电子信息学院';

INSERT INTO colleges (school_id, code, name, status)
SELECT s.id, 'CS', '计算机学院', 'ACTIVE'
FROM schools s
WHERE s.code = 'DEFAULT'
  AND NOT EXISTS (SELECT 1 FROM colleges c WHERE c.school_id = s.id AND c.code = 'CS');

INSERT INTO colleges (school_id, code, name, status)
SELECT s.id, 'EE', '电子信息学院', 'ACTIVE'
FROM schools s
WHERE s.code = 'DEFAULT'
  AND NOT EXISTS (SELECT 1 FROM colleges c WHERE c.school_id = s.id AND c.code = 'EE');

INSERT INTO colleges (school_id, code, name, status)
SELECT s.id, 'MATH', '数学学院', 'ACTIVE'
FROM schools s
WHERE s.code = 'DEFAULT'
  AND NOT EXISTS (SELECT 1 FROM colleges c WHERE c.school_id = s.id AND c.code = 'MATH');

INSERT INTO majors (college_id, code, name, status)
SELECT c.id, v.code, v.name, 'ACTIVE'
FROM colleges c
JOIN (
    SELECT 'CS' AS college_code, 'CS' AS code, '计算机科学' AS name
    UNION ALL SELECT 'CS', 'AI', '人工智能'
    UNION ALL SELECT 'CS', 'SE', '软件工程'
    UNION ALL SELECT 'CS', 'DS', '数据科学与大数据技术'
    UNION ALL SELECT 'CS', 'NE', '网络工程'
    UNION ALL SELECT 'CS', 'IS', '信息安全'
    UNION ALL SELECT 'EE', 'EE', '电子信息工程'
    UNION ALL SELECT 'MATH', 'MATH', '数学与应用数学'
) v ON v.college_code = c.code
WHERE NOT EXISTS (
    SELECT 1 FROM majors m WHERE m.college_id = c.id AND m.code = v.code
);

-- 行政班：以 classrooms 为事实源（course_id 全为空，见清单 §4）。
-- grade_year 按 D4：3001–3012 → 2025，3013–3020 → 2024，id 1–4 → 编码年份 2024。
INSERT INTO admin_classes (school_id, college_id, major_id, name, code, grade_year,
                           advisor_teacher_id, student_count, max_students, status)
SELECT s.id,
       m.college_id,
       m.id,
       c.name,
       c.code,
       CASE
           WHEN c.id BETWEEN 3001 AND 3012 THEN '2025'
           WHEN c.id BETWEEN 3013 AND 3020 THEN '2024'
           ELSE '2024'
       END,
       c.advisor_id,
       COALESCE(c.student_count, 0),
       c.max_students,
       CASE WHEN c.is_active = 0 THEN 'ARCHIVED' ELSE 'ACTIVE' END
FROM classrooms c
JOIN schools s ON s.code = 'DEFAULT'
JOIN majors m ON m.name = CASE c.major WHEN '数学' THEN '数学与应用数学' ELSE c.major END
WHERE NOT EXISTS (
    SELECT 1 FROM admin_classes ac WHERE ac.school_id = s.id AND ac.code = c.code
);

-- 学期：D1 的两个规范名（空学期 7 门不写入，见 D2）
INSERT INTO semesters (name, start_date, end_date, status)
SELECT '2025-2026-2', '2026-02-23', '2026-07-05', 'ACTIVE' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM semesters WHERE name = '2025-2026-2');

INSERT INTO semesters (name, start_date, end_date, status)
SELECT '2026-2027-1', '2026-08-31', '2027-01-17', 'ACTIVE' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM semesters WHERE name = '2026-2027-1');

-- --------------------------------------------------------------------
-- 2. users 组织回填（教师按 department、学生按 major、行政班按 class_id）
--    学生 2151 的 major 为空 → 不进入 major 回填，major_id 保持 NULL（D7）。
-- --------------------------------------------------------------------

UPDATE users
SET school_id = (SELECT id FROM schools WHERE code = 'DEFAULT' LIMIT 1)
WHERE school_id IS NULL;

UPDATE users
SET college_id = CASE department
        WHEN '计算机学院' THEN (SELECT id FROM colleges WHERE code = 'CS' LIMIT 1)
        WHEN '电子信息学院' THEN (SELECT id FROM colleges WHERE code = 'EE' LIMIT 1)
        WHEN '数学学院' THEN (SELECT id FROM colleges WHERE code = 'MATH' LIMIT 1)
    END
WHERE role = 'TEACHER'
  AND college_id IS NULL
  AND department IN ('计算机学院', '电子信息学院', '数学学院');

UPDATE users u
JOIN majors m ON m.name = u.major
SET u.major_id = m.id,
    u.college_id = m.college_id
WHERE u.role = 'STUDENT'
  AND u.major IS NOT NULL
  AND u.major <> ''
  AND u.major_id IS NULL;

UPDATE users u
JOIN classrooms c ON c.id = u.class_id
JOIN admin_classes ac ON ac.code = c.code
JOIN schools s ON s.id = ac.school_id AND s.code = 'DEFAULT'
SET u.admin_class_id = ac.id
WHERE u.class_id IS NOT NULL
  AND u.admin_class_id IS NULL;

-- --------------------------------------------------------------------
-- 3. courses：学院按 category 判定（D3，category 优先于教师学院）
--    学期白名单规范化（D1），空学期不在白名单内 → 保持 NULL（D2）
-- --------------------------------------------------------------------

UPDATE courses
SET college_id = CASE category
        WHEN '计算机' THEN (SELECT id FROM colleges WHERE code = 'CS' LIMIT 1)
        WHEN '数据科学' THEN (SELECT id FROM colleges WHERE code = 'CS' LIMIT 1)
        WHEN '数学' THEN (SELECT id FROM colleges WHERE code = 'MATH' LIMIT 1)
    END
WHERE category IN ('计算机', '数据科学', '数学');

UPDATE courses
SET semester = CASE semester
        WHEN '2026春' THEN '2025-2026-2'
        WHEN '2026秋' THEN '2026-2027-1'
    END
WHERE semester IN ('2026春', '2026秋');

-- --------------------------------------------------------------------
-- 4. 教学班表与教学班—行政班来源表
-- --------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS teaching_classes (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '教学班ID，主键自增',
  course_id BIGINT NOT NULL COMMENT '所属课程ID，关联courses表',
  name VARCHAR(120) NOT NULL COMMENT '教学班名称，如：Python程序设计 默认教学班',
  code VARCHAR(50) NULL COMMENT '教学班编号（可选）',
  lead_teacher_id BIGINT NOT NULL COMMENT '唯一主讲教师，关联users表',
  capacity INT NULL COMMENT '容量；NULL 表示不限制',
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/CLOSED',
  created_by BIGINT NULL COMMENT '创建人ID，关联users表',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_teaching_classes_course_name (course_id, name) COMMENT '课程-教学班名称唯一索引',
  KEY idx_teaching_classes_course (course_id) COMMENT '课程索引',
  KEY idx_teaching_classes_lead (lead_teacher_id) COMMENT '主讲教师索引',
  KEY idx_teaching_classes_status (status) COMMENT '状态索引',
  CONSTRAINT fk_teaching_classes_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_teaching_classes_lead FOREIGN KEY (lead_teacher_id) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_teaching_classes_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教学班：课程下的开课单元，一个主讲';

CREATE TABLE IF NOT EXISTS teaching_class_admin_classes (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  teaching_class_id BIGINT NOT NULL COMMENT '教学班ID，关联teaching_classes表',
  admin_class_id BIGINT NOT NULL COMMENT '来源行政班ID，关联admin_classes表',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REMOVED',
  imported_by BIGINT NULL COMMENT '导入人ID，关联users表',
  imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tc_admin_class (teaching_class_id, admin_class_id) COMMENT '教学班-行政班唯一索引',
  KEY idx_tc_admin_class_admin (admin_class_id) COMMENT '行政班索引',
  CONSTRAINT fk_tc_admin_class_tc FOREIGN KEY (teaching_class_id) REFERENCES teaching_classes(id) ON DELETE CASCADE,
  CONSTRAINT fk_tc_admin_class_admin FOREIGN KEY (admin_class_id) REFERENCES admin_classes(id) ON DELETE RESTRICT,
  CONSTRAINT fk_tc_admin_class_imported_by FOREIGN KEY (imported_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教学班—行政班来源关系；一个教学班可来自多个行政班';

-- 每门存量课程恰 1 个默认教学班（9A）：
--   lead_teacher_id = courses.teacher_id；name = concat(name,' 默认教学班')；
--   课程 ACTIVE → ACTIVE，INACTIVE → DRAFT（D8，停用课不产生可报名入口）。
INSERT INTO teaching_classes (course_id, name, code, lead_teacher_id, capacity,
                              status, created_by, created_at, updated_at)
SELECT c.id,
       CONCAT(c.name, ' 默认教学班'),
       CONCAT(c.code, '-DEFAULT'),
       c.teacher_id,
       c.max_students,
       CASE WHEN c.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'DRAFT' END,
       c.teacher_id,
       NOW(),
       NOW()
FROM courses c
WHERE c.teacher_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM teaching_classes tc
      WHERE tc.course_id = c.id AND tc.name = CONCAT(c.name, ' 默认教学班')
  );

-- --------------------------------------------------------------------
-- 5. course_enrollments 加列与存量回填（D9：全部回填，不过滤审批状态）
-- --------------------------------------------------------------------

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_enrollments' AND COLUMN_NAME = 'teaching_class_id');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE course_enrollments ADD COLUMN teaching_class_id BIGINT NULL COMMENT ''教学班ID，关联teaching_classes表''', 'SELECT 1');
PREPARE v14_stmt FROM @ddl;
EXECUTE v14_stmt;
DEALLOCATE PREPARE v14_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_enrollments' AND COLUMN_NAME = 'source');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE course_enrollments ADD COLUMN source VARCHAR(32) NULL COMMENT ''来源：ADMIN_CLASS_IMPORT/SELF_ENROLL/LEGACY''', 'SELECT 1');
PREPARE v14_stmt FROM @ddl;
EXECUTE v14_stmt;
DEALLOCATE PREPARE v14_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_enrollments' AND COLUMN_NAME = 'source_admin_class_id');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE course_enrollments ADD COLUMN source_admin_class_id BIGINT NULL COMMENT ''来源行政班ID，关联admin_classes表''', 'SELECT 1');
PREPARE v14_stmt FROM @ddl;
EXECUTE v14_stmt;
DEALLOCATE PREPARE v14_stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_enrollments' AND INDEX_NAME = 'idx_enrollments_class_status');
SET @ddl := IF(@idx_exists = 0, 'ALTER TABLE course_enrollments ADD INDEX idx_enrollments_class_status (teaching_class_id, approval_status, is_active)', 'SELECT 1');
PREPARE v14_stmt FROM @ddl;
EXECUTE v14_stmt;
DEALLOCATE PREPARE v14_stmt;

UPDATE course_enrollments ce
JOIN courses c ON c.id = ce.course_id
JOIN teaching_classes tc ON tc.course_id = c.id
                        AND tc.name = CONCAT(c.name, ' 默认教学班')
SET ce.teaching_class_id = tc.id,
    ce.source = 'LEGACY'
WHERE ce.teaching_class_id IS NULL;
