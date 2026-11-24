-- V35: 谓语全量归一 + 上游错位边隔离 + 原始谓语留痕。
--
-- 背景：V34 只归一了六值别名，存量仍有 388 条非六值边。逐条抽样后分为两类：
--   1) 真关系语义（功能/具有/定义/示例/依赖…）：可弱化归并到六值，原值留痕；
--   2) 上游三元组抽取错位（数据链路层/最大地址/路由表/序号字段长度…）：
--      谓语位置实际是实体名、数值或时间，不构成关系，且部分边主谓颠倒
--      （如「共享 —具有→ 网络」应为「网络 具有 共享」）。
-- 第二类不做语义洗白——不改成 RELATED 冒充合法边，而是打隔离标记，
-- 默认图谱可视化与 Neo4j 同步排除，原始行保留供审计或后续重抽。
--
-- 新增列：
--   raw_relation_type  原始谓语留痕（仅归一前为非六值的边有值），保证映射可回溯；
--   is_quarantined     1=错位隔离边，默认图谱查询排除。
--
-- 本迁移会删除 3 条映射后撞 (source_id,target_id,relation_type) 唯一键的重复边，
-- 保留 id 最小者：
--   7001393 (网络,协议,功能)、7001621 (网络,协议,关注)、7001620 (网络,路由,关注)
-- 执行前备份：项目文档/_audit_stage2/stage6-mysql-backup-before-v35.sql

ALTER TABLE knowledge_point_relations
    ADD COLUMN raw_relation_type varchar(30) NULL
        COMMENT '原始谓语留痕（归一前为非六值时的取值）' AFTER relation_type,
    ADD COLUMN is_quarantined tinyint(1) NOT NULL DEFAULT 0
        COMMENT '1=上游抽取错位隔离边，默认图谱查询与 Neo4j 同步排除' AFTER raw_relation_type;

-- 1. 原始谓语留痕：只针对归一前不是六值的边。
UPDATE knowledge_point_relations
SET raw_relation_type = relation_type
WHERE relation_type NOT IN ('PREREQUISITE', 'RELATED', 'FOLLOW_UP', 'CONTAINS_CHAPTER', 'CONTAINS', 'BELONGS_TO');

-- 2. 错位边隔离（36 种谓语 / 72 行）：保留原谓语，不改写为合法值。
UPDATE knowledge_point_relations
SET is_quarantined = 1
WHERE relation_type IN (
    '数据链路层', '最大地址', '三个基本问题', '最小值', '路由表', '单位', '数值', '末地址',
    '转发表', '首地址', 'IP源地址', '参数', '在系内通信', '宽度', '数据链路层协议', '时延',
    '最小地址', '等待时间', 'IP目的地址', '值', '到2020年5月', '吞吐量', '大/短', '密钥长度',
    '对象标识符', '序号字段', '序号字段长度', '总时延', '抽象的终点', '最大时延', '最小长度',
    '网络前缀长度', '设置抽象的门', '连接的主机', '通过路由器上网', '长度'
);

-- 3. 归一去重（必须先于 UPDATE）。
-- knowledge_point_relations 有 (source_id,target_id,relation_type) 唯一约束：
-- 同一三元组若同时存在「功能/具有/关注」等多个中文谓语，直接 UPDATE 成 RELATED 会撞唯一键。
-- 因此先按「归一后的语义」删除重复边，保留 id 最小的一条，再做映射。
-- 隔离边不参与去重，避免误删审计数据。
-- 本步实际删除 3 行：7001393 (网络,协议,功能)、7001621 (网络,协议,关注)、7001620 (网络,路由,关注)。
DELETE r2
FROM knowledge_point_relations r2
JOIN knowledge_point_relations r1
  ON r1.source_id = r2.source_id
 AND r1.target_id = r2.target_id
 AND r1.id < r2.id
 AND r1.is_quarantined = 0
 AND r2.is_quarantined = 0
 AND (CASE
        WHEN r1.relation_type IN ('依赖', '需要') THEN 'PREREQUISITE'
        WHEN r1.relation_type IN ('包含协议', '具有/包含') THEN 'CONTAINS'
        WHEN r1.relation_type = '具有/属于' THEN 'BELONGS_TO'
        WHEN r1.relation_type IN ('PREREQUISITE', 'RELATED', 'FOLLOW_UP', 'CONTAINS_CHAPTER', 'CONTAINS', 'BELONGS_TO')
            THEN r1.relation_type
        ELSE 'RELATED'
      END) = (CASE
        WHEN r2.relation_type IN ('依赖', '需要') THEN 'PREREQUISITE'
        WHEN r2.relation_type IN ('包含协议', '具有/包含') THEN 'CONTAINS'
        WHEN r2.relation_type = '具有/属于' THEN 'BELONGS_TO'
        WHEN r2.relation_type IN ('PREREQUISITE', 'RELATED', 'FOLLOW_UP', 'CONTAINS_CHAPTER', 'CONTAINS', 'BELONGS_TO')
            THEN r2.relation_type
        ELSE 'RELATED'
      END);

-- 4. 真关系谓语映射（未隔离边）。
-- 3.1 明确语义提升：依赖/需要 → 前置知识。
UPDATE knowledge_point_relations
SET relation_type = 'PREREQUISITE'
WHERE is_quarantined = 0 AND relation_type IN ('依赖', '需要');

-- 3.2 包含语义 → 包含。
UPDATE knowledge_point_relations
SET relation_type = 'CONTAINS'
WHERE is_quarantined = 0 AND relation_type IN ('包含协议', '具有/包含');

-- 3.3 归属语义 → 属于。
UPDATE knowledge_point_relations
SET relation_type = 'BELONGS_TO'
WHERE is_quarantined = 0 AND relation_type = '具有/属于';

-- 3.4 其余真关系谓语（67 种 / 305 行）弱化为「相关」。
-- 六值词表没有功能/定义/示例/因果等类型，统一弱化而非新增枚举值，
-- 原始谓语已在 raw_relation_type 留痕，需要更细粒度时可据此回建。
UPDATE knowledge_point_relations
SET relation_type = 'RELATED'
WHERE is_quarantined = 0 AND relation_type IN (
    '功能', '具有', '示例', '定义', '导致', '应用', '对应', '分类', '关注', '概念',
    '特性', '特点', '用途', '目标', '类型', '与', '功能/影响', '劣势', '定义/示例', '执行',
    '提供', '检测', '概念/用途', '等于', '通过', '重要性', 'SAME', '不提供', '代表', '优势',
    '优势/定义', '优势/难度很大', '传播', '传输', '位于/分用', '冲击', '减少串扰', '分类/定义', '功能/导致', '地位',
    '大于', '安全性', '必须达到', '意义', '成为', '抗电磁干扰', '换算成', '提供接口机制', '提升', '提高到',
    '支持', '标志', '检测方法', '正式名称', '泛滥', '激励', '目的', '目的/过程', '研究', '简称为',
    '绞合', '被入侵', '被导引', '运算速度', '连接', '通过运输层', '面临'
);

-- 5. 知识点类型空值补齐，与 PointType.canonicalCode 的默认 CONCEPT 口径一致。
UPDATE knowledge_points
SET point_type = 'CONCEPT'
WHERE point_type IS NULL OR point_type = '';
