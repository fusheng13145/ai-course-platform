-- V34: 收敛知识点类型/关系类型的持久化口径。
--
-- 背景：V1 注释使用小写 concept/skill/principle 与 prerequisite/related/follow-up；
-- 历史服务层写入大写；DeepGraph 适配器写入中文“包含章节/属于/相关”。
-- 阶段 3 起应用层统一写入 RelationType/PointType 的领域编码，本迁移负责修复存量。
--
-- 注意：knowledge_point_relations 存在 (source_id,target_id,relation_type) 唯一约束。
-- 同一三元组若同时存在中文别名与英文规范值，直接 UPDATE 会撞唯一键。
-- 因此在归一前先按“归一后的语义”删除同源同目标同语义的重复边，保留 id 最小的一条；
-- 该操作会丢失重复边的 strength/description 差异，属于有意的收敛，执行前应备份。

UPDATE knowledge_points
SET point_type = CASE UPPER(TRIM(point_type))
    WHEN '章节' THEN 'CHAPTER'
    WHEN '概念' THEN 'CONCEPT'
    WHEN '技能' THEN 'SKILL'
    WHEN '原理' THEN 'PRINCIPLE'
    WHEN '方法' THEN 'METHOD'
    WHEN '事实' THEN 'FACT'
    WHEN '小结' THEN 'SUMMARY'
    ELSE UPPER(TRIM(point_type))
END
WHERE point_type IS NOT NULL AND point_type <> '';

DELETE r2
FROM knowledge_point_relations r2
JOIN knowledge_point_relations r1
  ON r1.source_id = r2.source_id
 AND r1.target_id = r2.target_id
 AND r1.id < r2.id
 AND (CASE
        WHEN UPPER(TRIM(r1.relation_type)) IN ('PREREQUISITE', 'PREREQ', 'PRE', '前置', '前置知识') THEN 'PREREQUISITE'
        WHEN UPPER(TRIM(r1.relation_type)) IN ('RELATED', 'RELATION', 'RELATED_TO', '相关') THEN 'RELATED'
        WHEN UPPER(TRIM(r1.relation_type)) IN ('FOLLOW_UP', 'FOLLOWUP', 'SUCCESSOR', '后续', '后续知识') THEN 'FOLLOW_UP'
        WHEN UPPER(TRIM(r1.relation_type)) IN ('CONTAINS_CHAPTER', 'CHAPTER', '包含章节') THEN 'CONTAINS_CHAPTER'
        WHEN UPPER(TRIM(r1.relation_type)) IN ('CONTAINS', '包含') THEN 'CONTAINS'
        WHEN UPPER(TRIM(r1.relation_type)) IN ('BELONGS_TO', 'BELONG', 'BELONGS', '属于', '归属') THEN 'BELONGS_TO'
        ELSE UPPER(TRIM(r1.relation_type))
      END) = (CASE
        WHEN UPPER(TRIM(r2.relation_type)) IN ('PREREQUISITE', 'PREREQ', 'PRE', '前置', '前置知识') THEN 'PREREQUISITE'
        WHEN UPPER(TRIM(r2.relation_type)) IN ('RELATED', 'RELATION', 'RELATED_TO', '相关') THEN 'RELATED'
        WHEN UPPER(TRIM(r2.relation_type)) IN ('FOLLOW_UP', 'FOLLOWUP', 'SUCCESSOR', '后续', '后续知识') THEN 'FOLLOW_UP'
        WHEN UPPER(TRIM(r2.relation_type)) IN ('CONTAINS_CHAPTER', 'CHAPTER', '包含章节') THEN 'CONTAINS_CHAPTER'
        WHEN UPPER(TRIM(r2.relation_type)) IN ('CONTAINS', '包含') THEN 'CONTAINS'
        WHEN UPPER(TRIM(r2.relation_type)) IN ('BELONGS_TO', 'BELONG', 'BELONGS', '属于', '归属') THEN 'BELONGS_TO'
        ELSE UPPER(TRIM(r2.relation_type))
      END)
WHERE r2.relation_type IS NOT NULL AND r2.relation_type <> '';

UPDATE knowledge_point_relations
SET relation_type = CASE
    WHEN UPPER(TRIM(relation_type)) IN ('PREREQUISITE', 'PREREQ', 'PRE', '前置', '前置知识')
        THEN 'PREREQUISITE'
    WHEN UPPER(TRIM(relation_type)) IN ('RELATED', 'RELATION', 'RELATED_TO', '相关')
        THEN 'RELATED'
    WHEN UPPER(TRIM(relation_type)) IN ('FOLLOW_UP', 'FOLLOWUP', 'SUCCESSOR', '后续', '后续知识')
        THEN 'FOLLOW_UP'
    WHEN UPPER(TRIM(relation_type)) IN ('CONTAINS_CHAPTER', 'CHAPTER', '包含章节')
        THEN 'CONTAINS_CHAPTER'
    WHEN UPPER(TRIM(relation_type)) IN ('CONTAINS', '包含')
        THEN 'CONTAINS'
    WHEN UPPER(TRIM(relation_type)) IN ('BELONGS_TO', 'BELONG', 'BELONGS', '属于', '归属')
        THEN 'BELONGS_TO'
    ELSE relation_type
END
WHERE relation_type IS NOT NULL AND relation_type <> '';
