-- ====================================================================
-- V12.1：院系管理员演示种子（B-12 / P-08 · WP2）
-- --------------------------------------------------------------------
-- 定位：只进 db/seed-demo，不随生产库执行（生产 profile 仅加载 db/migration）。
-- 版本号用 12.1：必须晚于 V12（依赖 colleges/users 新列），且不占用 V13–V16 预留号。
--
-- 内容：
--   ① 两个演示学院（CS / EE），挂在 V12 的单校根 schools.code='DEFAULT'；
--   ② 两个院系管理员演示账号：ca_cs（CS 学院）、ca_ee（EE 学院），
--      role=ADMIN、admin_scope=COLLEGE、college_id 指向对应学院；
--      口令复用 V5 演示哈希（明文 123456），仅供本地演示。
--
-- 幂等：全部 INSERT ... SELECT + NOT EXISTS 守卫，手工重放不会失败或重复。
-- 说明：本脚本不重映射既有演示教师/学生（它们的 college_id 仍为 NULL）；
--       需要演示「本院可见」时，可在本地把 ts01..ts04 等账号的 college_id 指到 CS。
-- ====================================================================

-- ── 1. 演示学院 ──
INSERT INTO colleges (school_id, code, name, status)
SELECT s.id, 'CS', '计算机学院（演示）', 'ACTIVE'
FROM schools s
WHERE s.code = 'DEFAULT'
  AND NOT EXISTS (
      SELECT 1 FROM colleges c WHERE c.school_id = s.id AND c.code = 'CS'
  );

INSERT INTO colleges (school_id, code, name, status)
SELECT s.id, 'EE', '电子信息学院（演示）', 'ACTIVE'
FROM schools s
WHERE s.code = 'DEFAULT'
  AND NOT EXISTS (
      SELECT 1 FROM colleges c WHERE c.school_id = s.id AND c.code = 'EE'
  );

-- ── 2. 院系管理员演示账号（明文口令 123456） ──
INSERT INTO users (email, username, password, full_name, role, is_active, is_verified,
                   school_id, college_id, admin_scope, created_at)
SELECT 'ca_cs@aicourse.com', 'ca_cs',
       '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq',
       '计算机学院管理员', 'ADMIN', 1, 1,
       (SELECT id FROM schools WHERE code = 'DEFAULT' LIMIT 1),
       (SELECT id FROM colleges WHERE code = 'CS' LIMIT 1),
       'COLLEGE', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'ca_cs');

INSERT INTO users (email, username, password, full_name, role, is_active, is_verified,
                   school_id, college_id, admin_scope, created_at)
SELECT 'ca_ee@aicourse.com', 'ca_ee',
       '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq',
       '电子信息学院管理员', 'ADMIN', 1, 1,
       (SELECT id FROM schools WHERE code = 'DEFAULT' LIMIT 1),
       (SELECT id FROM colleges WHERE code = 'EE' LIMIT 1),
       'COLLEGE', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'ca_ee');
