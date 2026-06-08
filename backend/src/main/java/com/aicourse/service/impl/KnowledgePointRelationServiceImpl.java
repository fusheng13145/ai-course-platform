package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.KnowledgePointRelationMapper;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        // 校验源知识点和目标知识点是否存在
        KnowledgePoint source = knowledgePointService.getById(sourceId);
        if (source == null) {
            throw new BusinessException("源知识点不存在: " + sourceId);
        }
        KnowledgePoint target = knowledgePointService.getById(targetId);
        if (target == null) {
            throw new BusinessException("目标知识点不存在: " + targetId);
        }

        // 检查是否已存在相同关联
        LambdaQueryWrapper<KnowledgePointRelation> checkWrapper = new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getSourceId, sourceId)
                .eq(KnowledgePointRelation::getTargetId, targetId)
                .eq(KnowledgePointRelation::getRelationType, relationType);
        long count = count(checkWrapper);
        if (count > 0) {
            throw new BusinessException("该关联关系已存在");
        }

        KnowledgePointRelation relation = new KnowledgePointRelation();
        relation.setSourceId(sourceId);
        relation.setTargetId(targetId);
        relation.setRelationType(relationType);
        relation.setStrength(strength);
        relation.setDescription(description);
        save(relation);
        return relation;
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
    public Map<String, Object> getGraphData(Long courseId) {
        // 查询课程下所有知识点作为节点
        LambdaQueryWrapper<KnowledgePoint> pointWrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId);
        List<KnowledgePoint> points = knowledgePointService.list(pointWrapper);

        // 构建节点数据（供ECharts/D3渲染）
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (KnowledgePoint point : points) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", point.getId());
            node.put("name", point.getName());
            node.put("category", point.getPointType() != null ? point.getPointType() : "default");
            nodes.add(node);
        }

        // 查询课程内知识点之间的关联边
        Set<Long> pointIds = points.stream()
                .map(KnowledgePoint::getId)
                .collect(Collectors.toSet());

        List<Map<String, Object>> links = new ArrayList<>();
        if (!pointIds.isEmpty()) {
            LambdaQueryWrapper<KnowledgePointRelation> relationWrapper = new LambdaQueryWrapper<KnowledgePointRelation>()
                    .in(KnowledgePointRelation::getSourceId, pointIds)
                    .in(KnowledgePointRelation::getTargetId, pointIds);
            List<KnowledgePointRelation> relations = list(relationWrapper);

            for (KnowledgePointRelation rel : relations) {
                Map<String, Object> link = new HashMap<>();
                link.put("source", rel.getSourceId());
                link.put("target", rel.getTargetId());
                link.put("value", rel.getStrength());
                link.put("relationType", rel.getRelationType());
                links.add(link);
            }
        }

        Map<String, Object> graphData = new HashMap<>();
        graphData.put("nodes", nodes);
        graphData.put("links", links);
        return graphData;
    }
}
