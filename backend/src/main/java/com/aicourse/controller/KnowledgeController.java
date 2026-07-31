package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.JsonUtils;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.KnowledgeExtractDTO;
import com.aicourse.dto.KnowledgeRelationDTO;
import com.aicourse.dto.PathPlanDTO;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.service.AIService;
import com.aicourse.service.KnowledgeGraphService;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 知识点与知识图谱统一域控制器。
 * <p>
 * 由原 KnowledgePointController(/knowledge-points)、KnowledgeGraphController(/knowledge)、
 * KnowledgePointRelationController(/knowledge-relations) 三控制器合并而来，统一为单一
 * /knowledge 域，端点按 points / relations 子资源重排，消除三套路径与重复逻辑。
 * 基础路径: /api/knowledge
 */
@RestController
@RequestMapping("/knowledge")
@Slf4j
@Tag(name = "知识图谱")
public class KnowledgeController {

    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointRelationService relationService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final AIService aiService;

    private static final ObjectMapper KG_OM = new ObjectMapper();
    private static final Set<String> KP_TYPES = Set.of("CONCEPT", "PRINCIPLE", "METHOD", "FACT");

    public KnowledgeController(KnowledgePointService knowledgePointService,
                               KnowledgePointRelationService relationService,
                               KnowledgeGraphService knowledgeGraphService,
                               AIService aiService) {
        this.knowledgePointService = knowledgePointService;
        this.relationService = relationService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.aiService = aiService;
    }

    // ==================== 知识点 CRUD / 列表（/knowledge/points） ====================

    /**
     * 分页/条件查询知识点列表
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
     * 根据ID查询知识点详情
     * GET /api/knowledge/points/{id}
     */
    @GetMapping("/points/{id}")
    public ApiResponse<KnowledgePoint> getPointDetail(@PathVariable Long id) {
        return ApiResponse.success(knowledgePointService.getById(id));
    }

    /**
     * 新增知识点（教师/管理员权限）
     * POST /api/knowledge/points
     */
    @PostMapping("/points")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgePoint> createPoint(@Valid @RequestBody KnowledgePoint point) {
        knowledgePointService.save(point);
        return ApiResponse.success("创建成功", point);
    }

    /**
     * 修改知识点（教师/管理员权限）
     * PUT /api/knowledge/points/{id}
     */
    @PutMapping("/points/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgePoint> updatePoint(@PathVariable Long id, @Valid @RequestBody KnowledgePoint point) {
        point.setId(id);
        knowledgePointService.updateById(point);
        return ApiResponse.success("更新成功", knowledgePointService.getById(id));
    }

    /**
     * 删除知识点（教师/管理员权限）
     * DELETE /api/knowledge/points/{id}
     */
    @DeleteMapping("/points/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> deletePoint(@PathVariable Long id) {
        knowledgePointService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    // ==================== 知识点子资源（/knowledge/points/{id}/...） ====================

    /**
     * 查询当前知识点的所有关联关系（节点 + 边）
     * GET /api/knowledge/points/{id}/relations
     */
    @GetMapping("/points/{id}/relations")
    public ApiResponse<Map<String, Object>> getPointRelations(@PathVariable Long id) {
        LambdaQueryWrapper<KnowledgePointRelation> qw = new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getSourceId, id)
                .or()
                .eq(KnowledgePointRelation::getTargetId, id);
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
     * 获取知识点的前置依赖
     * GET /api/knowledge/points/{id}/prerequisites
     */
    @GetMapping("/points/{id}/prerequisites")
    public ApiResponse<List<KnowledgePoint>> getPrerequisites(@PathVariable Long id) {
        return ApiResponse.success(knowledgePointService.getPrerequisites(id));
    }

    /**
     * 获取知识点的后续知识点（被谁依赖）
     * GET /api/knowledge/points/{id}/successors
     */
    @GetMapping("/points/{id}/successors")
    public ApiResponse<List<KnowledgePoint>> getSubsequentPoints(@PathVariable Long id) {
        return ApiResponse.success(knowledgePointService.getSubsequentPoints(id));
    }

    /**
     * 获取某知识点的直接子节点
     * GET /api/knowledge/points/{id}/children
     */
    @GetMapping("/points/{id}/children")
    public ApiResponse<List<KnowledgePoint>> getChildPoints(@PathVariable Long id) {
        return ApiResponse.success(knowledgePointService.getChildPoints(id));
    }

    /**
     * 计算学生对某知识点的掌握度
     * GET /api/knowledge/points/{id}/mastery/{userId}
     */
    @GetMapping("/points/{id}/mastery/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER') or #userId == authentication.principal")
    public ApiResponse<BigDecimal> calculateMastery(@PathVariable Long id, @PathVariable Long userId) {
        return ApiResponse.success(knowledgePointService.calculateMastery(id, userId));
    }

    /**
     * 获取课程的完整知识树（层级结构）
     * GET /api/knowledge/points/tree/{courseId}
     */
    @GetMapping("/points/tree/{courseId}")
    public ApiResponse<List<Map<String, Object>>> getKnowledgeTree(@PathVariable Long courseId) {
        return ApiResponse.success(knowledgePointService.getKnowledgeTree(courseId));
    }

    /**
     * 获取课程的根级知识点
     * GET /api/knowledge/points/root/{courseId}
     */
    @GetMapping("/points/root/{courseId}")
    public ApiResponse<List<KnowledgePoint>> getRootPoints(@PathVariable Long courseId) {
        return ApiResponse.success(knowledgePointService.getRootPoints(courseId));
    }

    /**
     * 获取课程知识点图谱全量数据（nodes + edges + 统计）
     * GET /api/knowledge/points/graph?courseId=
     */
    @GetMapping("/points/graph")
    @Operation(summary = "获取知识图谱全量数据", description = "返回课程知识点图谱的所有节点和边关系数据")
    public ApiResponse<Map<String, Object>> getGraph(@RequestParam Long courseId) {
        LambdaQueryWrapper<KnowledgePoint> pointWrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId);
        List<KnowledgePoint> pointList = knowledgePointService.list(pointWrapper);
        List<KnowledgePointRelation> relationList = relationService.list();

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("nodes", pointList);
        dataMap.put("edges", relationList);
        dataMap.put("totalPoints", pointList.size());
        dataMap.put("totalRelations", relationList.size());
        return ApiResponse.success(dataMap);
    }

    // ==================== 知识点关联（/knowledge/relations） ====================

    /**
     * 创建知识点关联关系（教师/管理员权限，支持 strength/description）
     * POST /api/knowledge/relations
     */
    @PostMapping("/relations")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgePointRelation> createRelation(@Valid @RequestBody KnowledgeRelationDTO dto) {
        KnowledgePointRelation relation = relationService.createRelation(
                dto.getSourceId(), dto.getTargetId(), dto.getRelationType(), dto.getStrength(), dto.getDescription());
        return ApiResponse.success("创建成功", relation);
    }

    /**
     * 删除知识点关联关系（教师/管理员权限）
     * DELETE /api/knowledge/relations/{id}
     */
    @DeleteMapping("/relations/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> deleteRelation(@PathVariable Long id) {
        relationService.deleteRelation(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 获取关联关系列表（分页，支持课程筛选）
     * GET /api/knowledge/relations
     */
    @GetMapping("/relations")
    public ApiResponse<PagedResponse<KnowledgePointRelation>> getRelationList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long courseId) {
        Page<KnowledgePointRelation> result = relationService.getRelationList(page, size, courseId);
        return ApiResponse.success(PagedResponse.of(result));
    }

    /**
     * 获取某知识点的所有关联关系（入向+出向）
     * GET /api/knowledge/relations/point/{pointId}
     */
    @GetMapping("/relations/point/{pointId}")
    public ApiResponse<List<KnowledgePointRelation>> getRelationsByPoint(@PathVariable Long pointId) {
        return ApiResponse.success(relationService.getRelationsByPoint(pointId));
    }

    /**
     * 获取某知识点的出边（作为源节点）
     * GET /api/knowledge/relations/source/{sourceId}
     */
    @GetMapping("/relations/source/{sourceId}")
    public ApiResponse<List<KnowledgePointRelation>> getSourceRelations(@PathVariable Long sourceId) {
        return ApiResponse.success(relationService.getSourceRelations(sourceId));
    }

    /**
     * 获取某知识点的入边（作为目标节点）
     * GET /api/knowledge/relations/target/{targetId}
     */
    @GetMapping("/relations/target/{targetId}")
    public ApiResponse<List<KnowledgePointRelation>> getTargetRelations(@PathVariable Long targetId) {
        return ApiResponse.success(relationService.getTargetRelations(targetId));
    }

    /**
     * 获取课程知识图谱可视化图数据（nodes + links，供 ECharts/D3 渲染）
     * GET /api/knowledge/relations/graph/{courseId}
     */
    @GetMapping("/relations/graph/{courseId}")
    @Operation(summary = "获取知识图谱可视化数据", description = "返回可供前端 ECharts/D3 渲染的图数据，包含节点和连接关系")
    public ApiResponse<Map<String, Object>> getRelationGraphData(@PathVariable Long courseId) {
        return ApiResponse.success(relationService.getGraphData(courseId));
    }

    // ==================== 图谱查询（/knowledge/*） ====================

    /**
     * 知识点搜索（全文检索）
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

    /**
     * 学习路径规划（起点到目标的最优路径，BFS 算法由 KnowledgeGraphService 提供）
     * POST /api/knowledge/path/plan
     */
    @PostMapping("/path/plan")
    public ApiResponse<List<KnowledgePoint>> planPath(@Valid @RequestBody PathPlanDTO dto) {
        Long startPointId = dto.getStartPointId();
        Long targetPointId = dto.getTargetPointId();

        List<KnowledgePoint> path = knowledgeGraphService.planLearningPath(startPointId, targetPointId);

        if (path.isEmpty()) {
            // 起点与目标相同时服务层返回单点；此处为空说明两点不可达
            return ApiResponse.success("未找到从起点到目标的学习路径，建议先学习相关知识再尝试", Collections.emptyList());
        }
        if (path.size() == 1 && startPointId.equals(targetPointId)) {
            return ApiResponse.success("起点与目标相同", path);
        }
        return ApiResponse.success("路径规划成功，共" + path.size() + "个知识点", path);
    }

    /**
     * AI 抽取知识点与关系（教师/管理员权限）
     * 从课程内容调用大模型抽取结构，写入课程知识点表与关系表（真实持久化，前端列表立即可见）
     * POST /api/knowledge/extract
     */
    @PostMapping("/extract")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Callable<ApiResponse<Map<String, Object>>> extractGraph(@Valid @RequestBody KnowledgeExtractDTO dto) {
        final Long courseId = dto.getCourseId();
        final String content = dto.getContent() != null ? dto.getContent() : "";
        return () -> {
            if (content.isBlank()) {
                return ApiResponse.error(ApiCode.BAD_REQUEST, "请提供待抽取的课程内容");
            }
            String json = aiService.extractKnowledgeGraphJson(content);
            String raw = JsonUtils.extractJsonObject(json);
            Map<String, Object> result = new LinkedHashMap<>();
            List<KnowledgePoint> created = new ArrayList<>();
            Map<String, Long> nameToId = new HashMap<>();
            List<KnowledgePoint> existing = knowledgePointService.list(
                    new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));
            for (KnowledgePoint ep : existing) {
                if (ep.getName() != null) nameToId.put(ep.getName().trim().toLowerCase(), ep.getId());
            }
            if (raw != null) {
                try {
                    JsonNode root = KG_OM.readTree(raw);
                    if (root.has("nodes") && root.get("nodes").isArray()) {
                        for (JsonNode n : root.get("nodes")) {
                            String name = n.has("name") ? n.get("name").asText() : null;
                            if (name == null || name.isBlank()) continue;
                            String key = name.trim().toLowerCase();
                            if (nameToId.containsKey(key)) {
                                continue;
                            }
                            String kc = n.has("content") ? n.get("content").asText() : "";
                            String type = n.has("type") ? n.get("type").asText().toUpperCase() : "CONCEPT";
                            if (!KP_TYPES.contains(type)) type = "CONCEPT";
                            KnowledgePoint kp = new KnowledgePoint();
                            kp.setCourseId(courseId);
                            kp.setName(name.trim());
                            kp.setDescription(kc);
                            kp.setPointType(type);
                            knowledgePointService.save(kp);
                            created.add(kp);
                            nameToId.put(key, kp.getId());
                        }
                    }
                    List<Map<String, Object>> createdRelations = new ArrayList<>();
                    if (root.has("relations") && root.get("relations").isArray()) {
                        for (JsonNode r : root.get("relations")) {
                            String sName = r.has("source") ? r.get("source").asText() : null;
                            String tName = r.has("target") ? r.get("target").asText() : null;
                            String rel = r.has("relationType") ? r.get("relationType").asText().toUpperCase() : "RELATED_TO";
                            Long sId = sName != null ? nameToId.get(sName.trim().toLowerCase()) : null;
                            Long tId = tName != null ? nameToId.get(tName.trim().toLowerCase()) : null;
                            if (sId != null && tId != null && !sId.equals(tId)) {
                                try {
                                    KnowledgePointRelation relEntity = relationService.createRelation(sId, tId, rel, null, null);
                                    Map<String, Object> m = new LinkedHashMap<>();
                                    m.put("id", relEntity.getId());
                                    m.put("source", sName);
                                    m.put("target", tName);
                                    m.put("relationType", rel);
                                    createdRelations.add(m);
                                } catch (Exception ex) {
                                    log.debug("跳过关系 {} -> {}: {}", sName, tName, ex.getMessage());
                                }
                            }
                        }
                    }
                    result.put("relationCount", createdRelations.size());
                    result.put("relations", createdRelations);
                } catch (Exception e) {
                    log.warn("知识图谱抽取结果解析失败: {}", e.getMessage());
                    result.put("parseError", "AI 返回结构解析失败，请稍后重试");
                }
            } else {
                result.put("parseError", "AI 未返回有效结构");
            }
            result.put("nodeCount", created.size());
            result.put("nodes", created);
            return ApiResponse.success("AI 抽取完成，共写入 " + created.size() + " 个知识点", result);
        };
    }

    // ==================== 图谱可视化接口（关系库实现，非 Neo4j） ====================

    /**
     * 获取课程知识图谱可视化数据（基于关系表实现，非 Neo4j）
     * GET /api/knowledge/graph/course/{courseId}
     */
    @GetMapping("/graph/course/{courseId}")
    public ApiResponse<String> getGraphByCourse(@PathVariable Long courseId) {
        return ApiResponse.success("获取成功", knowledgeGraphService.getGraph(courseId, null));
    }

    /**
     * 获取节点详情（图谱节点）
     * GET /api/knowledge/graph/nodes/{nodeId}
     */
    @GetMapping("/graph/nodes/{nodeId}")
    public ApiResponse<String> getNodeDetail(@PathVariable Long nodeId) {
        return ApiResponse.success("获取成功", knowledgeGraphService.getGraph(null, nodeId));
    }

    /**
     * 获取节点关联关系（支持深度查询）
     * GET /api/knowledge/graph/nodes/{nodeId}/relations
     */
    @GetMapping("/graph/nodes/{nodeId}/relations")
    public ApiResponse<String> getNodeRelations(@PathVariable Long nodeId,
                                                @RequestParam(defaultValue = "2") int depth) {
        return ApiResponse.success("获取成功", knowledgeGraphService.getNodeRelations(nodeId, depth));
    }
}
