package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.QuestionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.QuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 题库题目服务实现类
 * 实现题目的创建、AI生成、审核、搜索、统计等核心业务逻辑
 */
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionServiceImpl.class);

    private final AIService aiService;
    private final ObjectMapper objectMapper;

    // @Lazy 打破与 AIServiceImpl 的构造期循环依赖：
    // AIServiceImpl -> RAGServiceImpl -> KnowledgePointServiceImpl -> QuestionServiceImpl -> AIServiceImpl
    // 本类仅在运行时方法（generateQuestion）中使用 AIService，构造期无需立即就绪。
    public QuestionServiceImpl(@Lazy AIService aiService) {
        this.aiService = aiService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 创建题目（含知识点关联处理）
     * 初始化使用次数和正确率，设置默认启用状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "questions", allEntries = true)
    public Question createQuestion(Question question) {
        log.info("创建题目");

        // 设置初始值
        if (question.getUsageCount() == null) {
            question.setUsageCount(0);
        }
        if (question.getCorrectRate() == null) {
            question.setCorrectRate(BigDecimal.ZERO);
        }
        if (question.getIsActive() == null) {
            question.setIsActive(true);
        }
        this.save(question);
        return question;
    }

    /**
     * AI生成题目（对接AIService）
     * 调用AIService生成题目内容并保存到数据库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "questions", allEntries = true)
    public Question generateByAI(Long courseId, String type, String difficulty, String knowledgePointNames) {
        // 调用AI服务生成题目JSON
        String generatedContent = aiService.generateQuestion(courseId, type, difficulty, 1);

        try {
            // 解析AI生成的题目内容
            Map<String, Object> questionData = objectMapper.readValue(generatedContent,
                    new TypeReference<Map<String, Object>>() {});

            Question question = new Question();
            question.setCourseId(courseId);
            question.setType(type);
            question.setDifficulty(difficulty);
            question.setContent(questionData.get("content") != null ? questionData.get("content").toString() : "");
            question.setOptions(questionData.get("options") != null ? objectMapper.writeValueAsString(questionData.get("options")) : null);
            question.setCorrectAnswers(questionData.get("correctAnswers") != null ? questionData.get("correctAnswers").toString() : "");
            question.setExplanation(questionData.get("explanation") != null ? questionData.get("explanation").toString() : "");
            question.setScore(questionData.get("score") != null ?
                    new BigDecimal(questionData.get("score").toString()) : new BigDecimal("5"));
            question.setKnowledgePoints(knowledgePointNames);
            question.setUsageCount(0);
            question.setCorrectRate(BigDecimal.ZERO);
            question.setIsActive(true);

            this.save(question);
            return question;
        } catch (Exception e) {
            log.error("生成题目失败: {}", e.getMessage(), e);

            throw new BusinessException("AI生成题目解析失败: " + e.getMessage());
        }
    }

    /**
     * AI 批量生成题目（进阶：解析 JSON 数组，一次生成多题）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "questions", allEntries = true)
    public List<Question> generateBatchByAI(Long courseId, String type, String difficulty,
                                            String knowledgePointNames, int count, Long createdBy) {
        String generated = aiService.generateQuestion(courseId, type, difficulty, count);
        List<Map<String, Object>> list;
        try {
            list = parseQuestionList(generated);
        } catch (Exception e) {
            log.error("生成题目失败: {}", e.getMessage(), e);

            throw new BusinessException("AI批量生成题目解析失败: " + e.getMessage());
        }
        List<Question> saved = new ArrayList<>();
        for (Map<String, Object> qd : list) {
            Question q = new Question();
            q.setCourseId(courseId);
            q.setType(type);
            q.setDifficulty(difficulty);
            q.setContent(qd.get("content") != null ? qd.get("content").toString() : "");
            q.setOptions(toJsonOrString(qd.get("options")));
            q.setCorrectAnswers(toJsonOrString(qd.get("correctAnswers")));
            q.setExplanation(qd.get("explanation") != null ? qd.get("explanation").toString() : "");
            q.setScore(qd.get("score") != null ? new BigDecimal(qd.get("score").toString()) : new BigDecimal("5"));
            q.setKnowledgePoints(knowledgePointNames);
            q.setUsageCount(0);
            q.setCorrectRate(BigDecimal.ZERO);
            q.setIsActive(true);
            q.setCreatedBy(createdBy);
            this.save(q);
            saved.add(q);
        }
        return saved;
    }

    /**
     * 批量导入题目（跳过无效项，落库并设置默认值）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "questions", allEntries = true)
    public int batchImport(List<Question> questions, Long createdBy) {
        int imported = 0;
        for (Question q : questions) {
            if (q.getContent() == null || q.getContent().isBlank()) {
                continue;
            }
            if (q.getType() == null || q.getType().isBlank()) {
                q.setType("single");
            }
            if (q.getDifficulty() == null || q.getDifficulty().isBlank()) {
                q.setDifficulty("medium");
            }
            if (q.getScore() == null) {
                q.setScore(new BigDecimal("5"));
            }
            if (q.getUsageCount() == null) {
                q.setUsageCount(0);
            }
            if (q.getCorrectRate() == null) {
                q.setCorrectRate(BigDecimal.ZERO);
            }
            if (q.getIsActive() == null) {
                q.setIsActive(true);
            }
            q.setCreatedBy(createdBy);
            this.save(q);
            imported++;
        }
        return imported;
    }

    /** 将 AI 返回的 options/correctAnswers 规范化为 JSON 字符串或 null */
    private String toJsonOrString(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            return (String) o;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.error("批量题目失败: {}", e.getMessage(), e);

            return o.toString();
        }
    }

    /** 健壮解析 AI 返回的题目列表（支持直接数组 / {questions:[...]} / 单个对象） */
    private List<Map<String, Object>> parseQuestionList(String json) throws Exception {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("批量题目失败: {}", e.getMessage(), e);

            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            if (m.get("questions") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nested = (List<Map<String, Object>>) m.get("questions");
                return nested;
            }
            List<Map<String, Object>> one = new ArrayList<>();
            one.add(m);
            return one;
        }
    }

    /**
     * 题目审核
     * 通过或拒绝题目，更新启用状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "questions", allEntries = true)
    public void reviewQuestion(Long questionId, boolean approved) {
        Question question = this.getById(questionId);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }
        question.setIsActive(approved);
        this.updateById(question);
    }

    /**
     * 复制题目（创建变式题）
     * 复制源题目所有字段，生成新ID的变式题
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "questions", allEntries = true)
    public Question copyQuestion(Long sourceQuestionId) {
        log.info("复制题目");

        Question source = this.getById(sourceQuestionId);
        if (source == null) {
            throw new BusinessException("源题目不存在");
        }

        Question copy = new Question();
        copy.setCourseId(source.getCourseId());
        copy.setType(source.getType());
        copy.setContent(source.getContent());
        copy.setOptions(source.getOptions());
        copy.setCorrectAnswers(source.getCorrectAnswers());
        copy.setDifficulty(source.getDifficulty());
        copy.setScore(source.getScore());
        copy.setKnowledgePoints(source.getKnowledgePoints());
        copy.setExplanation(source.getExplanation());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setUsageCount(0);
        copy.setCorrectRate(BigDecimal.ZERO);
        copy.setIsActive(true);

        this.save(copy);
        return copy;
    }

    /**
     * 智能搜索（全文检索+筛选）
     * 支持按课程、类型、难度、关键词多条件动态查询
     */
    @Override
    @Cacheable(value = "questions", key = "'search_' + #courseId + '_' + #type + '_' + #difficulty + '_' + #keyword + '_' + #page + '_' + #size")
    public Page<Question> searchQuestions(Long courseId, String type, String difficulty, String keyword, int page, int size) {
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId)
                .eq(type != null && !type.isEmpty(), Question::getType, type)
                .eq(difficulty != null && !difficulty.isEmpty(), Question::getDifficulty, difficulty)
                .like(keyword != null && !keyword.isEmpty(), Question::getContent, keyword)
                .orderByDesc(Question::getCreatedAt);

        return this.page(Page.of(page, size), queryWrapper);
    }

    /**
     * 获取题目详情（含选项解析后的结构化数据）
     * 返回题目基本信息及解析后的选项列表
     */
    @Override
    @Cacheable(value = "questions", key = "'detail_' + #questionId")
    public Map<String, Object> getQuestionDetail(Long questionId) {
        Question question = this.getById(questionId);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("id", question.getId());
        detail.put("type", question.getType());
        detail.put("content", question.getContent());
        detail.put("difficulty", question.getDifficulty());
        detail.put("score", question.getScore());
        detail.put("correctAnswers", question.getCorrectAnswers());
        detail.put("explanation", question.getExplanation());
        detail.put("usageCount", question.getUsageCount());
        detail.put("correctRate", question.getCorrectRate());
        detail.put("courseId", question.getCourseId());

        // 解析选项JSON
        try {
            if (question.getOptions() != null && !question.getOptions().isEmpty()) {
                List<Map<String, Object>> optionsList = objectMapper.readValue(question.getOptions(),
                        new TypeReference<List<Map<String, Object>>>() {});
                detail.put("options", optionsList);
            }
        } catch (Exception e) {
            log.error("查询题目失败: {}", e.getMessage(), e);

            // JSON解析失败时返回原始字符串
            detail.put("options", question.getOptions());
        }

        return detail;
    }

    /**
     * 随机抽取题目（用于组卷）
     * 修复大表 ORDER BY RAND() 性能陷阱：先仅查 id（索引覆盖扫描），在内存中随机洗牌后按 id 回查，
     * 时间复杂度由 O(N log N) 降至 O(N) 且避免全记录排序。
     */
    @Override
    public List<Question> randomSelect(Long courseId, String type, String difficulty, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Question> idWrapper = new LambdaQueryWrapper<Question>()
                .select(Question::getId)
                .eq(Question::getCourseId, courseId)
                .eq(type != null && !type.isEmpty(), Question::getType, type)
                .eq(difficulty != null && !difficulty.isEmpty(), Question::getDifficulty, difficulty)
                .eq(Question::getIsActive, true);
        List<Long> candidateIds = this.listObjs(idWrapper, id -> (Long) id);
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.shuffle(candidateIds);
        int limit = Math.min(count, candidateIds.size());
        List<Long> selectedIds = new ArrayList<>(candidateIds.subList(0, limit));
        if (selectedIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Question> selected = this.listByIds(selectedIds);
        // listByIds 不保证顺序，业务要求随机顺序则再次打乱
        Collections.shuffle(selected);
        return selected;
    }

    /**
     * 按知识点获取题目
     * 根据知识点ID查询关联的所有题目
     */
    @Override
    public List<Question> getByKnowledgePoint(Long knowledgePointId) {
        // 知识点存储为JSON/逗号分隔ID，使用LIKE模糊匹配
        String kpIdStr = knowledgePointId.toString();
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<Question>()
                .like(Question::getKnowledgePoints, kpIdStr)
                .eq(Question::getIsActive, true)
                .orderByDesc(Question::getCreatedAt);

        return this.list(queryWrapper);
    }

    /**
     * 更新正确率（批阅后回调）
     * 正确时: correctRate = (原正确数+1)/(usageCount+1)
     * 错误时: correctRate = 原正确数/(usageCount+1)
     * 同时 usageCount++
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    // 仅精准失效该题详情缓存：正确率随每次批阅高频更新，allEntries 会让搜索缓存完全失效；
    // 搜索列表中的 correctRate 允许 ≤10min（questions TTL）的短暂滞后
    @CacheEvict(value = "questions", key = "'detail_' + #questionId")
    public void updateCorrectRate(Long questionId, boolean isCorrect) {
        log.info("更新题目");

        Question question = this.getById(questionId);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }

        int usageCount = question.getUsageCount() != null ? question.getUsageCount() : 0;
        BigDecimal currentRate = question.getCorrectRate() != null ? question.getCorrectRate() : BigDecimal.ZERO;

        // 计算原有正确答题数
        double correctCount = currentRate.doubleValue() * usageCount;

        // 更新使用次数
        usageCount++;
        question.setUsageCount(usageCount);

        // 计算新的正确率
        if (isCorrect) {
            correctCount++;
        }
        BigDecimal newRate = BigDecimal.valueOf(correctCount / usageCount)
                .setScale(4, RoundingMode.HALF_UP);
        question.setCorrectRate(newRate);

        this.updateById(question);
    }

    /**
     * 获取题目使用统计
     * 返回使用次数、正确率等统计数据
     */
    @Override
    public Map<String, Object> getQuestionStats(Long questionId) {
        Question question = this.getById(questionId);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("questionId", question.getId());
        stats.put("usageCount", question.getUsageCount());
        stats.put("correctRate", question.getCorrectRate());
        stats.put("difficulty", question.getDifficulty());
        stats.put("type", question.getType());
        stats.put("score", question.getScore());
        stats.put("isActive", question.getIsActive());
        stats.put("createdAt", question.getCreatedAt());

        return stats;
    }

    /**
     * 获取易错题TOP N
     * 按正确率升序、使用次数降序排序取前N条
     */
    @Override
    public List<Question> getErrorProneQuestions(Long courseId, int limit) {
        // 钳制 limit 到 [1, 100]，避免非法参数导致 SQL 注入或全表扫描
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId)
                .eq(Question::getIsActive, true)
                .gt(Question::getUsageCount, 0)  // 至少被使用过
                .orderByAsc(Question::getCorrectRate)   // 正确率低的排前面
                .orderByDesc(Question::getUsageCount)   // 使用次数多的优先
                .last("LIMIT " + safeLimit);

        return this.list(queryWrapper);
    }
}
