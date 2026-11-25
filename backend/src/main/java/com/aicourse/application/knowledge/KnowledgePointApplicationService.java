package com.aicourse.application.knowledge;

import com.aicourse.common.ApiCode;
import com.aicourse.common.PagedResponse;
import com.aicourse.domain.knowledge.GraphSnapshotVersion;
import com.aicourse.dto.KnowledgePointSaveDTO;
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
import com.aicourse.vo.KnowledgeVOs;
import com.aicourse.vo.MasteryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识点应用服务（3E-05C）。
 *
 * <p>从 {@code KnowledgeController} 下移的 5 处 {@code LambdaQueryWrapper}、两处「可见范围为空
 * 直接回空」分支、图谱快照版本计算，以及知识点 CRUD 的守卫编排。控制器只剩路由与
 * {@code @PreAuthorize}。</p>
 *
 * <p>口径注意：</p>
 * <ul>
 *   <li>列表查询的可见范围由 {@code KnowledgeAccessSupport#applyCourseScope} 写入 wrapper，
 *       返回 {@code false} 表示无任何可见范围 → 不查库直接回空；</li>
 *   <li>分页版回显<b>请求的</b> page/size（不能用 {@code PagedResponse.empty()}，那会写死 1/10）；</li>
 *   <li>更新时归属课程只认库里的既有值，防止把知识点「搬」到别的课程绕过权限判据；</li>
 *   <li>掌握度对教师/管理员要求目标学生在本课程有有效选课，避免学情隐私越权。</li>
 * </ul>
 */
@Service
public class KnowledgePointApplicationService {

    private static final int MAX_PAGE_SIZE = 200;

    private final KnowledgePointService knowledgePointService;
    private final KnowledgePointRelationService relationService;
    private final CourseAccessControl courseAccessControl;
    private final CourseEnrollmentService courseEnrollmentService;
    private final KnowledgeAccessSupport knowledgeAccessSupport;

    public KnowledgePointApplicationService(KnowledgePointService knowledgePointService,
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

    // ==================== 列表 / 详情 ====================

    /** 条件查询知识点列表（按可见范围 + 名称模糊）。 */
    public List<KnowledgeVOs.PointVO> listPoints(Long courseId, String keyword, Authentication auth) {
        LambdaQueryWrapper<KnowledgePoint> qw = new LambdaQueryWrapper<>();
        if (!knowledgeAccessSupport.applyCourseScope(qw, courseId, auth)) {
            return Collections.emptyList();
        }
        qw.like(hasText(keyword), KnowledgePoint::getName, keyword);
        return KnowledgeVOs.PointVO.from(knowledgePointService.list(qw));
    }

    /** 分页查询知识点（size 上限 200，page 下限 1；无可见范围时回显请求 page/size 的空页）。 */
    public PagedResponse<KnowledgeVOs.PointVO> listPointsPaged(int page, int size, Long courseId,
                                                               String keyword, Authentication auth) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);
        LambdaQueryWrapper<KnowledgePoint> qw = new LambdaQueryWrapper<>();
        if (!knowledgeAccessSupport.applyCourseScope(qw, courseId, auth)) {
            return PagedResponse.of(List.of(), 0, safePage, safeSize);
        }
        qw.like(hasText(keyword), KnowledgePoint::getName, keyword)
                .orderByAsc(KnowledgePoint::getId);
        Page<KnowledgePoint> result = knowledgePointService.page(new Page<>(safePage, safeSize), qw);
        return PagedResponse.of(result.getRecords().stream().map(KnowledgeVOs.PointVO::from).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 知识点详情（先取对象，再按课程可读性授权）。 */
    public KnowledgeVOs.PointVO pointDetail(Long id, Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        return KnowledgeVOs.PointVO.from(point);
    }

    // ==================== 写路径 ====================

    /** 新增知识点：id/时间戳不接受客户端传入，归属课程由 DTO 指定并校验管理权。 */
    public KnowledgeVOs.PointVO createPoint(KnowledgePointSaveDTO dto, Authentication auth) {
        KnowledgePoint point = dto.toEntity();
        courseAccessControl.assertCanManageCourse(point.getCourseId(), auth);
        knowledgePointService.save(point);
        return KnowledgeVOs.PointVO.from(point);
    }

    /** 修改知识点：归属课程强制沿用库内既有值。 */
    public KnowledgeVOs.PointVO updatePoint(Long id, KnowledgePointSaveDTO dto, Authentication auth) {
        KnowledgePoint existing = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        KnowledgePoint point = dto.toEntity();
        point.setId(id);
        point.setCourseId(existing.getCourseId());
        knowledgePointService.updateById(point);
        return KnowledgeVOs.PointVO.from(knowledgePointService.getById(id));
    }

    /** 删除知识点。 */
    public void deletePoint(Long id, Authentication auth) {
        KnowledgePoint existing = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        knowledgePointService.removeById(id);
    }

    // ==================== 子资源 ====================

    /** 关联关系（节点 + 边），只下发同课程节点/边。 */
    public KnowledgePointRelationsVO pointRelations(Long id, Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);

        List<KnowledgePointRelation> relations = relationService.list(
                new LambdaQueryWrapper<KnowledgePointRelation>()
                        .eq(KnowledgePointRelation::getIsQuarantined, false)
                        .and(w -> w.eq(KnowledgePointRelation::getSourceId, id)
                                .or().eq(KnowledgePointRelation::getTargetId, id)));

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
        return new KnowledgePointRelationsVO(nodes, scopedRelations);
    }

    /** 前置依赖（只保留同课程节点）。 */
    public List<KnowledgeVOs.PointVO> prerequisites(Long id, Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        return KnowledgeVOs.PointVO.from(knowledgeAccessSupport.filterSameCourse(
                knowledgePointService.getPrerequisites(id), point.getCourseId()));
    }

    /** 后继知识点（被谁依赖，只保留同课程节点）。 */
    public List<KnowledgeVOs.PointVO> successors(Long id, Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        return KnowledgeVOs.PointVO.from(knowledgeAccessSupport.filterSameCourse(
                knowledgePointService.getSubsequentPoints(id), point.getCourseId()));
    }

    /** 直接子节点（只保留同课程节点）。 */
    public List<KnowledgeVOs.PointVO> children(Long id, Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        return KnowledgeVOs.PointVO.from(knowledgeAccessSupport.filterSameCourse(
                knowledgePointService.getChildPoints(id), point.getCourseId()));
    }

    /**
     * 掌握度。教师/管理员查询时必须确认目标学生在该课程有有效选课，
     * 避免学情隐私越权（学生本人查询亦走同一校验）。
     */
    public MasteryVO calculateMastery(Long id, Long userId, Authentication auth) {
        KnowledgePoint point = knowledgeAccessSupport.requirePoint(id);
        courseAccessControl.assertCanReadCourse(point.getCourseId(), auth);
        if (userId == null || !courseEnrollmentService.hasApprovedEnrollment(userId, point.getCourseId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "该学生不在此课程，无法读取掌握度");
        }
        return knowledgePointService.calculateMasteryDetail(id, userId);
    }

    // ==================== 课程知识树 / 图谱 ====================

    /** 课程完整知识树。 */
    public List<KnowledgeTreeNodeVO> knowledgeTree(Long courseId, Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        return knowledgePointService.getKnowledgeTree(courseId);
    }

    /** 课程根级知识点。 */
    public List<KnowledgeVOs.PointVO> rootPoints(Long courseId, Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        return KnowledgeVOs.PointVO.from(knowledgePointService.getRootPoints(courseId));
    }

    /** 图谱全量数据（nodes + edges + 快照版本）。 */
    public KnowledgeGraphVO graph(Long courseId, Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        List<KnowledgePoint> pointList = knowledgePointService.list(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));

        Set<Long> pointIds = pointList.stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
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
        return new KnowledgeGraphVO(pointList, relationList, pointList.size(), relationList.size(),
                snapshot.version(), snapshot.generatedAt());
    }

    /**
     * 课程内知识点名称，逗号（、）拼接并限条数（3E-05E：从 QuestionAiController 的
     * 「未指定知识点时自动从图谱提取」下移，原口径 limit 15、按库序）。
     */
    public java.util.Optional<String> namesByCourse(Long courseId, int limit) {
        List<KnowledgePoint> points = knowledgePointService.list(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));
        if (points.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(points.stream().map(KnowledgePoint::getName)
                .limit(limit).collect(Collectors.joining("、")));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }
}
