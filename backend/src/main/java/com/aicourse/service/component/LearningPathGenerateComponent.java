package com.aicourse.service.component;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.LearningPathMapper;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.LearningPathNodeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习路径生成组件：数据驱动节点生成 / 标准路径生成 / 掌握度驱动个性化生成。
 * <p>拆分自原 LearningPathServiceImpl（2026-08-29），跨组件调用掌握度组件与状态组件。</p>
 */
@Component
public class LearningPathGenerateComponent {

    private static final Logger log = LoggerFactory.getLogger(LearningPathGenerateComponent.class);

    private final KnowledgePointService knowledgePointService;
    private final LearningPathNodeService nodeService;
    private final LearningPathMapper learningPathMapper;
    private final LearningPathMasteryComponent masteryComponent;
    private final LearningPathStateComponent stateComponent;
    private final ObjectMapper objectMapper;

    public LearningPathGenerateComponent(KnowledgePointService knowledgePointService,
                                         LearningPathNodeService nodeService,
                                         LearningPathMapper learningPathMapper,
                                         LearningPathMasteryComponent masteryComponent,
                                         LearningPathStateComponent stateComponent,
                                         ObjectMapper objectMapper) {
        this.knowledgePointService = knowledgePointService;
        this.nodeService = nodeService;
        this.learningPathMapper = learningPathMapper;
        this.masteryComponent = masteryComponent;
        this.stateComponent = stateComponent;
        this.objectMapper = objectMapper;
    }

    /**
     * 自动生成学习节点（数据驱动版）：掌握度推导 → 知识点查询 → 薄弱优先排序 → 节点构建。
     * 由 Controller 内联实现下沉，行为保持一致。
     */
    public List<LearningPathNode> generateLearningPath(Long studentId, Long courseId) {
        log.info("生成学习路径");

        // 学情个性化：从错题本推导各知识点掌握度（无学情时为空 Map）
        Map<String, Double> mastery = masteryComponent.deriveMasteryFromWrongQuestions(studentId, courseId);

        // 数据驱动：取课程知识库知识点，按「薄弱优先 → 重要度」双关键字排序
        List<KnowledgePoint> points = new ArrayList<>(knowledgePointService.list(
                new LambdaQueryWrapper<KnowledgePoint>()
                        .eq(KnowledgePoint::getCourseId, courseId)
                        .orderByDesc(KnowledgePoint::getImportance)
                        .last("LIMIT 16")));

        points.sort(Comparator
                .comparingDouble((KnowledgePoint p) -> mastery.getOrDefault(p.getName(), 50.0))
                .thenComparing(p -> p.getImportance() == null ? 0 : -p.getImportance()));
        if (points.size() > 8) {
            points = points.subList(0, 8);
        }

        List<LearningPathNode> nodes = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            KnowledgePoint p = points.get(i);
            LearningPathNode node = new LearningPathNode();
            node.setTitle(p.getName());
            node.setNodeType("study");
            node.setStatus("PENDING");
            node.setOrderIndex(i);
            // 掌握度加权时长：mastery=0 → 2.0x，mastery=100 → 1.0x，未评估默认 1.5x
            double m = mastery.getOrDefault(p.getName(), 50.0);
            int baseTime = p.getEstimatedTime() != null ? p.getEstimatedTime() : 60;
            node.setEstimatedTime(Math.max(15, (int) (baseTime * (2.0 - m / 100.0))));
            String desc = p.getDescription();
            if (mastery.containsKey(p.getName())) {
                desc = (desc == null ? "" : desc + " ")
                        + "[学情诊断：掌握度约" + (int) m + "%，建议优先补强]";
            }
            node.setDescription(desc);
            nodes.add(node);
        }
        // 无知识点时返回空列表（不再返回硬编码主题）
        return nodes;
    }

    /**
     * 生成学习路径（根据课程知识点自动生成，初始化stages和dailyTasks的JSON结构）
     */
    public LearningPath generatePath(Long studentId, Long courseId, String pathName, String learningGoal) {
        // 检查是否已有活跃的学习路径
        LearningPath existingActive = stateComponent.getActivePath(studentId, courseId);
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
        learningPathMapper.insert(path);

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
            log.error("生成学习路径失败: {}", e.getMessage(), e);
            path.setStages("[{\"error\":\"序列化失败\"}]");
        }

        // 初始化dailyTasks JSON结构（生成前7天的每日任务计划）
        List<Map<String, Object>> dailyTasksList = generateDailyTasksPlan(stagesList);
        try {
            path.setDailyTasks(objectMapper.writeValueAsString(dailyTasksList));
        } catch (Exception e) {
            log.error("生成学习路径失败: {}", e.getMessage(), e);
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
            log.error("生成学习路径失败: {}", e.getMessage(), e);
            path.setPlanDetail("{}");
        }

        // 预估完成日期（默认30天后）
        path.setEstimatedCompletionDate(LocalDateTime.now().plusDays(30));

        learningPathMapper.updateById(path);
        return path;
    }

    /**
     * 掌握度驱动的个性化学习路径生成。
     * 复用标准生成流程，在此基础上做三个维度的个性化调整。
     */
    public LearningPath generatePathPersonalized(Long studentId, Long courseId, String pathName,
                                                  String learningGoal, Map<String, Double> masteryData) {
        // 无外部掌握度数据时，先尝试从错题学情自动推导；仍为空才退化为标准路径
        final Map<String, Double> effectiveMastery;
        if (masteryData == null || masteryData.isEmpty()) {
            Map<String, Double> derived = masteryComponent.deriveMasteryFromWrongQuestions(studentId, courseId);
            if (derived.isEmpty()) {
                return generatePath(studentId, courseId, pathName, learningGoal);
            }
            effectiveMastery = derived;
        } else {
            effectiveMastery = masteryData;
        }

        // 检查是否已有活跃的学习路径
        LearningPath existingActive = stateComponent.getActivePath(studentId, courseId);
        if (existingActive != null) {
            throw new BusinessException("该课程已有进行中的学习路径，请先完成或暂停后再创建新的");
        }

        // 查询课程下全部知识点
        List<KnowledgePoint> rootPoints = knowledgePointService.getRootPoints(courseId);
        if (rootPoints.isEmpty()) {
            throw new BusinessException("课程下暂无知识点，无法生成学习路径");
        }

        // ===== 个性化调整（三阶段） =====

        // 阶段 1: 按掌握度排序根知识点（薄弱 → 掌握）
        List<KnowledgePoint> sortedRoots = new ArrayList<>(rootPoints);
        sortedRoots.sort(Comparator.comparingDouble(kp -> {
            Double mastery = effectiveMastery.getOrDefault(kp.getName(), 50.0); // 默认 50（未评估）
            return mastery; // mastery 越低排在越前
        }));

        // 阶段 2: 创建学习路径实体
        LearningPath path = new LearningPath();
        path.setStudentId(studentId);
        path.setCourseId(courseId);
        path.setPathName(pathName != null ? pathName : "个性化学习路线");
        path.setDescription("基于掌握度诊断的个性化学习路线（薄弱优先）");
        path.setLearningGoal(learningGoal != null ? learningGoal : "针对性补强薄弱点，完成课程全部内容学习");
        path.setStatus("ACTIVE");
        path.setCurrentStage(1);
        path.setTotalStages(sortedRoots.size());
        path.setCompletionRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        learningPathMapper.insert(path);

        // 阶段 3: 构建 stages（含个性化时间分配）
        List<Map<String, Object>> stagesList = new ArrayList<>();
        int nodeOrderIndex = 0;
        for (int i = 0; i < sortedRoots.size(); i++) {
            KnowledgePoint kp = sortedRoots.get(i);
            List<KnowledgePoint> childPoints = knowledgePointService.getChildPoints(kp.getId());

            // 个性化 estimatedTime：掌握度越低，分配时间越多（1.0x ~ 2.0x）
            Double rootMastery = effectiveMastery.getOrDefault(kp.getName(), 50.0);
            double timeMultiplier = 2.0 - (rootMastery / 100.0); // mastery=0 → 2.0x, mastery=100 → 1.0x
            int baseTime = kp.getEstimatedTime() != null ? kp.getEstimatedTime() : 60;
            int personalizedTime = Math.max(15, (int) (baseTime * timeMultiplier));

            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("stageNumber", i + 1);
            stage.put("stageName", kp.getName());
            stage.put("stageDescription", kp.getDescription() != null ? kp.getDescription() : "");
            stage.put("knowledgePointId", kp.getId());
            stage.put("estimatedTime", personalizedTime);
            stage.put("masteryLevel", rootMastery.intValue());
            stage.put("status", i == 0 ? "IN_PROGRESS" : "NOT_STARTED");

            // 子知识点按难度排序（简单 → 困难），保证渐进式学习
            List<KnowledgePoint> sortedChildren = new ArrayList<>(childPoints);
            sortedChildren.sort(Comparator.comparing(cp -> {
                String diff = cp.getDifficultyLevel();
                if (diff == null) return 2;
                return switch (diff.toUpperCase()) {
                    case "EASY", "简单" -> 0;
                    case "MEDIUM", "中等" -> 1;
                    case "HARD", "困难" -> 2;
                    default -> 2;
                };
            }));

            List<Long> childNodeIds = new ArrayList<>();
            for (KnowledgePoint childKp : sortedChildren) {
                LearningPathNode node = new LearningPathNode();
                node.setLearningPathId(path.getId());
                node.setKnowledgePointId(childKp.getId());
                node.setTitle(childKp.getName());
                node.setDescription(childKp.getDescription());
                node.setNodeType(determineNodeType(childKp.getPointType()));
                node.setStatus(i == 0 && nodeOrderIndex == 0 ? "IN_PROGRESS" : "NOT_STARTED");
                node.setOrderIndex(++nodeOrderIndex);

                // 子节点同样按掌握度调时间
                Double childMastery = effectiveMastery.getOrDefault(childKp.getName(), 50.0);
                double childMultiplier = 2.0 - (childMastery / 100.0);
                int childBase = childKp.getEstimatedTime() != null ? childKp.getEstimatedTime() : 30;
                node.setEstimatedTime(Math.max(10, (int) (childBase * childMultiplier)));

                nodeService.save(node);
                childNodeIds.add(node.getId());
            }

            if (childNodeIds.isEmpty()) {
                LearningPathNode node = new LearningPathNode();
                node.setLearningPathId(path.getId());
                node.setKnowledgePointId(kp.getId());
                node.setTitle(kp.getName());
                node.setDescription(kp.getDescription());
                node.setNodeType(determineNodeType(kp.getPointType()));
                node.setStatus(i == 0 ? "IN_PROGRESS" : "NOT_STARTED");
                node.setOrderIndex(++nodeOrderIndex);
                node.setEstimatedTime(personalizedTime);
                nodeService.save(node);
                childNodeIds.add(node.getId());
            }

            stage.put("nodeIds", childNodeIds);
            stage.put("nodeCount", childNodeIds.size());
            stagesList.add(stage);
        }

        // 序列化 JSON
        try {
            path.setStages(objectMapper.writeValueAsString(stagesList));
        } catch (Exception e) {
            log.error("生成学习路径失败: {}", e.getMessage(), e);
            path.setStages("[{\"error\":\"序列化失败\"}]");
        }

        List<Map<String, Object>> dailyTasksList = generateDailyTasksPlan(stagesList);
        try {
            path.setDailyTasks(objectMapper.writeValueAsString(dailyTasksList));
        } catch (Exception e) {
            log.error("生成学习路径失败: {}", e.getMessage(), e);
            path.setDailyTasks("[]");
        }

        Map<String, Object> planDetailMap = new LinkedHashMap<>();
        planDetailMap.put("totalStages", sortedRoots.size());
        planDetailMap.put("totalNodes", nodeOrderIndex);
        planDetailMap.put("generatedAt", LocalDateTime.now().toString());
        planDetailMap.put("strategy", "mastery_driven_personalized");
        planDetailMap.put("hasMasteryData", true);
        try {
            path.setPlanDetail(objectMapper.writeValueAsString(planDetailMap));
        } catch (Exception e) {
            log.error("生成学习路径失败: {}", e.getMessage(), e);
            path.setPlanDetail("{}");
        }

        // 自适应完成时间：总 estimatedTime / 日均学习 60 分钟
        int totalMinutes = stagesList.stream()
                .mapToInt(s -> (Integer) s.getOrDefault("estimatedTime", 60))
                .sum();
        path.setEstimatedCompletionDate(LocalDateTime.now().plusDays(Math.max(7, totalMinutes / 60)));

        learningPathMapper.updateById(path);
        return path;
    }

    /**
     * 根据知识点类型确定节点类型
     */
    public String determineNodeType(String pointType) {
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
}
