-- V33（2026-11-22）：考试与作业提交次数默认改为「不限次」。
-- 语义约定：0 = 不限次数。
--   - ExamSessionLifecycleComponent#startExam 仅在 maxAttempts > 0 时校验次数；
--   - SubmissionWriteComponent#submitAssignment 仅在 maxSubmissions > 0 时校验次数。
-- 因此把建表默认值与存量数据都归零，即可让学生对任何考试重考、任何作业重做。
-- 幂等：本地已手工执行过同样语句时，UPDATE 命中 0 行、ALTER DEFAULT 无副作用。

ALTER TABLE exam_papers ALTER COLUMN max_attempts SET DEFAULT 0;
UPDATE exam_papers SET max_attempts = 0 WHERE max_attempts <> 0;

ALTER TABLE tasks ALTER COLUMN max_submissions SET DEFAULT 0;
UPDATE tasks SET max_submissions = 0 WHERE max_submissions <> 0;
