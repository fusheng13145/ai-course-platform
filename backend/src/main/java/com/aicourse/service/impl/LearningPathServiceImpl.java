package com.aicourse.service.impl;

import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.LearningPathMapper;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.service.LearningPathService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习路线服务实现类
 * 提供学习路径的进度管理、阶段控制、任务解析及详情查询等功能实现
 */
@Service
public class LearningPathServiceImpl extends ServiceImpl<LearningPathMapper, LearningPath> implements LearningPathService {

    private final LearningPathNodeService nodeService;
    private final ObjectMapper objectMapper;

    public LearningPathServiceImpl(LearningPathNodeService nodeService) {
        this.nodeService = nodeService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取学生的活跃学习路径（状态为ACTIVE）
     */
    @Override
    public LearningPath getActivePath(Long studentId, Long courseId) {
        LambdaQueryWrapper<LearningPath> wrapper = new LambdaQueryWrapper<LearningPath>()
                .eq(LearningPath::getStudentId, studentId)
                .eq(LearningPath::getCourseId, courseId)
                .eq(LearningPath::getStatus, "ACTIVE")
                .last("LIMIT 1");
        return getOne(wrapper);
    }

    /**
     * 更新整体进度（根据节点完成情况重算completionRate）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(Long pathId) {
        // 查询路径下所有节点
        List<LearningPathNode> nodes = nodeService.getPathNodes(pathId);
        if (nodes.isEmpty()) {
            return;
        }

        // 统计已完成节点数量
        long completedCount = nodes.stream()
                .filter(node -> "COMPLETED".equals(node.getStatus()))
                .count();

        // 计算完成率
        BigDecimal completionRate = BigDecimal.valueOf(completedCount)
                .divide(BigDecimal.valueOf(nodes.size()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        // 更新路径完成率
        LearningPath path = getById(pathId);
        path.setCompletionRate(completionRate);
        updateById(path);

        // 若全部完成，自动标记为已完成
        if (completedCount == nodes.size()) {
            completePath(pathId);
        }
    }

    /**
     * 进入下一阶段
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void advanceToNextStage(Long pathId) {
        LearningPath path = getById(pathId);
        if (path == null) {
            throw new BusinessException("学习路径不存在: " + pathId);
        }

        Integer currentStage = path.getCurrentStage() != null ? path.getCurrentStage() : 0;
        Integer totalStages = path.getTotalStages() != null ? path.getTotalStages() : 1;

        // 阶段数+1
        currentStage++;
        path.setCurrentStage(currentStage);

        // 若超过总阶段数，自动完成路径
        if (currentStage >= totalStages) {
            completePath(pathId);
        } else {
            updateById(path);
        }
    }

    /**
     * 完成学习路径
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completePath(Long pathId) {
        LearningPath path = getById(pathId);
        if (path == null) {
            throw new BusinessException("学习路径不存在: " + pathId);
        }
        path.setStatus("COMPLETED");
        path.setCompletionRate(BigDecimal.valueOf(100.00).setScale(2, RoundingMode.HALF_UP));
        path.setActualCompletionDate(LocalDateTime.now());
        updateById(path);
    }

    /**
     * 暂停学习路径
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pausePath(Long pathId) {
        LearningPath path = getById(pathId);
        if (path == null) {
            throw new BusinessException("学习路径不存在: " + pathId);
        }
        if (!"ACTIVE".equals(path.getStatus())) {
            throw new BusinessException("只有活跃状态的路径才能暂停");
        }
        path.setStatus("PAUSED");
        updateById(path);
    }

    /**
     * 恢复学习路径
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumePath(Long pathId) {
        LearningPath path = getById(pathId);
        if (path == null) {
            throw new BusinessException("学习路径不存在: " + pathId);
        }
        if (!"PAUSED".equals(path.getStatus())) {
            throw new BusinessException("只有暂停状态的路径才能恢复");
        }
        path.setStatus("ACTIVE");
        updateById(path);
    }

    /**
     * 获取每日任务清单（从dailyTasks JSON解析）
     */
    @Override
    public List<Map<String, Object>> getDailyTasks(Long pathId) {
        LearningPath path = getById(pathId);
        if (path == null || path.getDailyTasks() == null || path.getDailyTasks().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(path.getDailyTasks(),
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 获取学习路径完整详情（含节点列表及统计信息）
     */
    @Override
    public Map<String, Object> getPathDetail(Long pathId) {
        LearningPath path = getById(pathId);
        if (path == null) {
            throw new BusinessException("学习路径不存在: " + pathId);
        }

        // 查询所有节点（按orderIndex排序）
        List<LearningPathNode> nodes = nodeService.getPathNodes(pathId);

        // 统计各状态节点数
        Map<String, Long> statusStats = nodeService.getNodeStatusStats(pathId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("path", path);
        detail.put("nodes", nodes);
        detail.put("statusStats", statusStats);
        detail.put("totalNodes", nodes.size());
        return detail;
    }

    /**
     * 获取学生的学习路径列表（指定课程下）
     */
    @Override
    public List<LearningPath> getStudentPaths(Long studentId, Long courseId) {
        LambdaQueryWrapper<LearningPath> wrapper = new LambdaQueryWrapper<LearningPath>()
                .eq(LearningPath::getStudentId, studentId)
                .eq(LearningPath::getCourseId, courseId)
                .orderByDesc(LearningPath::getCreatedAt);
        return list(wrapper);
    }

    /**
     * 根据实际速度预估完成日期（基于已完成节点的平均耗时推算）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void estimateCompletionDate(Long pathId) {
        LearningPath path = getById(pathId);
        if (path == null) {
            throw new BusinessException("学习路径不存在: " + pathId);
        }

        // 查询已完成的节点
        List<LearningPathNode> nodes = nodeService.getPathNodes(pathId);
        List<LearningPathNode> completedNodes = nodes.stream()
                .filter(node -> "COMPLETED".equals(node.getStatus())
                        && node.getActualTime() != null && node.getActualTime() > 0)
                .collect(Collectors.toList());

        if (completedNodes.isEmpty()) {
            // 无已完成的节点，无法预估，使用默认值（30天后）
            path.setEstimatedCompletionDate(LocalDateTime.now().plusDays(30));
            updateById(path);
            return;
        }

        // 计算已完成节点的平均耗时（分钟）
        double avgTime = completedNodes.stream()
                .mapToInt(LearningPathNode::getActualTime)
                .average()
                .orElse(60.0);

        // 计算未完成的节点数
        long remainingCount = nodes.stream()
                .filter(node -> !"COMPLETED".equals(node.getStatus())
                        && !"SKIPPED".equals(node.getStatus()))
                .count();

        // 推算剩余所需分钟数，转换为天数（每天按2小时学习计算）
        double remainingMinutes = avgTime * remainingCount;
        int estimatedDays = (int) Math.ceil(remainingMinutes / 120.0);

        path.setEstimatedCompletionDate(LocalDateTime.now().plusDays(estimatedDays));
        updateById(path);
    }
}
