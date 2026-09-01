package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.KnowledgeTreeNodeVO;
import com.aicourse.service.WrongQuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
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

    private static final Logger log = LoggerFactory.getLogger(KnowledgePointServiceImpl.class);

    private static final String CACHE_NAME = "knowledgePoints";

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
    @Cacheable(cacheNames = CACHE_NAME, key = "'tree:' + #courseId")
    public List<KnowledgeTreeNodeVO> getKnowledgeTree(Long courseId) {
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
        List<KnowledgeTreeNodeVO> tree = new ArrayList<>();
        for (KnowledgePoint root : rootPoints) {
            tree.add(buildTreeNode(root, pointMap));
        }
        return tree;
    }

    /**
     * 递归构建树形节点
     */
    private KnowledgeTreeNodeVO buildTreeNode(KnowledgePoint point, Map<Long, KnowledgePoint> pointMap) {
        List<KnowledgeTreeNodeVO> children = new ArrayList<>();
        for (KnowledgePoint p : pointMap.values()) {
            if (point.getId().equals(p.getParentId())) {
                children.add(buildTreeNode(p, pointMap));
            }
        }
        return new KnowledgeTreeNodeVO(
                point.getId(), point.getName(), point.getDescription(),
                point.getDifficultyLevel(), point.getImportance(),
                point.getEstimatedTime(), point.getPointType(),
                point.getOrderIndex(), children);
    }

    /**
     * 获取某知识点的直接子节点（按orderIndex排序）
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'children:' + #parentId")
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
    @Cacheable(cacheNames = CACHE_NAME, key = "'prerequisites:' + #pointId")
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
    @Cacheable(cacheNames = CACHE_NAME, key = "'subsequent:' + #pointId")
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
     * 创建知识点（校验difficultyLevel和importance的取值范围）
     * difficultyLevel: EASY/MEDIUM/HARD
     * importance: 1-5
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public KnowledgePoint createWithValidation(KnowledgePoint knowledgePoint) {
        log.info("创建知识点点");

        // 校验必填字段
        if (knowledgePoint.getName() == null || knowledgePoint.getName().trim().isEmpty()) {
            throw new BusinessException("知识点名称不能为空");
        }
        if (knowledgePoint.getCourseId() == null) {
            throw new BusinessException("必须指定所属课程");
        }

        // 校验难度等级（如果提供了）
        if (knowledgePoint.getDifficultyLevel() != null && !knowledgePoint.getDifficultyLevel().isEmpty()) {
            String level = knowledgePoint.getDifficultyLevel().toUpperCase();
            if (!"EASY".equals(level) && !"MEDIUM".equals(level) && !"HARD".equals(level)) {
                throw new BusinessException("难度等级必须是EASY、MEDIUM或HARD");
            }
            knowledgePoint.setDifficultyLevel(level);
        }

        // 校验重要度（1-5）
        if (knowledgePoint.getImportance() != null) {
            int importance = knowledgePoint.getImportance();
            if (importance < 1 || importance > 5) {
                throw new BusinessException("重要度必须在1-5之间");
            }
        } else {
            // 默认设置为3
            knowledgePoint.setImportance(3);
        }

        this.save(knowledgePoint);
        return knowledgePoint;
    }

    /**
     * 获取知识点列表（支持按难度等级和重要度排序/筛选）
     * getTree()时将importance映射到节点大小
     */
    @Override
    public List<KnowledgePoint> getListWithFilters(Long courseId, String difficultyLevel,
                                                     Integer minImportance, Integer maxImportance,
                                                     String sortBy, boolean isAsc) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId);

        // 按难度等级筛选
        if (difficultyLevel != null && !difficultyLevel.isEmpty()) {
            wrapper.eq(KnowledgePoint::getDifficultyLevel, difficultyLevel.toUpperCase());
        }

        // 按重要度范围筛选
        if (minImportance != null) {
            wrapper.ge(KnowledgePoint::getImportance, minImportance);
        }
        if (maxImportance != null) {
            wrapper.le(KnowledgePoint::getImportance, maxImportance);
        }

        // 排序
        if ("importance".equals(sortBy)) {
            if (isAsc) {
                wrapper.orderByAsc(KnowledgePoint::getImportance);
            } else {
                wrapper.orderByDesc(KnowledgePoint::getImportance);
            }
        } else if ("difficultyLevel".equals(sortBy)) {
            if (isAsc) {
                wrapper.orderByAsc(KnowledgePoint::getDifficultyLevel);
            } else {
                wrapper.orderByDesc(KnowledgePoint::getDifficultyLevel);
            }
        } else {
            // 默认按orderIndex排序
            wrapper.orderByAsc(KnowledgePoint::getOrderIndex);
        }

        return list(wrapper);
    }

    /**
     * 计算知识点掌握度（基于错题本中该知识点的掌握情况）
     */
    @Override
    public BigDecimal calculateMastery(Long pointId, Long studentId) {
        // question.knowledgePoints 字段存储「逗号分隔的知识点名称列表」。
        // 须先取知识点名称再按名称匹配：原实现用 pointId 做 LIKE，既匹配不到名称（ID 数字 vs 中文名），
        // 又存在子串误匹配（如 1 命中 12/11），导致掌握度计算失真。
        KnowledgePoint point = this.getById(pointId);
        if (point == null || point.getName() == null || point.getName().isBlank()) {
            // 知识点不存在或无名称：视同无关联题目
            return BigDecimal.valueOf(0).setScale(2, RoundingMode.HALF_UP);
        }
        // 精确匹配：FIND_IN_SET 判定名称是否为 knowledge_points 的独立逗号分隔项；
        // 兼容中英文逗号/分号分隔符，避免 LIKE '%名称%' 误匹配「函数」命中「三角函数」。
        LambdaQueryWrapper<Question> questionWrapper = new LambdaQueryWrapper<Question>()
                .apply("FIND_IN_SET({0}, REPLACE(REPLACE(IFNULL(knowledge_points, ''), '，', ','), ';', ',')) > 0",
                        point.getName());
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
    @Cacheable(cacheNames = CACHE_NAME, key = "'roots:' + #courseId")
    public List<KnowledgePoint> getRootPoints(Long courseId) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId)
                .isNull(KnowledgePoint::getParentId)
                .orderByAsc(KnowledgePoint::getOrderIndex);
        return list(wrapper);
    }

    // ===== IService 写操作覆写，添加缓存失效 =====

    @Override
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public boolean save(KnowledgePoint entity) {
        log.info("保存知识点点");

        return super.save(entity);
    }


    /** 更新知识点点 */
    @Override
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public boolean updateById(KnowledgePoint entity) {
        log.info("更新知识点点");

        return super.updateById(entity);
    }


    /** 移除知识点点 */
    @Override
    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public boolean removeById(Serializable id) {
        log.info("移除知识点点");

        return super.removeById(id);
    }

    @Override
    public long countByCourse(Long courseId) {
        if (courseId == null) {
            return 0L;
        }
        return count(new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCourseId, courseId));
    }

    @Override
    public List<KnowledgePoint> listByCourse(Long courseId) {
        if (courseId == null) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<KnowledgePoint>()
                .eq(KnowledgePoint::getCourseId, courseId)
                .orderByAsc(KnowledgePoint::getOrderIndex));
    }
}
