package com.aicourse.service.component.learning;

import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.LearningPathMapper;
import com.aicourse.service.LearningPathNodeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 学习路径状态组件：活跃路径查询 / 进度更新 / 阶段推进 / 完成 / 暂停 / 恢复 / 完成日期预估。
 * <p>拆分自原 LearningPathServiceImpl（2026-08-29），组件持有 LearningPathMapper 直接落库。</p>
 */
@Component
public class LearningPathStateComponent {

    private static final Logger log = LoggerFactory.getLogger(LearningPathStateComponent.class);

    private final LearningPathNodeService nodeService;
    private final LearningPathMapper learningPathMapper;

    public LearningPathStateComponent(LearningPathNodeService nodeService,
                                      LearningPathMapper learningPathMapper) {
        this.nodeService = nodeService;
        this.learningPathMapper = learningPathMapper;
    }

    /**
     * 获取学生的活跃学习路径（状态为ACTIVE）
     */
    public LearningPath getActivePath(Long studentId, Long courseId) {
        LambdaQueryWrapper<LearningPath> wrapper = new LambdaQueryWrapper<LearningPath>()
                .eq(LearningPath::getStudentId, studentId)
                .eq(LearningPath::getCourseId, courseId)
                .eq(LearningPath::getStatus, "ACTIVE")
                .last("LIMIT 1");
        return learningPathMapper.selectOne(wrapper, false);
    }

    /**
     * 更新整体进度（根据节点完成情况重算completionRate）
     */
    public void updateProgress(Long pathId) {
        log.info("更新学习路径");

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
        LearningPath path = learningPathMapper.selectById(pathId);
        path.setCompletionRate(completionRate);
        learningPathMapper.updateById(path);

        // 若全部完成，自动标记为已完成
        if (completedCount == nodes.size()) {
            completePath(pathId);
        }
    }

    /**
     * 进入下一阶段
     */
    public void advanceToNextStage(Long pathId) {
        LearningPath path = learningPathMapper.selectById(pathId);
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
            learningPathMapper.updateById(path);
        }
    }

    /**
     * 完成学习路径
     */
    public void completePath(Long pathId) {
        LearningPath path = learningPathMapper.selectById(pathId);
        if (path == null) {
            throw new BusinessException("学习路径不存在: " + pathId);
        }
        path.setStatus("COMPLETED");
        path.setCompletionRate(BigDecimal.valueOf(100.00).setScale(2, RoundingMode.HALF_UP));
        path.setActualCompletionDate(LocalDateTime.now());
        learningPathMapper.updateById(path);
    }

    /**
     * 暂停学习路径
     */
    public void pausePath(Long pathId) {
        LearningPath path = learningPathMapper.selectById(pathId);
        if (path == null) {
            throw new BusinessException("学习路径不存在: " + pathId);
        }
        if (!"ACTIVE".equals(path.getStatus())) {
            throw new BusinessException("只有活跃状态的路径才能暂停");
        }
        path.setStatus("PAUSED");
        learningPathMapper.updateById(path);
    }

    /**
     * 恢复学习路径
     */
    public void resumePath(Long pathId) {
        LearningPath path = learningPathMapper.selectById(pathId);
        if (path == null) {
            throw new BusinessException("学习路径不存在: " + pathId);
        }
        if (!"PAUSED".equals(path.getStatus())) {
            throw new BusinessException("只有暂停状态的路径才能恢复");
        }
        path.setStatus("ACTIVE");
        learningPathMapper.updateById(path);
    }

    /**
     * 根据实际速度预估完成日期（基于已完成节点的平均耗时推算）
     */
    public void estimateCompletionDate(Long pathId) {
        LearningPath path = learningPathMapper.selectById(pathId);
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
            learningPathMapper.updateById(path);
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
        learningPathMapper.updateById(path);
    }
}
