package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiCode;
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
import java.util.*;
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

        // 修复（2026-10-30，B-13 ⑪）：relationType 统一归一化为大写。
        // 此前按入参原样落库，同一语义可能存在 "prerequisite" / "PREREQUISITE" 两种行，
        // 而 getPrerequisites 只按大写精确匹配 ⇒ 小写写入的边永远查不回来。
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
     * 关联类型归一化：去空白 + 转大写（B-13 ⑪）。
     *
     * <p>调用方可能传小写，历史数据也可能已按原样落库，所以写入侧统一大写、
     * 读取侧统一 {@code UPPER(...)} 比较，两边同口径后 {@code getPrerequisites}
     * 才能稳定查回。</p>
     *
     * @throws BusinessException relationType 为空时返回 400（而不是落一条空类型边）
     */
    private static String normalizeRelationType(String relationType) {
        if (relationType == null || relationType.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "关联类型不能为空");
        }
        return relationType.trim().toUpperCase(Locale.ROOT);
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
                    point.getPointType() != null ? point.getPointType() : "default",
                    difficulty, point.getImportance()));
        }

        // 查询课程内知识点之间的关联边
        Set<Long> pointIds = points.stream()
                .map(KnowledgePoint::getId)
                .collect(Collectors.toSet());

        List<GraphDataLinkVO> links = new ArrayList<>();
        if (!pointIds.isEmpty()) {
            LambdaQueryWrapper<KnowledgePointRelation> relationWrapper = new LambdaQueryWrapper<KnowledgePointRelation>()
                    .in(KnowledgePointRelation::getSourceId, pointIds)
                    .in(KnowledgePointRelation::getTargetId, pointIds);
            List<KnowledgePointRelation> relations = list(relationWrapper);

            for (KnowledgePointRelation rel : relations) {
                links.add(new GraphDataLinkVO(
                        rel.getSourceId(), rel.getTargetId(),
                        rel.getStrength(), rel.getRelationType()));
            }
        }

        return new KnowledgeGraphDataVO(nodes, links);
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
                    .or(w -> w.in(KnowledgePointRelation::getTargetId, pointIds));
        }
        wrapper.orderByDesc(KnowledgePointRelation::getCreatedAt);
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
