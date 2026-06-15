package com.aicourse.service.impl;

import com.aicourse.entity.LearningPathNode;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.LearningPathNodeMapper;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.LearningPathService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习路线节点服务实现类
 * 提供节点状态管理、进度跟踪及统计功能实现
 */
@Service
public class LearningPathNodeServiceImpl extends ServiceImpl<LearningPathNodeMapper, LearningPathNode>
        implements LearningPathNodeService {

    private final LearningPathService learningPathService;

    public LearningPathNodeServiceImpl(@Lazy LearningPathService learningPathService) {
        this.learningPathService = learningPathService;
    }

    /**
     * 获取学习路径的所有节点（按orderIndex有序排列）
     */
    @Override
    public List<LearningPathNode> getPathNodes(Long pathId) {
        LambdaQueryWrapper<LearningPathNode> wrapper = new LambdaQueryWrapper<LearningPathNode>()
                .eq(LearningPathNode::getLearningPathId, pathId)
                .orderByAsc(LearningPathNode::getOrderIndex);
        return list(wrapper);
    }

    /**
     * 获取当前进行中的节点（状态为IN_PROGRESS）
     */
    @Override
    public LearningPathNode getCurrentNode(Long pathId) {
        LambdaQueryWrapper<LearningPathNode> wrapper = new LambdaQueryWrapper<LearningPathNode>()
                .eq(LearningPathNode::getLearningPathId, pathId)
                .eq(LearningPathNode::getStatus, "IN_PROGRESS")
                .last("LIMIT 1");
        return getOne(wrapper);
    }

    /**
     * 开始学习某个节点（状态切换为IN_PROGRESS）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startNode(Long nodeId) {
        LearningPathNode node = getById(nodeId);
        if (node == null) {
            throw new BusinessException("学习节点不存在: " + nodeId);
        }
        if (!"NOT_STARTED".equals(node.getStatus())) {
            throw new BusinessException("该节点已开始或已完成，不能重复开始");
        }
        node.setStatus("IN_PROGRESS");
        updateById(node);
    }

    /**
     * 完成某个节点（更新状态、得分、耗时，并触发整体进度更新）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeNode(Long nodeId, BigDecimal score, Integer actualTime) {
        LearningPathNode node = getById(nodeId);
        if (node == null) {
            throw new BusinessException("学习节点不存在: " + nodeId);
        }
        if ("COMPLETED".equals(node.getStatus())) {
            throw new BusinessException("该节点已完成");
        }

        // 更新节点状态和相关信息
        node.setStatus("COMPLETED");
        node.setScore(score);
        node.setActualTime(actualTime);
        node.setCompletedAt(java.time.LocalDateTime.now());
        updateById(node);

        // 触发整体进度更新
        learningPathService.updateProgress(node.getLearningPathId());
    }

    /**
     * 跳过某个节点（状态切换为SKIPPED）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void skipNode(Long nodeId) {
        LearningPathNode node = getById(nodeId);
        if (node == null) {
            throw new BusinessException("学习节点不存在: " + nodeId);
        }
        if ("COMPLETED".equals(node.getStatus()) || "SKIPPED".equals(node.getStatus())) {
            throw new BusinessException("该节点已完成或已跳过");
        }
        node.setStatus("SKIPPED");
        updateById(node);

        // 跳过后也需更新整体进度
        learningPathService.updateProgress(node.getLearningPathId());
    }

    /**
     * 获取路径中各状态节点统计
     */
    @Override
    public Map<String, Long> getNodeStatusStats(Long pathId) {
        List<LearningPathNode> nodes = getPathNodes(pathId);

        Map<String, Long> stats = new HashMap<>();
        stats.put("NOT_STARTED", 0L);
        stats.put("IN_PROGRESS", 0L);
        stats.put("COMPLETED", 0L);
        stats.put("SKIPPED", 0L);

        for (LearningPathNode node : nodes) {
            String status = node.getStatus();
            if (status != null && stats.containsKey(status)) {
                stats.merge(status, 1L, Long::sum);
            } else {
                stats.merge("OTHER", 1L, Long::sum);
            }
        }
        return stats;
    }

    /**
     * 更新节点状态（通用方法，支持任意状态切换，同时更新实际耗时）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNodeStatus(Long nodeId, String status, Integer actualTime) {
        LearningPathNode node = getById(nodeId);
        if (node == null) {
            throw new BusinessException("学习节点不存在: " + nodeId);
        }
        // 校验状态值
        if (!isValidStatus(status)) {
            throw new BusinessException("无效的节点状态: " + status);
        }
        String oldStatus = node.getStatus();
        node.setStatus(status);

        // 如果提供了实际耗时，则记录
        if (actualTime != null && actualTime > 0) {
            node.setActualTime(actualTime);
        }

        // 如果切换到已完成状态，自动设置完成时间
        if ("COMPLETED".equals(status) && !"COMPLETED".equals(oldStatus)) {
            node.setCompletedAt(java.time.LocalDateTime.now());
        }

        updateById(node);

        // 状态变更后触发整体进度重新计算
        learningPathService.updateProgress(node.getLearningPathId());
    }

    /**
     * 记录节点得分（用于练习/测试后的成绩录入）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordScore(Long nodeId, BigDecimal score) {
        LearningPathNode node = getById(nodeId);
        if (node == null) {
            throw new BusinessException("学习节点不存在: " + nodeId);
        }
        if (score == null) {
            throw new BusinessException("得分不能为空");
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException("得分必须在0-100之间");
        }
        node.setScore(score);
        updateById(node);
    }

    /**
     * 校验是否为有效的节点状态值
     */
    private boolean isValidStatus(String status) {
        return "NOT_STARTED".equals(status) || "IN_PROGRESS".equals(status)
                || "COMPLETED".equals(status) || "SKIPPED".equals(status);
    }
}
