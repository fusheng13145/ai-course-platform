-- AI智能课程平台 - MyBatis-Plus版数据库初始化脚本
-- 新项目手写SQL，旧项目仅作表结构参考

CREATE DATABASE IF NOT EXISTS ai_course_platform
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE ai_course_platform;

SET FOREIGN_KEY_CHECKS = 0;

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

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(255) NOT NULL,
  username VARCHAR(100) NOT NULL,
  password VARCHAR(255) NOT NULL,
  full_name VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  is_verified TINYINT(1) NOT NULL DEFAULT 0,
  avatar_url VARCHAR(500) NULL,
  phone VARCHAR(20) NULL,
  student_id VARCHAR(50) NULL,
  major VARCHAR(100) NULL,
  grade VARCHAR(20) NULL,
  class_name VARCHAR(100) NULL,
  class_id BIGINT NULL,
  teacher_id VARCHAR(50) NULL,
  department VARCHAR(100) NULL,
  title VARCHAR(50) NULL,
  bio TEXT NULL,
  notification_settings JSON NULL,
  failed_login_attempts INT NOT NULL DEFAULT 0,
  locked_until DATETIME NULL,
  register_ip VARCHAR(50) NULL,
  last_login DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_username (username),
  KEY idx_users_role (role),
  KEY idx_users_class_id (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE user_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  session_token VARCHAR(500) NOT NULL,
  refresh_token VARCHAR(500) NULL,
  device_name VARCHAR(200) NULL,
  browser VARCHAR(100) NULL,
  operating_system VARCHAR(100) NULL,
  ip_address VARCHAR(50) NULL,
  location VARCHAR(200) NULL,
  user_agent VARCHAR(500) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  is_current TINYINT(1) NOT NULL DEFAULT 0,
  login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_activity DATETIME NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_sessions_user_id (user_id),
  KEY idx_user_sessions_token (session_token(191)),
  CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户会话表';

CREATE TABLE login_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  username VARCHAR(100) NULL,
  login_status VARCHAR(20) NOT NULL,
  ip_address VARCHAR(50) NULL,
  location VARCHAR(200) NULL,
  device_name VARCHAR(200) NULL,
  browser VARCHAR(100) NULL,
  operating_system VARCHAR(100) NULL,
  user_agent VARCHAR(500) NULL,
  failure_reason VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_login_logs_user_id (user_id),
  KEY idx_login_logs_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

CREATE TABLE verification_codes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(255) NULL,
  phone VARCHAR(20) NULL,
  code VARCHAR(10) NOT NULL,
  type VARCHAR(30) NOT NULL,
  used TINYINT(1) NOT NULL DEFAULT 0,
  expires_at DATETIME NOT NULL,
  ip_address VARCHAR(50) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_verification_codes_email (email),
  KEY idx_verification_codes_phone (phone),
  KEY idx_verification_codes_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验证码表';

CREATE TABLE classrooms (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  code VARCHAR(50) NOT NULL,
  description TEXT NULL,
  grade_level VARCHAR(50) NULL,
  major VARCHAR(100) NULL,
  advisor_id BIGINT NULL,
  student_count INT NOT NULL DEFAULT 0,
  max_students INT NOT NULL DEFAULT 50,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_classrooms_code (code),
  KEY idx_classrooms_advisor_id (advisor_id),
  CONSTRAINT fk_classrooms_advisor FOREIGN KEY (advisor_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级表';

ALTER TABLE users ADD CONSTRAINT fk_users_classroom FOREIGN KEY (class_id) REFERENCES classrooms(id) ON DELETE SET NULL;

CREATE TABLE courses (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  code VARCHAR(50) NOT NULL,
  description TEXT NULL,
  credits DECIMAL(5,2) NOT NULL DEFAULT 0,
  teacher_id BIGINT NOT NULL,
  semester VARCHAR(50) NULL,
  year INT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  approval_time DATETIME NULL,
  approved_by BIGINT NULL,
  approval_comment VARCHAR(500) NULL,
  enrollment_type VARCHAR(30) NOT NULL DEFAULT 'FREE',
  max_students INT NOT NULL DEFAULT 100,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  category VARCHAR(50) NULL,
  difficulty VARCHAR(20) NULL,
  price DECIMAL(10,2) NOT NULL DEFAULT 0,
  tags JSON NULL,
  start_date DATETIME NULL,
  end_date DATETIME NULL,
  cover_image VARCHAR(500) NULL,
  syllabus_url VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_courses_code (code),
  KEY idx_courses_teacher_id (teacher_id),
  KEY idx_courses_status (status),
  KEY idx_courses_approval_status (approval_status),
  CONSTRAINT fk_courses_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

CREATE TABLE course_enrollments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
  approval_time DATETIME NULL,
  approved_by BIGINT NULL,
  approval_comment VARCHAR(500) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  final_score DECIMAL(5,2) NULL,
  attendance_score DECIMAL(5,2) NULL,
  assignment_score DECIMAL(5,2) NULL,
  exam_score DECIMAL(5,2) NULL,
  progress DECIMAL(5,2) NOT NULL DEFAULT 0,
  total_study_time INT NOT NULL DEFAULT 0,
  enrolled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_enrollments_student_course (student_id, course_id),
  KEY idx_course_enrollments_course_id (course_id),
  CONSTRAINT fk_course_enrollments_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_course_enrollments_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程选课表';

CREATE TABLE course_chapters (
  id BIGINT NOT NULL AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT NULL,
  order_index INT NOT NULL DEFAULT 0,
  duration INT NULL,
  is_published TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_course_chapters_course_id (course_id),
  KEY idx_course_chapters_parent_id (parent_id),
  CONSTRAINT fk_course_chapters_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_course_chapters_parent FOREIGN KEY (parent_id) REFERENCES course_chapters(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程章节表';

CREATE TABLE resources (
  id BIGINT NOT NULL AUTO_INCREMENT,
  file_name VARCHAR(255) NOT NULL,
  file_type VARCHAR(50) NULL,
  url VARCHAR(500) NOT NULL,
  course_id BIGINT NULL,
  chapter_id BIGINT NULL,
  uploader_id BIGINT NULL,
  upload_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  description TEXT NULL,
  visible_to_all TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  KEY idx_resources_course_id (course_id),
  KEY idx_resources_chapter_id (chapter_id),
  CONSTRAINT fk_resources_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_resources_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapters(id) ON DELETE SET NULL,
  CONSTRAINT fk_resources_uploader FOREIGN KEY (uploader_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教学资源表';

CREATE TABLE resource_versions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  resource_id BIGINT NOT NULL,
  version_number INT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_type VARCHAR(50) NULL,
  url VARCHAR(500) NOT NULL,
  description TEXT NULL,
  file_size BIGINT NULL,
  uploader_id BIGINT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_resource_versions_resource_version (resource_id, version_number),
  CONSTRAINT fk_resource_versions_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源版本表';

CREATE TABLE tasks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  description TEXT NULL,
  task_type VARCHAR(50) NOT NULL,
  course_id BIGINT NULL,
  creator_id BIGINT NULL,
  due_date DATETIME NULL,
  max_score DECIMAL(5,2) NOT NULL DEFAULT 100,
  status VARCHAR(20) NOT NULL DEFAULT 'draft',
  is_published TINYINT(1) NOT NULL DEFAULT 0,
  submission_format VARCHAR(100) NULL,
  requirements TEXT NULL,
  attachment_url VARCHAR(500) NULL,
  start_date DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tasks_course_id (course_id),
  KEY idx_tasks_creator_id (creator_id),
  CONSTRAINT fk_tasks_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_tasks_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

CREATE TABLE submissions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  content TEXT NULL,
  file_urls JSON NULL,
  submission_data JSON NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
  submitted_at DATETIME NULL,
  graded_at DATETIME NULL,
  returned_at DATETIME NULL,
  score DECIMAL(5,2) NULL,
  max_score DECIMAL(5,2) NULL,
  percentage DECIMAL(5,2) NULL,
  feedback TEXT NULL,
  ai_feedback TEXT NULL,
  grader_id BIGINT NULL,
  attempt_number INT NOT NULL DEFAULT 1,
  is_late TINYINT(1) NOT NULL DEFAULT 0,
  late_penalty DECIMAL(5,2) NOT NULL DEFAULT 0,
  time_spent INT NOT NULL DEFAULT 0,
  view_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_submissions_task_id (task_id),
  KEY idx_submissions_student_id (student_id),
  CONSTRAINT fk_submissions_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
  CONSTRAINT fk_submissions_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_submissions_grader FOREIGN KEY (grader_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提交记录表';

CREATE TABLE plagiarism_checks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NULL,
  submission_id BIGINT NOT NULL,
  student_id BIGINT NULL,
  check_type VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  similarity_score DECIMAL(5,2) NULL,
  high_similarity_count INT NOT NULL DEFAULT 0,
  result_summary TEXT NULL,
  check_detail JSON NULL,
  checked_at DATETIME NULL,
  error_message TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_plagiarism_checks_submission_id (submission_id),
  CONSTRAINT fk_plagiarism_checks_submission FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='查重记录表';

CREATE TABLE plagiarism_matches (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plagiarism_check_id BIGINT NOT NULL,
  matched_submission_id BIGINT NULL,
  matched_source VARCHAR(500) NULL,
  similarity DECIMAL(5,2) NULL,
  original_text TEXT NULL,
  matched_text TEXT NULL,
  match_type VARCHAR(20) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_plagiarism_matches_check_id (plagiarism_check_id),
  CONSTRAINT fk_plagiarism_matches_check FOREIGN KEY (plagiarism_check_id) REFERENCES plagiarism_checks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='查重匹配表';

CREATE TABLE reminders (
  id BIGINT NOT NULL AUTO_INCREMENT,
  reminder_type VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  user_id BIGINT NOT NULL,
  task_id BIGINT NULL,
  course_id BIGINT NULL,
  title VARCHAR(200) NOT NULL,
  message TEXT NULL,
  due_date DATETIME NULL,
  trigger_day INT NULL,
  scheduled_at DATETIME NULL,
  sent_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_reminders_user_id (user_id),
  CONSTRAINT fk_reminders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='催交提醒表';

CREATE TABLE exam_papers (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  description TEXT NULL,
  course_id BIGINT NOT NULL,
  creator_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'draft',
  instructions TEXT NULL,
  start_time DATETIME NULL,
  end_time DATETIME NULL,
  total_score DECIMAL(5,2) NULL,
  duration INT NULL,
  question_count INT NULL,
  shuffle_questions TINYINT(1) NOT NULL DEFAULT 0,
  shuffle_options TINYINT(1) NOT NULL DEFAULT 0,
  show_score TINYINT(1) NOT NULL DEFAULT 0,
  show_correct_answer TINYINT(1) NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 1,
  time_limit INT NULL,
  allow_review TINYINT(1) NOT NULL DEFAULT 1,
  generation_rules JSON NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_exam_papers_course_id (course_id),
  CONSTRAINT fk_exam_papers_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_exam_papers_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷表';

CREATE TABLE questions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  type VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  options JSON NULL,
  correct_answers JSON NULL,
  difficulty VARCHAR(20) NOT NULL DEFAULT 'medium',
  score DECIMAL(5,2) NOT NULL DEFAULT 5,
  knowledge_points JSON NULL,
  explanation TEXT NULL,
  usage_count INT NOT NULL DEFAULT 0,
  correct_rate DECIMAL(5,2) NULL,
  course_id BIGINT NULL,
  created_by BIGINT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_questions_course_id (course_id),
  KEY idx_questions_created_by (created_by),
  CONSTRAINT fk_questions_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE SET NULL,
  CONSTRAINT fk_questions_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库表';

CREATE TABLE exam_questions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  exam_paper_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  order_index INT NOT NULL DEFAULT 0,
  points DECIMAL(5,2) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_exam_questions_paper_question (exam_paper_id, question_id),
  CONSTRAINT fk_exam_questions_paper FOREIGN KEY (exam_paper_id) REFERENCES exam_papers(id) ON DELETE CASCADE,
  CONSTRAINT fk_exam_questions_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷题目表';

CREATE TABLE exam_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  exam_paper_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
  attempt_number INT NOT NULL DEFAULT 1,
  start_time DATETIME NULL,
  submit_time DATETIME NULL,
  time_spent INT NULL,
  total_score DECIMAL(5,2) NULL,
  percentage DECIMAL(5,2) NULL,
  answers JSON NULL,
  question_scores JSON NULL,
  ip_address VARCHAR(50) NULL,
  user_agent VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_exam_sessions_paper_id (exam_paper_id),
  KEY idx_exam_sessions_student_id (student_id),
  CONSTRAINT fk_exam_sessions_paper FOREIGN KEY (exam_paper_id) REFERENCES exam_papers(id) ON DELETE CASCADE,
  CONSTRAINT fk_exam_sessions_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='考试会话表';

CREATE TABLE knowledge_points (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  description TEXT NULL,
  course_id BIGINT NULL,
  parent_id BIGINT NULL,
  difficulty_level VARCHAR(20) NULL,
  importance INT NOT NULL DEFAULT 5,
  estimated_time INT NULL,
  point_type VARCHAR(30) NULL,
  order_index INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_knowledge_points_course_id (course_id),
  CONSTRAINT fk_knowledge_points_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_knowledge_points_parent FOREIGN KEY (parent_id) REFERENCES knowledge_points(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点表';

CREATE TABLE knowledge_point_relations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_id BIGINT NOT NULL,
  target_id BIGINT NOT NULL,
  relation_type VARCHAR(30) NOT NULL,
  strength DECIMAL(3,2) NOT NULL DEFAULT 1.00,
  description VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_knowledge_relations_source_target_type (source_id, target_id, relation_type),
  CONSTRAINT fk_knowledge_relations_source FOREIGN KEY (source_id) REFERENCES knowledge_points(id) ON DELETE CASCADE,
  CONSTRAINT fk_knowledge_relations_target FOREIGN KEY (target_id) REFERENCES knowledge_points(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点关系表';

CREATE TABLE chapter_knowledge_points (
  id BIGINT NOT NULL AUTO_INCREMENT,
  chapter_id BIGINT NOT NULL,
  knowledge_point_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chapter_knowledge_points (chapter_id, knowledge_point_id),
  CONSTRAINT fk_chapter_knowledge_chapter FOREIGN KEY (chapter_id) REFERENCES course_chapters(id) ON DELETE CASCADE,
  CONSTRAINT fk_chapter_knowledge_point FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_points(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节知识点关联表';

CREATE TABLE wrong_questions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  course_id BIGINT NULL,
  wrong_answer TEXT NULL,
  reason TEXT NULL,
  mastered TINYINT(1) NOT NULL DEFAULT 0,
  source_type VARCHAR(50) NULL,
  practice_count INT NOT NULL DEFAULT 0,
  last_practiced_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_wrong_questions_student_question (student_id, question_id),
  CONSTRAINT fk_wrong_questions_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_wrong_questions_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
  CONSTRAINT fk_wrong_questions_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题表';

CREATE TABLE learning_paths (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  course_id BIGINT NULL,
  path_name VARCHAR(255) NOT NULL,
  description TEXT NULL,
  stages JSON NULL,
  plan_detail JSON NULL,
  daily_tasks JSON NULL,
  learning_goal VARCHAR(500) NULL,
  current_stage INT NOT NULL DEFAULT 0,
  total_stages INT NOT NULL DEFAULT 0,
  completion_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  estimated_completion_date DATETIME NULL,
  actual_completion_date DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_learning_paths_student_id (student_id),
  CONSTRAINT fk_learning_paths_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_learning_paths_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径表';

CREATE TABLE learning_path_nodes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  learning_path_id BIGINT NOT NULL,
  knowledge_point_id BIGINT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT NULL,
  node_type VARCHAR(30) NOT NULL DEFAULT 'study',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  order_index INT NOT NULL DEFAULT 0,
  estimated_time INT NULL,
  actual_time INT NOT NULL DEFAULT 0,
  score DECIMAL(5,2) NULL,
  completed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_learning_path_nodes_path FOREIGN KEY (learning_path_id) REFERENCES learning_paths(id) ON DELETE CASCADE,
  CONSTRAINT fk_learning_path_nodes_knowledge FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_points(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径节点表';

CREATE TABLE learning_records (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  resource_id BIGINT NULL,
  chapter_id BIGINT NULL,
  course_id BIGINT NULL,
  action_type VARCHAR(30) NOT NULL,
  duration INT NOT NULL DEFAULT 0,
  progress DECIMAL(5,2) NOT NULL DEFAULT 0,
  metadata JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_learning_records_user_id (user_id),
  KEY idx_learning_records_course_id (course_id),
  CONSTRAINT fk_learning_records_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习记录表';

CREATE TABLE video_learning_records (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  watch_duration INT NOT NULL DEFAULT 0,
  total_duration INT NOT NULL DEFAULT 0,
  progress DECIMAL(5,2) NOT NULL DEFAULT 0,
  last_position INT NOT NULL DEFAULT 0,
  complete_count INT NOT NULL DEFAULT 0,
  last_watched_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_video_learning_user_resource (user_id, resource_id),
  CONSTRAINT fk_video_learning_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_video_learning_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频学习记录表';

CREATE TABLE video_watch_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  start_position INT NOT NULL DEFAULT 0,
  end_position INT NOT NULL DEFAULT 0,
  duration INT NOT NULL DEFAULT 0,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_video_watch_sessions_user_id (user_id),
  CONSTRAINT fk_video_watch_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_video_watch_resource FOREIGN KEY (resource_id) REFERENCES resources(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频观看会话表';

CREATE TABLE notifications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT NULL,
  category VARCHAR(50) NOT NULL DEFAULT 'system',
  type VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  priority VARCHAR(20) NOT NULL DEFAULT 'normal',
  action_url VARCHAR(500) NULL,
  data JSON NULL,
  related_id BIGINT NULL,
  related_type VARCHAR(50) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_notifications_user_read (user_id, is_read),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';

CREATE TABLE role_permissions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  role VARCHAR(50) NOT NULL,
  permission_key VARCHAR(100) NOT NULL,
  permission_name VARCHAR(100) NOT NULL,
  permission_type VARCHAR(20) NOT NULL DEFAULT 'MENU',
  resource_path VARCHAR(255) NULL,
  parent_id BIGINT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  description VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_permissions_role_key (role, permission_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限表';

CREATE TABLE system_configs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  config_key VARCHAR(128) NOT NULL,
  config_value TEXT NULL,
  config_group VARCHAR(64) NULL,
  config_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
  description VARCHAR(512) NULL,
  is_public TINYINT(1) NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_configs_key (config_key),
  KEY idx_system_configs_group (config_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

CREATE TABLE file_upload_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  filename VARCHAR(255) NOT NULL,
  file_size BIGINT NULL,
  total_chunks INT NULL,
  uploaded_chunks INT NOT NULL DEFAULT 0,
  upload_path VARCHAR(500) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING',
  uploader_id BIGINT NULL,
  expires_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_file_upload_sessions_uploader (uploader_id),
  KEY idx_file_upload_sessions_status (status),
  CONSTRAINT fk_file_upload_sessions_uploader FOREIGN KEY (uploader_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传会话表';
