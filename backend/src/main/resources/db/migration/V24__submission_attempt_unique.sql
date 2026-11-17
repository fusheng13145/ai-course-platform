-- P0-B（2026-11-17）：作业提交尝试唯一键。
-- 背景：submissions 无 (task_id, student_id, attempt_number) 唯一约束，
-- 并发提交/重交可能产生同一学生的同一次尝试双行，批改与成绩统计会重复计数。
-- 前置数据：本地开发库 submissions 共 5 行，重复组 0、重复多余行 0；契约测试库由 V1 重建后为空。
-- 回滚：ALTER TABLE submissions DROP INDEX uk_submissions_task_student_attempt;
ALTER TABLE submissions
    ADD UNIQUE KEY uk_submissions_task_student_attempt (task_id, student_id, attempt_number)
    COMMENT '任务-学生-尝试次数唯一：防止并发生成重复提交记录';
