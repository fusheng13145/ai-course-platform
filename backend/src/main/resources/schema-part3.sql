-- ============================================
-- Part 3: 考试和题目相关表
-- ============================================
USE ai_course_platform;

-- 试卷表
DROP TABLE IF EXISTS `exam_questions`;
DROP TABLE IF EXISTS `exam_sessions`;
DROP TABLE IF EXISTS `exam_papers`;
CREATE TABLE `exam_papers` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(200) NOT NULL,
  `description` TEXT DEFAULT NULL,
  `course_id` BIGINT DEFAULT NULL,
  `creator_id` BIGINT DEFAULT NULL,
  `status` VARCHAR(20) DEFAULT "draft",
  `instructions` TEXT DEFAULT NULL,
  `start_time` DATETIME DEFAULT NULL,
  `end_time` DATETIME DEFAULT NULL,
  `total_score` DECIMAL(5,2) DEFAULT NULL,
  `duration` INT DEFAULT NULL,
  `question_count` INT DEFAULT 0,
  `shuffle_questions` TINYINT(1) DEFAULT 0,
  `shuffle_options` TINYINT(1) DEFAULT 0,
  `max_attempts` INT DEFAULT 1,
  `is_active` TINYINT(1) DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `exam_questions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `exam_paper_id` BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `order_index` INT DEFAULT NULL,
  `points` DECIMAL(5,2) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_paper_id` (`exam_paper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `exam_sessions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `exam_paper_id` BIGINT NOT NULL,
  `student_id` BIGINT NOT NULL,
  `status` VARCHAR(20) DEFAULT "STARTED",
  `attempt_number` INT DEFAULT 1,
  `start_time` DATETIME DEFAULT NULL,
  `submit_time` DATETIME DEFAULT NULL,
  `time_spent` INT DEFAULT NULL,
  `total_score` DECIMAL(5,2) DEFAULT NULL,
  `answers` JSON DEFAULT NULL,
  `question_scores` JSON DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_paper_id` (`exam_paper_id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 题目表
DROP TABLE IF EXISTS `questions`;
CREATE TABLE `questions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `type` VARCHAR(20) NOT NULL,
  `content` TEXT NOT NULL,
  `options` JSON DEFAULT NULL,
  `correct_answers` JSON DEFAULT NULL,
  `difficulty` VARCHAR(20) DEFAULT "medium",
  `score` DECIMAL(5,2) DEFAULT 5,
  `knowledge_points` JSON DEFAULT NULL,
  `explanation` TEXT DEFAULT NULL,
  `course_id` BIGINT DEFAULT NULL,
  `creator_id` BIGINT DEFAULT NULL,
  `status` VARCHAR(20) DEFAULT "published",
  `usage_count` INT DEFAULT 0,
  `correct_rate` DECIMAL(5,2) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识点体系
DROP TABLE IF EXISTS `knowledge_point_relations`;
DROP TABLE IF EXISTS `chapter_knowledge_points`;
DROP TABLE IF EXISTS `knowledge_points`;
CREATE TABLE `knowledge_points` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(200) NOT NULL,
  `description` TEXT DEFAULT NULL,
  `course_id` BIGINT DEFAULT NULL,
  `parent_id` BIGINT DEFAULT NULL,
  `level` INT DEFAULT 0,
  `sort_order` INT DEFAULT 0,
  `importance` INT DEFAULT 0,
  `difficulty` VARCHAR(20) DEFAULT "medium",
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `knowledge_point_relations` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `source_id` BIGINT NOT NULL,
  `target_id` BIGINT NOT NULL,
  `relation_type` VARCHAR(50) NOT NULL,
  `weight` INT DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_src_tgt_type` (`source_id`, `target_id`, `relation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学习路径
DROP TABLE IF EXISTS `learning_path_nodes`;
DROP TABLE IF EXISTS `learning_paths`;
CREATE TABLE `learning_paths` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_id` BIGINT NOT NULL,
  `name` VARCHAR(200) DEFAULT NULL,
  `description` TEXT DEFAULT NULL,
  `goal` TEXT DEFAULT NULL,
  `status` VARCHAR(20) DEFAULT "ACTIVE",
  `progress` DECIMAL(5,2) DEFAULT 0,
  `estimated_days` INT DEFAULT NULL,
  `generated_by` VARCHAR(50) DEFAULT "AI",
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `learning_path_nodes` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `path_id` BIGINT NOT NULL,
  `knowledge_point_id` BIGINT DEFAULT NULL,
  `node_name` VARCHAR(200) DEFAULT NULL,
  `node_type` VARCHAR(50) DEFAULT NULL,
  `sort_order` INT DEFAULT 0,
  `is_completed` TINYINT(1) DEFAULT 0,
  `completed_at` DATETIME DEFAULT NULL,
  `estimated_minutes` INT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_path_id` (`path_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学习记录
DROP TABLE IF EXISTS `learning_records`;
CREATE TABLE `learning_records` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_id` BIGINT NOT NULL,
  `course_id` BIGINT DEFAULT NULL,
  `chapter_id` BIGINT DEFAULT NULL,
  `knowledge_point_id` BIGINT DEFAULT NULL,
  `activity_type` VARCHAR(50) DEFAULT NULL,
  `duration` INT DEFAULT 0,
  `score` DECIMAL(5,2) DEFAULT NULL,
  `study_date` DATE DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_study_date` (`study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 视频学习记录
DROP TABLE IF EXISTS `video_watch_sessions`;
DROP TABLE IF EXISTS `video_learning_records`;
CREATE TABLE `video_learning_records` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_id` BIGINT NOT NULL,
  `resource_id` BIGINT NOT NULL,
  `watch_duration` INT DEFAULT 0,
  `total_duration` INT DEFAULT 0,
  `progress` DECIMAL(5,2) DEFAULT 0,
  `last_position` INT DEFAULT 0,
  `is_completed` TINYINT(1) DEFAULT 0,
  `play_count` INT DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_video` (`student_id`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `video_watch_sessions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `video_record_id` BIGINT NOT NULL,
  `student_id` BIGINT NOT NULL,
  `start_position` INT DEFAULT 0,
  `end_position` INT DEFAULT 0,
  `duration` INT DEFAULT 0,
  `session_start` DATETIME DEFAULT NULL,
  `session_end` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_record_id` (`video_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 错题本
DROP TABLE IF EXISTS `wrong_questions`;
CREATE TABLE `wrong_questions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_id` BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `source` VARCHAR(50) DEFAULT "exam",
  `source_id` BIGINT DEFAULT NULL,
  `student_answer` JSON DEFAULT NULL,
  `correct_answer` JSON DEFAULT NULL,
  `is_mastered` TINYINT(1) DEFAULT 0,
  `mastered_at` DATETIME DEFAULT NULL,
  `wrong_count` INT DEFAULT 1,
  `last_wrong_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_question` (`student_id`, `question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
