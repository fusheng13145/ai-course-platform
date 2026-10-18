package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.JsonUtils;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.KnowledgeExtractDTO;
import com.aicourse.dto.KnowledgeRelationDTO;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.dto.KnowledgePathPlanDTO;
import com.aicourse.service.AIService;
import com.aicourse.service.KnowledgeGraphService;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.component.ai.AiCallContext;
import com.aicourse.vo.KnowledgeGraphDataVO;
import com.aicourse.vo.KnowledgeGraphVO;
import com.aicourse.vo.KnowledgeOverviewVO;
import com.aicourse.vo.KnowledgePointRelationsVO;
import com.aicourse.vo.KnowledgeTreeNodeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    private final AIService aiService;
    private final KnowledgeGraphService knowledgeGraphService;

    private static final ObjectMapper KG_OM = new ObjectMapper();
    private static final Set<String> KP_TYPES = Set.of("CONCEPT", "PRINCIPLE", "METHOD", "FACT");
    /** 核心知识点判定阈值：重要度不低于该值 */
    private static final int CORE_IMPORTANCE = 8;

    public KnowledgeController(KnowledgePointService knowledgePointService,
                               KnowledgePointRelationService relationService,
                               AIService aiService,
                               KnowledgeGraphService knowledgeGraphService) {
        this.knowledgePointService = knowledgePointService;
        this.relationService = relationService;
        this.aiService = aiService;
        this.knowledgeGraphService = knowledgeGraphService;
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
     * 分页查询知识点列表（性能维度补强：消除图谱/列表全量加载瓶颈）
     * GET /api/knowledge/points/page?page=1&size=10&courseId=&keyword=
     * 响应含 total / pages / records，供前端渐进迁移分页展示。
     * 与原 /points 并存：不传分页参数仍走全量，向后完全兼容。
     */
    @GetMapping("/points/page")
    public ApiResponse<PagedResponse<KnowledgePoint>> listPointsPaged(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword) {
        // size 上限保护：防止单次拉取过大（默认 10，最大 200）
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 1);
        LambdaQueryWrapper<KnowledgePoint> qw = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(courseId != null, KnowledgePoint::getCourseId, courseId)
                .like(keyword != null && !keyword.isEmpty(), KnowledgePoint::getName, keyword)
                .orderByAsc(KnowledgePoint::getId);
        Page<KnowledgePoint> result = knowledgePointService.page(
                new Page<>(safePage, safeSize), qw);
        return ApiResponse.success(PagedResponse.of(result));
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
    public ApiResponse<KnowledgePointRelationsVO> getPointRelations(@PathVariable Long id) {
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

        return ApiResponse.success(new KnowledgePointRelationsVO(nodes, relations));
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
    public ApiResponse<List<KnowledgeTreeNodeVO>> getKnowledgeTree(@PathVariable Long courseId) {
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
    public ApiResponse<KnowledgeGraphVO> getGraph(@RequestParam Long courseId) {
        LambdaQueryWrapper<KnowledgePoint> pointWrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId);
        List<KnowledgePoint> pointList = knowledgePointService.list(pointWrapper);

        // 边与节点必须同域：本端点一次返回整图数据，若按课程过滤节点却取全表关系，
        // 其他课程的关联行会一起下发（力导向图里表现为悬空边与虚高的 totalRelations）。
        Set<Long> pointIds = new HashSet<>();
        for (KnowledgePoint point : pointList) {
            pointIds.add(point.getId());
        }
        List<KnowledgePointRelation> relationList = pointIds.isEmpty()
                ? Collections.emptyList()
                : relationService.list(new LambdaQueryWrapper<KnowledgePointRelation>()
                        .in(KnowledgePointRelation::getSourceId, pointIds)
                        .in(KnowledgePointRelation::getTargetId, pointIds));

        return ApiResponse.success(new KnowledgeGraphVO(
                pointList, relationList, pointList.size(), relationList.size()));
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
    public ApiResponse<KnowledgeGraphDataVO> getRelationGraphData(@PathVariable Long courseId) {
        return ApiResponse.success(relationService.getGraphData(courseId));
    }

    // ==================== 查询与抽取（/knowledge/*） ====================

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
     * <p>给定 courseId 时为课程口径：关系数、核心知识点、覆盖度均限定在该课程的知识点集合内，
     * 与图谱可视化端点保持一致；courseId 为空时退化为全平台口径。</p>
     */
    @GetMapping("/overview")
    public ApiResponse<KnowledgeOverviewVO> overview(
            @RequestParam(required = false) Long courseId) {
        List<KnowledgePoint> points = courseId == null
                ? knowledgePointService.list()
                : knowledgePointService.list(new LambdaQueryWrapper<KnowledgePoint>()
                        .eq(KnowledgePoint::getCourseId, courseId));

        Set<Long> pointIds = new HashSet<>();
        for (KnowledgePoint point : points) {
            pointIds.add(point.getId());
        }

        List<KnowledgePointRelation> relations = pointIds.isEmpty()
                ? Collections.emptyList()
                : relationService.list(new LambdaQueryWrapper<KnowledgePointRelation>()
                        .in(KnowledgePointRelation::getSourceId, pointIds)
                        .in(KnowledgePointRelation::getTargetId, pointIds));

        Set<Long> coveredIds = new HashSet<>();
        for (KnowledgePointRelation relation : relations) {
            coveredIds.add(relation.getSourceId());
            coveredIds.add(relation.getTargetId());
        }
        long corePoints = 0;
        for (KnowledgePoint point : points) {
            if (point.getImportance() != null && point.getImportance() >= CORE_IMPORTANCE) {
                corePoints++;
            }
        }
        double coverageRate = points.isEmpty() ? 0d : (double) coveredIds.size() / points.size();

        return ApiResponse.success(new KnowledgeOverviewVO(
                (long) points.size(), (long) relations.size(), corePoints, coverageRate, courseId));
    }


    /**
     * AI 抽取知识点与关系（教师/管理员权限）
     * 从课程内容调用大模型抽取结构，写入课程知识点表与关系表（真实持久化，前端列表立即可见）
     * POST /api/knowledge/extract
     */
    @PostMapping("/extract")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Callable<ApiResponse<Map<String, Object>>> extractGraph(@Valid @RequestBody KnowledgeExtractDTO dto,
                                                                   Authentication auth) {
        final Long courseId = dto.getCourseId();
        final String content = dto.getContent() != null ? dto.getContent() : "";
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        return () -> {
            if (content.isBlank()) {
                return ApiResponse.error(ApiCode.BAD_REQUEST, "请提供待抽取的课程内容");
            }
            // 跨课程 AI 统计：图谱抽取同样是一次真实大模型调用，按课程归属落库
            String json = AiCallContext.run(userId, courseId, "GRAPH_EXTRACT",
                    () -> aiService.extractKnowledgeGraphJson(content));
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

    // ==================== 学习路径规划（/knowledge/path/plan） ====================

    /**
     * 起点到目标知识点的最优（最短跳数）学习路径规划。
     * <p>
     * 对应 PRD 知识图谱模块 {@code POST /api/knowledge/path/plan}（起点到目标的最优路径）。
     * 业务委托 {@code KnowledgeGraphService.planLearningPath}：优先由图谱引擎（Python 8001）执行多跳推理，
     * 引擎未启用/不可达时由本端消费侧本地 BFS 兜底（直读 {@code knowledge_point_relations}），
     * 保证端点始终返回真实路径数据。
     * </p>
     * POST /api/knowledge/path/plan
     */
    @PostMapping("/path/plan")
    public ResponseEntity<ApiResponse<com.aicourse.vo.KnowledgePathPlanVO>> planLearningPath(
            @Valid @RequestBody KnowledgePathPlanDTO dto) {
        com.aicourse.vo.KnowledgePathPlanVO plan = knowledgeGraphService.planLearningPath(
                dto.getSourceId(), dto.getTargetId(), dto.getMaxHops() != null ? dto.getMaxHops() : 0);
        if (!plan.isFound()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ApiCode.NOT_FOUND,
                    plan.getNote() != null ? plan.getNote() : "未找到从起点到目标的学习路径"));
        }
        if (dto.getSourceId().equals(dto.getTargetId())) {
            return ResponseEntity.ok(ApiResponse.success("起点与终点相同，无需规划", plan));
        }
        return ResponseEntity.ok(ApiResponse.success(plan));
    }

    /**
     * 知识图谱摄取（将课程知识点/关系同步至图谱引擎）。
     * 引擎未接入时返回消费侧本地确认（{@code mode=LOCAL}），端点始终可用，与 {@code planLearningPath} 同款降级策略。
     */
    @PostMapping("/ingest")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Map<String, Object>> ingestGraph(@RequestParam Long courseId) {
        String raw = knowledgeGraphService.ingest(courseId);
        return ApiResponse.success(parseKgJson(raw));
    }

    /**
     * 获取课程知识子图（节点 + 边）。
     * 引擎未接入时返回消费侧 MySQL 直读结果（{@code mode=LOCAL}），端点始终可用。
     */
    @GetMapping("/subgraph/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Map<String, Object>> getSubGraph(@PathVariable Long courseId,
                                                        @RequestParam(required = false) Integer maxHops) {
        String raw = knowledgeGraphService.getSubGraph(courseId, maxHops != null ? maxHops : 0);
        return ApiResponse.success(parseKgJson(raw));
    }

    /**
     * 将图谱服务返回的 JSON 字符串安全解析为 Map；解析失败或含 503 占位时返回友好提示。
     */
    private Map<String, Object> parseKgJson(String raw) {
        if (raw == null || raw.isBlank()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("mode", "LOCAL");
            fallback.put("note", "知识图谱服务返回为空，已使用本地降级");
            return fallback;
        }
        try {
            JsonNode node = KG_OM.readTree(raw);
            if (node.has("code") && node.get("code").asInt() == 503) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("mode", "UNAVAILABLE");
                fallback.put("note", "知识图谱引擎未接入或不可用");
                return fallback;
            }
            Map<String, Object> map = KG_OM.convertValue(node, Map.class);
            return map != null ? map : Map.of("mode", "LOCAL");
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("mode", "LOCAL");
            fallback.put("note", "知识图谱服务返回解析失败，已使用本地降级");
            return fallback;
        }
    }


}
