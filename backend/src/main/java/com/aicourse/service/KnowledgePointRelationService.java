package com.aicourse.service;

import com.aicourse.entity.KnowledgePointRelation;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 知识点关联关系服务接口
 * 提供关联关系创建、查询、删除及图谱可视化数据获取功能
 */
public interface KnowledgePointRelationService extends IService<KnowledgePointRelation> {

    /**
     * 创建关联关系
     * @param sourceId 源知识点ID
     * @param targetId 目标知识点ID
     * @param relationType 关联类型
     * @param strength 关联强度（0-1）
     * @param description 关系描述
     * @return 创建的关联关系对象
     */
    KnowledgePointRelation createRelation(Long sourceId, Long targetId, String relationType,
                                          BigDecimal strength, String description);

    /**
     * 获取知识点的所有关联（入向+出向）
     * @param pointId 知识点ID
     * @return 关联关系列表
     */
    List<KnowledgePointRelation> getRelationsByPoint(Long pointId);

    /**
     * 删除关联关系
     * @param relationId 关联记录ID
     */
    void deleteRelation(Long relationId);

    /**
     * 获取可视化图数据（节点+边，供前端ECharts/D3渲染）
     * @param courseId 课程ID
     * @return 包含nodes和links的图数据Map
     */
    Map<String, Object> getGraphData(Long courseId);

    /**
     * 获取关联关系列表（分页，支持课程筛选）
     * @param page 页码
     * @param size 每页大小
     * @param courseId 课程ID筛选（可选）
     * @return 分页关联关系列表
     */
    Page<KnowledgePointRelation> getRelationList(int page, int size, Long courseId);

    /**
     * 获取某知识点的出边（作为源节点的关联关系）
     * @param sourceId 源知识点ID
     * @return 出边关联关系列表
     */
    List<KnowledgePointRelation> getSourceRelations(Long sourceId);

    /**
     * 获取某知识点的入边（作为目标节点的关联关系）
     * @param targetId 目标知识点ID
     * @return 入边关联关系列表
     */
    List<KnowledgePointRelation> getTargetRelations(Long targetId);
}
