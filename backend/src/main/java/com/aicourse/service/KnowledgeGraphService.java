package com.aicourse.service;

import com.aicourse.entity.KnowledgePoint;

import java.util.List;

/**
 * 知识图谱服务接口
 * 对接Python知识图谱微服务（端口 8001）
 * Python端需实现对应的 /api/v1/graph/* 接口
 */
public interface KnowledgeGraphService {

    /**
     * 健康检查
     * @return true表示Python服务可达
     */
    boolean healthCheck();

    /**
     * 查询知识图谱节点
     * @param courseId 课程ID
     * @param nodeId 节点ID（可选，为null则返回整张图）
     * @return 知识图谱数据JSON
     */
    String getGraph(Long courseId, Long nodeId);

    /**
     * 查询节点关联关系
     * @param nodeId 节点ID
     * @param depth 关联深度（1-3）
     * @return 关联节点数据JSON
     */
    String getNodeRelations(Long nodeId, int depth);

    /**
     * 添加知识节点
     * @param courseId 课程ID
     * @param name 节点名称
     * @param content 节点内容
     * @param type 节点类型（CONCEPT/PRINCIPLE/METHOD/FACT）
     * @return 新建节点信息JSON
     */
    String addNode(Long courseId, String name, String content, String type);

    /**
     * 添加节点关系
     * @param sourceId 源节点ID
     * @param targetId 目标节点ID
     * @param relationType 关系类型（PREREQUISITE/INCLUDES/RELATED/EXTENDS）
     * @return 关系信息JSON
     */
    String addRelation(Long sourceId, Long targetId, String relationType);

    /**
     * 删除节点
     * @param nodeId 节点ID
     * @return 操作结果
     */
    String deleteNode(Long nodeId);

    /**
     * 删除关系
     * @param relationId 关系ID
     * @return 操作结果
     */
    String deleteRelation(Long relationId);

    /**
     * 学习路径规划（基于知识关系图的最短路径 BFS）
     * @param startPointId 起点知识点ID
     * @param targetPointId 目标知识点ID
     * @return 有序路径上的知识点列表；起点与目标相同时返回该单点；无法到达时返回空列表
     */
    List<KnowledgePoint> planLearningPath(Long startPointId, Long targetPointId);
}