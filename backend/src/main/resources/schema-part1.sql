-- ============================================
-- AI智能课程平台 - 数据库初始化脚本 (Part 1: 表结构)
-- ============================================

CREATE DATABASE IF NOT EXISTS ai_course_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_course_platform;

-- 1. 用户表
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT "主键",
  `email` VARCHAR(255) DEFAULT NULL COMMENT "邮箱",
  `username` VARCHAR(100) NOT NULL COMMENT "用户名",
  `password` VARCHAR(255) NOT NULL COMMENT "BCrypt加密密码",
  `full_name` VARCHAR(100) DEFAULT NULL COMMENT "姓名",
  `role` VARCHAR(20) NOT NULL DEFAULT "STUDENT" COMMENT "角色: ADMIN/TEACHER/STUDENT",
  `is_active` TINYINT(1) DEFAULT 1 COMMENT "是否启用",
  `is_verified` TINYINT(1) DEFAULT 0 COMMENT "是否验证",
  `avatar_url` VARCHAR(500) DEFAULT NULL COMMENT "头像URL",
  `phone` VARCHAR(20) DEFAULT NULL COMMENT "手机号",
  `student_id` VARCHAR(50) DEFAULT NULL COMMENT "学号",
  `major` VARCHAR(100) DEFAULT NULL COMMENT "专业",
  `grade` VARCHAR(20) DEFAULT NULL COMMENT "年级",
  `class_name` VARCHAR(50) DEFAULT NULL COMMENT "班级名称",
  `class_id` BIGINT DEFAULT NULL COMMENT "班级ID",
  `department` VARCHAR(100) DEFAULT NULL COMMENT "院系",
  `title` VARCHAR(50) DEFAULT NULL COMMENT "职称",
  `bio` TEXT DEFAULT NULL COMMENT "简介",
  `failed_login_attempts` INT DEFAULT 0 COMMENT "登录失败次数",
  `locked_until` DATETIME DEFAULT NULL COMMENT "锁定截至时间",
  `register_ip` VARCHAR(45) DEFAULT NULL COMMENT "注册IP",
  `last_login` DATETIME DEFAULT NULL COMMENT "最后登录时间",
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT="用户表";
