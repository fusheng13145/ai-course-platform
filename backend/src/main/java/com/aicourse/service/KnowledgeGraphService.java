package com.aicourse.service;

/**
 * 知识图谱服务接口
 * 对接Python知识图谱微服务
 */
public interface KnowledgeGraphService {

    /**
     * 查询知识图谱节点
     * @param courseId 课程ID
     * @param nodeId 节点ID（可选）
     * @return 知识图谱数据JSON
     */
    String getGraph(Long courseId, Long nodeId);

    /**
     * 查询节点关联关系
     * @param nodeId 节点ID
     * @param depth 关联深度
     * @return 关联节点数据JSON
     */
    String getNodeRelations(Long nodeId, int depth);

    /**
     * 添加知识节点
     * @param courseId 课程ID
     * @param name 节点名称
     * @param content 节点内容
     * @param type 节点类型
     * @return 新建节点信息JSON
     */
    String addNode(Long courseId, String name, String content, String type);

    /**
     * 添加节点关系
     * @param sourceId 源节点ID
     * @param targetId 目标节点ID
     * @param relationType 关系类型
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
}