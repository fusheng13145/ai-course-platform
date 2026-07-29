package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.mapper.KnowledgePointRelationMapper;
import com.aicourse.service.GraphRAGService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * GraphRAG 图谱增强检索实现（MySQL 自包含版）。
 * <p>
 * 核心流程：问句实体链接 → BFS 子图遍历 → 结构化上下文注入 LLM Prompt。
 * 不依赖 Neo4j/Milvus 等外部组件，完全基于现有 MySQL 关系表运行。
 */
@Service
@Slf4j
public class GraphRAGServiceImpl implements GraphRAGService {

    /** 实体链接：问句中匹配知识点名称的最小子串长度 */
    private static final int MIN_MATCH_LENGTH = 2;

    private final KnowledgePointMapper knowledgePointMapper;
    private final KnowledgePointRelationMapper relationMapper;
    private final int defaultMaxHops;

    public GraphRAGServiceImpl(KnowledgePointMapper knowledgePointMapper,
                               KnowledgePointRelationMapper relationMapper,
                               @Value("${knowledge-graph.rag.max-hops:2}") int defaultMaxHops) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.relationMapper = relationMapper;
        this.defaultMaxHops = defaultMaxHops;
    }

    @Override
    public boolean isGraphAvailable(Long courseId) {
        if (courseId == null) return false;
        Long count = knowledgePointMapper.selectCount(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));
        return count != null && count > 0;
    }

    @Override
    public Map<String, Object> retrieveGraphContext(String question, Long courseId, int maxHops) {
        int hops = maxHops > 0 ? maxHops : defaultMaxHops;
        Map<String, Object> empty = Map.of("entities", Collections.emptyList(),
                "triples", Collections.emptyList(),
                "relatedPoints", Collections.emptyList());

        if (question == null || question.isBlank() || courseId == null) {
            return empty;
        }

        // 1. 加载课程知识点全量（当前规模下全量加载 + 内存匹配，规模增大后可按课程预建倒排索引）
        List<KnowledgePoint> allPoints = knowledgePointMapper.selectList(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));
        if (allPoints.isEmpty()) {
            log.debug("[GraphRAG] 课程 {} 无知识点数据", courseId);
            return empty;
        }

        // 2. 实体链接：基于子串匹配，找到问题中命中的知识点
        List<KnowledgePoint> matched = entityLink(question, allPoints);
        if (matched.isEmpty()) {
            log.debug("[GraphRAG] 问句中未匹配到任何课程知识点");
            return empty;
        }

        // 3. 加载该课程所有关系
        List<KnowledgePointRelation> allRelations = loadRelations(allPoints);
        if (allRelations.isEmpty()) {
            // 有关系表数据但无 link → 即使无图谱关系，也返回匹配到的实体给 LLM 参考
            log.debug("[GraphRAG] 课程 {} 图谱关系为空，仅返回实体链接结果", courseId);
            return buildResult(matched, Collections.emptyList(), Collections.emptyList());
        }

        // 4. BFS 子图扩展
        Set<Long> visitedIds = new HashSet<>();
        List<KnowledgePoint> subgraphPoints = new ArrayList<>();
        List<KnowledgePointRelation> subgraphRelations = new ArrayList<>();

        // 构建邻接表：sourceId → [(targetId, relation)]
        Map<Long, List<KnowledgePointRelation>> adj = buildAdjacency(allRelations);

        // BFS 队列：{pointId, depth}
        Deque<long[]> queue = new ArrayDeque<>();
        for (KnowledgePoint p : matched) {
            queue.offer(new long[]{p.getId(), 0});
            visitedIds.add(p.getId());
        }

        while (!queue.isEmpty()) {
            long[] cur = queue.poll();
            long curId = cur[0];
            int depth = (int) cur[1];

            if (depth >= hops) continue;

            List<KnowledgePointRelation> neighbors = adj.getOrDefault(curId, Collections.emptyList());
            for (KnowledgePointRelation rel : neighbors) {
                long neighborId = rel.getTargetId();
                if (visitedIds.add(neighborId)) {
                    queue.offer(new long[]{neighborId, depth + 1});
                    subgraphRelations.add(rel);
                }
            }
        }

        // 收集子图中所有知识点
        Map<Long, KnowledgePoint> pointMap = allPoints.stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, p -> p, (a, b) -> a));
        for (Long id : visitedIds) {
            KnowledgePoint p = pointMap.get(id);
            if (p != null) {
                subgraphPoints.add(p);
            }
        }

        log.info("[GraphRAG] 课程 {} 实体链接 {} 个 → 子图扩展 {} 个知识点、{} 条关系 (hops={})",
                courseId, matched.size(), subgraphPoints.size(), subgraphRelations.size(), hops);

        return buildResult(matched, subgraphPoints, subgraphRelations);
    }

    @Override
    public String formatGraphContextForPrompt(Map<String, Object> graphContext) {
        if (graphContext == null || graphContext.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n【知识图谱关联信息】\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> triples = (List<Map<String, Object>>) graphContext.getOrDefault("triples",
                Collections.emptyList());

        if (triples.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entities = (List<Map<String, Object>>) graphContext.getOrDefault("entities",
                    Collections.emptyList());
            if (!entities.isEmpty()) {
                sb.append("用户在以下知识点的语义范围内提问：\n");
                for (int i = 0; i < Math.min(entities.size(), 10); i++) {
                    Map<String, Object> e = entities.get(i);
                    sb.append("  ").append(i + 1).append(". ").append(e.getOrDefault("name", "未知"))
                            .append("（").append(e.getOrDefault("type", "")).append("）\n");
                }
            }
        } else {
            sb.append("以下为与用户问题相关的知识点间关系（用于辅助推理）：\n");
            int shown = 0;
            for (Map<String, Object> t : triples) {
                if (shown >= 20) break; // 防止 Prompt 过长
                String relLabel = mapRelationLabel((String) t.getOrDefault("relationType", ""));
                sb.append("  - 「").append(t.getOrDefault("sourceName", ""))
                        .append("」").append(relLabel)
                        .append("「").append(t.getOrDefault("targetName", "")).append("」\n");
                shown++;
            }
            if (triples.size() > 20) {
                sb.append("  ... 及更多 ").append(triples.size() - 20).append(" 条关联关系\n");
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relatedPoints = (List<Map<String, Object>>) graphContext.getOrDefault("relatedPoints",
                Collections.emptyList());
        if (!relatedPoints.isEmpty()) {
            sb.append("\n关联知识点摘要：\n");
            for (int i = 0; i < Math.min(relatedPoints.size(), 10); i++) {
                Map<String, Object> p = relatedPoints.get(i);
                sb.append("  - ").append(p.getOrDefault("name", ""));
                String desc = (String) p.getOrDefault("description", "");
                if (desc != null && !desc.isBlank() && desc.length() < 200) {
                    sb.append("：").append(desc);
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    // ===================== 内部实现 =====================

    /**
     * 实体链接：在问句中查找课程知识点名称的最长匹配。
     * 使用贪心最长匹配策略，避免短名称错误截断长名称。
     */
    private List<KnowledgePoint> entityLink(String question, List<KnowledgePoint> allPoints) {
        // 按名称长度降序排列（优先匹配长名称，如"传输控制协议"不被"传输"截断）
        List<KnowledgePoint> sorted = allPoints.stream()
                .filter(p -> p.getName() != null && p.getName().length() >= MIN_MATCH_LENGTH)
                .sorted(Comparator.comparingInt(p -> -((KnowledgePoint) p).getName().length()))
                .collect(Collectors.toList());

        Set<String> matchedNames = new HashSet<>();
        List<KnowledgePoint> result = new ArrayList<>();

        // 第一遍：贪心匹配，已匹配的短名不再重复加入
        for (KnowledgePoint p : sorted) {
            String name = p.getName();
            // 已匹配了包含它的更长名称？
            boolean covered = matchedNames.stream().anyMatch(n -> n.contains(name));
            if (covered) continue;

            if (question.contains(name)) {
                matchedNames.add(name);
                result.add(p);
            }
        }

        // 第二遍：对未命中的，尝试分词匹配（简单按 2-gram 匹配比率 ≥ 50%）
        if (result.isEmpty() && sorted.size() > 0) {
            for (KnowledgePoint p : sorted) {
                String name = p.getName();
                if (name.length() < 3) continue;
                Set<String> nameBigrams = bigrams(name);
                Set<String> qBigrams = bigrams(question);
                if (nameBigrams.isEmpty() || qBigrams.isEmpty()) continue;
                nameBigrams.retainAll(qBigrams);
                double ratio = (double) nameBigrams.size() / bigrams(name).size();
                if (ratio >= 0.5) {
                    result.add(p);
                    if (result.size() >= 5) break; // 模糊匹配最多 5 个
                }
            }
        }

        return result;
    }

    private Set<String> bigrams(String s) {
        Set<String> set = new LinkedHashSet<>();
        for (int i = 0; i + 1 < s.length(); i++) {
            set.add(s.substring(i, i + 2));
        }
        return set;
    }

    /** 加载课程内所有知识点间的关系 */
    private List<KnowledgePointRelation> loadRelations(List<KnowledgePoint> allPoints) {
        Set<Long> pointIds = allPoints.stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
        if (pointIds.isEmpty()) return Collections.emptyList();

        // 分两次查询：sourceId ∈ pointIds OR targetId ∈ pointIds
        // MyBatis-Plus 的 OR 条件
        return relationMapper.selectList(
                new LambdaQueryWrapper<KnowledgePointRelation>()
                        .and(w -> w.in(KnowledgePointRelation::getSourceId, pointIds)
                                .or().in(KnowledgePointRelation::getTargetId, pointIds)));
    }

    /** 构建邻接表：sourceId → [(targetId, relation)] */
    private Map<Long, List<KnowledgePointRelation>> buildAdjacency(List<KnowledgePointRelation> relations) {
        Map<Long, List<KnowledgePointRelation>> adj = new HashMap<>();
        for (KnowledgePointRelation rel : relations) {
            adj.computeIfAbsent(rel.getSourceId(), k -> new ArrayList<>()).add(rel);
            // 也为反向关系建边（graph 为无向图时 BFS 可沿任意方向走）
            // 创建一个反向关系记录
            KnowledgePointRelation reverse = new KnowledgePointRelation();
            reverse.setSourceId(rel.getTargetId());
            reverse.setTargetId(rel.getSourceId());
            reverse.setRelationType(rel.getRelationType()); // 保留原关系类型
            adj.computeIfAbsent(rel.getTargetId(), k -> new ArrayList<>()).add(reverse);
        }
        return adj;
    }

    /** 关系类型友好化映射 */
    private String mapRelationLabel(String type) {
        if (type == null) return " → ";
        return switch (type.toUpperCase()) {
            case "PREREQUISITE", "DEPENDS_ON", "前驱" -> " 的前置知识是 ";
            case "INCLUDES", "CONTAINS", "包含" -> " 包含 ";
            case "EXTENDS", "延伸" -> " 延伸自 ";
            case "RELATED", "BROTHER", "关联" -> " 与 ";
            default -> " → ";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildResult(List<KnowledgePoint> matched,
                                            List<KnowledgePoint> subgraphPoints,
                                            List<KnowledgePointRelation> subgraphRelations) {
        // entities
        List<Map<String, Object>> entities = matched.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("type", p.getPointType() != null ? p.getPointType() : "CONCEPT");
            return m;
        }).collect(Collectors.toList());

        // triples
        Map<Long, String> nameMap = new HashMap<>();
        for (KnowledgePoint p : subgraphPoints) {
            nameMap.put(p.getId(), p.getName());
        }
        nameMap.putAll(matched.stream().collect(Collectors.toMap(KnowledgePoint::getId, KnowledgePoint::getName,
                (a, b) -> a)));

        List<Map<String, Object>> triples = subgraphRelations.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sourceName", nameMap.getOrDefault(r.getSourceId(), "节点" + r.getSourceId()));
            m.put("targetName", nameMap.getOrDefault(r.getTargetId(), "节点" + r.getTargetId()));
            m.put("relationType", r.getRelationType());
            return m;
        }).collect(Collectors.toList());

        // relatedPoints
        List<Map<String, Object>> relatedPoints = subgraphPoints.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("description", p.getDescription());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entities", entities);
        result.put("triples", triples);
        result.put("relatedPoints", relatedPoints);
        return result;
    }
}
