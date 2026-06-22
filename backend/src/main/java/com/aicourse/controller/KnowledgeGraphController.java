package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.service.KnowledgeGraphService;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 知识图谱接口控制器（需求文档对齐版）
 * 按照需求文档 4.14 节定义，提供12个知识图谱相关REST API
 * 基础路径: /api/knowledge
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;
    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointRelationService relationService;

    public KnowledgeGraphController(KnowledgeGraphService knowledgeGraphService,
                                     KnowledgePointService knowledgePointService,
                                     KnowledgePointRelationService relationService) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.knowledgePointService = knowledgePointService;
        this.relationService = relationService;
    }

    // ==================== 知识点CRUD（6个接口） ====================

    /**
     * 获取知识点列表（支持课程筛选、关键词搜索）
     * GET /api/knowledge/points
     */
    @GetMapping("/points")
    public ApiResponse<List<KnowledgePoint>> listPoints(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<KnowledgePoint> qw = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(courseId != null, KnowledgePoint::getCourseId, courseId)
                .like(keyword != null && !keyword.isEmpty(), KnowledgePoint::getName, keyword);
        return ApiResponse.success(knowledgePointService.list(qw));
    }

    /**
     * 获取知识点详情
     * GET /api/knowledge/points/{pointId}
     */
    @GetMapping("/points/{pointId}")
    public ApiResponse<KnowledgePoint> getPointDetail(@PathVariable Long pointId) {
        KnowledgePoint point = knowledgePointService.getById(pointId);
        return ApiResponse.success(point);
    }

    /**
     * 创建知识点（教师/管理员权限）
     * POST /api/knowledge/points
     */
    @PostMapping("/points")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgePoint> createPoint(@RequestBody KnowledgePoint point) {
        knowledgePointService.save(point);
        return ApiResponse.success("创建成功", point);
    }

    /**
     * 更新知识点（教师/管理员权限）
     * PUT /api/knowledge/points/{pointId}
     */
    @PutMapping("/points/{pointId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgePoint> updatePoint(@PathVariable Long pointId,
                                                    @RequestBody KnowledgePoint point) {
        point.setId(pointId);
        knowledgePointService.updateById(point);
        return ApiResponse.success("更新成功", knowledgePointService.getById(pointId));
    }

    /**
     * 删除知识点（教师/管理员权限）
     * DELETE /api/knowledge/points/{pointId}
     */
    @DeleteMapping("/points/{pointId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> deletePoint(@PathVariable Long pointId) {
        knowledgePointService.removeById(pointId);
        return ApiResponse.success("删除成功", null);
    }

    // ==================== 知识点关联（3个接口） ====================

    /**
     * 获取知识点关联关系
     * GET /api/knowledge/points/{pointId}/relations
     */
    @GetMapping("/points/{pointId}/relations")
    public ApiResponse<Map<String, Object>> getPointRelations(@PathVariable Long pointId) {
        LambdaQueryWrapper<KnowledgePointRelation> qw = new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getSourceId, pointId)
                .or()
                .eq(KnowledgePointRelation::getTargetId, pointId);
        List<KnowledgePointRelation> relations = relationService.list(qw);

        Set<Long> pointIdSet = new HashSet<>();
        for (KnowledgePointRelation r : relations) {
            pointIdSet.add(r.getSourceId());
            pointIdSet.add(r.getTargetId());
        }
        List<KnowledgePoint> nodes = knowledgePointService.listByIds(pointIdSet);

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", relations);
        return ApiResponse.success(result);
    }

    /**
     * 获取前置知识点（学习依赖路径）
     * GET /api/knowledge/points/{pointId}/prerequisites
     */
    @GetMapping("/points/{pointId}/prerequisites")
    public ApiResponse<List<KnowledgePoint>> getPrerequisites(@PathVariable Long pointId) {
        List<KnowledgePoint> prerequisites = knowledgePointService.getPrerequisites(pointId);
        return ApiResponse.success(prerequisites);
    }

    /**
     * 获取后继知识点（后续学习建议）
     * GET /api/knowledge/points/{pointId}/successors
     */
    @GetMapping("/points/{pointId}/successors")
    public ApiResponse<List<KnowledgePoint>> getSuccessors(@PathVariable Long pointId) {
        List<KnowledgePoint> successors = knowledgePointService.getSubsequentPoints(pointId);
        return ApiResponse.success(successors);
    }

    // ==================== 图谱查询（3个接口） ====================

    /**
     * 创建知识点关系（教师/管理员权限）
     * POST /api/knowledge/relations
     */
    @PostMapping("/relations")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgePointRelation> createRelation(@RequestBody Map<String, Object> params) {
        Long sourceId = Long.valueOf(params.get("sourceId").toString());
        Long targetId = Long.valueOf(params.get("targetId").toString());
        String relationType = (String) params.getOrDefault("relationType", "RELATED_TO");
        KnowledgePointRelation relation = relationService.createRelation(
                sourceId, targetId, relationType, null, null);
        return ApiResponse.success("创建成功", relation);
    }

    /**
     * 学习路径规划（起点到目标的最优路径，使用BFS算法）
     * POST /api/knowledge/path/plan
     */
    @PostMapping("/path/plan")
    public ApiResponse<List<KnowledgePoint>> planPath(@RequestBody Map<String, Object> body) {
        Long startPointId = Long.valueOf(body.get("startPointId").toString());
        Long targetPointId = Long.valueOf(body.get("targetPointId").toString());

        if (startPointId.equals(targetPointId)) {
            KnowledgePoint point = knowledgePointService.getById(startPointId);
            return ApiResponse.success("起点与目标相同", Collections.singletonList(point));
        }

        // 获取所有关联关系，构建有向邻接表
        List<KnowledgePointRelation> allRelations = relationService.list();
        Map<Long, List<Long>> adjacency = new HashMap<>();
        for (KnowledgePointRelation rel : allRelations) {
            adjacency.computeIfAbsent(rel.getSourceId(), k -> new ArrayList<>()).add(rel.getTargetId());
        }

        // BFS寻路
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
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        if (!found) {
            return ApiResponse.success("未找到从起点到目标的学习路径，建议先学习相关知识再尝试", Collections.emptyList());
        }

        // 回溯重构路径
        List<Long> pathIds = new ArrayList<>();
        Long cur = targetPointId;
        while (cur != null) {
            pathIds.add(cur);
            cur = parent.get(cur);
        }
        Collections.reverse(pathIds);

        // 按路径顺序获取知识点
        List<KnowledgePoint> allPoints = knowledgePointService.listByIds(pathIds);
        Map<Long, KnowledgePoint> pointMap = new HashMap<>();
        for (KnowledgePoint p : allPoints) {
            pointMap.put(p.getId(), p);
        }
        List<KnowledgePoint> orderedPath = new ArrayList<>();
        for (Long id : pathIds) {
            KnowledgePoint p = pointMap.get(id);
            if (p != null) {
                orderedPath.add(p);
            }
        }

        return ApiResponse.success("路径规划成功，共" + orderedPath.size() + "个知识点", orderedPath);
    }

    /**
     * 知识点搜索（全文搜索）
     * GET /api/knowledge/search
     */
    @GetMapping("/search")
    public ApiResponse<List<KnowledgePoint>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Long courseId) {
        LambdaQueryWrapper<KnowledgePoint> qw = new LambdaQueryWrapper<KnowledgePoint>()
                .like(KnowledgePoint::getName, keyword)
                .eq(courseId != null, KnowledgePoint::getCourseId, courseId);
        return ApiResponse.success(knowledgePointService.list(qw));
    }

    /**
     * 知识图谱概览统计
     * GET /api/knowledge/overview
     */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(
            @RequestParam(required = false) Long courseId) {
        LambdaQueryWrapper<KnowledgePoint> qw = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(courseId != null, KnowledgePoint::getCourseId, courseId);
        long totalPoints = knowledgePointService.count(qw);
        long totalRelations = relationService.count();

        Map<String, Object> data = new HashMap<>();
        data.put("totalPoints", totalPoints);
        data.put("totalRelations", totalRelations);
        data.put("courseId", courseId);
        return ApiResponse.success(data);
    }

    // ==================== 保留Neo4j图谱可视化接口（兼容） ====================

    /**
     * 获取课程知识图谱Neo4j可视化数据
     */
    @GetMapping("/graph/course/{courseId}")
    public String getGraphByCourse(@PathVariable Long courseId) {
        return knowledgeGraphService.getGraph(courseId, null);
    }

    /**
     * 获取节点详情（Neo4j）
     */
    @GetMapping("/graph/nodes/{nodeId}")
    public String getNodeDetail(@PathVariable Long nodeId) {
        return knowledgeGraphService.getGraph(null, nodeId);
    }

    /**
     * 获取节点关联关系（Neo4j，支持深度查询）
     */
    @GetMapping("/graph/nodes/{nodeId}/relations")
    public String getNodeRelations(@PathVariable Long nodeId,
                                    @RequestParam(defaultValue = "2") int depth) {
        return knowledgeGraphService.getNodeRelations(nodeId, depth);
    }
}