-- P0-B（2026-11-17）：资源默认不公开。
-- 背景：resources.visible_to_all 建表默认 1（所有学生可见），应用层上传虽已显式写 FALSE，
-- 但任何漏设字段的写入（脚本导入、历史入口、未来新增写入路径）都会默认公开课程资源。
-- 前置数据：本地开发库 resources 共 12 行，visible_to_all=1 为 0 行，收紧默认值不会改动存量。
-- 回滚：ALTER TABLE resources ALTER COLUMN visible_to_all SET DEFAULT 1;
ALTER TABLE resources
    ALTER COLUMN visible_to_all SET DEFAULT 0;
