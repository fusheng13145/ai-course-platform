package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识图谱控制器的共享读面/归属辅助。
 *
 * <p>拆分后 points / relations / search / extraction 四个控制器共用本组件，
 * 避免课程作用域、同课程过滤与 point 存在性校验各写一份而分叉。</p>
 */
@Component
public class KnowledgeAccessSupport {

    private final KnowledgePointService knowledgePointService;
    private final CourseAccessControl courseAccessControl;

    public KnowledgeAccessSupport(KnowledgePointService knowledgePointService,
                                  CourseAccessControl courseAccessControl) {
        this.knowledgePointService = knowledgePointService;
        this.courseAccessControl = courseAccessControl;
    }

    /**
     * 给知识点查询套上「显式课程 ACL 或当前用户可读课程集合」。
     * 返回 false 表示当前用户没有任何可读课程，调用方应直接返回空结果，不得退化为全平台查询。
     * 管理员校级作用域的 readableCourseIds 为 null，保留全量口径。
     */
    public boolean applyCourseScope(LambdaQueryWrapper<KnowledgePoint> qw,
                                    Long courseId,
                                    Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
            qw.eq(KnowledgePoint::getCourseId, courseId);
            return true;
        }
        List<Long> readableIds = courseAccessControl.readableCourseIds(auth);
        if (readableIds == null) {
            return true;
        }
        if (readableIds.isEmpty()) {
            return false;
        }
        qw.in(KnowledgePoint::getCourseId, readableIds);
        return true;
    }

    /** 无 courseId 的概览口径：按当前用户可读课程集合统计，不返回全站目录。 */
    public List<KnowledgePoint> listReadablePoints(Authentication auth) {
        List<Long> readableIds = courseAccessControl.readableCourseIds(auth);
        if (readableIds == null) {
            return knowledgePointService.list();
        }
        if (readableIds.isEmpty()) {
            return Collections.emptyList();
        }
        return knowledgePointService.list(new LambdaQueryWrapper<KnowledgePoint>()
                .in(KnowledgePoint::getCourseId, readableIds));
    }

    /** 过滤服务层返回的跨课程历史数据，保证只下发当前知识点所属课程。 */
    public List<KnowledgePoint> filterSameCourse(List<KnowledgePoint> points, Long courseId) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        return points.stream()
                .filter(point -> Objects.equals(point.getCourseId(), courseId))
                .toList();
    }

    /** 关系两端都必须在同一课程内，避免历史跨课程脏数据带出他课节点/边。 */
    public List<KnowledgePointRelation> filterRelationsSameCourse(
            List<KnowledgePointRelation> relations, Long courseId) {
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> pointIds = new HashSet<>();
        for (KnowledgePointRelation relation : relations) {
            pointIds.add(relation.getSourceId());
            pointIds.add(relation.getTargetId());
        }
        Set<Long> allowedIds = knowledgePointService.listByIds(pointIds).stream()
                .filter(point -> Objects.equals(point.getCourseId(), courseId))
                .map(KnowledgePoint::getId)
                .collect(Collectors.toSet());
        return relations.stream()
                .filter(relation -> allowedIds.contains(relation.getSourceId())
                        && allowedIds.contains(relation.getTargetId()))
                .toList();
    }

    public boolean isAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + RoleConstants.ADMIN)::equals);
    }

    public KnowledgePoint requirePoint(Long id) {
        KnowledgePoint point = knowledgePointService.getById(id);
        if (point == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "知识点不存在");
        }
        return point;
    }
}
