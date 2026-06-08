package com.aicourse.service;

import com.aicourse.entity.LearningPathNode;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 学习路线节点服务接口
 * 提供节点状态管理、进度跟踪及统计功能
 */
public interface LearningPathNodeService extends IService<LearningPathNode> {

    /**
     * 获取学习路径的所有节点（按orderIndex有序排列）
     * @param pathId 学习路径ID
     * @return 有序节点列表
     */
    List<LearningPathNode> getPathNodes(Long pathId);

    /**
     * 获取当前进行中的节点（状态为IN_PROGRESS）
     * @param pathId 学习路径ID
     * @return 当前进行中的节点，无则返回null
     */
    LearningPathNode getCurrentNode(Long pathId);

    /**
     * 开始学习某个节点（状态切换为IN_PROGRESS）
     * @param nodeId 节点ID
     */
    void startNode(Long nodeId);

    /**
     * 完成某个节点（更新状态、得分、耗时，并触发整体进度更新）
     * @param nodeId 节点ID
     * @param score 得分
     * @param actualTime 实际耗时（分钟）
     */
    void completeNode(Long nodeId, BigDecimal score, Integer actualTime);

    /**
     * 跳过某个节点（状态切换为SKIPPED）
     * @param nodeId 节点ID
     */
    void skipNode(Long nodeId);

    /**
     * 获取路径中各状态节点统计
     * @param pathId 学习路径ID
     * @return 各状态节点数量统计（NOT_STARTED/IN_PROGRESS/COMPLETED/SKIPPED）
     */
    Map<String, Long> getNodeStatusStats(Long pathId);
}
