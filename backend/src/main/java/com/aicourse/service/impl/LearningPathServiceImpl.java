package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.LearningPathMapper;
import com.aicourse.service.KnowledgePointService;
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
    private final KnowledgePointService knowledgePointService;
    private final ObjectMapper objectMapper;

    public LearningPathServiceImpl(LearningPathNodeService nodeService,
                                   KnowledgePointService knowledgePointService,
                                   ObjectMapper objectMapper) {
        this.nodeService = nodeService;
        this.knowledgePointService = knowledgePointService;
        this.objectMapper = objectMapper;
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
     * 解析stages和dailyTasks的JSON字段并返回结构化数据
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

        // 解析stages JSON字段为结构化数据
        if (path.getStages() != null && !path.getStages().isEmpty()) {
            try {
                List<Map<String, Object>> stagesData = objectMapper.readValue(path.getStages(),
                        new TypeReference<List<Map<String, Object>>>() {});
                detail.put("stagesParsed", stagesData);
            } catch (Exception e) {
                // JSON解析失败时返回原始字符串
                detail.put("stagesRaw", path.getStages());
            }
        }

        // 解析planDetail JSON字段
        if (path.getPlanDetail() != null && !path.getPlanDetail().isEmpty()) {
            try {
                Map<String, Object> planDetailData = objectMapper.readValue(path.getPlanDetail(),
                        new TypeReference<Map<String, Object>>() {});
                detail.put("planDetailParsed", planDetailData);
            } catch (Exception e) {
                detail.put("planDetailRaw", path.getPlanDetail());
            }
        }

        return detail;
    }

    /**
     * 生成学习路径（根据课程知识点自动生成，初始化stages和dailyTasks的JSON结构）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LearningPath generatePath(Long studentId, Long courseId, String pathName, String learningGoal) {
        // 检查是否已有活跃的学习路径
        LearningPath existingActive = getActivePath(studentId, courseId);
        if (existingActive != null) {
            throw new BusinessException("该课程已有进行中的学习路径，请先完成或暂停后再创建新的");
        }

        // 查询课程下的根级知识点（作为阶段划分依据）
        List<KnowledgePoint> rootPoints = knowledgePointService.getRootPoints(courseId);
        if (rootPoints.isEmpty()) {
            throw new BusinessException("课程下暂无知识点，无法生成学习路径");
        }

        // 创建学习路径实体
        LearningPath path = new LearningPath();
        path.setStudentId(studentId);
        path.setCourseId(courseId);
        path.setPathName(pathName != null ? pathName : "默认学习路线");
        path.setDescription("基于课程知识点的自动生成学习路线");
        path.setLearningGoal(learningGoal != null ? learningGoal : "完成课程全部内容学习");
        path.setStatus("ACTIVE");
        path.setCurrentStage(1);
        path.setTotalStages(rootPoints.size());
        path.setCompletionRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        this.save(path);

        // 初始化stages JSON结构（按根级知识点划分为阶段）
        List<Map<String, Object>> stagesList = new ArrayList<>();
        int nodeOrderIndex = 0;
        for (int i = 0; i < rootPoints.size(); i++) {
            KnowledgePoint kp = rootPoints.get(i);

            // 查询该根级知识点的子知识点
            List<KnowledgePoint> childPoints = knowledgePointService.getChildPoints(kp.getId());

            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("stageNumber", i + 1);
            stage.put("stageName", kp.getName());
            stage.put("stageDescription", kp.getDescription() != null ? kp.getDescription() : "");
            stage.put("knowledgePointId", kp.getId());
            stage.put("estimatedTime", kp.getEstimatedTime() != null ? kp.getEstimatedTime() : 60);
            stage.put("status", i == 0 ? "IN_PROGRESS" : "NOT_STARTED");

            // 为每个子知识点创建学习路径节点
            List<Long> childNodeIds = new ArrayList<>();
            for (KnowledgePoint childKp : childPoints) {
                LearningPathNode node = new LearningPathNode();
                node.setLearningPathId(path.getId());
                node.setKnowledgePointId(childKp.getId());
                node.setTitle(childKp.getName());
                node.setDescription(childKp.getDescription());
                node.setNodeType(determineNodeType(childKp.getPointType()));
                node.setStatus(i == 0 && nodeOrderIndex == 0 ? "IN_PROGRESS" : "NOT_STARTED");
                node.setOrderIndex(++nodeOrderIndex);
                node.setEstimatedTime(childKp.getEstimatedTime() != null ? childKp.getEstimatedTime() : 30);
                nodeService.save(node);
                childNodeIds.add(node.getId());
            }
            // 如果没有子知识点，则直接为根知识点创建一个节点
            if (childNodeIds.isEmpty()) {
                LearningPathNode node = new LearningPathNode();
                node.setLearningPathId(path.getId());
                node.setKnowledgePointId(kp.getId());
                node.setTitle(kp.getName());
                node.setDescription(kp.getDescription());
                node.setNodeType(determineNodeType(kp.getPointType()));
                node.setStatus(i == 0 ? "IN_PROGRESS" : "NOT_STARTED");
                node.setOrderIndex(++nodeOrderIndex);
                node.setEstimatedTime(kp.getEstimatedTime() != null ? kp.getEstimatedTime() : 60);
                nodeService.save(node);
                childNodeIds.add(node.getId());
            }
            stage.put("nodeIds", childNodeIds);
            stage.put("nodeCount", childNodeIds.size());

            stagesList.add(stage);
        }

        // 将stages序列化为JSON存入字段
        try {
            path.setStages(objectMapper.writeValueAsString(stagesList));
        } catch (Exception e) {
            path.setStages("[{\"error\":\"序列化失败\"}]");
        }

        // 初始化dailyTasks JSON结构（生成前7天的每日任务计划）
        List<Map<String, Object>> dailyTasksList = generateDailyTasksPlan(stagesList);
        try {
            path.setDailyTasks(objectMapper.writeValueAsString(dailyTasksList));
        } catch (Exception e) {
            path.setDailyTasks("[]");
        }

        // 初始化planDetail JSON结构
        Map<String, Object> planDetailMap = new LinkedHashMap<>();
        planDetailMap.put("totalStages", rootPoints.size());
        planDetailMap.put("totalNodes", nodeOrderIndex);
        planDetailMap.put("generatedAt", LocalDateTime.now().toString());
        planDetailMap.put("strategy", "knowledge_point_based");
        try {
            path.setPlanDetail(objectMapper.writeValueAsString(planDetailMap));
        } catch (Exception e) {
            path.setPlanDetail("{}");
        }

        // 预估完成日期（默认30天后）
        path.setEstimatedCompletionDate(LocalDateTime.now().plusDays(30));

        updateById(path);
        return path;
    }

    /**
     * 根据知识点类型确定节点类型
     */
    private String determineNodeType(String pointType) {
        if (pointType == null) return "STUDY";
        switch (pointType.toUpperCase()) {
            case "EXAM":
            case "TEST": return "TEST";
            case "PRACTICE":
            case "EXERCISE": return "PRACTICE";
            case "VIDEO":
            case "COURSEWARE": return "VIDEO";
            default: return "STUDY";
        }
    }

    /**
     * 根据阶段信息生成前7天的每日任务计划
     */
    private List<Map<String, Object>> generateDailyTasksPlan(List<Map<String, Object>> stages) {
        List<Map<String, Object>> dailyTasks = new ArrayList<>();
        java.time.LocalDate baseDate = java.time.LocalDate.now();

        // 简单策略：将阶段均匀分配到7天中
        int totalStages = stages.size();
        for (int day = 0; day < Math.min(7, totalStages); day++) {
            Map<String, Object> dayTask = new LinkedHashMap<>();
            dayTask.put("dayNumber", day + 1);
            dayTask.put("date", baseDate.plusDays(day).toString());

            if (day < stages.size()) {
                Map<String, Object> stage = stages.get(day);
                dayTask.put("taskTitle", "第" + (day + 1) + "阶段：" + stage.get("stageName"));
                dayTask.put("taskDescription", "学习知识点：" + stage.get("stageName"));
                dayTask.put("stageNumber", stage.get("stageNumber"));
                dayTask.put("estimatedMinutes", stage.get("estimatedTime"));
                dayTask.put("status", day == 0 ? "TODAY" : "UPCOMING");
            } else {
                dayTask.put("taskTitle", "复习巩固日");
                dayTask.put("taskDescription", "复习前面已学内容");
                dayTask.put("estimatedMinutes", 30);
                dayTask.put("status", "UPCOMING");
            }

            dailyTasks.add(dayTask);
        }

        return dailyTasks;
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
