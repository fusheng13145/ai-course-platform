package com.aicourse.application.knowledge;

import com.aicourse.domain.knowledge.GraphSnapshotVersion;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.KnowledgeAccessSupport;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.vo.KnowledgeOverviewVO;
import com.aicourse.vo.KnowledgeVOs;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 知识点检索与图谱概览应用服务（3E-05D）。
 *
 * <p>从 {@code KnowledgeSearchController} 下移 3 处 {@code LambdaQueryWrapper}、可见范围短路
 * 与概览统计（覆盖率、核心点计数、快照版本）。</p>
 *
 * <p>概览口径：给 {@code courseId} 时按课程授权后统计该课程；不给时按「当前用户可读课程集合」
 * 统计（学生=已选课程、教师=本人课程、管理员=全校）。覆盖率 = 至少参与一条关系的知识点数 /
 * 知识点总数；核心点 = 重要度 ≥ 8。</p>
 */
@Service
public class KnowledgeSearchApplicationService {

    /** 核心知识点判定阈值：重要度不低于该值。 */
    private static final int CORE_IMPORTANCE = 8;

    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointRelationService relationService;
    private final CourseAccessControl courseAccessControl;
    private final KnowledgeAccessSupport knowledgeAccessSupport;

    public KnowledgeSearchApplicationService(KnowledgePointService knowledgePointService,
                                              KnowledgePointRelationService relationService,
                                              CourseAccessControl courseAccessControl,
                                              KnowledgeAccessSupport knowledgeAccessSupport) {
        this.knowledgePointService = knowledgePointService;
        this.relationService = relationService;
        this.courseAccessControl = courseAccessControl;
        this.knowledgeAccessSupport = knowledgeAccessSupport;
    }

    /** 知识点搜索（按名称模糊匹配，限定在可见范围内）。 */
    public List<KnowledgeVOs.PointVO> search(String keyword, Long courseId, Authentication auth) {
        LambdaQueryWrapper<KnowledgePoint> qw = new LambdaQueryWrapper<>();
        if (!knowledgeAccessSupport.applyCourseScope(qw, courseId, auth)) {
            return Collections.emptyList();
        }
        qw.like(KnowledgePoint::getName, keyword);
        return KnowledgeVOs.PointVO.from(knowledgePointService.list(qw));
    }

    /** 知识图谱概览统计（课程口径 / 可读课程集合口径）。 */
    public KnowledgeOverviewVO overview(Long courseId, Authentication auth) {
        List<KnowledgePoint> points;
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
            points = knowledgePointService.list(
                    new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));
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
                        .eq(KnowledgePointRelation::getIsQuarantined, false)
                        .in(KnowledgePointRelation::getSourceId, pointIds)
                        .in(KnowledgePointRelation::getTargetId, pointIds));

        Set<Long> coveredIds = new HashSet<>();
        for (KnowledgePointRelation relation : relations) {
            coveredIds.add(relation.getSourceId());
            coveredIds.add(relation.getTargetId());
        }
        long corePoints = points.stream()
                .filter(p -> p.getImportance() != null && p.getImportance() >= CORE_IMPORTANCE)
                .count();
        double coverageRate = points.isEmpty() ? 0d : (double) coveredIds.size() / points.size();

        LocalDateTime pointUpdatedAt = points.stream()
                .map(KnowledgePoint::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime relationUpdatedAt = relations.stream()
                .map(KnowledgePointRelation::getCreatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        GraphSnapshotVersion.Snapshot snapshot = GraphSnapshotVersion.of(
                courseId, points.size(), relations.size(), pointUpdatedAt, relationUpdatedAt);

        return new KnowledgeOverviewVO(
                (long) points.size(), (long) relations.size(), corePoints, coverageRate, courseId,
                snapshot.version(), snapshot.generatedAt());
    }
}
