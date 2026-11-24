package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.dto.KnowledgeExtractDTO;
import com.aicourse.dto.KnowledgePathPlanDTO;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.exception.BusinessException;
import com.aicourse.integration.ContractPaths;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.KnowledgeAccessSupport;
import com.aicourse.service.AIService;
import com.aicourse.service.KnowledgeGraphService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.common.AiCallContext;
import com.aicourse.vo.KnowledgePathPlanVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 图谱抽取、学习路径与图谱引擎读写（2026-11-18 从 KnowledgeController 拆出）。
 */
@RestController
@RequestMapping("/knowledge")
@Tag(name = "知识图谱-抽取与路径")
public class KnowledgeExtractionController {

    /** 本类日志（DEF-05：降级分支必须留痕，否则「知识图谱返回解析失败」在生产里无声无息）。 */
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(KnowledgeExtractionController.class);

    private static final ObjectMapper KG_OM = new ObjectMapper();

    private final KnowledgeAccessSupport knowledgeAccessSupport;
    private final KnowledgePointService knowledgePointService;
    private final AIService aiService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final CourseAccessControl courseAccessControl;

    public KnowledgeExtractionController(KnowledgeAccessSupport knowledgeAccessSupport,
                                         KnowledgePointService knowledgePointService,
                                         AIService aiService,
                                         KnowledgeGraphService knowledgeGraphService,
                                         CourseAccessControl courseAccessControl) {
        this.knowledgeAccessSupport = knowledgeAccessSupport;
        this.knowledgePointService = knowledgePointService;
        this.aiService = aiService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.courseAccessControl = courseAccessControl;
    }

    /**
     * AI 抽取知识点与关系（教师/管理员权限）。
     * 解析与逐条落库已下沉到 KnowledgePointService#extractAndSaveGraph（事务化 + 缓存逐出）。
     */
    @PostMapping("/extract")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Callable<ApiResponse<Map<String, Object>>> extractGraph(@Valid @RequestBody KnowledgeExtractDTO dto,
                                                                   Authentication auth) {
        final Long courseId = dto.getCourseId();
        final String content = dto.getContent() != null ? dto.getContent() : "";
        final Long userId = auth != null ? (Long) auth.getPrincipal() : null;
        courseAccessControl.assertCanManageCourse(courseId, auth);
        return () -> {
            if (content.isBlank()) {
                return ApiResponse.error(ApiCode.BAD_REQUEST, "请提供待抽取的课程内容");
            }
            String json = AiCallContext.run(userId, courseId, "GRAPH_EXTRACT",
                    () -> aiService.extractKnowledgeGraphJson(content));
            Map<String, Object> result = knowledgePointService.extractAndSaveGraph(courseId, json);
            Object nodeCount = result.getOrDefault("nodeCount", 0);
            return ApiResponse.success("AI 抽取完成，共写入 " + nodeCount + " 个知识点", result);
        };
    }

    /** 起点到目标知识点的最优（最短跳数）学习路径规划。 */
    @PostMapping("/path/plan")
    public ResponseEntity<ApiResponse<KnowledgePathPlanVO>> planLearningPath(
            @Valid @RequestBody KnowledgePathPlanDTO dto, Authentication auth) {
        KnowledgePoint source = knowledgeAccessSupport.requirePoint(dto.getSourceId());
        KnowledgePoint target = knowledgeAccessSupport.requirePoint(dto.getTargetId());
        courseAccessControl.assertCanReadCourse(source.getCourseId(), auth);
        if (!Objects.equals(source.getCourseId(), target.getCourseId())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "学习路径只能在同一课程的知识点之间规划");
        }
        KnowledgePathPlanVO plan = knowledgeGraphService.planLearningPath(
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

    /** 知识图谱摄取；引擎不可用时返回 LOCAL/UNAVAILABLE 降级。 */
    @PostMapping("/ingest")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Map<String, Object>> ingestGraph(@RequestParam Long courseId, Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        String raw = knowledgeGraphService.ingest(courseId);
        return ApiResponse.success(parseKgJson(raw));
    }

    /** 获取课程知识子图；引擎不可用时返回消费侧 MySQL 直读结果。 */
    @GetMapping("/subgraph/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Map<String, Object>> getSubGraph(@PathVariable Long courseId,
                                                        @RequestParam(required = false) Integer maxHops,
                                                        Authentication auth) {
        courseAccessControl.assertCanManageCourse(courseId, auth);
        String raw = knowledgeGraphService.getSubGraph(courseId, maxHops != null ? maxHops : 0);
        return ApiResponse.success(parseKgJson(raw));
    }

    private Map<String, Object> parseKgJson(String raw) {
        if (raw == null || raw.isBlank()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put(ContractPaths.R_MODE, ContractPaths.MODE_LOCAL);
            fallback.put("note", "知识图谱服务返回为空，已使用本地降级");
            return fallback;
        }
        try {
            JsonNode node = KG_OM.readTree(raw);
            // 判定口径保持与改造前一致（只看 code=503），仅把魔法数换成契约常量；
            // 若改用 ContractPaths.isUnavailable 会把 mode=UNAVAILABLE 也判为不可用，属于行为变更，不在本批范围。
            if (node.has(ContractPaths.R_CODE)
                    && node.get(ContractPaths.R_CODE).asInt() == ContractPaths.CODE_UNAVAILABLE) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put(ContractPaths.R_MODE, ContractPaths.MODE_UNAVAILABLE);
                fallback.put("note", "知识图谱引擎未接入或不可用");
                return fallback;
            }
            Map<String, Object> map = KG_OM.convertValue(node, Map.class);
            return map != null ? map : Map.of(ContractPaths.R_MODE, ContractPaths.MODE_LOCAL);
        } catch (Exception e) {
            log.warn("知识图谱返回解析失败，降级为本地模式: {}", e.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put(ContractPaths.R_MODE, ContractPaths.MODE_LOCAL);
            fallback.put("note", "知识图谱服务返回解析失败，已使用本地降级");
            return fallback;
        }
    }
}
