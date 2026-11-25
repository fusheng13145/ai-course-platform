package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PageSupport;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.KnowledgeRelationDTO;
import com.aicourse.vo.KnowledgeVOs;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.KnowledgeAccessSupport;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.vo.KnowledgeGraphDataVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 知识点关系读写接口（2026-11-18 从 KnowledgeController 拆出）。
 * 基础路径仍为 /api/knowledge，端点集合与拆分前逐条一致。
 */
@RestController
@RequestMapping("/knowledge")
@Tag(name = "知识图谱-关系")
public class KnowledgeRelationController {

    private final KnowledgePointRelationService relationService;
    private final CourseAccessControl courseAccessControl;
    private final KnowledgeAccessSupport knowledgeAccessSupport;

    public KnowledgeRelationController(KnowledgePointRelationService relationService,
                                       CourseAccessControl courseAccessControl,
                                       KnowledgeAccessSupport knowledgeAccessSupport) {
        this.relationService = relationService;
        this.courseAccessControl = courseAccessControl;
        this.knowledgeAccessSupport = knowledgeAccessSupport;
    }

    /** 创建知识点关联关系（教师/管理员权限）。 */
    @PostMapping("/relations")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<KnowledgeVOs.RelationVO> createRelation(@Valid @RequestBody KnowledgeRelationDTO dto,
                                                              Authentication auth) {
        KnowledgePoint source = knowledgeAccessSupport.requirePoint(dto.getSourceId());
        KnowledgePoint target = knowledgeAccessSupport.requirePoint(dto.getTargetId());
        courseAccessControl.assertCanManageCourse(source.getCourseId(), auth);
        if (!Objects.equals(source.getCourseId(), target.getCourseId())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "关联关系只能建立在同一课程的知识点之间");
        }
        KnowledgePointRelation relation = relationService.createRelation(
                dto.getSourceId(), dto.getTargetId(), dto.getRelationType(), dto.getStrength(), dto.getDescription());
        return ApiResponse.success("创建成功", KnowledgeVOs.RelationVO.from(relation));
    }

    /** 删除知识点关联关系（教师/管理员权限）。 */
    @DeleteMapping("/relations/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> deleteRelation(@PathVariable Long id, Authentication auth) {
        KnowledgePointRelation relation = relationService.getById(id);
        if (relation == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "知识点关系不存在");
        }
        KnowledgePoint source = knowledgeAccessSupport.requirePoint(relation.getSourceId());
        courseAccessControl.assertCanManageCourse(source.getCourseId(), auth);
        relationService.deleteRelation(id);
        return ApiResponse.success("删除成功", null);
    }

    /** 获取关联关系列表（分页，支持课程筛选；非管理员必须显式指定课程）。 */
    @GetMapping("/relations")
    public ApiResponse<PagedResponse<KnowledgeVOs.RelationVO>> getRelationList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        Page<KnowledgePointRelation> result;
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
            result = relationService.getRelationList(page, size, courseId);
        } else if (!knowledgeAccessSupport.isAdmin(auth)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请指定课程ID后再查询知识点关系");
        } else {
            List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
            result = readableCourseIds == null
                    ? relationService.getRelationList(page, size, (Long) null)
                    : relationService.getRelationListByCourses(page, size, readableCourseIds);
        }
        return ApiResponse.success(PageSupport.toPage(result, KnowledgeVOs.RelationVO::from));
    }

    /** 获取某知识点的所有关联关系（入向+出向）。 */
    @GetMapping("/relations/point/{pointId}")
    public ApiResponse<List<KnowledgeVOs.RelationVO>> getRelationsByPoint(@PathVariable Long pointId,
                                                                         Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(pointId);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        return ApiResponse.success(KnowledgeVOs.RelationVO.from(knowledgeAccessSupport.filterRelationsSameCourse(
                relationService.getRelationsByPoint(pointId), point.getCourseId())));
    }

    /** 获取某知识点的出边（作为源节点）。 */
    @GetMapping("/relations/source/{sourceId}")
    public ApiResponse<List<KnowledgeVOs.RelationVO>> getSourceRelations(@PathVariable Long sourceId,
                                                                        Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(sourceId);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        return ApiResponse.success(KnowledgeVOs.RelationVO.from(knowledgeAccessSupport.filterRelationsSameCourse(
                relationService.getSourceRelations(sourceId), point.getCourseId())));
    }

    /** 获取某知识点的入边（作为目标节点）。 */
    @GetMapping("/relations/target/{targetId}")
    public ApiResponse<List<KnowledgeVOs.RelationVO>> getTargetRelations(@PathVariable Long targetId,
                                                                        Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(targetId);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        return ApiResponse.success(KnowledgeVOs.RelationVO.from(knowledgeAccessSupport.filterRelationsSameCourse(
                relationService.getTargetRelations(targetId), point.getCourseId())));
    }

    /** 获取课程知识图谱可视化图数据（nodes + links）。 */
    @GetMapping("/relations/graph/{courseId}")
    @Operation(summary = "获取知识图谱可视化数据", description = "返回可供前端 ECharts/D3 渲染的图数据，包含节点和连接关系")
    public ApiResponse<KnowledgeGraphDataVO> getRelationGraphData(@PathVariable Long courseId,
                                                                  Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        return ApiResponse.success(relationService.getGraphData(courseId));
    }
}


