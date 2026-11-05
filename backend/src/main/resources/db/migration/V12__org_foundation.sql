-- ====================================================================
-- V12：组织与管理员作用域底座（B-12 / P-08 · WP1）
-- --------------------------------------------------------------------
-- 目标（交接文档 §3.1 / §3.2 / §4.1，决策 1A）：
--   ① 建组织四表：schools → colleges → majors → admin_classes（单校部署，不引入 tenant_id）
--   ② users 加组织列与 admin_scope（SCHOOL/COLLEGE，仅 ADMIN 使用；教师/学生保持 NULL）
--   ③ role_permissions 加 scope（ALL/SCHOOL/COLLEGE），旧行回填 ALL，
--      唯一键由 (role, permission_key) 改为 (role, scope, permission_key)
--
-- expand–contract 口径：
--   本迁移只做 expand：新列一律可空或带默认值，不删除既有 classrooms / users.class_id。
--   NOT NULL 收紧、外键补全与旧列清理留给 V16（WP10，单独发布窗口）。
--   本迁移不映射任何旧自由文本，不做「计算机学院/计科 2301」式样例值兜底（决策 9A）。
--
-- 可重复执行说明：
--   MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，故所有加列/加索引/换唯一键都用
--   information_schema 判存在 + PREPARE 动态 SQL；对象已存在时执行 SELECT 1 空操作。
--   CREATE TABLE 使用 IF NOT EXISTS，种子插入使用 NOT EXISTS 守卫。
--   Flyway 本身按版本只执行一次；上述守卫保证手工重放同一脚本也不会报错。
--
-- 回滚说明（Flyway 只前向，回退必须手工执行下列补偿 SQL 或另建补偿迁移）：
--   ① 恢复 role_permissions 旧唯一键并删除 scope 列：
--      ALTER TABLE role_permissions DROP INDEX uk_role_permissions_role_scope_key;
--      ALTER TABLE role_permissions ADD UNIQUE KEY uk_role_permissions_role_key (role, permission_key);
--      ALTER TABLE role_permissions DROP COLUMN scope;
--   ② 删除 users 新增索引与列：
--      ALTER TABLE users DROP INDEX idx_users_admin_scope;
--      ALTER TABLE users DROP INDEX idx_users_admin_class;
--      ALTER TABLE users DROP INDEX idx_users_college;
--      ALTER TABLE users DROP COLUMN admin_scope, DROP COLUMN admin_class_id,
--                        DROP COLUMN major_id, DROP COLUMN college_id, DROP COLUMN school_id;
--   ③ 删除组织四表（先子后父，注意外键）：
--      DROP TABLE IF EXISTS admin_classes;
--      DROP TABLE IF EXISTS majors;
--      DROP TABLE IF EXISTS colleges;
--      DROP TABLE IF EXISTS schools;
--   注意：回滚会丢失 V12 之后写入的组织数据，必须先做库备份与恢复演练。
-- ====================================================================

-- --------------------------------------------------------------------
-- 1. 组织四表（单校 → 学院 → 专业 → 行政班）
-- --------------------------------------------------------------------

-- 学校根：单校部署时恰一行
CREATE TABLE IF NOT EXISTS schools (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '学校ID，主键自增',
  code VARCHAR(32) NOT NULL COMMENT '学校编码',
  name VARCHAR(128) NOT NULL COMMENT '学校名称',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用，ARCHIVED-归档',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_schools_code (code) COMMENT '学校编码唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学校根（单校部署，一行）';

-- 学院：课程审批与院系管理员的数据边界
CREATE TABLE IF NOT EXISTS colleges (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '学院ID，主键自增',
  school_id BIGINT NOT NULL COMMENT '所属学校ID，关联schools表',
  code VARCHAR(32) NOT NULL COMMENT '学院编码（校内唯一）',
  name VARCHAR(128) NOT NULL COMMENT '学院名称',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用，ARCHIVED-归档',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_colleges_school_code (school_id, code) COMMENT '学校-学院编码唯一索引',
  KEY idx_colleges_school (school_id) COMMENT '学校索引',
  CONSTRAINT fk_colleges_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学院';

-- 专业：学院下的专业归属
CREATE TABLE IF NOT EXISTS majors (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '专业ID，主键自增',
  college_id BIGINT NOT NULL COMMENT '所属学院ID，关联colleges表',
  code VARCHAR(32) NOT NULL COMMENT '专业编码（院内唯一）',
  name VARCHAR(128) NOT NULL COMMENT '专业名称',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用，ARCHIVED-归档',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_majors_college_code (college_id, code) COMMENT '学院-专业编码唯一索引',
  KEY idx_majors_college (college_id) COMMENT '学院索引',
  CONSTRAINT fk_majors_college FOREIGN KEY (college_id) REFERENCES colleges(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专业';

-- 行政班：学生学籍归属与班主任工作对象，不绑定课程
CREATE TABLE IF NOT EXISTS admin_classes (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '行政班ID，主键自增',
  school_id BIGINT NOT NULL COMMENT '所属学校ID，关联schools表',
  college_id BIGINT NOT NULL COMMENT '所属学院ID，关联colleges表',
  major_id BIGINT NOT NULL COMMENT '所属专业ID，关联majors表',
  name VARCHAR(100) NOT NULL COMMENT '行政班名称，如：计科 2301',
  code VARCHAR(50) NOT NULL COMMENT '行政班编号',
  grade_year VARCHAR(16) NULL COMMENT '年级，如 2023',
  advisor_teacher_id BIGINT NULL COMMENT '班主任（教师职责，不是登录角色），关联users表',
  student_count INT NOT NULL DEFAULT 0 COMMENT '当前学生人数',
  max_students INT NULL COMMENT '最大容纳人数',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-启用，ARCHIVED-归档（归档不删学生历史）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_classes_school_code (school_id, code) COMMENT '学校-行政班编号唯一索引',
  KEY idx_admin_classes_college (college_id) COMMENT '学院索引',
  KEY idx_admin_classes_major (major_id) COMMENT '专业索引',
  KEY idx_admin_classes_advisor (advisor_teacher_id) COMMENT '班主任索引',
  CONSTRAINT fk_admin_classes_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE RESTRICT,
  CONSTRAINT fk_admin_classes_college FOREIGN KEY (college_id) REFERENCES colleges(id) ON DELETE RESTRICT,
  CONSTRAINT fk_admin_classes_major FOREIGN KEY (major_id) REFERENCES majors(id) ON DELETE RESTRICT,
  CONSTRAINT fk_admin_classes_advisor FOREIGN KEY (advisor_teacher_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行政班：学生归属与班主任工作对象，不绑定课程';

-- --------------------------------------------------------------------
-- 2. users 加组织列（全部可空，V16 才收紧；过渡期保留 class_id）
-- --------------------------------------------------------------------

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'school_id');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE users ADD COLUMN school_id BIGINT NULL COMMENT ''学校ID''', 'SELECT 1');
PREPARE v12_stmt FROM @ddl;
EXECUTE v12_stmt;
DEALLOCATE PREPARE v12_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'college_id');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE users ADD COLUMN college_id BIGINT NULL COMMENT ''学院ID''', 'SELECT 1');
PREPARE v12_stmt FROM @ddl;
EXECUTE v12_stmt;
DEALLOCATE PREPARE v12_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'major_id');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE users ADD COLUMN major_id BIGINT NULL COMMENT ''专业ID''', 'SELECT 1');
PREPARE v12_stmt FROM @ddl;
EXECUTE v12_stmt;
DEALLOCATE PREPARE v12_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'admin_class_id');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE users ADD COLUMN admin_class_id BIGINT NULL COMMENT ''行政班ID（目标列，过渡期保留 class_id）''', 'SELECT 1');
PREPARE v12_stmt FROM @ddl;
EXECUTE v12_stmt;
DEALLOCATE PREPARE v12_stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'admin_scope');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE users ADD COLUMN admin_scope VARCHAR(16) NULL COMMENT ''SCHOOL/COLLEGE；仅 ADMIN 使用''', 'SELECT 1');
PREPARE v12_stmt FROM @ddl;
EXECUTE v12_stmt;
DEALLOCATE PREPARE v12_stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_users_college');
SET @ddl := IF(@idx_exists = 0, 'ALTER TABLE users ADD INDEX idx_users_college (college_id)', 'SELECT 1');
PREPARE v12_stmt FROM @ddl;
EXECUTE v12_stmt;
DEALLOCATE PREPARE v12_stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_users_admin_class');
SET @ddl := IF(@idx_exists = 0, 'ALTER TABLE users ADD INDEX idx_users_admin_class (admin_class_id)', 'SELECT 1');
PREPARE v12_stmt FROM @ddl;
EXECUTE v12_stmt;
DEALLOCATE PREPARE v12_stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_users_admin_scope');
SET @ddl := IF(@idx_exists = 0, 'ALTER TABLE users ADD INDEX idx_users_admin_scope (admin_scope, college_id)', 'SELECT 1');
PREPARE v12_stmt FROM @ddl;
EXECUTE v12_stmt;
DEALLOCATE PREPARE v12_stmt;

-- --------------------------------------------------------------------
-- 3. role_permissions 加 scope 并换唯一键
--    旧行回填 ALL 后，非管理员仍只读 ALL、管理员读 ALL ∪ 自身 scope，
--    在尚无 SCHOOL/COLLEGE 权限项时结果与现状等价。
-- --------------------------------------------------------------------

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'role_permissions' AND COLUMN_NAME = 'scope');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE role_permissions ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT ''ALL'' COMMENT ''ALL/SCHOOL/COLLEGE''', 'SELECT 1');
PREPARE v12_stmt FROM @ddl;
EXECUTE v12_stmt;
DEALLOCATE PREPARE v12_stmt;

UPDATE role_permissions SET scope = 'ALL' WHERE scope IS NULL;

SET @old_uk_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'role_permissions'
      AND INDEX_NAME = 'uk_role_permissions_role_key' AND SEQ_IN_INDEX = 1);
SET @ddl := IF(@old_uk_exists > 0, 'ALTER TABLE role_permissions DROP INDEX uk_role_permissions_role_key', 'SELECT 1');
PREPARE v12_stmt FROM @ddl;
EXECUTE v12_stmt;
DEALLOCATE PREPARE v12_stmt;

SET @new_uk_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'role_permissions'
      AND INDEX_NAME = 'uk_role_permissions_role_scope_key' AND SEQ_IN_INDEX = 1);
SET @ddl := IF(@new_uk_exists = 0, 'ALTER TABLE role_permissions ADD UNIQUE KEY uk_role_permissions_role_scope_key (role, scope, permission_key)', 'SELECT 1');
PREPARE v12_stmt FROM @ddl;
EXECUTE v12_stmt;
DEALLOCATE PREPARE v12_stmt;

-- --------------------------------------------------------------------
-- 4. 种子与回填
--    单校根仅作组织容器，不代表任何旧自由文本映射结果（9A）。
-- --------------------------------------------------------------------

INSERT INTO schools (code, name, status)
SELECT 'DEFAULT', '默认学校', 'ACTIVE' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM schools WHERE code = 'DEFAULT');

UPDATE users
SET admin_scope = 'SCHOOL',
    school_id = (SELECT id FROM schools WHERE code = 'DEFAULT' LIMIT 1)
WHERE role = 'ADMIN' AND admin_scope IS NULL;
