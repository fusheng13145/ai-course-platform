package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.domain.knowledge.GraphSnapshotVersion;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.KnowledgeAccessSupport;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.vo.KnowledgeGraphVO;
import com.aicourse.vo.KnowledgePointRelationsVO;
import com.aicourse.vo.KnowledgeTreeNodeVO;
import com.aicourse.vo.MasteryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识点与课程知识树接口（2026-11-18 从 709 行 KnowledgeController 拆分）。
 *
 * <p>保留 points 的 CRUD、分页、详情、知识点子资源（关联/前置/后继/子节点/掌握度）、
 * 课程知识树与图谱全量数据。关系写入见 {@link KnowledgeRelationController}。</p>
 */
@RestController
@RequestMapping("/knowledge")
@Tag(name = "知识图谱-知识点")
public class KnowledgeController {

    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointRelationService relationService;
    private final CourseAccessControl courseAccessControl;
    private final CourseEnrollmentService courseEnrollmentService;
    private final KnowledgeAccessSupport knowledgeAccessSupport;

    public KnowledgeController(KnowledgePointService knowledgePointService,
                               KnowledgePointRelationService relationService,
                               CourseAccessControl courseAccessControl,
                               CourseEnrollmentService courseEnrollmentService,
                               KnowledgeAccessSupport knowledgeAccessSupport) {
        this.knowledgePointService = knowledgePointService;
        this.relationService = relationService;
        this.courseAccessControl = courseAccessControl;
        this.courseEnrollmentService = courseEnrollmentService;
        this.knowledgeAccessSupport = knowledgeAccessSupport;
    }

    // ==================== 知识点 CRUD / 列表（/knowledge/points） ====================

    /** 分页/条件查询知识点列表。 */
    @GetMapping("/points")
    public ApiResponse<List<KnowledgePoint>> listPoints(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword,
            Authentication auth) {
        LambdaQueryWrapper<KnowledgePoint> qw = new LambdaQueryWrapper<>();
        if (!knowledgeAccessSupport.applyCourseScope(qw, courseId, auth)) {
            return ApiResponse.success(Collections.emptyList());
        }
        qw.like(keyword != null && !keyword.isEmpty(), KnowledgePoint::getName, keyword);
        return ApiResponse.success(knowledgePointService.list(qw));
    }

    /** 分页查询知识点列表；size 上限 200，page 下限 1。 */
    @GetMapping("/points/page")
    public ApiResponse<PagedResponse<KnowledgePoint>> listPointsPaged(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword,
            Authentication auth) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 1);
        LambdaQueryWrapper<KnowledgePoint> qw = new LambdaQueryWrapper<>();
        if (!knowledgeAccessSupport.applyCourseScope(qw, courseId, auth)) {
            return ApiResponse.success(PagedResponse.of(new Page<>(safePage, safeSize)));
        }
        qw.like(keyword != null && !keyword.isEmpty(), KnowledgePoint::getName, keyword)
                .orderByAsc(KnowledgePoint::getId);
        Page<KnowledgePoint> result = knowledgePointService.page(
                new Page<>(safePage, safeSize), qw);
        return ApiResponse.success(PagedResponse.of(result));
    }

    /** 根据 ID 查询知识点详情。 */
    @GetMapping("/points/{id}")
    public ApiResponse<KnowledgePoint> getPointDetail(@PathVariable Long id, Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        return ApiResponse.success(point);
    }

    /** 新增知识点（教师/管理员权限）。 */
    @PostMapping("/points")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgePoint> createPoint(@Valid @RequestBody KnowledgePoint point,
                                                   Authentication auth) {
        courseAccessControl.assertCanManageCourse(point.getCourseId(), auth);
        knowledgePointService.save(point);
        return ApiResponse.success("创建成功", point);
    }

    /** 修改知识点（教师/管理员权限）。 */
    @PutMapping("/points/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgePoint> updatePoint(@PathVariable Long id,
                                                   @Valid @RequestBody KnowledgePoint point,
                                                   Authentication auth) {
        KnowledgePoint existing = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        point.setId(id);
        point.setCourseId(existing.getCourseId());
        knowledgePointService.updateById(point);
        return ApiResponse.success("更新成功", knowledgePointService.getById(id));
    }

    /** 删除知识点（教师/管理员权限）。 */
    @DeleteMapping("/points/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> deletePoint(@PathVariable Long id, Authentication auth) {
        KnowledgePoint existing = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        knowledgePointService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    // ==================== 知识点子资源（/knowledge/points/{id}/...） ====================

    /** 查询当前知识点的所有关联关系（节点 + 边），只下发同课程节点/边。 */
    @GetMapping("/points/{id}/relations")
    public ApiResponse<KnowledgePointRelationsVO> getPointRelations(@PathVariable Long id,
                                                                   Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);

        LambdaQueryWrapper<KnowledgePointRelation> qw = new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getIsQuarantined, false)
                .and(w -> w.eq(KnowledgePointRelation::getSourceId, id)
                        .or().eq(KnowledgePointRelation::getTargetId, id));
        List<KnowledgePointRelation> relations = relationService.list(qw);

        Set<Long> pointIdSet = new HashSet<>();
        for (KnowledgePointRelation r : relations) {
            pointIdSet.add(r.getSourceId());
            pointIdSet.add(r.getTargetId());
        }
        List<KnowledgePoint> nodes = knowledgePointService.listByIds(pointIdSet).stream()
                .filter(node -> Objects.equals(node.getCourseId(), point.getCourseId()))
                .toList();
        Set<Long> allowedIds = nodes.stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
        List<KnowledgePointRelation> scopedRelations = relations.stream()
                .filter(r -> allowedIds.contains(r.getSourceId()) && allowedIds.contains(r.getTargetId()))
                .toList();

        return ApiResponse.success(new KnowledgePointRelationsVO(nodes, scopedRelations));
    }

    /** 获取知识点的前置依赖。 */
    @GetMapping("/points/{id}/prerequisites")
    public ApiResponse<List<KnowledgePoint>> getPrerequisites(@PathVariable Long id, Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        return ApiResponse.success(knowledgeAccessSupport.filterSameCourse(
                knowledgePointService.getPrerequisites(id), point.getCourseId()));
    }

    /** 获取知识点的后续知识点（被谁依赖）。 */
    @GetMapping("/points/{id}/successors")
    public ApiResponse<List<KnowledgePoint>> getSubsequentPoints(@PathVariable Long id, Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        return ApiResponse.success(knowledgeAccessSupport.filterSameCourse(
                knowledgePointService.getSubsequentPoints(id), point.getCourseId()));
    }

    /** 获取某知识点的直接子节点。 */
    @GetMapping("/points/{id}/children")
    public ApiResponse<List<KnowledgePoint>> getChildPoints(@PathVariable Long id, Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        return ApiResponse.success(knowledgeAccessSupport.filterSameCourse(
                knowledgePointService.getChildPoints(id), point.getCourseId()));
    }

    /**
     * 计算学生对某知识点的掌握度。
     * 教师/管理员查询时必须确认目标学生确实在该课程中有有效选课，避免学情隐私越权。
     */
    @GetMapping("/points/{id}/mastery/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER') or #userId == authentication.principal")
    public ApiResponse<MasteryVO> calculateMastery(@PathVariable Long id, @PathVariable Long userId,
                                                   Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        if (userId == null || !courseEnrollmentService.hasApprovedEnrollment(userId, point.getCourseId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "该学生不在此课程，无法读取掌握度");
        }
        return ApiResponse.success(knowledgePointService.calculateMasteryDetail(id, userId));
    }

    /** 获取课程的完整知识树（层级结构）。 */
    @GetMapping("/points/tree/{courseId}")
    public ApiResponse<List<KnowledgeTreeNodeVO>> getKnowledgeTree(@PathVariable Long courseId,
                                                                   Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        return ApiResponse.success(knowledgePointService.getKnowledgeTree(courseId));
    }

    /** 获取课程的根级知识点。 */
    @GetMapping("/points/root/{courseId}")
    public ApiResponse<List<KnowledgePoint>> getRootPoints(@PathVariable Long courseId,
                                                           Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        return ApiResponse.success(knowledgePointService.getRootPoints(courseId));
    }

    /** 获取课程知识点图谱全量数据（nodes + edges + 统计）。 */
    @GetMapping("/points/graph")
    @Operation(summary = "获取知识图谱全量数据", description = "返回课程知识点图谱的所有节点和边关系数据")
    public ApiResponse<KnowledgeGraphVO> getGraph(@RequestParam Long courseId, Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        LambdaQueryWrapper<KnowledgePoint> pointWrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId);
        List<KnowledgePoint> pointList = knowledgePointService.list(pointWrapper);

        Set<Long> pointIds = new HashSet<>();
        for (KnowledgePoint point : pointList) {
            pointIds.add(point.getId());
        }
        List<KnowledgePointRelation> relationList = pointIds.isEmpty()
                ? Collections.emptyList()
                : relationService.list(new LambdaQueryWrapper<KnowledgePointRelation>()
                        .eq(KnowledgePointRelation::getIsQuarantined, false)
                        .in(KnowledgePointRelation::getSourceId, pointIds)
                        .in(KnowledgePointRelation::getTargetId, pointIds));

        LocalDateTime pointUpdatedAt = pointList.stream()
                .map(KnowledgePoint::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime relationUpdatedAt = relationList.stream()
                .map(KnowledgePointRelation::getCreatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        GraphSnapshotVersion.Snapshot snapshot = GraphSnapshotVersion.of(
                courseId, pointList.size(), relationList.size(), pointUpdatedAt, relationUpdatedAt);
        return ApiResponse.success(new KnowledgeGraphVO(
                pointList, relationList, pointList.size(), relationList.size(),
                snapshot.version(), snapshot.generatedAt()));
    }
}
