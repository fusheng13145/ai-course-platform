-- V20: 考试会话人工阅卷评语列（2026-11-14，审计 D5/P1-8）
--
-- 背景：ExamSessionGradingService.manualGrade 此前接收 comment 参数但从未持久化——
-- API 契约接受评语、静默丢弃，教师阅卷评语无留痕。新增 question_comments JSON 列，
-- 按 {questionId: comment} 与 question_scores 对称存储，成绩报告经
-- ExamSessionQueryComponent.generateScoreReport 回传。
--
-- 纯加列（无建表、无 DML），全新空库与既有库均可安全执行；列可空，历史会话读作 {}。

ALTER TABLE exam_sessions
    ADD COLUMN question_comments JSON NULL COMMENT '人工阅卷评语，JSON格式：{questionId: comment}，与 question_scores 同键（V20）' AFTER question_scores;
