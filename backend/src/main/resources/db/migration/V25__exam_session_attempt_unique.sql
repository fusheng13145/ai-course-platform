-- P0-B（2026-11-17）：考试会话尝试唯一键。
-- 背景：exam_sessions 无 (student_id, exam_paper_id, attempt_number) 唯一约束，
-- 同一学生对同一试卷的同一次尝试在并发开始考试下可能产生双会话，成绩列表/乱序答案会错乱。
-- 前置数据：本地开发库 exam_sessions 共 6 行，重复组 0、重复多余行 0；契约测试库由 V1 重建后为空。
-- 回滚：ALTER TABLE exam_sessions DROP INDEX uk_exam_sessions_student_paper_attempt;
ALTER TABLE exam_sessions
    ADD UNIQUE KEY uk_exam_sessions_student_paper_attempt (student_id, exam_paper_id, attempt_number)
    COMMENT '学生-试卷-考试次数唯一：防止并发开考生成重复会话';
