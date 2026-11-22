package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiCode;
import com.aicourse.domain.knowledge.GraphSnapshotVersion;
import com.aicourse.domain.knowledge.PointType;
import com.aicourse.domain.knowledge.RelationType;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.KnowledgePointRelationMapper;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.vo.GraphDataLinkVO;
import com.aicourse.vo.GraphDataNodeVO;
import com.aicourse.vo.KnowledgeGraphDataVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识点关联关系服务实现类
 * 提供关联关系的创建、查询、删除及图谱可视化数据功能
 */
@Service
public class KnowledgePointRelationServiceImpl extends ServiceImpl<KnowledgePointRelationMapper, KnowledgePointRelation>
        implements KnowledgePointRelationService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgePointRelationServiceImpl.class);

    private final KnowledgePointService knowledgePointService;

    public KnowledgePointRelationServiceImpl(@Lazy KnowledgePointService knowledgePointService) {
        this.knowledgePointService = knowledgePointService;
    }

    /**
     * 创建关联关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgePointRelation createRelation(Long sourceId, Long targetId, String relationType,
                                                  BigDecimal strength, String description) {
        log.info("创建知识点点关系");

        // 修复（2026-10-30，B-13 ⑪）：relationType 统一归一化为领域编码。
        // 历史上同一语义可能存在 "prerequisite" / "PREREQUISITE" / "前置知识" 多种行，
        // 现在由 RelationType 统一收口，持久化列只写领域编码。
        String normalizedType = normalizeRelationType(relationType);

        // 校验源知识点和目标知识点是否存在
        KnowledgePoint source = knowledgePointService.getById(sourceId);
        if (source == null) {
            throw new BusinessException("源知识点不存在: " + sourceId);
        }
        KnowledgePoint target = knowledgePointService.getById(targetId);
        if (target == null) {
            throw new BusinessException("目标知识点不存在: " + targetId);
        }

        // 检查是否已存在相同关联（去重也按大小写不敏感比较，避免 "a"/"A" 各存一条）
        LambdaQueryWrapper<KnowledgePointRelation> checkWrapper = new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getSourceId, sourceId)
                .eq(KnowledgePointRelation::getTargetId, targetId)
                .apply("UPPER(relation_type) = {0}", normalizedType);
        long count = count(checkWrapper);
        if (count > 0) {
            throw new BusinessException("该关联关系已存在");
        }

        KnowledgePointRelation relation = new KnowledgePointRelation();
        relation.setSourceId(sourceId);
        relation.setTargetId(targetId);
        relation.setRelationType(normalizedType);
        relation.setStrength(strength);
        relation.setDescription(description);
        save(relation);
        return relation;
    }

    /**
     * 关联类型归一化：去空白，已知别名归一到 {@link RelationType#code()}。
     *
     * <p>调用方可能传小写或中文历史标签，写入侧统一为领域编码；
     * 读取侧由迁移 V34 和 {@link RelationType#known(String)} 兼容存量。</p>
     *
     * @throws BusinessException relationType 为空时返回 400（而不是落一条空类型边）
     */
    private static String normalizeRelationType(String relationType) {
        if (relationType == null || relationType.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "关联类型不能为空");
        }
        return RelationType.canonicalCode(relationType);
    }

    /**
     * 获取知识点的所有关联（入向+出向）
     */
    @Override
    public List<KnowledgePointRelation> getRelationsByPoint(Long pointId) {
        LambdaQueryWrapper<KnowledgePointRelation> wrapper = new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getSourceId, pointId)
                .or(w -> w.eq(KnowledgePointRelation::getTargetId, pointId));
        return list(wrapper);
    }

    /**
     * 删除关联关系
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation(Long relationId) {
        log.info("删除知识点点关系");

        KnowledgePointRelation relation = getById(relationId);
        if (relation == null) {
            throw new BusinessException("关联关系不存在: " + relationId);
        }
        removeById(relationId);
    }

    /**
     * 获取可视化图数据（节点+边格式）
     */
    @Override
    public KnowledgeGraphDataVO getGraphData(Long courseId) {
        // 查询课程下所有知识点作为节点
        LambdaQueryWrapper<KnowledgePoint> pointWrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId);
        List<KnowledgePoint> points = knowledgePointService.list(pointWrapper);

        // 构建节点数据（供ECharts/D3渲染）
        List<GraphDataNodeVO> nodes = new ArrayList<>();
        for (KnowledgePoint point : points) {
            String difficulty = point.getDifficultyLevel() != null
                    ? point.getDifficultyLevel().toUpperCase() : null;
            nodes.add(new GraphDataNodeVO(
                    point.getId(), point.getName(),
                    PointType.canonicalCode(point.getPointType()),
                    difficulty, point.getImportance()));
        }

        // 查询课程内知识点之间的关联边
        Set<Long> pointIds = points.stream()
                .map(KnowledgePoint::getId)
                .collect(Collectors.toSet());

        List<GraphDataLinkVO> links = new ArrayList<>();
        List<KnowledgePointRelation> relations = List.of();
        if (!pointIds.isEmpty()) {
            LambdaQueryWrapper<KnowledgePointRelation> relationWrapper = new LambdaQueryWrapper<KnowledgePointRelation>()
                    .in(KnowledgePointRelation::getSourceId, pointIds)
                    .in(KnowledgePointRelation::getTargetId, pointIds);
            relations = list(relationWrapper);

            for (KnowledgePointRelation rel : relations) {
                links.add(new GraphDataLinkVO(
                        rel.getSourceId(), rel.getTargetId(),
                        rel.getStrength(), RelationType.graphLabel(rel.getRelationType())));
            }
        }

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
                courseId, nodes.size(), links.size(), pointUpdatedAt, relationUpdatedAt);
        return new KnowledgeGraphDataVO(nodes, links, snapshot.version(), snapshot.generatedAt());
    }

    /**
     * 获取关联关系列表（分页，支持课程筛选）
     */
    @Override
    public Page<KnowledgePointRelation> getRelationList(int page, int size, Long courseId) {
        LambdaQueryWrapper<KnowledgePointRelation> wrapper = new LambdaQueryWrapper<KnowledgePointRelation>();
        if (courseId != null) {
            // 通过知识点ID关联课程：查询课程下所有知识点，再查这些知识点的关联
            List<KnowledgePoint> points = knowledgePointService.list(
                    new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId)
            );
            if (points.isEmpty()) {
                return Page.of(page, size);
            }
            Set<Long> pointIds = points.stream()
                    .map(KnowledgePoint::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            wrapper.in(KnowledgePointRelation::getSourceId, pointIds)
                    .in(KnowledgePointRelation::getTargetId, pointIds);
        }
        wrapper.orderByDesc(KnowledgePointRelation::getCreatedAt);
        return this.page(Page.of(page, size), wrapper);
    }

    @Override
    public Page<KnowledgePointRelation> getRelationListByCourses(int page, int size, List<Long> courseIds) {
        if (courseIds == null) {
            return getRelationList(page, size, (Long) null);
        }
        if (courseIds.isEmpty()) {
            return Page.of(page, size);
        }
        List<KnowledgePoint> points = knowledgePointService.list(
                new LambdaQueryWrapper<KnowledgePoint>().in(KnowledgePoint::getCourseId, courseIds)
        );
        if (points.isEmpty()) {
            return Page.of(page, size);
        }
        Set<Long> pointIds = points.stream()
                .map(KnowledgePoint::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        LambdaQueryWrapper<KnowledgePointRelation> wrapper = new LambdaQueryWrapper<KnowledgePointRelation>()
                .in(KnowledgePointRelation::getSourceId, pointIds)
                .in(KnowledgePointRelation::getTargetId, pointIds)
                .orderByDesc(KnowledgePointRelation::getCreatedAt);
        return this.page(Page.of(page, size), wrapper);
    }

    /**
     * 获取某知识点的出边（作为源节点的关联关系）
     */
    @Override
    public List<KnowledgePointRelation> getSourceRelations(Long sourceId) {
        return this.list(new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getSourceId, sourceId)
                .orderByDesc(KnowledgePointRelation::getCreatedAt));
    }

    /**
     * 获取某知识点的入边（作为目标节点的关联关系）
     */
    @Override
    public List<KnowledgePointRelation> getTargetRelations(Long targetId) {
        return this.list(new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getTargetId, targetId)
                .orderByDesc(KnowledgePointRelation::getCreatedAt));
    }

    /**
     * 获取某课程下的全部关联关系（本地降级用）。
     * 通过关联知识点归属课程筛选：关系两端的知识点任一属于该课程即纳入。
     */
    @Override
    public List<KnowledgePointRelation> listByCourse(Long courseId) {
        if (courseId == null) {
            return Collections.emptyList();
        }
        List<KnowledgePoint> coursePoints = knowledgePointService.listByCourse(courseId);
        if (coursePoints == null || coursePoints.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> coursePointIds = coursePoints.stream()
                .map(KnowledgePoint::getId).collect(Collectors.toSet());
        return this.list(new LambdaQueryWrapper<KnowledgePointRelation>()
                .in(KnowledgePointRelation::getSourceId, coursePointIds)
                .or()
                .in(KnowledgePointRelation::getTargetId, coursePointIds));
    }
}
