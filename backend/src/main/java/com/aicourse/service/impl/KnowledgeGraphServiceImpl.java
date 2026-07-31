package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.service.KnowledgeGraphService;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识图谱服务实现类（自包含版）
 * <p>
 * 原设计通过 HTTP 调用 Python 知识图谱微服务（端口 8001），但该微服务并未随项目交付，
 * 导致文档对齐的 12 个知识图谱 API 整体返回 503。为满足需求文档「知识可视化 / 知识库管理」
 * 的硬性要求，本实现改为直接读写 MySQL 的 {@code knowledge_points} / {@code knowledge_point_relations}
 * 表，复用既有的知识点 CRUD 与关联服务，使图谱真正可用，无需任何外部进程。
 * <p>
 * 图数据返回结构与前端可视化（ECharts/D3）保持一致：{@code {nodes:[{id,name,category}], links:[{source,target,value,relationType}]}}。
 */
@Service
@Slf4j
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {


    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointRelationService relationService;
    private final ObjectMapper objectMapper;

    public KnowledgeGraphServiceImpl(KnowledgePointService knowledgePointService,
                                    KnowledgePointRelationService relationService,
                                    ObjectMapper objectMapper) {
        this.knowledgePointService = knowledgePointService;
        this.relationService = relationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean healthCheck() {
        // 自包含实现，始终可用
        return true;
    }

    @Override
    public String getGraph(Long courseId, Long nodeId) {
        if (courseId != null) {
            // 课程全量图谱，直接复用可视化图数据构建逻辑
            Map<String, Object> data = buildCourseGraph(courseId);
            return toJson(data);
        }
        if (nodeId != null) {
            // 单节点图谱：该节点 + 直接关联节点与边
            KnowledgePoint point = knowledgePointService.getById(nodeId);
            if (point == null) {
                return toJson(Map.of("nodes", Collections.emptyList(), "links", Collections.emptyList()));
            }
            List<KnowledgePointRelation> relations = relationService.getRelationsByPoint(nodeId);
            Set<Long> ids = new HashSet<>();
            ids.add(nodeId);
            for (KnowledgePointRelation r : relations) {
                ids.add(r.getSourceId());
                ids.add(r.getTargetId());
            }
            List<KnowledgePoint> points = knowledgePointService.listByIds(ids);
            Map<String, Object> data = toGraph(points, relations);
            return toJson(data);
        }
        return toJson(Map.of("nodes", Collections.emptyList(), "links", Collections.emptyList()));
    }

    @Override
    public String getNodeRelations(Long nodeId, int depth) {
        int d = depth <= 0 ? 1 : Math.min(depth, 4);
        Set<Long> visited = new HashSet<>();
        Set<Long> current = new HashSet<>();
        current.add(nodeId);
        visited.add(nodeId);
        List<KnowledgePointRelation> edges = new ArrayList<>();
        for (int i = 0; i < d; i++) {
            Set<Long> next = new HashSet<>();
            for (Long id : current) {
                List<KnowledgePointRelation> rels = relationService.getRelationsByPoint(id);
                for (KnowledgePointRelation r : rels) {
                    edges.add(r);
                    if (r.getSourceId() != null && !visited.contains(r.getSourceId())) next.add(r.getSourceId());
                    if (r.getTargetId() != null && !visited.contains(r.getTargetId())) next.add(r.getTargetId());
                }
            }
            visited.addAll(next);
            current = next;
            if (current.isEmpty()) break;
        }
        List<KnowledgePoint> points = knowledgePointService.listByIds(visited);
        Map<String, Object> data = toGraph(points, edges);
        return toJson(data);
    }

    @Override
    public List<KnowledgePoint> planLearningPath(Long startPointId, Long targetPointId) {
        if (startPointId == null || targetPointId == null) {
            return Collections.emptyList();
        }
        // 起点与目标相同：直接返回该知识点
        if (startPointId.equals(targetPointId)) {
            KnowledgePoint point = knowledgePointService.getById(startPointId);
            return point == null ? Collections.emptyList() : Collections.singletonList(point);
        }

        // 以知识关系为有向边构建邻接表
        List<KnowledgePointRelation> allRelations = relationService.list();
        Map<Long, List<Long>> adjacency = new HashMap<>();
        for (KnowledgePointRelation rel : allRelations) {
            if (rel.getSourceId() != null) {
                adjacency.computeIfAbsent(rel.getSourceId(), k -> new ArrayList<>()).add(rel.getTargetId());
            }
        }

        // BFS 求最短路径
        Queue<Long> queue = new LinkedList<>();
        Map<Long, Long> parent = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        queue.add(startPointId);
        visited.add(startPointId);

        boolean found = false;
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current.equals(targetPointId)) {
                found = true;
                break;
            }
            List<Long> neighbors = adjacency.getOrDefault(current, Collections.emptyList());
            for (Long neighbor : neighbors) {
                if (neighbor != null && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }
        if (!found) {
            return Collections.emptyList();
        }

        // 从目标回溯重建路径并保持起点→目标顺序
        List<Long> pathIds = new ArrayList<>();
        Long cur = targetPointId;
        while (cur != null) {
            pathIds.add(cur);
            cur = parent.get(cur);
        }
        Collections.reverse(pathIds);

        Map<Long, KnowledgePoint> pointMap = knowledgePointService.listByIds(pathIds).stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, p -> p, (a, b) -> a));
        List<KnowledgePoint> orderedPath = new ArrayList<>();
        for (Long id : pathIds) {
            KnowledgePoint p = pointMap.get(id);
            if (p != null) {
                orderedPath.add(p);
            }
        }
        return orderedPath;
    }

    @Override
    public String addNode(Long courseId, String name, String content, String type) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setCourseId(courseId);
        kp.setName(name);
        kp.setDescription(content);
        kp.setPointType(type != null && !type.isBlank() ? type.toUpperCase() : "CONCEPT");
        knowledgePointService.save(kp);
        return toJson(kp);
    }

    @Override
    public String addRelation(Long sourceId, Long targetId, String relationType) {
        KnowledgePointRelation rel = relationService.createRelation(
                sourceId, targetId, relationType, BigDecimal.ONE, null);
        return toJson(rel);
    }

    @Override
    public String deleteNode(Long nodeId) {
        knowledgePointService.removeById(nodeId);
        // 级联删除关联边
        relationService.remove(new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getSourceId, nodeId)
                .or(w -> w.eq(KnowledgePointRelation::getTargetId, nodeId)));
        return toJson(Map.of("deleted", nodeId));
    }

    @Override
    public String deleteRelation(Long relationId) {
        relationService.removeById(relationId);
        return toJson(Map.of("deleted", relationId));
    }

    // ===================== 内部工具 =====================

    private Map<String, Object> buildCourseGraph(Long courseId) {
        List<KnowledgePoint> points = knowledgePointService.list(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));
        Set<Long> ids = points.stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
        List<KnowledgePointRelation> relations = Collections.emptyList();
        if (!ids.isEmpty()) {
            relations = relationService.list(new LambdaQueryWrapper<KnowledgePointRelation>()
                    .in(KnowledgePointRelation::getSourceId, ids)
                    .in(KnowledgePointRelation::getTargetId, ids));
        }
        return toGraph(points, relations);
    }

    private Map<String, Object> toGraph(List<KnowledgePoint> points, List<KnowledgePointRelation> relations) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (KnowledgePoint p : points) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", p.getId());
            node.put("name", p.getName());
            node.put("category", p.getPointType() != null ? p.getPointType() : "default");
            node.put("description", p.getDescription());
            nodes.add(node);
        }
        List<Map<String, Object>> links = new ArrayList<>();
        for (KnowledgePointRelation r : relations) {
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("source", r.getSourceId());
            link.put("target", r.getTargetId());
            link.put("value", r.getStrength());
            link.put("relationType", r.getRelationType());
            links.add(link);
        }
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("links", links);
        return graph;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("知识图谱 JSON 序列化失败: {}", e.getMessage());
            return "{\"error\":\"序列化失败\",\"code\":500}";
        }
    }
}
