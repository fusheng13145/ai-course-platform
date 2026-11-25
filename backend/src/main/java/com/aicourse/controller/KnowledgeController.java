package com.aicourse.controller;

import com.aicourse.application.knowledge.KnowledgePointApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.KnowledgePointSaveDTO;
import com.aicourse.vo.KnowledgeGraphVO;
import com.aicourse.vo.KnowledgePointRelationsVO;
import com.aicourse.vo.KnowledgeTreeNodeVO;
import com.aicourse.vo.KnowledgeVOs;
import com.aicourse.vo.MasteryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识点与课程知识树接口（2026-11-18 从 709 行 KnowledgeController 拆分）。
 *
 * <p>保留 points 的 CRUD、分页、详情、知识点子资源（关联/前置/后继/子节点/掌握度）、
 * 课程知识树与图谱全量数据。关系写入见 {@link KnowledgeRelationController}。</p>
 *
 * <p>3E-05C：可见范围过滤、5 处 wrapper、图谱快照版本与 CRUD 守卫编排已下移到
 * {@link KnowledgePointApplicationService}；本类只留路由与 {@code @PreAuthorize}。</p>
 */
@RestController
@RequestMapping("/knowledge")
@Tag(name = "知识图谱-知识点")
public class KnowledgeController {

    private final KnowledgePointApplicationService knowledgePointApplicationService;

    public KnowledgeController(KnowledgePointApplicationService knowledgePointApplicationService) {
        this.knowledgePointApplicationService = knowledgePointApplicationService;
    }

    // ==================== 知识点 CRUD / 列表（/knowledge/points） ====================

    /** 分页/条件查询知识点列表。 */
    @GetMapping("/points")
    public ApiResponse<List<KnowledgeVOs.PointVO>> listPoints(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword,
            Authentication auth) {
        return ApiResponse.success(
                knowledgePointApplicationService.listPoints(courseId, keyword, auth));
    }

    /** 分页查询知识点列表；size 上限 200，page 下限 1。 */
    @GetMapping("/points/page")
    public ApiResponse<PagedResponse<KnowledgeVOs.PointVO>> listPointsPaged(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword,
            Authentication auth) {
        return ApiResponse.success(
                knowledgePointApplicationService.listPointsPaged(page, size, courseId, keyword, auth));
    }

    /** 根据 ID 查询知识点详情。 */
    @GetMapping("/points/{id}")
    public ApiResponse<KnowledgeVOs.PointVO> getPointDetail(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(knowledgePointApplicationService.pointDetail(id, auth));
    }

    /** 新增知识点（教师/管理员权限）。 */
    @PostMapping("/points")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgeVOs.PointVO> createPoint(@Valid @RequestBody KnowledgePointSaveDTO dto,
                                                         Authentication auth) {
        return ApiResponse.success("创建成功", knowledgePointApplicationService.createPoint(dto, auth));
    }

    /** 修改知识点（教师/管理员权限）。 */
    @PutMapping("/points/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgeVOs.PointVO> updatePoint(@PathVariable Long id,
                                                         @Valid @RequestBody KnowledgePointSaveDTO dto,
                                                         Authentication auth) {
        return ApiResponse.success("更新成功", knowledgePointApplicationService.updatePoint(id, dto, auth));
    }

    /** 删除知识点（教师/管理员权限）。 */
    @DeleteMapping("/points/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> deletePoint(@PathVariable Long id, Authentication auth) {
        knowledgePointApplicationService.deletePoint(id, auth);
        return ApiResponse.success("删除成功", null);
    }

    // ==================== 知识点子资源（/knowledge/points/{id}/...） ====================

    /** 查询当前知识点的所有关联关系（节点 + 边），只下发同课程节点/边。 */
    @GetMapping("/points/{id}/relations")
    public ApiResponse<KnowledgePointRelationsVO> getPointRelations(@PathVariable Long id,
                                                                    Authentication auth) {
        return ApiResponse.success(knowledgePointApplicationService.pointRelations(id, auth));
    }

    /** 获取知识点的前置依赖。 */
    @GetMapping("/points/{id}/prerequisites")
    public ApiResponse<List<KnowledgeVOs.PointVO>> getPrerequisites(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(knowledgePointApplicationService.prerequisites(id, auth));
    }

    /** 获取知识点的后续知识点（被谁依赖）。 */
    @GetMapping("/points/{id}/successors")
    public ApiResponse<List<KnowledgeVOs.PointVO>> getSubsequentPoints(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(knowledgePointApplicationService.successors(id, auth));
    }

    /** 获取某知识点的直接子节点。 */
    @GetMapping("/points/{id}/children")
    public ApiResponse<List<KnowledgeVOs.PointVO>> getChildPoints(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(knowledgePointApplicationService.children(id, auth));
    }

    /**
     * 计算学生对某知识点的掌握度。
     * 教师/管理员查询时必须确认目标学生确实在该课程中有有效选课，避免学情隐私越权。
     */
    @GetMapping("/points/{id}/mastery/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER') or #userId == authentication.principal")
    public ApiResponse<MasteryVO> calculateMastery(@PathVariable Long id, @PathVariable Long userId,
                                                   Authentication auth) {
        return ApiResponse.success(knowledgePointApplicationService.calculateMastery(id, userId, auth));
    }

    /** 获取课程的完整知识树（层级结构）。 */
    @GetMapping("/points/tree/{courseId}")
    public ApiResponse<List<KnowledgeTreeNodeVO>> getKnowledgeTree(@PathVariable Long courseId,
                                                                   Authentication auth) {
        return ApiResponse.success(knowledgePointApplicationService.knowledgeTree(courseId, auth));
    }

    /** 获取课程的根级知识点。 */
    @GetMapping("/points/root/{courseId}")
    public ApiResponse<List<KnowledgeVOs.PointVO>> getRootPoints(@PathVariable Long courseId,
                                                                Authentication auth) {
        return ApiResponse.success(knowledgePointApplicationService.rootPoints(courseId, auth));
    }

    /** 获取课程知识点图谱全量数据（nodes + edges + 统计）。 */
    @GetMapping("/points/graph")
    @Operation(summary = "获取知识图谱全量数据", description = "返回课程知识点图谱的所有节点和边关系数据")
    public ApiResponse<KnowledgeGraphVO> getGraph(@RequestParam Long courseId, Authentication auth) {
        return ApiResponse.success(knowledgePointApplicationService.graph(courseId, auth));
    }
}
