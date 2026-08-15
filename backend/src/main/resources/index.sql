-- ====================================================================
-- AI 智能课程平台 · 数据库初始化脚本（统一版）
-- ====================================================================
-- 用途：开发环境全量初始化（DROP + CREATE + SEED）
-- 警告：本脚本会删除所有已有数据，仅适用于全新部署！
-- 运行：mysql -u root -p < index.sql
-- 
-- 变更记录：
--   1. 添加 SET NAMES utf8mb4 修复管道模式下 GBK 编码导致的 INSERT 失败
--   2. 合并 db/ 目录下 7 个增量迁移脚本的变化到 CREATE TABLE 定义
--   3. 按外键依赖顺序重排全部 47 张建表语句
--   4. 移除冗余的 ALTER TABLE 增量迁移（已在 CREATE TABLE 中体现）
--   5. 统一 DROP 覆盖全部 47 张表
-- ====================================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS ai_course_platform
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE ai_course_platform;

-- ====================================================================
-- Phase 1: 删除所有已有表（逆依赖序）
-- ====================================================================
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS grade_items;
DROP TABLE IF EXISTS grade_policies;
DROP TABLE IF EXISTS proctor_events;
DROP TABLE IF EXISTS proctor_sessions;
DROP TABLE IF EXISTS discussion_post_likes;
DROP TABLE IF EXISTS discussion_posts;
DROP TABLE IF EXISTS discussion_topics;
DROP TABLE IF EXISTS vote_records;
DROP TABLE IF EXISTS votes;
DROP TABLE IF EXISTS sign_in_records;
DROP TABLE IF EXISTS sign_ins;
DROP TABLE IF EXISTS ai_chat_messages;
DROP TABLE IF EXISTS ai_chat_sessions;
DROP TABLE IF EXISTS operation_logs;
DROP TABLE IF EXISTS file_upload_sessions;
DROP TABLE IF EXISTS system_configs;
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS video_watch_sessions;
DROP TABLE IF EXISTS video_learning_records;
DROP TABLE IF EXISTS learning_records;
DROP TABLE IF EXISTS notes;
DROP TABLE IF EXISTS learning_path_nodes;
DROP TABLE IF EXISTS learning_paths;
DROP TABLE IF EXISTS wrong_questions;
DROP TABLE IF EXISTS chapter_knowledge_points;
DROP TABLE IF EXISTS knowledge_point_relations;
DROP TABLE IF EXISTS knowledge_points;
DROP TABLE IF EXISTS exam_sessions;
DROP TABLE IF EXISTS exam_questions;
DROP TABLE IF EXISTS questions;
DROP TABLE IF EXISTS exam_papers;
DROP TABLE IF EXISTS reminders;
DROP TABLE IF EXISTS plagiarism_matches;
DROP TABLE IF EXISTS plagiarism_checks;
DROP TABLE IF EXISTS submissions;
DROP TABLE IF EXISTS task_completions;
DROP TABLE IF EXISTS tasks;
DROP TABLE IF EXISTS resource_versions;
DROP TABLE IF EXISTS resources;
DROP TABLE IF EXISTS course_enrollments;
DROP TABLE IF EXISTS course_chapters;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS verification_codes;
DROP TABLE IF EXISTS login_logs;
DROP TABLE IF EXISTS user_sessions;
DROP TABLE IF EXISTS classrooms;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

-- ====================================================================
-- Phase 2: 创建全部 47 张表（按外键依赖顺序）
-- ====================================================================

-- ── 2.1 用户核心模块 ──

-- users
CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID，主键自增',
  email VARCHAR(255) NOT NULL COMMENT '用户邮箱，唯一登录凭证',
  username VARCHAR(100) NOT NULL COMMENT '用户名，唯一登录凭证',
  password VARCHAR(255) NOT NULL COMMENT '加密后的用户密码（BCrypt）',
  full_name VARCHAR(255) NOT NULL COMMENT '用户真实姓名',
  role VARCHAR(20) NOT NULL DEFAULT 'STUDENT' COMMENT '用户角色：ADMIN-管理员，TEACHER-教师，STUDENT-学生',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '账户是否激活：0-禁用，1-启用',
  is_verified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '邮箱是否验证：0-未验证，1-已验证',
  avatar_url VARCHAR(500) NULL COMMENT '用户头像URL',
  phone VARCHAR(20) NULL COMMENT '手机号码',
  student_id VARCHAR(50) NULL COMMENT '学号（仅学生）',
  major VARCHAR(100) NULL COMMENT '专业（仅学生）',
  grade VARCHAR(20) NULL COMMENT '年级（仅学生）',
  class_id BIGINT NULL COMMENT '班级ID，关联classrooms表',
  teacher_id VARCHAR(50) NULL COMMENT '教师工号（仅教师）',
  department VARCHAR(100) NULL COMMENT '所属院系（仅教师）',
  title VARCHAR(50) NULL COMMENT '职称（仅教师）',
  bio TEXT NULL COMMENT '个人简介',
  notification_settings JSON NULL COMMENT '通知设置，JSON格式存储',
  failed_login_attempts INT NOT NULL DEFAULT 0 COMMENT '登录失败次数，用于账户锁定',
  locked_until DATETIME NULL COMMENT '账户锁定截止时间',
  register_ip VARCHAR(50) NULL COMMENT '注册IP地址',
  last_login DATETIME NULL COMMENT '最后登录时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email) COMMENT '邮箱唯一索引',
  UNIQUE KEY uk_users_username (username) COMMENT '用户名唯一索引',
  KEY idx_users_role (role) COMMENT '角色索引，用于按角色查询',
  KEY idx_users_class_id (class_id) COMMENT '班级ID索引，用于查询班级学生'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';;

-- ── 2.2 班级管理模块 ──

-- classrooms
CREATE TABLE classrooms (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '班级ID，主键自增',
  name VARCHAR(100) NOT NULL COMMENT '班级名称',
  code VARCHAR(50) NOT NULL COMMENT '班级编号，唯一标识',
  description TEXT NULL COMMENT '班级描述',
  grade_level VARCHAR(50) NULL COMMENT '年级',
  major VARCHAR(100) NULL COMMENT '专业',
  advisor_id BIGINT NULL COMMENT '班主任ID，关联users表',
  student_count INT NOT NULL DEFAULT 0 COMMENT '学生人数',
  max_students INT NOT NULL DEFAULT 50 COMMENT '最大学生人数',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '班级是否有效：0-无效，1-有效',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_classrooms_code (code) COMMENT '班级编号唯一索引',
  KEY idx_classrooms_advisor_id (advisor_id) COMMENT '班主任ID索引，用于查询教师管理的班级',
  CONSTRAINT fk_classrooms_advisor FOREIGN KEY (advisor_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级表';;

-- user_sessions
CREATE TABLE user_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID，主键自增',
  user_id BIGINT NOT NULL COMMENT '用户ID，关联users表',
  session_token VARCHAR(500) NOT NULL COMMENT '访问令牌（JWT）',
  refresh_token VARCHAR(500) NULL COMMENT '刷新令牌',
  device_name VARCHAR(200) NULL COMMENT '设备名称',
  browser VARCHAR(100) NULL COMMENT '浏览器信息',
  operating_system VARCHAR(100) NULL COMMENT '操作系统信息',
  ip_address VARCHAR(50) NULL COMMENT '登录IP地址',
  location VARCHAR(200) NULL COMMENT '登录地理位置',
  user_agent VARCHAR(500) NULL COMMENT '完整User-Agent字符串',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '会话是否有效：0-失效，1-有效',
  is_current TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为当前会话：0-否，1-是',
  login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  last_activity DATETIME NULL COMMENT '最后活动时间',
  expires_at DATETIME NOT NULL COMMENT '会话过期时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_user_sessions_user_id (user_id) COMMENT '用户ID索引，用于查询用户所有会话',
  KEY idx_user_sessions_token (session_token(191)) COMMENT '访问令牌索引（前缀索引，避免超长索引）',
  CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户会话表';;

-- login_logs
CREATE TABLE login_logs (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID，主键自增',
  user_id BIGINT NULL COMMENT '用户ID（登录成功时不为空）',
  username VARCHAR(100) NULL COMMENT '尝试登录的用户名',
  login_status VARCHAR(20) NOT NULL COMMENT '登录状态：SUCCESS-成功，FAILURE-失败',
  ip_address VARCHAR(50) NULL COMMENT '登录IP地址',
  location VARCHAR(200) NULL COMMENT '登录地理位置',
  device_name VARCHAR(200) NULL COMMENT '设备名称',
  browser VARCHAR(100) NULL COMMENT '浏览器信息',
  operating_system VARCHAR(100) NULL COMMENT '操作系统信息',
  user_agent VARCHAR(500) NULL COMMENT '完整User-Agent字符串',
  failure_reason VARCHAR(500) NULL COMMENT '登录失败原因',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  PRIMARY KEY (id),
  KEY idx_login_logs_user_id (user_id) COMMENT '用户ID索引，用于查询用户登录历史',
  KEY idx_login_logs_created_at (created_at) COMMENT '创建时间索引，用于按时间范围查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';;

-- verification_codes
CREATE TABLE verification_codes (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '验证码ID，主键自增',
  email VARCHAR(255) NULL COMMENT '邮箱（邮箱验证码时不为空）',
  phone VARCHAR(20) NULL COMMENT '手机号（手机验证码时不为空）',
  code VARCHAR(10) NOT NULL COMMENT '验证码内容',
  type VARCHAR(30) NOT NULL COMMENT '验证码类型：REGISTER-注册，LOGIN-登录，RESET_PASSWORD-重置密码',
  used TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已使用：0-未使用，1-已使用',
  expires_at DATETIME NOT NULL COMMENT '验证码过期时间',
  ip_address VARCHAR(50) NULL COMMENT '发送验证码的IP地址',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_verification_codes_email (email) COMMENT '邮箱索引，用于查询邮箱验证码',
  KEY idx_verification_codes_phone (phone) COMMENT '手机号索引，用于查询手机验证码',
  KEY idx_verification_codes_expires_at (expires_at) COMMENT '过期时间索引，用于清理过期验证码'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验证码表';;

-- ── 2.3 课程管理模块 ──

-- courses
CREATE TABLE courses (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '课程ID，主键自增',
  name VARCHAR(255) NOT NULL COMMENT '课程名称',
  code VARCHAR(50) NOT NULL COMMENT '课程编号，唯一标识',
  description TEXT NULL COMMENT '课程详细描述',
  credits DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '课程学分',
  teacher_id BIGINT NOT NULL COMMENT '授课教师ID，关联users表',
  semester VARCHAR(50) NULL COMMENT '学期',
  year INT NULL COMMENT '学年',
  status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '课程状态：active-进行中，inactive-已结束',
  approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审批状态：PENDING-待审批，APPROVED-已通过，REJECTED-已拒绝',
  approval_time DATETIME NULL COMMENT '审批时间',
  approved_by BIGINT NULL COMMENT '审批人ID，关联users表',
  approval_comment VARCHAR(500) NULL COMMENT '审批意见',
  enrollment_type VARCHAR(30) NOT NULL DEFAULT 'FREE' COMMENT '选课类型：FREE-自由选课，APPROVAL-需审批',
  max_students INT NOT NULL DEFAULT 100 COMMENT '最大选课人数',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效：0-无效，1-有效',
  category VARCHAR(50) NULL COMMENT '课程分类',
  difficulty VARCHAR(20) NULL COMMENT '课程难度：入门-简单-中等-困难-专家',
  price DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '课程价格（付费课程）',
  tags JSON NULL COMMENT '课程标签，JSON数组格式',
  start_date DATETIME NULL COMMENT '课程开始日期',
  end_date DATETIME NULL COMMENT '课程结束日期',
  cover_image VARCHAR(500) NULL COMMENT '课程封面图片URL',
  syllabus_url VARCHAR(500) NULL COMMENT '课程大纲URL',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_courses_code (code) COMMENT '课程编号唯一索引',
  KEY idx_courses_teacher_id (teacher_id) COMMENT '教师ID索引，用于查询教师教授的课程',
  KEY idx_courses_status (status) COMMENT '课程状态索引',
  KEY idx_courses_approval_status (approval_status) COMMENT '审批状态索引',
  CONSTRAINT fk_courses_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';;

-- course_chapters
CREATE TABLE course_chapters (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '章节ID，主键自增',
  course_id BIGINT NOT NULL COMMENT '课程ID，关联courses表',
  parent_id BIGINT NULL COMMENT '父章节ID，关联自身表（顶级章节为NULL）',
  title VARCHAR(255) NOT NULL COMMENT '章节标题',
  content TEXT NULL COMMENT '章节内容',
  order_index INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  duration INT NULL COMMENT '预计学习时长（分钟）',
  is_published TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已发布：0-未发布，1-已发布',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_course_chapters_course_id (course_id) COMMENT '课程ID索引，用于查询课程所有章节',
  KEY idx_course_chapters_parent_id (parent_id) COMMENT '父章节ID索引，用于查询子章节',
  CONSTRAINT fk_course_chapters_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_course_chapters_parent FOREIGN KEY (parent_id) REFERENCES course_chapters(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程章节表';;

-- course_enrollments
CREATE TABLE course_enrollments (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '选课ID，主键自增',
  student_id BIGINT NOT NULL COMMENT '学生ID，关联users表',
  course_id BIGINT NOT NULL COMMENT '课程ID，关联courses表',
  approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED' COMMENT '选课审批状态：PENDING-待审批，APPROVED-已通过，REJECTED-已拒绝',
  approval_time DATETIME NULL COMMENT '审批时间',
  approved_by BIGINT NULL COMMENT '审批人ID，关联users表',
  approval_comment VARCHAR(500) NULL COMMENT '审批意见',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '选课是否有效：0-已退课，1-有效',
  final_score DECIMAL(5,2) NULL COMMENT '最终成绩',
  attendance_score DECIMAL(5,2) NULL COMMENT '平时成绩',
  assignment_score DECIMAL(5,2) NULL COMMENT '作业成绩',
  exam_score DECIMAL(5,2) NULL COMMENT '考试成绩',
  progress DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '学习进度（百分比）',
  total_study_time INT NOT NULL DEFAULT 0 COMMENT '总学习时长（秒）',
  enrolled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '选课时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_enrollments_student_course (student_id, course_id) COMMENT '学生-课程唯一索引，防止重复选课',
  KEY idx_course_enrollments_course_id (course_id) COMMENT '课程ID索引，用于查询课程选课学生',
  CONSTRAINT fk_course_enrollments_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_course_enrollments_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程选课表';;

-- resources
CREATE TABLE resources (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '资源ID，主键自增',
  file_name VARCHAR(255) NOT NULL COMMENT '文件名称',
  file_type VARCHAR(50) NULL COMMENT '文件类型：video-视频，document-文档，image-图片，other-其他',
  url VARCHAR(500) NOT NULL COMMENT '资源访问URL',
  course_id BIGINT NULL COMMENT '课程ID，关联courses表',
  chapter_id BIGINT NULL COMMENT '章节ID，关联course_chapters表',
  uploader_id BIGINT NULL COMMENT '上传者ID，关联users表',
  upload_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  description TEXT NULL COMMENT '资源描述',
  visible_to_all TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否对所有学生可见：0-仅教师可见，1-所有学生可见',
  PRIMARY KEY (id),
  KEY idx_resources_course_id (course_id) COMMENT '课程ID索引，用于查询课程所有资源',
  KEY idx_resources_chapter_id (chapter_id) COMMENT '章节ID索引，用于查询章节所有资源',
  CONSTRAINT fk_resources_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_resources_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapters(id) ON DELETE SET NULL,
  CONSTRAINT fk_resources_uploader FOREIGN KEY (uploader_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教学资源表';;

-- resource_versions
CREATE TABLE resource_versions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本ID，主键自增',
  resource_id BIGINT NOT NULL COMMENT '资源ID，关联resources表',
  version_number INT NOT NULL COMMENT '版本号（从1开始递增）',
  file_name VARCHAR(255) NOT NULL COMMENT '文件名称',
  file_type VARCHAR(50) NULL COMMENT '文件类型',
  url VARCHAR(500) NOT NULL COMMENT '资源访问URL',
  description TEXT NULL COMMENT '版本更新说明',
  file_size BIGINT NULL COMMENT '文件大小（字节）',
  uploader_id BIGINT NULL COMMENT '上传者ID，关联users表',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否为当前版本：0-历史版本，1-当前版本',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_resource_versions_resource_version (resource_id, version_number) COMMENT '资源-版本号唯一索引',
  CONSTRAINT fk_resource_versions_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源版本表';;

-- ── 2.4 作业与任务模块 ──

-- tasks
CREATE TABLE tasks (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID，主键自增',
  title VARCHAR(255) NOT NULL COMMENT '任务标题',
  description TEXT NULL COMMENT '任务描述',
  task_type VARCHAR(50) NOT NULL COMMENT '任务类型：assignment-作业，quiz-测验，project-项目',
  course_id BIGINT NULL COMMENT '课程ID，关联courses表',
  creator_id BIGINT NULL COMMENT '创建者ID，关联users表',
  chapter_id BIGINT NULL COMMENT '关联章节ID，用于章节任务点绑定',
  due_date DATETIME NULL COMMENT '截止日期',
  max_score DECIMAL(5,2) NOT NULL DEFAULT 100 COMMENT '满分',
  status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '任务状态：draft-草稿，published-已发布，closed-已关闭',
  is_published TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已发布：0-未发布，1-已发布',
  submission_format VARCHAR(100) NULL COMMENT '提交格式：text-文本，file-文件，both-两者均可',
  requirements TEXT NULL COMMENT '详细要求',
  attachment_url VARCHAR(500) NULL COMMENT '任务附件URL',
  start_date DATETIME NULL COMMENT '开始日期',
  late_penalty_per_day DECIMAL(5,2) NULL DEFAULT 10.00 COMMENT '逾期每天扣分比例(%)：可配逾期策略，替代硬编码10%/天；NULL表示用默认10%',
  max_submissions INT NULL DEFAULT 0 COMMENT '最大提交次数：0或不设表示不限；>0时超过即禁止再提交',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_tasks_course_id (course_id) COMMENT '课程ID索引，用于查询课程所有任务',
  KEY idx_tasks_creator_id (creator_id) COMMENT '创建者ID索引',
  KEY idx_tasks_chapter_id (chapter_id) COMMENT '章节ID索引，用于查询章节下任务点',
  CONSTRAINT fk_tasks_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_tasks_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_tasks_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapters(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';;

-- task_completions
CREATE TABLE task_completions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '完成记录ID，主键自增',
  student_id BIGINT NOT NULL COMMENT '学生ID，关联users表',
  task_id BIGINT NOT NULL COMMENT '任务ID，关联tasks表',
  course_id BIGINT NULL COMMENT '课程ID，关联courses表',
  chapter_id BIGINT NULL COMMENT '章节ID，关联course_chapters表',
  task_type VARCHAR(32) NULL COMMENT '任务类型（与tasks.task_type对齐）',
  completion_type VARCHAR(32) NULL COMMENT '完成方式：VIDEO/DOC/READING/QUIZ/ASSIGNMENT',
  watched_duration INT NULL COMMENT '视频已观看时长（秒，累计）',
  last_position INT NULL COMMENT '视频最近播放位置（秒）',
  total_duration INT NULL COMMENT '视频总时长（秒）',
  score DECIMAL(10,2) NULL COMMENT '测验得分',
  completed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已完成：0-否，1-是',
  completed_at DATETIME NULL COMMENT '完成时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_completion (student_id, task_id) COMMENT '学生-任务唯一，避免重复记录',
  KEY idx_tc_course (course_id) COMMENT '课程索引',
  KEY idx_tc_chapter (chapter_id) COMMENT '章节索引',
  KEY idx_tc_student_course (student_id, course_id) COMMENT '学生-课程联合索引：课程进度聚合与学习中心查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务点完成记录表';;

-- submissions
CREATE TABLE submissions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '提交ID，主键自增',
  task_id BIGINT NOT NULL COMMENT '任务ID，关联tasks表',
  student_id BIGINT NOT NULL COMMENT '学生ID，关联users表',
  content TEXT NULL COMMENT '提交的文本内容',
  file_urls JSON NULL COMMENT '提交的文件URL列表，JSON数组格式',
  submission_data JSON NULL COMMENT '提交的其他数据，JSON格式',
  status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' COMMENT '提交状态：SUBMITTED-已提交，GRADED-已批改，RETURNED-已退回',
  submitted_at DATETIME NULL COMMENT '提交时间',
  graded_at DATETIME NULL COMMENT '批改时间',
  returned_at DATETIME NULL COMMENT '退回时间',
  score DECIMAL(5,2) NULL COMMENT '得分',
  feedback TEXT NULL COMMENT '教师反馈',
  ai_feedback TEXT NULL COMMENT 'AI自动反馈',
  grader_id BIGINT NULL COMMENT '批改人ID，关联users表',
  attempt_number INT NOT NULL DEFAULT 1 COMMENT '提交次数',
  is_late TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否迟交：0-按时，1-迟交',
  late_penalty DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '迟交扣分',
  time_spent INT NOT NULL DEFAULT 0 COMMENT '花费时间（秒）',
  view_count INT NOT NULL DEFAULT 0 COMMENT '查看次数',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_submissions_task_id (task_id) COMMENT '任务ID索引，用于查询任务所有提交',
  KEY idx_submissions_student_id (student_id) COMMENT '学生ID索引，用于查询学生所有提交',
  CONSTRAINT fk_submissions_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
  CONSTRAINT fk_submissions_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_submissions_grader FOREIGN KEY (grader_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提交记录表';;

-- ── 2.5 查重与催交模块 ──

-- plagiarism_checks
CREATE TABLE plagiarism_checks (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '查重ID，主键自增',
  task_id BIGINT NULL COMMENT '任务ID，关联tasks表',
  submission_id BIGINT NOT NULL COMMENT '提交ID，关联submissions表',
  student_id BIGINT NULL COMMENT '学生ID，关联users表',
  check_type VARCHAR(20) NOT NULL DEFAULT 'INTERNAL' COMMENT '查重类型：INTERNAL-内部库，EXTERNAL-外部库',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '查重状态：PENDING-待处理，PROCESSING-处理中，COMPLETED-已完成，FAILED-失败',
  similarity_score DECIMAL(5,2) NULL COMMENT '相似度（百分比）',
  high_similarity_count INT NOT NULL DEFAULT 0 COMMENT '高相似度片段数量',
  result_summary TEXT NULL COMMENT '结果摘要',
  check_detail JSON NULL COMMENT '查重详细结果，JSON格式',
  checked_at DATETIME NULL COMMENT '查重完成时间',
  error_message TEXT NULL COMMENT '错误信息（查重失败时）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_plagiarism_checks_submission_id (submission_id) COMMENT '提交ID索引，用于查询提交的查重记录',
  CONSTRAINT fk_plagiarism_checks_submission FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='查重记录表';;

-- plagiarism_matches
CREATE TABLE plagiarism_matches (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '匹配ID，主键自增',
  plagiarism_check_id BIGINT NOT NULL COMMENT '查重ID，关联plagiarism_checks表',
  matched_submission_id BIGINT NULL COMMENT '匹配的提交ID（内部匹配时）',
  matched_source VARCHAR(500) NULL COMMENT '匹配的来源URL（外部匹配时）',
  similarity DECIMAL(5,2) NULL COMMENT '该片段相似度',
  original_text TEXT NULL COMMENT '原文片段',
  matched_text TEXT NULL COMMENT '匹配片段',
  match_type VARCHAR(20) NULL COMMENT '匹配类型：INTERNAL-内部匹配，EXTERNAL-外部匹配',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_plagiarism_matches_check_id (plagiarism_check_id) COMMENT '查重ID索引，用于查询查重的所有匹配',
  CONSTRAINT fk_plagiarism_matches_check FOREIGN KEY (plagiarism_check_id) REFERENCES plagiarism_checks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='查重匹配表';;

-- reminders
CREATE TABLE reminders (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '提醒ID，主键自增',
  reminder_type VARCHAR(40) NOT NULL COMMENT '提醒类型：ASSIGNMENT_DUE-作业截止，EXAM_REMINDER-考试提醒',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '提醒状态：PENDING-待发送，SENT-已发送，FAILED-发送失败',
  user_id BIGINT NOT NULL COMMENT '用户ID，关联users表',
  task_id BIGINT NULL COMMENT '任务ID，关联tasks表',
  course_id BIGINT NULL COMMENT '课程ID，关联courses表',
  title VARCHAR(200) NOT NULL COMMENT '提醒标题',
  message TEXT NULL COMMENT '提醒内容',
  due_date DATETIME NULL COMMENT '截止日期',
  trigger_day INT NULL COMMENT '提前几天提醒',
  scheduled_at DATETIME NULL COMMENT '计划发送时间',
  sent_at DATETIME NULL COMMENT '实际发送时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_reminders_user_id (user_id) COMMENT '用户ID索引，用于查询用户所有提醒',
  CONSTRAINT fk_reminders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='催交提醒表';;

-- ── 2.6 考试管理模块 ──

-- exam_papers
CREATE TABLE exam_papers (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '试卷ID，主键自增',
  title VARCHAR(255) NOT NULL COMMENT '试卷标题',
  description TEXT NULL COMMENT '试卷描述',
  course_id BIGINT NOT NULL COMMENT '课程ID，关联courses表',
  creator_id BIGINT NOT NULL COMMENT '创建者ID，关联users表',
  status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '试卷状态：draft-草稿，published-已发布，closed-已关闭',
  instructions TEXT NULL COMMENT '考试说明',
  start_time DATETIME NULL COMMENT '考试开始时间',
  end_time DATETIME NULL COMMENT '考试结束时间',
  total_score DECIMAL(5,2) NULL COMMENT '总分',
  duration INT NULL COMMENT '考试时长（分钟）',
  question_count INT NULL COMMENT '题目数量',
  shuffle_questions TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否打乱题目顺序：0-否，1-是',
  shuffle_options TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否打乱选项顺序：0-否，1-是',
  show_score TINYINT(1) NOT NULL DEFAULT 0 COMMENT '考试结束后是否显示分数：0-否，1-是',
  show_correct_answer TINYINT(1) NOT NULL DEFAULT 0 COMMENT '考试结束后是否显示正确答案：0-否，1-是',
  max_attempts INT NOT NULL DEFAULT 1 COMMENT '最大考试次数',
  time_limit INT NULL COMMENT '时间限制（分钟）',
  allow_review TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许查看试卷：0-否，1-是',
  generation_rules JSON NULL COMMENT '自动组卷规则，JSON格式',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效：0-无效，1-有效',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_exam_papers_course_id (course_id) COMMENT '课程ID索引，用于查询课程所有试卷',
  CONSTRAINT fk_exam_papers_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_exam_papers_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷表';;

-- questions
CREATE TABLE questions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '题目ID，主键自增',
  type VARCHAR(20) NOT NULL COMMENT '题目类型：single-单选题，multiple-多选题，true_false-判断题，fill_blank-填空题，essay-简答题',
  content TEXT NOT NULL COMMENT '题目内容',
  options JSON NULL COMMENT '选项，JSON数组格式',
  correct_answers JSON NULL COMMENT '正确答案，JSON数组格式',
  difficulty VARCHAR(20) NOT NULL DEFAULT 'medium' COMMENT '题目难度：easy-简单，medium-中等，hard-困难',
  score DECIMAL(5,2) NOT NULL DEFAULT 5 COMMENT '题目分值',
  knowledge_points JSON NULL COMMENT '关联知识点，JSON数组格式',
  explanation TEXT NULL COMMENT '答案解析',
  usage_count INT NOT NULL DEFAULT 0 COMMENT '使用次数',
  correct_rate DECIMAL(5,2) NULL COMMENT '正确率（百分比）',
  course_id BIGINT NULL COMMENT '课程ID，关联courses表',
  created_by BIGINT NULL COMMENT '创建者ID，关联users表',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效：0-无效，1-有效',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_questions_course_id (course_id) COMMENT '课程ID索引，用于查询课程所有题目',
  KEY idx_questions_created_by (created_by) COMMENT '创建者ID索引',
  CONSTRAINT fk_questions_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE SET NULL,
  CONSTRAINT fk_questions_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库表';;

-- exam_questions
CREATE TABLE exam_questions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID，主键自增',
  exam_paper_id BIGINT NOT NULL COMMENT '试卷ID，关联exam_papers表',
  question_id BIGINT NOT NULL COMMENT '题目ID，关联questions表',
  order_index INT NOT NULL DEFAULT 0 COMMENT '题目在试卷中的排序',
  points DECIMAL(5,2) NOT NULL COMMENT '该题在试卷中的分值',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_exam_questions_paper_question (exam_paper_id, question_id) COMMENT '试卷-题目唯一索引，防止重复添加',
  CONSTRAINT fk_exam_questions_paper FOREIGN KEY (exam_paper_id) REFERENCES exam_papers(id) ON DELETE CASCADE,
  CONSTRAINT fk_exam_questions_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷题目表';;

-- exam_sessions
CREATE TABLE exam_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID，主键自增',
  exam_paper_id BIGINT NOT NULL COMMENT '试卷ID，关联exam_papers表',
  student_id BIGINT NOT NULL COMMENT '学生ID，关联users表',
  status VARCHAR(20) NOT NULL DEFAULT 'STARTED' COMMENT '考试状态：STARTED-已开始，SUBMITTED-已提交，GRADED-已批改',
  attempt_number INT NOT NULL DEFAULT 1 COMMENT '考试次数',
  start_time DATETIME NULL COMMENT '开始时间',
  submit_time DATETIME NULL COMMENT '提交时间',
  time_spent INT NULL COMMENT '花费时间（秒）',
  total_score DECIMAL(5,2) NULL COMMENT '总分',
  percentage DECIMAL(5,2) NULL COMMENT '得分百分比',
  answers JSON NULL COMMENT '学生答案，JSON格式',
  question_scores JSON NULL COMMENT '每题得分，JSON格式',
  ip_address VARCHAR(50) NULL COMMENT '考试IP地址',
  user_agent VARCHAR(500) NULL COMMENT 'User-Agent字符串',
  cheating_flags JSON NULL COMMENT '作弊检测结果JSON，独立于answers存储（detectedAt/flagCount/details/riskLevel）',
  question_order JSON NULL COMMENT '本场考试的题目乱序（按展示顺序排列的questionId数组，每位学生独立随机）',
  option_orders JSON NULL COMMENT '本场考试各题选项乱序映射，结构：{questionId:[原选项下标...]}，下标为展示位、值为原题选项下标',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_exam_sessions_paper_id (exam_paper_id) COMMENT '试卷ID索引，用于查询试卷所有考试会话',
  KEY idx_exam_sessions_student_id (student_id) COMMENT '学生ID索引，用于查询学生所有考试会话',
  KEY idx_exam_sessions_student_paper (student_id, exam_paper_id) COMMENT '学生-试卷联合索引：查询学生某卷最新会话与成绩列表',
  CONSTRAINT fk_exam_sessions_paper FOREIGN KEY (exam_paper_id) REFERENCES exam_papers(id) ON DELETE CASCADE,
  CONSTRAINT fk_exam_sessions_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试会话表';;

-- ── 2.7 知识点与学习路径模块 ──

-- knowledge_points
CREATE TABLE knowledge_points (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识点ID，主键自增',
  name VARCHAR(255) NOT NULL COMMENT '知识点名称',
  description TEXT NULL COMMENT '知识点描述',
  course_id BIGINT NULL COMMENT '课程ID，关联courses表',
  parent_id BIGINT NULL COMMENT '父知识点ID，关联自身表（顶级知识点为NULL）',
  difficulty_level VARCHAR(20) NULL COMMENT '难度等级',
  importance INT NOT NULL DEFAULT 5 COMMENT '重要程度（1-10）',
  estimated_time INT NULL COMMENT '预计学习时间（分钟）',
  point_type VARCHAR(30) NULL COMMENT '知识点类型：concept-概念，skill-技能，principle-原理',
  order_index INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_knowledge_points_course_id (course_id) COMMENT '课程ID索引，用于查询课程所有知识点',
  CONSTRAINT fk_knowledge_points_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_knowledge_points_parent FOREIGN KEY (parent_id) REFERENCES knowledge_points(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点表';;

-- knowledge_point_relations
CREATE TABLE knowledge_point_relations (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '关系ID，主键自增',
  source_id BIGINT NOT NULL COMMENT '源知识点ID，关联knowledge_points表',
  target_id BIGINT NOT NULL COMMENT '目标知识点ID，关联knowledge_points表',
  relation_type VARCHAR(30) NOT NULL COMMENT '关系类型：prerequisite-前置知识，related-相关知识，follow-up-后续知识',
  strength DECIMAL(3,2) NOT NULL DEFAULT 1.00 COMMENT '关系强度（0-1）',
  description VARCHAR(500) NULL COMMENT '关系描述',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_knowledge_relations_source_target_type (source_id, target_id, relation_type) COMMENT '源-目标-类型唯一索引，防止重复关系',
  CONSTRAINT fk_knowledge_relations_source FOREIGN KEY (source_id) REFERENCES knowledge_points(id) ON DELETE CASCADE,
  CONSTRAINT fk_knowledge_relations_target FOREIGN KEY (target_id) REFERENCES knowledge_points(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点关系表';;

-- chapter_knowledge_points
CREATE TABLE chapter_knowledge_points (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID，主键自增',
  chapter_id BIGINT NOT NULL COMMENT '章节ID，关联course_chapters表',
  knowledge_point_id BIGINT NOT NULL COMMENT '知识点ID，关联knowledge_points表',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chapter_knowledge_points (chapter_id, knowledge_point_id) COMMENT '章节-知识点唯一索引，防止重复关联',
  CONSTRAINT fk_chapter_knowledge_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapters(id) ON DELETE CASCADE,
  CONSTRAINT fk_chapter_knowledge_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_points(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节知识点关联表';;

-- wrong_questions
CREATE TABLE wrong_questions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '错题ID，主键自增',
  student_id BIGINT NOT NULL COMMENT '学生ID，关联users表',
  question_id BIGINT NOT NULL COMMENT '题目ID，关联questions表',
  course_id BIGINT NULL COMMENT '课程ID，关联courses表',
  wrong_answer TEXT NULL COMMENT '错误答案',
  reason TEXT NULL COMMENT '错误原因',
  ai_analysis TEXT NULL COMMENT 'AI 错因分析（错题产生时由 AI 自动生成，支撑"错题→AI分析→推送练习"闭环）',
  mastered TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已掌握：0-未掌握，1-已掌握',
  source_type VARCHAR(50) NULL COMMENT '来源类型：exam-考试，quiz-测验，practice-练习',
  practice_count INT NOT NULL DEFAULT 0 COMMENT '练习次数',
  last_practiced_at DATETIME NULL COMMENT '最后练习时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_wrong_questions_student_question (student_id, question_id) COMMENT '学生-题目唯一索引，防止重复记录',
  CONSTRAINT fk_wrong_questions_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_wrong_questions_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
  CONSTRAINT fk_wrong_questions_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题表';;

-- learning_paths
CREATE TABLE learning_paths (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '学习路径ID，主键自增',
  student_id BIGINT NOT NULL COMMENT '学生ID，关联users表',
  course_id BIGINT NULL COMMENT '课程ID，关联courses表',
  path_name VARCHAR(255) NOT NULL COMMENT '学习路径名称',
  description TEXT NULL COMMENT '学习路径描述',
  stages JSON NULL COMMENT '学习阶段，JSON数组格式',
  plan_detail JSON NULL COMMENT '详细计划，JSON格式',
  daily_tasks JSON NULL COMMENT '每日任务，JSON格式',
  learning_goal VARCHAR(500) NULL COMMENT '学习目标',
  current_stage INT NOT NULL DEFAULT 0 COMMENT '当前阶段',
  total_stages INT NOT NULL DEFAULT 0 COMMENT '总阶段数',
  completion_rate DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '完成率（百分比）',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT-草稿，IN_PROGRESS-进行中，COMPLETED-已完成',
  estimated_completion_date DATETIME NULL COMMENT '预计完成日期',
  actual_completion_date DATETIME NULL COMMENT '实际完成日期',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_learning_paths_student_id (student_id) COMMENT '学生ID索引，用于查询学生所有学习路径',
  CONSTRAINT fk_learning_paths_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_learning_paths_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径表';;

-- learning_path_nodes
CREATE TABLE learning_path_nodes (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '节点ID，主键自增',
  learning_path_id BIGINT NOT NULL COMMENT '学习路径ID，关联learning_paths表',
  knowledge_point_id BIGINT NULL COMMENT '关联知识点ID，关联knowledge_points表',
  task_id BIGINT NULL COMMENT '关联真实任务点ID，关联tasks表（修复 P1-4 挂载真实任务点）',
  chapter_id BIGINT NULL COMMENT '关联章节ID，关联course_chapters表',
  title VARCHAR(255) NOT NULL COMMENT '节点标题',
  description TEXT NULL COMMENT '节点描述',
  node_type VARCHAR(30) NOT NULL DEFAULT 'study' COMMENT '节点类型：study-学习，practice-练习，exam-考试',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待完成，IN_PROGRESS-进行中，COMPLETED-已完成',
  order_index INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  estimated_time INT NULL COMMENT '预计时间（分钟）',
  actual_time INT NOT NULL DEFAULT 0 COMMENT '实际时间（分钟）',
  score DECIMAL(5,2) NULL COMMENT '得分',
  completed_at DATETIME NULL COMMENT '完成时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  CONSTRAINT fk_learning_path_nodes_path FOREIGN KEY (learning_path_id) REFERENCES learning_paths(id) ON DELETE CASCADE,
  CONSTRAINT fk_learning_path_nodes_knowledge FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_points(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径节点表';;

-- ── 2.8 学习记录模块 ──

-- learning_records
CREATE TABLE learning_records (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID，主键自增',
  user_id BIGINT NOT NULL COMMENT '用户ID，关联users表',
  resource_id BIGINT NULL COMMENT '资源ID，关联resources表',
  chapter_id BIGINT NULL COMMENT '章节ID，关联course_chapters表',
  course_id BIGINT NULL COMMENT '课程ID，关联courses表',
  action_type VARCHAR(30) NOT NULL COMMENT '操作类型：view-查看，download-下载，complete-完成',
  duration INT NOT NULL DEFAULT 0 COMMENT '持续时间（秒）',
  progress DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '进度（百分比）',
  metadata JSON NULL COMMENT '其他元数据，JSON格式',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_learning_records_user_id (user_id) COMMENT '用户ID索引，用于查询用户所有学习记录',
  KEY idx_learning_records_course_id (course_id) COMMENT '课程ID索引，用于查询课程学习记录',
  KEY idx_learning_records_created_at (created_at) COMMENT '时间索引：活跃度统计与时间范围查询（记录增长快，避免全表扫描）',
  CONSTRAINT fk_learning_records_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习记录表';;

-- notes（笔记与收藏）
CREATE TABLE notes (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '笔记ID，主键自增',
  user_id BIGINT NOT NULL COMMENT '用户ID，关联users表',
  course_id BIGINT NULL COMMENT '课程ID，关联courses表',
  chapter_id BIGINT NULL COMMENT '章节ID，关联course_chapters表',
  knowledge_point_id BIGINT NULL COMMENT '知识点ID，关联knowledge_points表',
  title VARCHAR(255) NULL COMMENT '笔记标题',
  content TEXT NOT NULL COMMENT '笔记正文（Markdown 文本）',
  is_favorite TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收藏：0-否，1-是',
  created_at DATETIME NULL COMMENT '创建时间',
  updated_at DATETIME NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_notes_user_id (user_id) COMMENT '用户ID索引，用于查询用户所有笔记',
  KEY idx_notes_knowledge_point_id (knowledge_point_id) COMMENT '知识点ID索引，用于查询知识点相关笔记'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户笔记与收藏表';

-- video_learning_records
CREATE TABLE video_learning_records (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID，主键自增',
  user_id BIGINT NOT NULL COMMENT '用户ID，关联users表',
  resource_id BIGINT NOT NULL COMMENT '视频资源ID，关联resources表',
  course_id BIGINT NULL COMMENT '课程ID，由resource_id关联resources解析，用于按课程统计视频完播',
  watch_duration INT NOT NULL DEFAULT 0 COMMENT '观看时长（秒）',
  progress DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '观看进度（百分比）',
  last_position INT NOT NULL DEFAULT 0 COMMENT '上次观看位置（秒）',
  complete_count INT NOT NULL DEFAULT 0 COMMENT '完成观看次数',
  last_watched_at DATETIME NULL COMMENT '上次观看时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_video_learning_user_resource (user_id, resource_id) COMMENT '用户-资源唯一索引，每个用户每个视频只有一条记录',
  CONSTRAINT fk_video_learning_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_video_learning_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE,
  CONSTRAINT fk_video_learning_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频学习记录表';;

-- video_watch_sessions
CREATE TABLE video_watch_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID，主键自增',
  user_id BIGINT NOT NULL COMMENT '用户ID，关联users表',
  resource_id BIGINT NOT NULL COMMENT '视频资源ID，关联resources表',
  start_position INT NOT NULL DEFAULT 0 COMMENT '开始位置（秒）',
  end_position INT NOT NULL DEFAULT 0 COMMENT '结束位置（秒）',
  duration INT NOT NULL DEFAULT 0 COMMENT '本次观看时长（秒）',
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  ended_at DATETIME NULL COMMENT '结束时间',
  PRIMARY KEY (id),
  KEY idx_video_watch_sessions_user_id (user_id) COMMENT '用户ID索引，用于查询用户所有观看会话',
  CONSTRAINT fk_video_watch_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_video_watch_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频观看会话表';;

-- ── 2.9 系统通用模块 ──

-- notifications
CREATE TABLE notifications (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID，主键自增',
  user_id BIGINT NOT NULL COMMENT '用户ID，关联users表',
  title VARCHAR(255) NOT NULL COMMENT '通知标题',
  content TEXT NULL COMMENT '通知内容',
  category VARCHAR(50) NOT NULL DEFAULT 'system' COMMENT '通知分类：system-系统通知，course-课程通知，assignment-作业通知',
  type VARCHAR(50) NOT NULL DEFAULT 'SYSTEM' COMMENT '通知类型',
  is_read TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
  priority VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT '优先级：low-低，normal-中，high-高',
  action_url VARCHAR(500) NULL COMMENT '点击跳转URL',
  data JSON NULL COMMENT '附加数据，JSON格式',
  related_id BIGINT NULL COMMENT '关联ID',
  related_type VARCHAR(50) NULL COMMENT '关联类型',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  read_at DATETIME NULL COMMENT '阅读时间',
  PRIMARY KEY (id),
  KEY idx_notifications_user_read (user_id, is_read) COMMENT '用户-已读联合索引，用于查询用户未读通知',
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';;

-- role_permissions
CREATE TABLE role_permissions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限ID，主键自增',
  role VARCHAR(50) NOT NULL COMMENT '角色标识',
  permission_key VARCHAR(100) NOT NULL COMMENT '权限标识',
  permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
  permission_type VARCHAR(20) NOT NULL DEFAULT 'MENU' COMMENT '权限类型：MENU-菜单权限，BUTTON-按钮权限',
  resource_path VARCHAR(255) NULL COMMENT '资源路径',
  parent_id BIGINT NULL COMMENT '父权限ID',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效：0-无效，1-有效',
  description VARCHAR(255) NULL COMMENT '权限描述',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_permissions_role_key (role, permission_key) COMMENT '角色-权限唯一索引，防止重复授权'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限表';;

-- system_configs
CREATE TABLE system_configs (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID，主键自增',
  config_key VARCHAR(128) NOT NULL COMMENT '配置键',
  config_value TEXT NULL COMMENT '配置值',
  config_group VARCHAR(64) NULL COMMENT '配置分组',
  config_type VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '配置类型：STRING-字符串，NUMBER-数字，BOOLEAN-布尔，JSON-JSON',
  description VARCHAR(512) NULL COMMENT '配置描述',
  is_public TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否公开：0-不公开，1-公开',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_configs_key (config_key) COMMENT '配置键唯一索引',
  KEY idx_system_configs_group (config_group) COMMENT '配置分组索引，用于按分组查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';;

-- file_upload_sessions
CREATE TABLE file_upload_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID，主键自增',
  filename VARCHAR(255) NOT NULL COMMENT '文件名',
  file_size BIGINT NULL COMMENT '文件大小（字节）',
  total_chunks INT NULL COMMENT '总分片数',
  uploaded_chunks INT NOT NULL DEFAULT 0 COMMENT '已上传分片数',
  upload_path VARCHAR(500) NULL COMMENT '上传路径',
  status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING' COMMENT '上传状态：UPLOADING-上传中，COMPLETED-已完成，FAILED-失败',
  uploader_id BIGINT NULL COMMENT '上传者ID，关联users表',
  expires_at DATETIME NULL COMMENT '会话过期时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_file_upload_sessions_uploader (uploader_id) COMMENT '上传者ID索引',
  KEY idx_file_upload_sessions_status (status) COMMENT '上传状态索引',
  CONSTRAINT fk_file_upload_sessions_uploader FOREIGN KEY (uploader_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传会话表';;

-- operation_logs
CREATE TABLE operation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(64),
    module VARCHAR(64) COMMENT '模块名',
    action VARCHAR(32) COMMENT 'CREATE/UPDATE/DELETE/STATE_CHANGE/IMPORT/EXPORT/EXECUTE/SEND',
    target VARCHAR(255) COMMENT '目标描述',
    target_id VARCHAR(64),
    ip VARCHAR(64),
    user_agent VARCHAR(512),
    method VARCHAR(10) COMMENT 'HTTP方法',
    params TEXT COMMENT '请求参数JSON截断',
    duration_ms BIGINT COMMENT '执行耗时ms',
    status VARCHAR(16) DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
    error_msg VARCHAR(512),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_module (module),
    INDEX idx_action (action),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';;

-- ── 2.10 AI 与课堂互动模块 ──

-- ai_chat_sessions
CREATE TABLE ai_chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL COMMENT '业务会话ID(UUID)',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    title VARCHAR(255) DEFAULT '' COMMENT '会话标题',
    message_count INT DEFAULT 0 COMMENT '消息条数',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除:0-未删,1-已删',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话表';;

-- ai_chat_messages
CREATE TABLE ai_chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL COMMENT '所属会话ID',
    role VARCHAR(16) NOT NULL COMMENT '角色:user/assistant',
    content TEXT COMMENT '消息内容',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除:0-未删,1-已删',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息表';;

-- sign_ins
CREATE TABLE sign_ins (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '签到ID，主键自增',
    course_id BIGINT NULL COMMENT '课程ID，关联courses表',
    chapter_id BIGINT NULL COMMENT '章节ID，关联course_chapters表，空表示整课签到',
    title VARCHAR(255) NOT NULL COMMENT '签到标题',
    type VARCHAR(20) NOT NULL COMMENT '签到类型：NORMAL/GESTURE/LOCATION/QR/CODE',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/ONGOING/CLOSED',
    duration_minutes INT NULL COMMENT '签到窗口时长(分钟)',
    gesture VARCHAR(50) NULL COMMENT '手势签到手势码(GESTURE类型使用)',
    sign_code VARCHAR(64) NULL COMMENT '二维码/签到码(QR/CODE类型使用)',
    creator_id BIGINT NULL COMMENT '发起教师ID，关联users表',
    started_at DATETIME NULL COMMENT '开始时间',
    closed_at DATETIME NULL COMMENT '结束时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_sign_ins_course_id (course_id) COMMENT '课程ID索引',
    KEY idx_sign_ins_chapter_id (chapter_id) COMMENT '章节ID索引',
    KEY idx_sign_ins_creator_id (creator_id) COMMENT '发起教师索引',
    KEY idx_sign_ins_status (status) COMMENT '状态索引',
    CONSTRAINT fk_sign_ins_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_sign_ins_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapters(id) ON DELETE SET NULL,
    CONSTRAINT fk_sign_ins_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课堂签到表';;

-- sign_in_records
CREATE TABLE sign_in_records (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID，主键自增',
    sign_in_id BIGINT NOT NULL COMMENT '关联签到活动ID，关联sign_ins表',
    student_id BIGINT NOT NULL COMMENT '学生ID，关联users表',
    status VARCHAR(20) NOT NULL DEFAULT 'ABSENT' COMMENT '签到结果：SIGNED/LATE/ABSENT/LEAVE',
    sign_time DATETIME NULL COMMENT '签到时间',
    ip VARCHAR(64) NULL COMMENT '签到IP',
    user_agent VARCHAR(512) NULL COMMENT '用户代理(UA)',
    photo_url VARCHAR(500) NULL COMMENT '拍照核验地址(人脸/普通签到使用)',
    latitude DOUBLE NULL COMMENT '纬度(LOCATION类型使用)',
    longitude DOUBLE NULL COMMENT '经度(LOCATION类型使用)',
    submitted_code VARCHAR(64) NULL COMMENT '学生提交的签到码/二维码(CODE/QR类型校验用，P1-2)',
    submitted_gesture VARCHAR(64) NULL COMMENT '学生提交的手势(GESTURE类型校验用，P1-2)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sign_in_student (sign_in_id, student_id) COMMENT '同一签到每位学生仅一条记录',
    KEY idx_sign_in_records_student_id (student_id) COMMENT '学生ID索引',
    KEY idx_sign_in_records_status (status) COMMENT '状态索引',
    CONSTRAINT fk_sign_in_records_sign_in FOREIGN KEY (sign_in_id) REFERENCES sign_ins(id) ON DELETE CASCADE,
    CONSTRAINT fk_sign_in_records_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='签到记录表';;

-- votes
CREATE TABLE votes (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '投票ID，主键自增',
    course_id BIGINT NULL COMMENT '课程ID，关联courses表',
    chapter_id BIGINT NULL COMMENT '章节ID，关联course_chapters表，空表示整课投票',
    title VARCHAR(255) NOT NULL COMMENT '投票标题',
    description TEXT NULL COMMENT '投票描述',
    vote_mode VARCHAR(20) NOT NULL COMMENT '投票模式：SINGLE_CHOICE/MULTIPLE_CHOICE/TEXT',
    options TEXT NULL COMMENT '选项JSON数组：[{"id":"a","text":"..."}]',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/ONGOING/CLOSED',
    creator_id BIGINT NULL COMMENT '发起教师ID，关联users表',
    started_at DATETIME NULL COMMENT '开始时间',
    closed_at DATETIME NULL COMMENT '结束时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_votes_course_id (course_id) COMMENT '课程ID索引',
    KEY idx_votes_chapter_id (chapter_id) COMMENT '章节ID索引',
    KEY idx_votes_creator_id (creator_id) COMMENT '发起教师索引',
    KEY idx_votes_status (status) COMMENT '状态索引',
    CONSTRAINT fk_votes_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_votes_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapters(id) ON DELETE SET NULL,
    CONSTRAINT fk_votes_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课堂投票问卷表';;

-- vote_records
CREATE TABLE vote_records (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID，主键自增',
    vote_id BIGINT NOT NULL COMMENT '关联投票ID，关联votes表',
    student_id BIGINT NOT NULL COMMENT '学生ID，关联users表',
    choice TEXT NULL COMMENT '选择项JSON：单选择字符串，多选择数组',
    text_answer TEXT NULL COMMENT '文本回答(TEXT模式使用)',
    ip VARCHAR(64) NULL COMMENT '投票IP',
    user_agent VARCHAR(512) NULL COMMENT '用户代理(UA)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vote_student (vote_id, student_id) COMMENT '同一投票每位学生仅一条记录',
    KEY idx_vote_records_student_id (student_id) COMMENT '学生ID索引',
    CONSTRAINT fk_vote_records_vote FOREIGN KEY (vote_id) REFERENCES votes(id) ON DELETE CASCADE,
    CONSTRAINT fk_vote_records_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投票记录表';;

-- discussion_topics
CREATE TABLE discussion_topics (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '话题ID，主键自增',
    course_id BIGINT NULL COMMENT '课程ID，关联courses表，空表示全站公共讨论',
    chapter_id BIGINT NULL COMMENT '章节ID，关联course_chapters表，空表示整课讨论',
    title VARCHAR(255) NOT NULL COMMENT '话题标题',
    content TEXT NULL COMMENT '话题内容',
    type VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '类型：NORMAL/QA',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/CLOSED',
    creator_id BIGINT NOT NULL COMMENT '发起者ID，关联users表',
    creator_role VARCHAR(20) NOT NULL COMMENT '发起者角色：TEACHER/STUDENT/ADMIN',
    is_pinned TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否，1-是',
    is_essence TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否精华：0-否，1-是（对标超星精华帖，教师/管理员标记）',
    reply_count INT NOT NULL DEFAULT 0 COMMENT '回复数',
    last_reply_at DATETIME NULL COMMENT '最后回复时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_discussion_topics_course_id (course_id) COMMENT '课程ID索引',
    KEY idx_discussion_topics_chapter_id (chapter_id) COMMENT '章节ID索引',
    KEY idx_discussion_topics_status (status) COMMENT '状态索引',
    KEY idx_discussion_topics_creator (creator_id) COMMENT '发起者索引',
    CONSTRAINT fk_discussion_topics_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_discussion_topics_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapters(id) ON DELETE SET NULL,
    CONSTRAINT fk_discussion_topics_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程讨论话题表';;

-- discussion_posts
CREATE TABLE discussion_posts (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '回复ID，主键自增',
    topic_id BIGINT NOT NULL COMMENT '所属话题ID，关联discussion_topics表',
    parent_id BIGINT NULL COMMENT '父回复ID，楼中楼，顶层为NULL',
    author_id BIGINT NOT NULL COMMENT '作者ID，关联users表',
    author_role VARCHAR(20) NOT NULL COMMENT '作者角色：TEACHER/STUDENT/ADMIN',
    content TEXT NOT NULL COMMENT '回复内容',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    floor INT NULL COMMENT '楼层号，按话题内回复顺序递增（顶层与楼中楼统一编号）',
    mentions VARCHAR(500) NULL COMMENT '被@提及的用户ID列表，逗号分隔（对标超星@提醒）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_discussion_posts_topic_id (topic_id) COMMENT '话题ID索引',
    KEY idx_discussion_posts_author_id (author_id) COMMENT '作者ID索引',
    KEY idx_discussion_posts_parent_id (parent_id) COMMENT '父回复ID索引',
    CONSTRAINT fk_discussion_posts_topic FOREIGN KEY (topic_id) REFERENCES discussion_topics(id) ON DELETE CASCADE,
    CONSTRAINT fk_discussion_posts_parent FOREIGN KEY (parent_id) REFERENCES discussion_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_discussion_posts_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程讨论回复表';;

-- discussion_post_likes
CREATE TABLE discussion_post_likes (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '点赞记录ID，主键自增',
    post_id BIGINT NOT NULL COMMENT '回复ID，关联discussion_posts表',
    user_id BIGINT NOT NULL COMMENT '点赞用户ID，关联users表',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_user (post_id, user_id) COMMENT '同一用户对同一回复仅可点赞一次',
    KEY idx_discussion_post_likes_post_id (post_id) COMMENT '回复ID索引',
    KEY idx_discussion_post_likes_user_id (user_id) COMMENT '用户ID索引',
    CONSTRAINT fk_discussion_post_likes_post FOREIGN KEY (post_id) REFERENCES discussion_posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_discussion_post_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程讨论回复点赞记录表';;

-- ── 2.11 在线监考模块 ──

-- proctor_sessions
CREATE TABLE proctor_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '监考会话ID，主键自增',
    exam_session_id BIGINT NOT NULL COMMENT '考试会话ID，关联exam_sessions表',
    student_id BIGINT NOT NULL COMMENT '考生ID，关联users表',
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态：IN_PROGRESS/ENDED/FLAGGED',
    started_at DATETIME NULL COMMENT '开始时间',
    ended_at DATETIME NULL COMMENT '结束时间',
    risk_score INT NOT NULL DEFAULT 0 COMMENT '风险评分（事件严重度累加）',
    risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW' COMMENT '风险等级：LOW/MEDIUM/HIGH',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_proctor_exam_student (exam_session_id, student_id) COMMENT '同一考试同一考生仅一个会话',
    KEY idx_proctor_sessions_student_id (student_id) COMMENT '考生ID索引',
    KEY idx_proctor_sessions_status (status) COMMENT '状态索引',
    CONSTRAINT fk_proctor_sessions_exam FOREIGN KEY (exam_session_id) REFERENCES exam_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_proctor_sessions_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在线监考会话表';;

-- proctor_events
CREATE TABLE proctor_events (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '事件ID，主键自增',
    proctor_session_id BIGINT NOT NULL COMMENT '监考会话ID，关联proctor_sessions表',
    type VARCHAR(30) NOT NULL COMMENT '事件类型：TAB_SWITCH/FACE_MISSING/MULTIPLE_FACE/MOBILE_DETECTED/COPY_PASTE/FULLSCREEN_EXIT/UNKNOWN',
    severity VARCHAR(20) NOT NULL DEFAULT 'LOW' COMMENT '严重度：LOW/MEDIUM/HIGH',
    detail TEXT NULL COMMENT '事件详情',
    evidence_url VARCHAR(500) NULL COMMENT '证据截图/录屏地址',
    captured_at DATETIME NULL COMMENT '事件捕获时间',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '复核状态：PENDING/REVIEWED/EXEMPTED',
    review_note VARCHAR(500) NULL COMMENT '复核结论备注',
    reviewed_at DATETIME NULL COMMENT '复核时间',
    reviewer_id BIGINT NULL COMMENT '复核人ID（教师/管理员）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_proctor_events_session_id (proctor_session_id) COMMENT '会话ID索引',
    KEY idx_proctor_events_type (type) COMMENT '类型索引',
    CONSTRAINT fk_proctor_events_session FOREIGN KEY (proctor_session_id) REFERENCES proctor_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='监考风险事件表';;

-- ── 2.12 成绩方案模块 ──

-- grade_policies
CREATE TABLE grade_policies (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '方案ID，主键自增',
    course_id BIGINT NOT NULL COMMENT '课程ID，关联courses表',
    name VARCHAR(255) NOT NULL COMMENT '方案名称',
    components TEXT NULL COMMENT '维度权重JSON：[{"key","name","weight","source"}]',
    creator_id BIGINT NULL COMMENT '创建者ID，关联users表',
    is_default TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否课程默认方案：0-否，1-是',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_grade_policy_course (course_id) COMMENT '每门课程一套默认方案',
    KEY idx_grade_policies_creator (creator_id) COMMENT '创建者索引',
    CONSTRAINT fk_grade_policies_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_grade_policies_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成绩方案表';;

-- grade_items
CREATE TABLE grade_items (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID，主键自增',
    policy_id BIGINT NOT NULL COMMENT '成绩方案ID，关联grade_policies表',
    student_id BIGINT NOT NULL COMMENT '学生ID，关联users表',
    component_key VARCHAR(50) NOT NULL COMMENT '维度键（与方案components[].key对应）',
    score DECIMAL(5,2) NULL COMMENT '归一化得分（0-100）',
    raw_score DECIMAL(8,2) NULL COMMENT '原始分',
    source_id BIGINT NULL COMMENT '数据来源ID（submission/exam/sign_in等）',
    remark VARCHAR(255) NULL COMMENT '备注',
    grader_id BIGINT NULL COMMENT '录入者ID，关联users表',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_grade_item (policy_id, student_id, component_key) COMMENT '同一方案同一学生同一维度唯一',
    KEY idx_grade_items_student_id (student_id) COMMENT '学生ID索引',
    KEY idx_grade_items_policy_id (policy_id) COMMENT '方案ID索引',
    CONSTRAINT fk_grade_items_policy FOREIGN KEY (policy_id) REFERENCES grade_policies(id) ON DELETE CASCADE,
    CONSTRAINT fk_grade_items_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_grade_items_grader FOREIGN KEY (grader_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成绩明细项表';;

-- ====================================================================
-- Phase 3: 交叉外键约束（表创建完成后补充）
-- ====================================================================

ALTER TABLE users ADD CONSTRAINT fk_users_classroom FOREIGN KEY (class_id) REFERENCES classrooms(id) ON DELETE SET NULL;

-- ====================================================================
-- Phase 4: 初始化种子数据
-- ====================================================================
-- ⚠️ 安全警告：以下种子用户密码均为 "123456"（BCrypt 加密），
--    仅适用于本地开发环境。生产部署前必须：
--    1) 在数据库直接执行 UPDATE 修改密码哈希
--    2) 或通过系统提供的接口重置密码
--    3) 或添加首次登录强制修改密码逻辑
-- ====================================================================
-- ====================================================================

-- 初始化数据
-- ============================================================

-- 1. 用户数据
-- ============================================================
-- 【安全警告 ⚠】以下演示账号密码（123456）仅供开发/测试环境使用！
-- 生产环境部署前必须执行以下操作：
--   1. 删除或注释掉以下演示用户 INSERT 语句
--   2. 通过系统注册页面或管理后台重新创建管理员账号（使用强密码）
--   3. 确保所有用户密码符合复杂度要求（≥8位，含大小写字母、数字、特殊字符）
-- 使用弱密码或演示账号上生产将导致严重的安全风险！
-- ============================================================
-- 所有用户默认密码：123456（BCrypt加密）
INSERT INTO users (id, email, username, password, full_name, role, is_active, is_verified, phone, student_id, major, grade, department, title) VALUES
(1, 'admin@aicourse.com', 'admin', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '系统管理员', 'ADMIN', 1, 1, '13800000001', NULL, NULL, NULL, NULL, NULL),
(2, 'teacher@aicourse.com', 'teacher', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '张教授', 'TEACHER', 1, 1, '13800000002', NULL, NULL, NULL, '计算机学院', '教授'),
(3, 'teacher2@aicourse.com', 'teacher2', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '李副教授', 'TEACHER', 1, 1, '13800000003', NULL, NULL, NULL, '数学学院', '副教授'),
(4, 'student@aicourse.com', 'student', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '王同学', 'STUDENT', 1, 1, '13800000004', '2024001', '计算机科学', '大一', NULL, NULL),
(5, 'student2@aicourse.com', 'student2', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '赵同学', 'STUDENT', 1, 1, '13800000005', '2024002', '计算机科学', '大一', NULL, NULL),
(6, 'student3@aicourse.com', 'student3', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '刘同学', 'STUDENT', 1, 1, '13800000006', '2024003', '软件工程', '大一', NULL, NULL),
(7, 'student4@aicourse.com', 'student4', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '陈同学', 'STUDENT', 1, 1, '13800000007', '2024004', '软件工程', '大一', NULL, NULL),
(8, 'student5@aicourse.com', 'student5', '$2a$10$JiGc4/6C64A/jcAODg4JpOwB6z3KbNxAJnRsYLJAl/BHNBfsuU4tq', '杨同学', 'STUDENT', 1, 1, '13800000008', '2024005', '人工智能', '大一', NULL, NULL);

-- 2. 班级数据
INSERT INTO classrooms (id, name, code, grade_level, major, advisor_id, student_count) VALUES
(1, '计算机1班', 'CS2024-1', '大一', '计算机科学', 2, 2),
(2, '软件1班', 'SE2024-1', '大一', '软件工程', 2, 2),
(3, 'AI班', 'AI2024-1', '大一', '人工智能', 2, 1),
(4, '数学1班', 'MATH2024-1', '大一', '数学', 3, 0);

-- 更新学生的class_id
UPDATE users SET class_id = 1 WHERE id IN (4, 5);
UPDATE users SET class_id = 2 WHERE id IN (6, 7);
UPDATE users SET class_id = 3 WHERE id = 8;

-- 3. 课程数据
INSERT INTO courses (id, name, code, description, credits, teacher_id, status, approval_status, enrollment_type, category, difficulty) VALUES
(1, 'Python程序设计', 'CS101', 'Python编程语言基础课程，适合零基础学生', 3.00, 2, 'active', 'APPROVED', 'FREE', '计算机', '入门'),
(2, '数据结构与算法', 'CS201', '数据结构与算法核心课程，涵盖常见数据结构和算法设计', 4.00, 2, 'active', 'APPROVED', 'APPROVAL', '计算机', '困难'),
(3, '机器学习基础', 'CS301', '机器学习入门课程，介绍基本概念和常用算法', 3.00, 2, 'active', 'APPROVED', 'APPROVAL', '计算机', '中等'),
(4, '高等数学A', 'MATH101', '高等数学基础课程，包含微积分、线性代数等内容', 5.00, 3, 'active', 'APPROVED', 'FREE', '数学', '中等'),
(5, 'Web前端开发', 'CS102', 'Web前端开发技术课程，涵盖HTML/CSS/JavaScript', 3.00, 2, 'active', 'APPROVED', 'FREE', '计算机', '中等'),
(6, '数据库原理与应用', 'CS202', '数据库系统原理及SQL实践课程', 3.00, 2, 'active', 'APPROVED', 'FREE', '计算机', '中等');

-- 4. 选课数据
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

-- 5. 角色权限数据
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

-- 6. 系统配置数据
INSERT INTO system_configs (config_key, config_value, config_group, config_type, description, is_public, sort_order) VALUES
('site_name', 'AI智能课程平台', 'basic', 'STRING', '站点名称', 1, 1),
('site_description', '基于AI的智能在线教育平台', 'basic', 'STRING', '站点描述', 1, 2),
('ai_model', 'deepseek-chat', 'ai', 'STRING', 'AI模型名称', 0, 1),
('ai_api_key', '', 'ai', 'STRING', 'AI API密钥', 0, 2),
('ai.provider', 'deepseek', 'ai', 'STRING', '当前启用的LLM供应商（运行时热切换：deepseek/openai/qwen）', 0, 0),
('max_upload_size', '100', 'system', 'NUMBER', '最大上传文件大小(MB)', 0, 1),
-- 在线监考（考试防作弊）动态阈值配置：管理员可在治理页调控
('proctor.threshold.high', '6', 'proctor', 'NUMBER', '风险评分达到该值判定为高风险(HIGH)', 0, 1),
('proctor.threshold.medium', '3', 'proctor', 'NUMBER', '风险评分达到该值判定为中风险(MEDIUM)', 0, 2),
('proctor.weight.low', '1', 'proctor', 'NUMBER', 'LOW级事件计入风险评分的权重', 0, 3),
('proctor.weight.medium', '2', 'proctor', 'NUMBER', 'MEDIUM级事件计入风险评分的权重', 0, 4),
('proctor.weight.high', '3', 'proctor', 'NUMBER', 'HIGH级事件计入风险评分的权重', 0, 5);

-- 7. 通知数据 - 给每个学生创建欢迎通知
INSERT INTO notifications (user_id, title, content, category, type, is_read, priority) VALUES
(4, '欢迎加入AI智能课程平台', '王同学，欢迎加入AI智能课程平台！在这里你可以选课、学习、参加考试，体验AI辅助学习的乐趣。', 'system', 'SYSTEM', 0, 'normal'),
(5, '欢迎加入AI智能课程平台', '赵同学，欢迎加入AI智能课程平台！在这里你可以选课、学习、参加考试，体验AI辅助学习的乐趣。', 'system', 'SYSTEM', 0, 'normal'),
(6, '欢迎加入AI智能课程平台', '刘同学，欢迎加入AI智能课程平台！在这里你可以选课、学习、参加考试，体验AI辅助学习的乐趣。', 'system', 'SYSTEM', 0, 'normal'),
(7, '欢迎加入AI智能课程平台', '陈同学，欢迎加入AI智能课程平台！在这里你可以选课、学习、参加考试，体验AI辅助学习的乐趣。', 'system', 'SYSTEM', 0, 'normal'),
(8, '欢迎加入AI智能课程平台', '杨同学，欢迎加入AI智能课程平台！在这里你可以选课、学习、参加考试，体验AI辅助学习的乐趣。', 'system', 'SYSTEM', 0, 'normal');

-- ====================================================================
-- Phase 3: 性能索引优化（高频查询加速）
-- ====================================================================
-- users表：username + full_name + email 全文搜索索引（解决 LIKE '%keyword%' 全表扫描）
ALTER TABLE users ADD FULLTEXT INDEX ft_users_search (username, full_name, email);

-- courses表：category + status 复合索引（按分类和状态筛选课程）
ALTER TABLE courses ADD INDEX idx_courses_category_status (category, status);

-- submissions表：task_id + student_id 复合索引（按任务和学生查提交记录）
ALTER TABLE submissions ADD INDEX idx_submissions_task_student (task_id, student_id);

-- questions表：course_id + type 复合索引（按课程和题型查询题目）
ALTER TABLE questions ADD INDEX idx_questions_course_type (course_id, type);
