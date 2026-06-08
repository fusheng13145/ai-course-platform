package com.aicourse.service.impl;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识点服务实现类
 * 提供知识树构建、父子关系查询、前置依赖、掌握度计算等功能实现
 */
@Service
public class KnowledgePointServiceImpl extends ServiceImpl<KnowledgePointMapper, KnowledgePoint> implements KnowledgePointService {

    private final KnowledgePointRelationService relationService;
    private final QuestionService questionService;
    private final WrongQuestionService wrongQuestionService;

    public KnowledgePointServiceImpl(@Lazy KnowledgePointRelationService relationService,
                                     QuestionService questionService,
                                     WrongQuestionService wrongQuestionService) {
        this.relationService = relationService;
        this.questionService = questionService;
        this.wrongQuestionService = wrongQuestionService;
    }

    /**
     * 获取课程的完整知识树（递归构建层级结构）
     */
    @Override
    public List<Map<String, Object>> getKnowledgeTree(Long courseId) {
        // 查询课程下所有知识点
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId)
                .orderByAsc(KnowledgePoint::getOrderIndex);
        List<KnowledgePoint> allPoints = list(wrapper);

        // 按ID建立Map便于查找
        Map<Long, KnowledgePoint> pointMap = allPoints.stream()
                .collect(Collectors.toMap(KnowledgePoint::getId, p -> p, (a, b) -> a));

        // 找出根节点（parentId为null）
        List<KnowledgePoint> rootPoints = allPoints.stream()
                .filter(p -> p.getParentId() == null)
                .collect(Collectors.toList());

        // 递归构建树
        List<Map<String, Object>> tree = new ArrayList<>();
        for (KnowledgePoint root : rootPoints) {
            tree.add(buildTreeNode(root, pointMap));
        }
        return tree;
    }

    /**
     * 递归构建树形节点
     */
    private Map<String, Object> buildTreeNode(KnowledgePoint point, Map<Long, KnowledgePoint> pointMap) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", point.getId());
        node.put("name", point.getName());
        node.put("description", point.getDescription());
        node.put("difficultyLevel", point.getDifficultyLevel());
        node.put("importance", point.getImportance());
        node.put("estimatedTime", point.getEstimatedTime());
        node.put("pointType", point.getPointType());
        node.put("orderIndex", point.getOrderIndex());

        // 查找子节点
        List<Map<String, Object>> children = new ArrayList<>();
        for (KnowledgePoint p : pointMap.values()) {
            if (point.getId().equals(p.getParentId())) {
                children.add(buildTreeNode(p, pointMap));
            }
        }
        node.put("children", children);
        return node;
    }

    /**
     * 获取某知识点的直接子节点（按orderIndex排序）
     */
    @Override
    public List<KnowledgePoint> getChildPoints(Long parentId) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getParentId, parentId)
                .orderByAsc(KnowledgePoint::getOrderIndex);
        return list(wrapper);
    }

    /**
     * 获取知识点的前置依赖（通过关联关系中relationType=PREREQUISITE查询）
     */
    @Override
    public List<KnowledgePoint> getPrerequisites(Long pointId) {
        // 查询以当前知识点为target且类型为PREREQUISITE的关联关系
        LambdaQueryWrapper<KnowledgePointRelation> wrapper = new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getTargetId, pointId)
                .eq(KnowledgePointRelation::getRelationType, "PREREQUISITE");
        List<KnowledgePointRelation> relations = relationService.list(wrapper);

        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查出前置知识点
        List<Long> sourceIds = relations.stream()
                .map(KnowledgePointRelation::getSourceId)
                .collect(Collectors.toList());
        return listByIds(sourceIds);
    }

    /**
     * 获取知识点的后续知识点（被谁作为前置依赖）
     */
    @Override
    public List<KnowledgePoint> getSubsequentPoints(Long pointId) {
        // 查询以当前知识点为source且类型为PREREQUISITE的关联关系
        LambdaQueryWrapper<KnowledgePointRelation> wrapper = new LambdaQueryWrapper<KnowledgePointRelation>()
                .eq(KnowledgePointRelation::getSourceId, pointId)
                .eq(KnowledgePointRelation::getRelationType, "PREREQUISITE");
        List<KnowledgePointRelation> relations = relationService.list(wrapper);

        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查出后续知识点
        List<Long> targetIds = relations.stream()
                .map(KnowledgePointRelation::getTargetId)
                .collect(Collectors.toList());
        return listByIds(targetIds);
    }

    /**
     * 计算知识点掌握度（基于错题本中该知识点的掌握情况）
     */
    @Override
    public BigDecimal calculateMastery(Long pointId, Long studentId) {
        // 查询该知识点关联的题目（通过question.knowledgePoints字段匹配）
        LambdaQueryWrapper<Question> questionWrapper = new LambdaQueryWrapper<Question>()
                .like(Question::getKnowledgePoints, String.valueOf(pointId));
        List<Question> relatedQuestions = questionService.list(questionWrapper);

        if (relatedQuestions.isEmpty()) {
            // 无关联题目，返回默认值
            return BigDecimal.valueOf(0).setScale(2, RoundingMode.HALF_UP);
        }

        List<Long> questionIds = relatedQuestions.stream()
                .map(Question::getId)
                .collect(Collectors.toList());

        // 查询学生针对这些题目的错题记录
        LambdaQueryWrapper<WrongQuestion> wrongWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .in(WrongQuestion::getQuestionId, questionIds);
        List<WrongQuestion> wrongRecords = wrongQuestionService.list(wrongWrapper);

        int totalQuestions = questionIds.size();
        if (wrongRecords.isEmpty()) {
            // 无错题记录，视为全部正确
            return BigDecimal.valueOf(100.00).setScale(2, RoundingMode.HALF_UP);
        }

        // 统计已掌握的错题数量（mastered=true）
        long masteredCount = wrongRecords.stream()
                .filter(wr -> Boolean.TRUE.equals(wr.getMastered()))
                .count();

        // 计算掌握比例：(已掌握数 + 未出错数) / 总题数 * 100
        int notWrongCount = totalQuestions - wrongRecords.size();
        double masteryRate = ((double) masteredCount + notWrongCount) / totalQuestions * 100;
        return BigDecimal.valueOf(masteryRate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 获取课程的根级知识点（parentId为null）
     */
    @Override
    public List<KnowledgePoint> getRootPoints(Long courseId) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId)
                .isNull(KnowledgePoint::getParentId)
                .orderByAsc(KnowledgePoint::getOrderIndex);
        return list(wrapper);
    }
}
