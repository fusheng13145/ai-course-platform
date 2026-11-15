-- V19: 清理压测残留数据（2026-11-13）
--
-- 背景：2026-11-10 目标环境压测（scripts/e2e/e2e_stress_verify.py）在开发库遗留
-- 4 门「压测专用课程」(id 4174-4177)。经连库实测（2026-11-13，只读探测），
-- 其挂载的数据为：submissions 483（恰好为全库 submissions 总量）、
-- course_enrollments 16、course_approval_records 8、course_chapters/tasks/
-- exam_papers/questions/resources/teaching_classes/task_completions 各 4、
-- exam_questions 4、resource_versions 4；知识图谱/讨论/签到/投票/通知零挂载。
--
-- 删除采用「显式子表在前、父表在后」的有序 DELETE，全程不关闭外键校验
-- （修正 V18 以 SET FOREIGN_KEY_CHECKS=0 粗放清理的教训）；压测产生的磁盘
-- 上传文件不在此清理（资源行删除后 files 成为惰性孤儿，由运维按需回收）。
-- 对全新空库（生产开通形态）本迁移全部语句影响 0 行，可安全执行。
--
-- 勘误注记（不改已应用的 V18，其 checksum 必须保持不变）：
-- V18 头注声称「保留展示课 4007 及其 65 知识点/120 关系、保留 e2e 建课 4030+」，
-- 但实际 DELETE 区间为 id BETWEEN 4001 AND 4069，4007 与 4030-4069 已一并删除。
-- 实库核验（2026-11-13）：4001-4069 区间课程与知识点均为 0 行，五类子表无孤儿。
-- 该矛盾已在 docs/项目手册.md §1.2 与 docs/独立验收报告-2026-11-07.md 登记勘误，
-- e2e 图谱锚点已改用课程 1。本注记仅为迁移链内自证，V18 文件不再改动。

-- 1) 提交与完成记录（挂在压测任务上）
DELETE FROM submissions
WHERE task_id IN (SELECT id FROM tasks WHERE course_id BETWEEN 4174 AND 4177);

DELETE FROM task_completions
WHERE task_id IN (SELECT id FROM tasks WHERE course_id BETWEEN 4174 AND 4177)
   OR course_id BETWEEN 4174 AND 4177;

-- 2) 教学班联表
DELETE FROM task_teaching_classes
WHERE task_id IN (SELECT id FROM tasks WHERE course_id BETWEEN 4174 AND 4177);

DELETE FROM exam_teaching_classes
WHERE exam_paper_id IN (SELECT id FROM exam_papers WHERE course_id BETWEEN 4174 AND 4177);

DELETE FROM teaching_class_admin_classes
WHERE teaching_class_id IN (SELECT id FROM teaching_classes WHERE course_id BETWEEN 4174 AND 4177);

-- 3) 试卷题目关联（双向：压测试卷的挂题、压测题目的被引用）
DELETE FROM exam_questions
WHERE exam_paper_id IN (SELECT id FROM exam_papers WHERE course_id BETWEEN 4174 AND 4177)
   OR question_id IN (SELECT id FROM questions WHERE course_id BETWEEN 4174 AND 4177);

-- 4) 资源版本
DELETE FROM resource_versions
WHERE resource_id IN (SELECT id FROM resources WHERE course_id BETWEEN 4174 AND 4177);

-- 5) 课程直接子表
DELETE FROM course_approval_records WHERE course_id BETWEEN 4174 AND 4177;
DELETE FROM course_chapters          WHERE course_id BETWEEN 4174 AND 4177;
DELETE FROM course_enrollments       WHERE course_id BETWEEN 4174 AND 4177;
DELETE FROM exam_papers              WHERE course_id BETWEEN 4174 AND 4177;
DELETE FROM questions                WHERE course_id BETWEEN 4174 AND 4177;
DELETE FROM resources                WHERE course_id BETWEEN 4174 AND 4177;
DELETE FROM tasks                    WHERE course_id BETWEEN 4174 AND 4177;
DELETE FROM teaching_classes         WHERE course_id BETWEEN 4174 AND 4177;

-- 6) 课程本体
DELETE FROM courses WHERE id BETWEEN 4174 AND 4177;
