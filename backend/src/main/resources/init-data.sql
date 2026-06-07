-- AI智能课程平台 - 初始数据
-- 所有密码明文为 123456，BCrypt哈希值为 $2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq

USE ai_course_platform;

SET NAMES utf8mb4;

-- ============================================================
-- 1. 用户数据
-- ============================================================
INSERT INTO users (id, email, username, password, full_name, role, is_active, is_verified, phone, student_id, major, grade, department, title) VALUES
(1, 'admin@aicourse.com', 'admin', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '系统管理员', 'ADMIN', 1, 1, '13800000001', NULL, NULL, NULL, NULL, NULL),
(2, 'teacher@aicourse.com', 'teacher', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '张教授', 'TEACHER', 1, 1, '13800000002', NULL, NULL, NULL, '计算机学院', '教授'),
(3, 'teacher2@aicourse.com', 'teacher2', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '李副教授', 'TEACHER', 1, 1, '13800000003', NULL, NULL, NULL, '数学学院', '副教授'),
(4, 'student@aicourse.com', 'student', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '王同学', 'STUDENT', 1, 1, '13800000004', '2024001', '计算机科学', '大一', NULL, NULL),
(5, 'student2@aicourse.com', 'student2', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '赵同学', 'STUDENT', 1, 1, '13800000005', '2024002', '计算机科学', '大一', NULL, NULL),
(6, 'student3@aicourse.com', 'student3', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '刘同学', 'STUDENT', 1, 1, '13800000006', '2024003', '软件工程', '大一', NULL, NULL),
(7, 'student4@aicourse.com', 'student4', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '陈同学', 'STUDENT', 1, 1, '13800000007', '2024004', '软件工程', '大一', NULL, NULL),
(8, 'student5@aicourse.com', 'student5', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '杨同学', 'STUDENT', 1, 1, '13800000008', '2024005', '人工智能', '大一', NULL, NULL);

-- ============================================================
-- 2. 班级数据
-- ============================================================
INSERT INTO classrooms (id, name, code, grade_level, major, advisor_id, student_count) VALUES
(1, '计算机1班', 'CS2024-1', '大一', '计算机科学', 2, 2),
(2, '软件1班', 'SE2024-1', '大一', '软件工程', 2, 2),
(3, 'AI班', 'AI2024-1', '大一', '人工智能', 2, 1),
(4, '数学1班', 'MATH2024-1', '大一', '数学', 3, 0);

-- 更新学生的class_id
UPDATE users SET class_id = 1 WHERE id IN (4, 5);
UPDATE users SET class_id = 2 WHERE id IN (6, 7);
UPDATE users SET class_id = 3 WHERE id = 8;

-- ============================================================
-- 3. 课程数据
-- ============================================================
INSERT INTO courses (id, name, code, description, credits, teacher_id, status, approval_status, enrollment_type, category, difficulty) VALUES
(1, 'Python程序设计', 'CS101', 'Python编程语言基础课程，适合零基础学生', 3.00, 2, 'active', 'APPROVED', 'FREE', '计算机', '入门'),
(2, '数据结构与算法', 'CS201', '数据结构与算法核心课程，涵盖常见数据结构和算法设计', 4.00, 2, 'active', 'APPROVED', 'APPROVAL', '计算机', '困难'),
(3, '机器学习基础', 'CS301', '机器学习入门课程，介绍基本概念和常用算法', 3.00, 2, 'active', 'APPROVED', 'APPROVAL', '计算机', '中等'),
(4, '高等数学A', 'MATH101', '高等数学基础课程，包含微积分、线性代数等内容', 5.00, 3, 'active', 'APPROVED', 'FREE', '数学', '中等'),
(5, 'Web前端开发', 'CS102', 'Web前端开发技术课程，涵盖HTML/CSS/JavaScript', 3.00, 2, 'active', 'APPROVED', 'FREE', '计算机', '中等'),
(6, '数据库原理与应用', 'CS202', '数据库系统原理及SQL实践课程', 3.00, 2, 'active', 'APPROVED', 'FREE', '计算机', '中等');

-- ============================================================
-- 4. 选课数据
-- ============================================================
INSERT INTO course_enrollments (student_id, course_id, approval_status) VALUES
-- student (id=4) 选了Python、数据结构、机器学习
(4, 1, 'APPROVED'),
(4, 2, 'APPROVED'),
(4, 3, 'APPROVED'),
-- student2 (id=5) 选了Python、数据结构
(5, 1, 'APPROVED'),
(5, 2, 'APPROVED'),
-- student3 (id=6) 选了机器学习、Web前端
(6, 3, 'APPROVED'),
(6, 5, 'APPROVED'),
-- student4 (id=7) 选了数据库、高等数学
(7, 6, 'APPROVED'),
(7, 4, 'APPROVED'),
-- student5 (id=8) 选了Python、机器学习
(8, 1, 'APPROVED'),
(8, 3, 'APPROVED');

-- ============================================================
-- 5. 角色权限数据
-- ============================================================
-- ADMIN角色权限
INSERT INTO role_permissions (role, permission_key, permission_name, permission_type, sort_order) VALUES
('ADMIN', 'user:manage', '用户管理', 'MENU', 1),
('ADMIN', 'course:manage', '课程管理', 'MENU', 2),
('ADMIN', 'course:approve', '课程审批', 'MENU', 3),
('ADMIN', 'exam:manage', '考试管理', 'MENU', 4),
('ADMIN', 'question:manage', '题库管理', 'MENU', 5),
('ADMIN', 'system:config', '系统配置', 'MENU', 6),
('ADMIN', 'system:log', '系统日志', 'MENU', 7),
('ADMIN', 'system:backup', '系统备份', 'MENU', 8),
('ADMIN', 'analytics:view', '数据分析', 'MENU', 9),
('ADMIN', 'notification:manage', '通知管理', 'MENU', 10),
('ADMIN', 'permission:manage', '权限管理', 'MENU', 11);

-- TEACHER角色权限
INSERT INTO role_permissions (role, permission_key, permission_name, permission_type, sort_order) VALUES
('TEACHER', 'course:own', '我的课程', 'MENU', 1),
('TEACHER', 'course:view', '课程查看', 'MENU', 2),
('TEACHER', 'exam:own', '我的考试', 'MENU', 3),
('TEACHER', 'question:own', '我的题库', 'MENU', 4),
('TEACHER', 'knowledge:manage', '知识点管理', 'MENU', 5),
('TEACHER', 'task:manage', '任务管理', 'MENU', 6),
('TEACHER', 'submission:grade', '作业批改', 'MENU', 7),
('TEACHER', 'analytics:view', '数据分析', 'MENU', 8),
('TEACHER', 'ai:use', 'AI功能', 'MENU', 9);

-- STUDENT角色权限
INSERT INTO role_permissions (role, permission_key, permission_name, permission_type, sort_order) VALUES
('STUDENT', 'course:view', '课程查看', 'MENU', 1),
('STUDENT', 'course:enroll', '课程选课', 'MENU', 2),
('STUDENT', 'exam:take', '参加考试', 'MENU', 3),
('STUDENT', 'task:submit', '提交作业', 'MENU', 4),
('STUDENT', 'learning:view', '学习记录', 'MENU', 5),
('STUDENT', 'wrong-question:own', '我的错题', 'MENU', 6),
('STUDENT', 'ai:use', 'AI功能', 'MENU', 7),
('STUDENT', 'notification:view', '通知查看', 'MENU', 8);

-- ============================================================
-- 6. 系统配置数据
-- ============================================================
INSERT INTO system_configs (config_key, config_value, config_group, config_type, description, is_public, sort_order) VALUES
('site_name', 'AI智能课程平台', 'basic', 'STRING', '站点名称', 1, 1),
('site_description', '基于AI的智能在线教育平台', 'basic', 'STRING', '站点描述', 1, 2),
('ai_model', 'deepseek-chat', 'ai', 'STRING', 'AI模型名称', 0, 1),
('ai_api_key', '', 'ai', 'STRING', 'AI API密钥', 0, 2),
('max_upload_size', '100', 'system', 'NUMBER', '最大上传文件大小(MB)', 0, 1);

-- ============================================================
-- 7. 通知数据 - 给每个学生创建欢迎通知
-- ============================================================
INSERT INTO notifications (user_id, title, content, category, type, is_read, priority) VALUES
(4, '欢迎加入AI智能课程平台', '王同学，欢迎加入AI智能课程平台！在这里你可以选课、学习、参加考试，体验AI辅助学习的乐趣。', 'system', 'SYSTEM', 0, 'normal'),
(5, '欢迎加入AI智能课程平台', '赵同学，欢迎加入AI智能课程平台！在这里你可以选课、学习、参加考试，体验AI辅助学习的乐趣。', 'system', 'SYSTEM', 0, 'normal'),
(6, '欢迎加入AI智能课程平台', '刘同学，欢迎加入AI智能课程平台！在这里你可以选课、学习、参加考试，体验AI辅助学习的乐趣。', 'system', 'SYSTEM', 0, 'normal'),
(7, '欢迎加入AI智能课程平台', '陈同学，欢迎加入AI智能课程平台！在这里你可以选课、学习、参加考试，体验AI辅助学习的乐趣。', 'system', 'SYSTEM', 0, 'normal'),
(8, '欢迎加入AI智能课程平台', '杨同学，欢迎加入AI智能课程平台！在这里你可以选课、学习、参加考试，体验AI辅助学习的乐趣。', 'system', 'SYSTEM', 0, 'normal');
