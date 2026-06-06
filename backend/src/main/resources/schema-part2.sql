-- ============================================
-- AI智能课程平台 - 数据库初始化 (Part 2: 业务表)
-- ============================================

USE ai_course_platform;

-- 2. 用户会话表
DROP TABLE IF EXISTS `user_sessions`;
CREATE TABLE `user_sessions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `session_token` VARCHAR(500) DEFAULT NULL,
  `refresh_token` VARCHAR(500) DEFAULT NULL,
  `device_name` VARCHAR(200) DEFAULT NULL,
  `browser` VARCHAR(100) DEFAULT NULL,
  `operating_system` VARCHAR(100) DEFAULT NULL,
  `ip_address` VARCHAR(45) DEFAULT NULL,
  `location` VARCHAR(200) DEFAULT NULL,
  `user_agent` VARCHAR(500) DEFAULT NULL,
  `is_active` TINYINT(1) DEFAULT 1,
  `is_current` TINYINT(1) DEFAULT 0,
  `login_time` DATETIME DEFAULT NULL,
  `last_activity` DATETIME DEFAULT NULL,
  `expires_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 登录日志表
DROP TABLE IF EXISTS `login_logs`;
CREATE TABLE `login_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT DEFAULT NULL,
  `username` VARCHAR(100) DEFAULT NULL,
  `login_status` VARCHAR(20) DEFAULT NULL,
  `ip_address` VARCHAR(45) DEFAULT NULL,
  `location` VARCHAR(200) DEFAULT NULL,
  `device_name` VARCHAR(200) DEFAULT NULL,
  `browser` VARCHAR(100) DEFAULT NULL,
  `operating_system` VARCHAR(100) DEFAULT NULL,
  `user_agent` VARCHAR(500) DEFAULT NULL,
  `failure_reason` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 验证码表
DROP TABLE IF EXISTS `verification_codes`;
CREATE TABLE `verification_codes` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(255) DEFAULT NULL,
  `phone` VARCHAR(20) DEFAULT NULL,
  `code` VARCHAR(10) NOT NULL,
  `type` VARCHAR(20) NOT NULL DEFAULT "REGISTER",
  `is_used` TINYINT(1) DEFAULT 0,
  `expires_at` DATETIME NOT NULL,
  `ip_address` VARCHAR(45) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_email_code` (`email`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 班级表
DROP TABLE IF EXISTS `classrooms`;
CREATE TABLE `classrooms` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `code` VARCHAR(20) DEFAULT NULL,
  `description` TEXT DEFAULT NULL,
  `grade_level` VARCHAR(20) DEFAULT NULL,
  `major` VARCHAR(100) DEFAULT NULL,
  `advisor_id` BIGINT DEFAULT NULL,
  `student_count` INT DEFAULT 0,
  `max_students` INT DEFAULT 50,
  `is_active` TINYINT(1) DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 课程表
DROP TABLE IF EXISTS `courses`;
CREATE TABLE `courses` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(200) NOT NULL,
  `code` VARCHAR(50) DEFAULT NULL,
  `description` TEXT DEFAULT NULL,
  `credits` INT DEFAULT NULL,
  `teacher_id` BIGINT DEFAULT NULL,
  `semester` VARCHAR(20) DEFAULT NULL,
  `year` INT DEFAULT NULL,
  `status` VARCHAR(20) DEFAULT "active",
  `approval_status` VARCHAR(20) DEFAULT "APPROVED",
  `enrollment_type` VARCHAR(20) DEFAULT "FREE",
  `max_students` INT DEFAULT 100,
  `is_active` TINYINT(1) DEFAULT 1,
  `category` VARCHAR(50) DEFAULT NULL,
  `difficulty` VARCHAR(20) DEFAULT "medium",
  `price` DECIMAL(10,2) DEFAULT 0,
  `tags` TEXT DEFAULT NULL,
  `start_date` DATE DEFAULT NULL,
  `end_date` DATE DEFAULT NULL,
  `cover_image` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. 课程选课表
DROP TABLE IF EXISTS `course_enrollments`;
CREATE TABLE `course_enrollments` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_id` BIGINT NOT NULL,
  `course_id` BIGINT NOT NULL,
  `approval_status` VARCHAR(20) DEFAULT "APPROVED",
  `is_active` TINYINT(1) DEFAULT 1,
  `final_score` DECIMAL(5,2) DEFAULT NULL,
  `attendance_score` DECIMAL(5,2) DEFAULT NULL,
  `assignment_score` DECIMAL(5,2) DEFAULT NULL,
  `exam_score` DECIMAL(5,2) DEFAULT NULL,
  `progress` DECIMAL(5,2) DEFAULT 0,
  `total_study_time` INT DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_course` (`student_id`, `course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. 课程章节表
DROP TABLE IF EXISTS `course_chapters`;
CREATE TABLE `course_chapters` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `course_id` BIGINT NOT NULL,
  `parent_id` BIGINT DEFAULT NULL,
  `chapter_name` VARCHAR(200) NOT NULL,
  `content` TEXT DEFAULT NULL,
  `sort_order` INT DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9-10. 资源表
DROP TABLE IF EXISTS `resource_versions`;
DROP TABLE IF EXISTS `resources`;
CREATE TABLE `resources` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `file_name` VARCHAR(255) NOT NULL,
  `file_type` VARCHAR(50) DEFAULT NULL,
  `url` VARCHAR(500) NOT NULL,
  `course_id` BIGINT DEFAULT NULL,
  `chapter_id` BIGINT DEFAULT NULL,
  `uploader_id` BIGINT DEFAULT NULL,
  `description` TEXT DEFAULT NULL,
  `visible_to_all` TINYINT(1) DEFAULT 1,
  `size` BIGINT DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `resource_versions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `resource_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `file_name` VARCHAR(255) DEFAULT NULL,
  `url` VARCHAR(500) DEFAULT NULL,
  `description` TEXT DEFAULT NULL,
  `size` BIGINT DEFAULT NULL,
  `uploader_id` BIGINT DEFAULT NULL,
  `is_active` TINYINT(1) DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_resource_id` (`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11-12. 作业提交表
DROP TABLE IF EXISTS `submissions`;
DROP TABLE IF EXISTS `tasks`;
CREATE TABLE `tasks` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(200) NOT NULL,
  `description` TEXT DEFAULT NULL,
  `task_type` VARCHAR(50) NOT NULL DEFAULT "homework",
  `course_id` BIGINT DEFAULT NULL,
  `creator_id` BIGINT DEFAULT NULL,
  `due_date` DATETIME DEFAULT NULL,
  `max_score` DECIMAL(5,2) DEFAULT 100,
  `is_published` TINYINT(1) DEFAULT 0,
  `status` VARCHAR(20) DEFAULT "draft",
  `requirements` TEXT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `submissions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `student_id` BIGINT NOT NULL,
  `content` TEXT DEFAULT NULL,
  `file_urls` JSON DEFAULT NULL,
  `status` VARCHAR(20) DEFAULT "SUBMITTED",
  `score` DECIMAL(5,2) DEFAULT NULL,
  `max_score` DECIMAL(5,2) DEFAULT NULL,
  `feedback` TEXT DEFAULT NULL,
  `ai_feedback` TEXT DEFAULT NULL,
  `grader_id` BIGINT DEFAULT NULL,
  `attempt_number` INT DEFAULT 1,
  `late_penalty` DECIMAL(5,2) DEFAULT 0,
  `time_spent` INT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
