-- 2026-11-16 E2E 真实链路补齐：课程 1（Python程序设计）在全新库中只有课程与选课，
-- 没有知识点/关系种子。CI 的 E2E 使用全新 MySQL，`/api/knowledge/relations/graph/1`
-- 返回 0 节点，教师路径规划 1→3 也只能得到 404，知识图谱两条用例无法执行。
--
-- 本文件位于 db/seed-demo：开发/演示与 E2E 会加载；生产 profile 只加载 db/migration，
-- 不会把演示课程的知识点带进生产库。

INSERT IGNORE INTO knowledge_points
(id, name, description, course_id, parent_id, difficulty_level, importance, estimated_time, point_type, order_index, created_at)
VALUES
(10001, 'Python 基础与运行环境', '解释器、脚本运行方式与第一个程序', 1, NULL, 'easy', 5, 30, 'concept', 0, '2026-11-16 09:00:00'),
(10002, '变量与基本数据类型', 'int/float/str/bool 与变量命名', 1, NULL, 'easy', 6, 35, 'concept', 1, '2026-11-16 09:00:00'),
(10003, '分支与循环结构', 'if/for/while 的条件与迭代控制', 1, NULL, 'medium', 8, 45, 'skill', 2, '2026-11-16 09:00:00'),
(10004, '函数与模块', 'def、参数传递、import 与模块组织', 1, NULL, 'medium', 7, 50, 'skill', 3, '2026-11-16 09:00:00');

INSERT IGNORE INTO knowledge_point_relations
(id, source_id, target_id, relation_type, strength, description, created_at)
VALUES
(100001, 10001, 10002, 'prerequisite', 0.80, '先会运行 Python，再理解变量与数据类型', '2026-11-16 09:00:00'),
(100002, 10001, 10003, 'prerequisite', 0.75, '运行环境是分支与循环实践的前置', '2026-11-16 09:00:00'),
(100003, 10003, 10004, 'prerequisite', 0.85, '掌握流程控制后再学习函数封装', '2026-11-16 09:00:00');
