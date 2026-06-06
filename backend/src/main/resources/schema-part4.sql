-- ============================================
-- Part 4: 通知/权限/配置表 + Mock数据
-- ============================================
USE ai_course_platform;

-- 通知表
DROP TABLE IF EXISTS `notifications`;
CREATE TABLE `notifications` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `content` TEXT DEFAULT NULL,
  `category` VARCHAR(50) DEFAULT "system",
  `priority` VARCHAR(20) DEFAULT "normal",
  `is_read` TINYINT(1) DEFAULT 0,
  `action_url` VARCHAR(500) DEFAULT NULL,
  `data` JSON DEFAULT NULL,
  `sender_id` BIGINT DEFAULT NULL,
  `read_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_is_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色权限表
DROP TABLE IF EXISTS `role_permissions`;
CREATE TABLE `role_permissions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role` VARCHAR(20) NOT NULL,
  `permission_key` VARCHAR(100) NOT NULL,
  `permission_name` VARCHAR(100) DEFAULT NULL,
  `permission_type` VARCHAR(20) DEFAULT "MENU",
  `resource_path` VARCHAR(200) DEFAULT NULL,
  `sort_order` INT DEFAULT 0,
  `is_active` TINYINT(1) DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role`, `permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 查重表
DROP TABLE IF EXISTS `plagiarism_matches`;
DROP TABLE IF EXISTS `plagiarism_checks`;
CREATE TABLE `plagiarism_checks` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT DEFAULT NULL,
  `submission_id` BIGINT DEFAULT NULL,
  `student_id` BIGINT DEFAULT NULL,
  `check_type` VARCHAR(20) DEFAULT "INTERNAL",
  `status` VARCHAR(20) DEFAULT "PENDING",
  `similarity_score` DECIMAL(5,2) DEFAULT NULL,
  `high_similarity_count` INT DEFAULT 0,
  `result_summary` TEXT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 系统配置表
DROP TABLE IF EXISTS `system_configs`;
CREATE TABLE `system_configs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_key` VARCHAR(100) NOT NULL,
  `config_value` TEXT DEFAULT NULL,
  `config_group` VARCHAR(50) DEFAULT "general",
  `description` TEXT DEFAULT NULL,
  `sort_order` INT DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
