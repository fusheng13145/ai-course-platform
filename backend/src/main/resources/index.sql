-- ============================================================
-- 数据库创建
-- ============================================================
-- 创建数据库，使用utf8mb4字符集支持emoji和所有中文
CREATE DATABASE IF NOT EXISTS ai_course_platform
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- 使用目标数据库
USE ai_course_platform;

-- 临时关闭外键约束，方便删除表
SET FOREIGN_KEY_CHECKS = 0;

-- 按依赖关系逆序删除表，避免外键约束错误
DROP TABLE IF EXISTS file_upload_sessions;
DROP TABLE IF EXISTS system_configs;
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS video_watch_sessions;
DROP TABLE IF EXISTS video_learning_records;
DROP TABLE IF EXISTS learning_records;
DROP TABLE IF EXISTS learning_path_nodes;
DROP TABLE IF EXISTS learning_paths;
DROP TABLE IF EXISTS wrong_questions;
DROP TABLE IF EXISTS chapter_knowledge_points;
DROP TABLE IF EXISTS knowledge_point_relations;
DROP TABLE IF EXISTS knowledge_points;
DROP TABLE IF EXISTS questions;
DROP TABLE IF EXISTS exam_sessions;
DROP TABLE IF EXISTS exam_questions;
DROP TABLE IF EXISTS exam_papers;
DROP TABLE IF EXISTS reminders;
DROP TABLE IF EXISTS plagiarism_matches;
DROP TABLE IF EXISTS plagiarism_checks;
DROP TABLE IF EXISTS submissions;
DROP TABLE IF EXISTS tasks;
DROP TABLE IF EXISTS resource_versions;
DROP TABLE IF EXISTS resources;
DROP TABLE IF EXISTS course_chapters;
DROP TABLE IF EXISTS course_enrollments;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS classrooms;
DROP TABLE IF EXISTS verification_codes;
DROP TABLE IF EXISTS login_logs;
DROP TABLE IF EXISTS user_sessions;
DROP TABLE IF EXISTS users;

-- 恢复外键约束
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 1. 用户核心模块
-- ============================================================

-- 用户表：存储系统所有用户信息，包括管理员、教师、学生
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
                       notification_settings JSON NULL COMMENT '通知设置，JSON格式',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 用户会话表：存储用户登录会话信息，支持多设备登录
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户会话表';

-- 登录日志表：记录所有登录尝试，用于安全审计
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- 验证码表：存储邮箱和手机验证码
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验证码表';

-- ============================================================
-- 2. 班级管理模块
-- ============================================================

-- 班级表：存储班级信息
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级表';

-- 为用户表添加班级外键约束（因为classrooms表在users表之后创建）
ALTER TABLE users ADD CONSTRAINT fk_users_classroom FOREIGN KEY (class_id) REFERENCES classrooms(id) ON DELETE SET NULL;

-- ============================================================
-- 3. 课程管理模块
-- ============================================================

-- 课程表：存储课程基本信息
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

-- 课程选课表：存储学生选课信息
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程选课表';

-- 课程章节表：存储课程章节信息，支持多级章节
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程章节表';

-- 教学资源表：存储课程相关的教学资源（视频、文档、PPT等）
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教学资源表';

-- 资源版本表：存储资源的历史版本
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源版本表';

-- ============================================================
-- 4. 作业与任务模块
-- ============================================================

-- 任务表：存储课程作业、测验等任务
CREATE TABLE tasks (
                       id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID，主键自增',
                       title VARCHAR(255) NOT NULL COMMENT '任务标题',
                       description TEXT NULL COMMENT '任务描述',
                       task_type VARCHAR(50) NOT NULL COMMENT '任务类型：assignment-作业，quiz-测验，project-项目',
                       course_id BIGINT NULL COMMENT '课程ID，关联courses表',
                       creator_id BIGINT NULL COMMENT '创建者ID，关联users表',
                       due_date DATETIME NULL COMMENT '截止日期',
                       max_score DECIMAL(5,2) NOT NULL DEFAULT 100 COMMENT '满分',
                       status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '任务状态：draft-草稿，published-已发布，closed-已关闭',
                       is_published TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已发布：0-未发布，1-已发布',
                       submission_format VARCHAR(100) NULL COMMENT '提交格式：text-文本，file-文件，both-两者均可',
                       requirements TEXT NULL COMMENT '详细要求',
                       attachment_url VARCHAR(500) NULL COMMENT '任务附件URL',
                       start_date DATETIME NULL COMMENT '开始日期',
                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                       updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                       PRIMARY KEY (id),
                       KEY idx_tasks_course_id (course_id) COMMENT '课程ID索引，用于查询课程所有任务',
                       KEY idx_tasks_creator_id (creator_id) COMMENT '创建者ID索引',
                       CONSTRAINT fk_tasks_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
                       CONSTRAINT fk_tasks_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

-- 提交记录表：存储学生作业提交记录
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提交记录表';

-- 查重记录表：存储作业查重结果
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='查重记录表';

-- 查重匹配表：存储查重匹配的详细片段
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='查重匹配表';

-- 催交提醒表：存储作业催交提醒
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='催交提醒表';

-- ============================================================
-- 5. 考试管理模块
-- ============================================================

-- 试卷表：存储考试试卷信息
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷表';

-- 题库表：存储所有题目
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库表';

-- 试卷题目表：存储试卷与题目的关联关系
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷题目表';

-- 考试会话表：存储学生考试会话信息
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
                               created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (id),
                               KEY idx_exam_sessions_paper_id (exam_paper_id) COMMENT '试卷ID索引，用于查询试卷所有考试会话',
                               KEY idx_exam_sessions_student_id (student_id) COMMENT '学生ID索引，用于查询学生所有考试会话',
                               CONSTRAINT fk_exam_sessions_paper FOREIGN KEY (exam_paper_id) REFERENCES exam_papers(id) ON DELETE CASCADE,
                               CONSTRAINT fk_exam_sessions_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试会话表';

-- ============================================================
-- 6. 知识点与学习路径模块
-- ============================================================

-- 知识点表：存储课程知识点信息，支持多级知识点
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点表';

-- 知识点关系表：存储知识点之间的关联关系
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点关系表';

-- 章节知识点关联表：存储章节与知识点的关联关系
CREATE TABLE chapter_knowledge_points (
                                          id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID，主键自增',
                                          chapter_id BIGINT NOT NULL COMMENT '章节ID，关联course_chapters表',
                                          knowledge_point_id BIGINT NOT NULL COMMENT '知识点ID，关联knowledge_points表',
                                          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                          PRIMARY KEY (id),
                                          UNIQUE KEY uk_chapter_knowledge_points (chapter_id, knowledge_point_id) COMMENT '章节-知识点唯一索引，防止重复关联',
                                          CONSTRAINT fk_chapter_knowledge_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapters(id) ON DELETE CASCADE,
                                          CONSTRAINT fk_chapter_knowledge_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_points(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节知识点关联表';

-- 错题表：存储学生的错题记录
CREATE TABLE wrong_questions (
                                 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '错题ID，主键自增',
                                 student_id BIGINT NOT NULL COMMENT '学生ID，关联users表',
                                 question_id BIGINT NOT NULL COMMENT '题目ID，关联questions表',
                                 course_id BIGINT NULL COMMENT '课程ID，关联courses表',
                                 wrong_answer TEXT NULL COMMENT '错误答案',
                                 reason TEXT NULL COMMENT '错误原因',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题表';

-- 学习路径表：存储学生的个性化学习路径
CREATE TABLE learning_paths (
                                id BIGINT NOT NULL AUTO_INCREMENT COMMENT '学习路径表ID，主键自增',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径表';

-- 学习路径节点表：存储学习路径的具体节点
CREATE TABLE learning_path_nodes (
                                     id BIGINT NOT NULL AUTO_INCREMENT COMMENT '节点ID，主键自增',
                                     learning_path_id BIGINT NOT NULL COMMENT '学习路径ID，关联learning_paths表',
                                     knowledge_point_id BIGINT NULL COMMENT '关联知识点ID，关联knowledge_points表',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径节点表';

-- ============================================================
-- 7. 学习记录模块
-- ============================================================

-- 学习记录表：存储学生的通用学习记录
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
                                  CONSTRAINT fk_learning_records_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习记录表';

-- 视频学习记录表：存储学生的视频学习详细记录
CREATE TABLE video_learning_records (
                                        id BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID，主键自增',
                                        user_id BIGINT NOT NULL COMMENT '用户ID，关联users表',
                                        resource_id BIGINT NOT NULL COMMENT '视频资源ID，关联resources表',
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
                                        CONSTRAINT fk_video_learning_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频学习记录表';

-- 视频观看会话表：存储学生的每次视频观看会话
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频观看会话表';

-- ============================================================
-- 8. 系统通用模块
-- ============================================================

-- 通知表：存储系统通知和消息
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';

-- 角色权限表：存储角色与权限的关联关系
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
                                  UNIQUE KEY uk_role_permission (role, permission_key) COMMENT '角色-权限唯一索引',
                                  KEY idx_role_permissions_parent (parent_id) COMMENT '父权限索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限表';

-- 系统配置表：存储系统全局配置参数
CREATE TABLE system_configs (
                                id BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID',
                                config_key VARCHAR(100) NOT NULL COMMENT '配置键名',
                                config_value TEXT NULL COMMENT '配置值',
                                config_type VARCHAR(50) NOT NULL DEFAULT 'string' COMMENT '配置类型：string/number/boolean/json',
                                module VARCHAR(50) NOT NULL DEFAULT 'system' COMMENT '所属模块',
                                description VARCHAR(255) NULL COMMENT '配置说明',
                                is_editable TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可编辑',
                                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (id),
                                UNIQUE KEY uk_system_configs_key (config_key) COMMENT '配置键唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 文件上传会话表：大文件分片上传会话记录
CREATE TABLE file_upload_sessions (
                                      id BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID',
                                      session_id VARCHAR(100) NOT NULL COMMENT '上传会话唯一标识',
                                      file_name VARCHAR(255) NOT NULL COMMENT '文件名',
                                      file_size BIGINT NOT NULL COMMENT '文件总大小(字节)',
                                      chunk_size INT NOT NULL COMMENT '分片大小(字节)',
                                      total_chunks INT NOT NULL COMMENT '总分片数',
                                      uploaded_chunks INT NOT NULL DEFAULT 0 COMMENT '已上传分片数',
                                      uploader_id BIGINT NULL COMMENT '上传用户ID',
                                      file_type VARCHAR(50) NULL COMMENT '文件类型',
                                      save_path VARCHAR(500) NULL COMMENT '文件存储路径',
                                      status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING' COMMENT '状态：UPLOADING/COMPLETED/FAILED',
                                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      completed_at DATETIME NULL COMMENT '完成时间',
                                      PRIMARY KEY (id),
                                      UNIQUE KEY uk_upload_session_id (session_id) COMMENT '会话ID唯一索引',
                                      KEY idx_uploader_id (uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传会话表';

-- 1. 班级表 classrooms
INSERT INTO classrooms (name, grade, student_count, teacher_id, created_at)
VALUES
    ('高一(1)班', '高一', 0, 1, NOW()),
    ('高一(2)班', '高一', 0, 1, NOW()),
    ('高二(1)班', '高二', 0, 2, NOW());

-- 2. 用户表 users 补充教师、学生账号
-- 密码统一：123456 加密串 $2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2
INSERT INTO users (email, username, password, full_name, role, class_id, phone, is_active, is_verified)
VALUES
    ('teacher1@aicourse.com', 'teacher1', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '张老师', 'TEACHER', NULL, '13800138001', 1, 1),
    ('teacher2@aicourse.com', 'teacher2', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李老师', 'TEACHER', NULL, '13800138002', 1, 1),
    ('stu01@aicourse.com', 'stu01', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '学生一号', 'STUDENT', 1, '13900139001', 1, 1),
    ('stu02@aicourse.com', 'stu02', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '学生二号', 'STUDENT', 1, '13900139002', 1, 1),
    ('stu03@aicourse.com', 'stu03', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '学生三号', 'STUDENT', 2, '13900139003', 1, 1);

-- 3. 课程表 courses
INSERT INTO courses (name, cover, description, teacher_id, status, start_time, end_time)
VALUES
    ('Python人工智能入门', '/cover/python.jpg', '零基础学习Python与AI基础', 1, 'published', '2026-03-01', '2026-07-01'),
    ('Java后端开发实战', '/cover/java.jpg', 'Java Web 全栈开发课程', 2, 'published', '2026-03-01', '2026-07-01'),
    ('计算机网络基础', '/cover/network.jpg', '网络原理与通信协议讲解', 1, 'draft', '2026-04-01', '2026-08-01');

-- 4. 课程章节表 course_chapters
INSERT INTO course_chapters (course_id, title, sort_num, is_published)
VALUES
    (1, '第一章 Python环境搭建', 1, 1),
    (1, '第二章 基础语法', 2, 1),
    (1, '第三章 函数与模块', 3, 1),
    (2, '第一章 Java环境配置', 1, 1),
    (2, '第二章 面向对象', 2, 1);

-- 5. 课程资源表 resources
INSERT INTO resources (course_id, chapter_id, name, file_url, file_type, file_size)
VALUES
    (1, 1, 'Python安装教程.pdf', '/res/python_install.pdf', 'document', 204800),
    (1, 2, '基础语法讲解视频', '/res/syntax.mp4', 'video', 10240000),
    (2, 1, 'JDK配置文档', '/res/jdk.pdf', 'document', 153600);

-- 6. 选课记录表 course_enrollments
INSERT INTO course_enrollments (student_id, course_id, progress, total_study_time, attendance_score, assignment_score, exam_score, final_score)
VALUES
    (3, 1, 45.00, 3600, 85, 88, 90, 88),
    (4, 1, 30.00, 2400, 82, 80, 85, 82),
    (5, 2, 20.00, 1800, 90, 86, 88, 88);

-- 7. 作业表 tasks
INSERT INTO tasks (course_id, chapter_id, title, content, due_date, status)
VALUES
    (1, 2, 'Python变量练习', '完成课后变量与运算符习题', '2026-06-15 23:59:59', 'published'),
    (1, 3, '函数编程作业', '自定义函数实现简单计算器', '2026-06-20 23:59:59', 'published'),
    (2, 1, 'JDK环境测试', '提交环境测试截图', '2026-06-18 23:59:59', 'draft');

-- 8. 作业提交表 submissions
INSERT INTO submissions (task_id, student_id, content, file_urls, status, score)
VALUES
    (1, 3, '已完成所有习题', '/sub/ans1.jpg', 'submitted', 86),
    (1, 4, '基础练习完成', '/sub/ans2.jpg', 'submitted', 82);

-- 9. 题库表 question_bank
INSERT INTO question_bank (course_id, title, question_type, content, answer, score)
VALUES
    (1, 'Python注释符号是？', 'single', 'A. //  B. #  C. /* */', 'B', 5),
    (1, '下列不属于数据类型的是？', 'single', 'A. int B. str C. func', 'C', 5),
    (2, 'Java主方法关键字', 'single', 'A. main B. Main C. MAIN', 'A', 5);

-- 10. 试卷表 exam_papers
INSERT INTO exam_papers (course_id, title, total_score, duration, status)
VALUES
    (1, 'Python期中测试卷', 100, 90, 'published'),
    (2, 'Java入门测验', 100, 60, 'draft');

-- 11. 考试记录表 exam_sessions
INSERT INTO exam_sessions (exam_paper_id, student_id, start_time, end_time, score, status)
VALUES
    (1, 3, '2026-06-01 10:00:00', '2026-06-01 11:20:00', 89, 'finished'),
    (1, 4, '2026-06-01 10:10:00', NULL, NULL, 'ongoing');

-- 12. 学习记录表 learning_records
INSERT INTO learning_records (user_id, chapter_id, action_type, study_duration)
VALUES
    (3, 1, 'complete', 1200),
    (3, 2, 'view', 800),
    (4, 1, 'complete', 900);

-- 13. 通知消息表 notifications
INSERT INTO notifications (user_id, title, content, is_read)
VALUES
    (3, '课程更新提醒', '《Python人工智能入门》新增章节', 0),
    (4, '作业截止提醒', '变量练习作业即将到期', 1);

-- 14. 提醒表 reminders
INSERT INTO reminders (reminder_type, user_id, task_id, course_id, title, message, due_date, trigger_day, scheduled_at)
VALUES
    ('ASSIGNMENT_DUE', 3, 1, 1, '作业即将截止', '变量练习作业还有3天截止', '2026-06-15 23:59:59', 3, '2026-06-12');

-- 15. 作业查重记录表 plagiarism_checks
INSERT INTO plagiarism_checks (submission_id, student_id, task_id, status, similarity)
VALUES
    (1, 3, 1, 'pass', 5.2),
    (2, 4, 1, 'pass', 6.8);

-- 16. 登录日志表 login_logs
INSERT INTO login_logs (user_id, login_ip, login_address, login_status)
VALUES
    (1, '127.0.0.1', '本地内网', 'success'),
    (3, '192.168.1.101', '校园网', 'success');

-- 17. 文件上传会话表 file_upload_sessions
INSERT INTO file_upload_sessions (session_id, file_name, file_size, chunk_size, total_chunks, uploaded_chunks, uploader_id, file_type, save_path, status)
VALUES
    ('sess_001', '课件.pptx', 5242880, 1048576, 5, 5, 1, 'document', '/upload/ppt/', 'COMPLETED'),
    ('sess_002', '讲课视频.mp4', 20971520, 2097152, 10, 6, 1, 'video', '/upload/video/', 'UPLOADING');