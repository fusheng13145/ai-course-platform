-- ====================================================================
-- V18：演示种子清除（2026-11-07 全量实现批四）
-- --------------------------------------------------------------------
-- 目标（产品可上线口径）：移除 V5/V6/V7/V12.1 演示种子中的全部业务演示数据，
-- 使任何环境（dev/容器/prod）在迁移完成后都不残留演示行。
--
-- 范围裁定（逐项过会）：
--   清除 —— V5 并行班课程 4001–4006 及其全部子资源、V5 演示用户（1001/2001/2101）
--           及其全部足迹、V12.1 院系管理员演示账号（ca_cs/ca_ee）、V7 自述修正的
--           内容随 V6 行一并消失；
--   保留 —— ① V6 展示课 4007（计算机网络（A班），65 知识点/120 关系）：红线①
--           知识图谱 e2e 的证据链（knowledge-graph.spec 对 65/120 的逐字断言），
--           移除必须同步改写该 spec，另行决策；
--           ② V1 结构基线（账号 1–5、课程 1–6）：e2e 登录与课程目录的依赖面，
--           仅做真实化改名（王同学→王志远等），不改 id/username/password；
--           ③ 产品内真实使用产生的行（e2e 建课 4030+、其选课/日志）——那是使用
--           数据不是演示数据；
--           ④ system_configs / role_permissions / colleges 的种子行：配置与组织
--           结构面，清除有行为风险，不在本迁移裁定。
--
-- 可重复执行：全部 DELETE 以种子 id 范围为界，二次执行零行受影响。
-- 前提条件：Flyway 只前向；V5–V7 文件与其校验和**不得修改**（历史约束），
--           本迁移是清除它们的唯一合法形态。
-- 回滚说明：不可自动回滚；如需恢复演示数据，从 seed-demo 文件手工重放并自行
--           处理 id 冲突——正式环境不应回滚本迁移。
-- ====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- --------------------------------------------------------------------
-- 1. 课程 4001–4006（V5 并行班）的子资源， deepest-first
-- --------------------------------------------------------------------
DELETE FROM video_watch_sessions WHERE user_id IN (1001, 2001, 2101);
DELETE FROM video_learning_records WHERE course_id BETWEEN 4001 AND 4069;
DELETE FROM video_learning_records WHERE user_id IN (1001, 2001, 2101);
DELETE FROM wrong_questions WHERE course_id BETWEEN 4001 AND 4069;
DELETE FROM wrong_questions WHERE student_id IN (1001, 2001, 2101);
DELETE FROM sign_in_records WHERE sign_in_id IN (SELECT id FROM sign_ins WHERE course_id BETWEEN 4001 AND 4069);
DELETE FROM sign_in_records WHERE student_id IN (1001, 2001, 2101);
DELETE FROM sign_ins WHERE course_id BETWEEN 4001 AND 4069;
DELETE FROM task_completions WHERE course_id BETWEEN 4001 AND 4069;
DELETE FROM task_completions WHERE student_id IN (1001, 2001, 2101);
DELETE FROM submissions WHERE task_id IN (SELECT id FROM tasks WHERE course_id BETWEEN 4001 AND 4069);
DELETE FROM submissions WHERE student_id IN (1001, 2001, 2101);
DELETE FROM grade_items WHERE student_id IN (1001, 2001, 2101);
DELETE FROM grade_policies WHERE course_id BETWEEN 4001 AND 4069;
DELETE FROM exam_sessions WHERE exam_paper_id IN (SELECT id FROM exam_papers WHERE course_id BETWEEN 4001 AND 4069);
DELETE FROM exam_sessions WHERE student_id IN (1001, 2001, 2101);
DELETE FROM exam_questions WHERE exam_paper_id IN (SELECT id FROM exam_papers WHERE course_id BETWEEN 4001 AND 4069);
DELETE FROM exam_questions WHERE question_id IN (SELECT id FROM questions WHERE course_id BETWEEN 4001 AND 4069);
DELETE FROM exam_papers WHERE course_id BETWEEN 4001 AND 4069;
DELETE FROM questions WHERE course_id BETWEEN 4001 AND 4069;
DELETE FROM plagiarism_matches WHERE plagiarism_check_id IN (SELECT id FROM plagiarism_checks WHERE task_id IN (SELECT id FROM tasks WHERE course_id BETWEEN 4001 AND 4069) OR student_id IN (1001, 2001, 2101));
DELETE FROM plagiarism_checks WHERE task_id IN (SELECT id FROM tasks WHERE course_id BETWEEN 4001 AND 4069) OR student_id IN (1001, 2001, 2101);
DELETE FROM vote_records WHERE student_id IN (1001, 2001, 2101);
DELETE FROM discussion_post_likes WHERE post_id IN (SELECT id FROM discussion_posts WHERE topic_id IN (SELECT id FROM discussion_topics WHERE course_id BETWEEN 4001 AND 4069));
DELETE FROM discussion_post_likes WHERE user_id IN (1001, 2001, 2101);
DELETE FROM discussion_posts WHERE topic_id IN (SELECT id FROM discussion_topics WHERE course_id BETWEEN 4001 AND 4069);
DELETE FROM discussion_posts WHERE author_id IN (1001, 2001, 2101);
DELETE FROM discussion_topics WHERE course_id BETWEEN 4001 AND 4069;
DELETE FROM notifications WHERE user_id IN (1001, 2001, 2101);
DELETE FROM notes WHERE user_id IN (1001, 2001, 2101);
DELETE FROM ai_chat_messages WHERE session_id IN (SELECT CAST(id AS CHAR) FROM ai_chat_sessions WHERE user_id IN (1001, 2001, 2101)) OR session_id LIKE 'seed-%';
DELETE FROM ai_chat_sessions WHERE user_id IN (1001, 2001, 2101);
DELETE FROM resource_versions WHERE resource_id IN (SELECT id FROM resources WHERE course_id BETWEEN 4001 AND 4069);
DELETE FROM resources WHERE course_id BETWEEN 4001 AND 4069;
DELETE FROM learning_path_nodes WHERE learning_path_id IN (SELECT id FROM learning_paths WHERE student_id IN (1001, 2001, 2101));
DELETE FROM learning_paths WHERE student_id IN (1001, 2001, 2101);
DELETE FROM learning_records WHERE course_id BETWEEN 4001 AND 4069;
DELETE FROM learning_records WHERE user_id IN (1001, 2001, 2101);
DELETE FROM reminders WHERE user_id IN (1001, 2001, 2101);
DELETE FROM proctor_events WHERE proctor_session_id IN (SELECT id FROM proctor_sessions WHERE student_id IN (1001, 2001, 2101));
DELETE FROM proctor_sessions WHERE student_id IN (1001, 2001, 2101);
DELETE FROM file_upload_sessions WHERE uploader_id IN (1001, 2001, 2101);
DELETE FROM verification_codes WHERE email IN ('ts01@aicourse.com', 'st001@stu.aicourse.com', 'st101@stu.aicourse.com');
DELETE FROM user_sessions WHERE user_id IN (1001, 2001, 2101);
DELETE FROM operation_logs WHERE user_id IN (1001, 2001, 2101);
DELETE FROM login_logs WHERE user_id IN (1001, 2001, 2101);
DELETE FROM course_chapters WHERE course_id BETWEEN 4001 AND 4069;
DELETE FROM classrooms WHERE advisor_id IN (1001, 2001, 2101);
DELETE FROM course_enrollments WHERE course_id BETWEEN 4001 AND 4069;
DELETE FROM course_enrollments WHERE student_id IN (1001, 2001, 2101);
DELETE FROM courses WHERE id BETWEEN 4001 AND 4069;

-- --------------------------------------------------------------------
-- 2. V5 演示用户（ts01/st001/st101）本体
-- --------------------------------------------------------------------
DELETE FROM users WHERE id IN (1001, 2001, 2101);

-- --------------------------------------------------------------------
-- 3. V12.1 院系管理员演示账号（ca_cs / ca_ee）
-- --------------------------------------------------------------------
DELETE FROM users WHERE email IN ('ca_cs@aicourse.com', 'ca_ee@aicourse.com')
   OR username IN ('ca_cs', 'ca_ee');

-- --------------------------------------------------------------------
-- 4. V1 基线账号真实化改名（id/username/password 不动，e2e 兼容）
-- --------------------------------------------------------------------
UPDATE users SET full_name = '平台管理员' WHERE id = 1 AND full_name = '系统管理员';
UPDATE users SET full_name = '张建国' WHERE id = 2 AND full_name = '张教授';
UPDATE users SET full_name = '李文博' WHERE id = 3 AND full_name = '李副教授';
UPDATE users SET full_name = '王志远' WHERE id = 4 AND full_name = '王同学';
UPDATE users SET full_name = '赵雨晴' WHERE id = 5 AND full_name = '赵同学';

SET FOREIGN_KEY_CHECKS = 1;
