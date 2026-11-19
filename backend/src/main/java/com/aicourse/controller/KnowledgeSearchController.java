package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.KnowledgeAccessSupport;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.vo.KnowledgeOverviewVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识点检索与图谱概览（2026-11-18 从 KnowledgeController 拆出）。
 */
@RestController
@RequestMapping("/knowledge")
@Tag(name = "知识图谱-检索与概览")
public class KnowledgeSearchController {

    /** 核心知识点判定阈值：重要度不低于该值。 */
    private static final int CORE_IMPORTANCE = 8;

    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointRelationService relationService;
    private final CourseAccessControl courseAccessControl;
    private final KnowledgeAccessSupport knowledgeAccessSupport;

    public KnowledgeSearchController(KnowledgePointService knowledgePointService,
                                     KnowledgePointRelationService relationService,
                                     CourseAccessControl courseAccessControl,
                                     KnowledgeAccessSupport knowledgeAccessSupport) {
        this.knowledgePointService = knowledgePointService;
        this.relationService = relationService;
        this.courseAccessControl = courseAccessControl;
        this.knowledgeAccessSupport = knowledgeAccessSupport;
    }

    /** 知识点搜索（按名称模糊匹配）。 */
    @GetMapping("/search")
    public ApiResponse<List<KnowledgePoint>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        LambdaQueryWrapper<KnowledgePoint> qw = new LambdaQueryWrapper<>();
        if (!knowledgeAccessSupport.applyCourseScope(qw, courseId, auth)) {
            return ApiResponse.success(Collections.emptyList());
        }
        qw.like(KnowledgePoint::getName, keyword);
        return ApiResponse.success(knowledgePointService.list(qw));
    }

    /**
     * 知识图谱概览统计。
     * 给定 courseId 时为课程口径；courseId 为空时按当前用户可读课程集合统计。
     */
    @GetMapping("/overview")
    public ApiResponse<KnowledgeOverviewVO> overview(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        List<KnowledgePoint> points;
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
            points = knowledgePointService.list(new LambdaQueryWrapper<KnowledgePoint>()
                    .eq(KnowledgePoint::getCourseId, courseId));
        } else {
            points = knowledgeAccessSupport.listReadablePoints(auth);
        }

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
}
