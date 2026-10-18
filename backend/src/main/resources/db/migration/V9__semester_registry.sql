-- ====================================================================
-- V9：学期注册表（角色矩阵「① 创建学期 / ② 归档过期学期」的实现落点）
-- --------------------------------------------------------------------
-- 背景（手册 §D.12.4 取证）：这两项能力此前在仓库里完全没有落点——
-- 全仓无 Semester 实体，`courses.semester` 只是一列自由文本（VARCHAR），
-- 也没有任何"学期"的创建/归档界面。
--
-- 本迁移只补「学期」这一治理维度本身：
--   · 学期名唯一（防重名注册）；状态 ACTIVE/ARCHIVED；归档时间留痕。
-- ⚠️ 与 courses 的绑定**刻意未建立**（如实记录，勿误读为已完成）：
--   `courses.semester` 保持自由文本、不做外键/回填——把既有 38 门课的文本学期
--   映射到新表需要先确认命名口径（如 "2026-1" 与 "2026-2027-1" 是否同一学期），
--   未经项目主确认不得用猜测的映射改写既有数据。
-- ====================================================================

CREATE TABLE semesters (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(64) NOT NULL COMMENT '学期名称，如 2026-2027-1',
    start_date DATE NOT NULL COMMENT '开始日期',
    end_date DATE NOT NULL COMMENT '结束日期',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE-在册；ARCHIVED-已归档',
    archived_at DATETIME NULL COMMENT '归档时间（未归档为空）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_semesters_name (name) COMMENT '学期名唯一，防重名注册',
    KEY idx_semesters_status_end (status, end_date) COMMENT '归档过期学期的扫描索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学期注册表（2026-10-13 新增，角色矩阵①②）';
