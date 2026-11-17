-- V23：课程级学生刷题开关（P0-A1）。
-- 教师可在课程设置中决定是否把本课程题库开放给学生练习；默认关闭，避免上线即暴露。
ALTER TABLE courses
    ADD COLUMN question_practice_enabled TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '学生题库刷题开关：0-关闭 1-开放（教师可在课程设置中切换）';
