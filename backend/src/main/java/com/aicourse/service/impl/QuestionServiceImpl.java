package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.JsonUtils;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.QuestionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.QuestionBankStatisticsVO;
import com.aicourse.vo.QuestionStatsVO;
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
import java.time.LocalDate;
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
    // 修复 CR-1：题库写入只清空 questionSearch（列表缓存），不再 allEntries 清空 questions，
    // 避免跨课程、跨题目的详情缓存（questionDetail）被无辜击穿；详情缓存由 updateCorrectRate 精确失效。
    @CacheEvict(value = "questionSearch", allEntries = true)
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
    // 修复 CR-1：题库写入只清空 questionSearch（列表缓存），不再 allEntries 清空 questions，
    // 避免跨课程、跨题目的详情缓存（questionDetail）被无辜击穿；详情缓存由 updateCorrectRate 精确失效。
    @CacheEvict(value = "questionSearch", allEntries = true)
    public Question generateByAI(Long courseId, String type, String difficulty, String knowledgePointNames) {
        // 调用AI服务生成题目JSON
        String generatedContent = aiService.generateQuestion(courseId, type, difficulty, 1);

        try {
            // 解析AI生成的题目内容。模型经常把 JSON 包进 ```json ``` 栅栏（DR-87 实测：
            // 真实 Key 下本端点 400，Jackson 在第 1 列撞到反引号），必须先过 extractJsonObject。
            String json = JsonUtils.extractJsonObject(generatedContent);
            Map<String, Object> questionData = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            Question question = new Question();
            question.setCourseId(courseId);
            question.setType(type);
            question.setDifficulty(difficulty);
            question.setContent(questionData.get("content") != null ? questionData.get("content").toString() : "");
            if (question.getContent().isBlank()) {
                // 模型没按点名的键位返回（真实 Key 下实测把题干放进 stem）时必须中止，
                // 否则落库一条空题干题目并返回 200——那是假成功（DR-89）。
                throw new BusinessException("AI 返回的题目缺少 content 字段，已中止落库以免产生空题干题目");
            }
            question.setOptions(questionData.get("options") != null ? objectMapper.writeValueAsString(questionData.get("options")) : null);
            question.setCorrectAnswers(toJsonColumnValue(questionData.get("correctAnswers")));
            question.setExplanation(questionData.get("explanation") != null ? questionData.get("explanation").toString() : "");
            question.setScore(questionData.get("score") != null ?
                    new BigDecimal(questionData.get("score").toString()) : new BigDecimal("5"));
            question.setKnowledgePoints(toJsonArrayValue(knowledgePointNames));
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
    // 修复 CR-1：题库写入只清空 questionSearch（列表缓存），不再 allEntries 清空 questions，
    // 避免跨课程、跨题目的详情缓存（questionDetail）被无辜击穿；详情缓存由 updateCorrectRate 精确失效。
    @CacheEvict(value = "questionSearch", allEntries = true)
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
            Object stem = qd.get("content");
            // 没有 content 的项一律不落库（DR-89）：空题干题目会在题库、组卷、错题本里
            // 一路以"看起来存在"的形态扩散，比一次失败难查得多。
            if (stem == null || stem.toString().isBlank()) {
                continue;
            }
            Question q = new Question();
            q.setCourseId(courseId);
            q.setType(type);
            q.setDifficulty(difficulty);
            q.setContent(stem.toString());
            q.setOptions(toJsonOrString(qd.get("options")));
            q.setCorrectAnswers(toJsonColumnValue(qd.get("correctAnswers")));
            q.setExplanation(qd.get("explanation") != null ? qd.get("explanation").toString() : "");
            q.setScore(qd.get("score") != null ? new BigDecimal(qd.get("score").toString()) : new BigDecimal("5"));
            q.setKnowledgePoints(toJsonArrayValue(knowledgePointNames));
            q.setUsageCount(0);
            q.setCorrectRate(BigDecimal.ZERO);
            q.setIsActive(true);
            q.setCreatedBy(createdBy);
            this.save(q);
            saved.add(q);
        }
        if (saved.isEmpty()) {
            throw new BusinessException("AI 批量生成未返回任何含 content 题干的题目，已中止落库");
        }
        return saved;
    }

    /**
     * 批量导入题目（跳过无效项，落库并设置默认值）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    // 修复 CR-1：题库写入只清空 questionSearch（列表缓存），不再 allEntries 清空 questions，
    // 避免跨课程、跨题目的详情缓存（questionDetail）被无辜击穿；详情缓存由 updateCorrectRate 精确失效。
    @CacheEvict(value = "questionSearch", allEntries = true)
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

    /**
     * 把 AI 返回的答案落成 {@code questions.correct_answers} 能接受的形态。
     * <p>该列是 {@code JSON NULL}（V1__init_schema.sql:505），只有合法 JSON 才写得进去：
     * 集合序列化成 JSON 数组，标量答案序列化成 JSON 字符串，缺失/空串落 null。
     * <p>刻意**不**把标量包成数组：{@code ExamSessionGradingService.normalizeAnswer:339-348}
     * 对 {@code [} 开头是原样返回、对带引号的字符串才脱引号，而单选题判分走
     * {@code studentAnswer.equalsIgnoreCase(correctAnswer)}——存成 {@code ["A"]} 会让
     * AI 生成的单选题永远判错。
     * <p>反面教材（DR-88 实测复现）：原实现用 {@code .toString()} 并回退空串，于是
     * {@code A} 与 {@code ""} 都不是合法 JSON，MySQL 报
     * {@code Invalid JSON text: "The document is empty."}，出题必然 400 并回滚整事务。
     */
    private String toJsonColumnValue(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            String s = ((String) o).trim();
            if (s.isEmpty()) {
                return null;
            }
            if (s.startsWith("[") || s.startsWith("{") || s.startsWith("\"")) {
                return s;
            }
            o = s;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            log.error("题目答案落 JSON 列失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 把「逗号/分号分隔的知识点名」落成 {@code questions.knowledge_points}（同为 JSON 列）能接受的数组形态。
     * <p>口径来源：列注释「JSON数组格式」（V1__init_schema.sql:508）、Excel 导入侧
     * {@code ExcelUtil.normalizeJsonArray}，以及读取侧
     * {@code WrongQuestionServiceImpl.extractKnowledgePointNames}（先按 JSON 数组解，失败才退回分隔符）。
     * 分隔符集合与 {@code ExamPaperServiceImpl:267} 的匹配逻辑保持一致，否则同一道题在组卷与错题本里
     * 会被切成不同的知识点。
     */
    private String toJsonArrayValue(String names) {
        if (names == null || names.isBlank()) {
            return null;
        }
        String trimmed = names.trim();
        if (trimmed.startsWith("[")) {
            return trimmed;
        }
        List<String> parts = Arrays.stream(trimmed.split("[,，;；]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (parts.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(parts);
        } catch (Exception e) {
            log.error("知识点落 JSON 列失败: {}", e.getMessage(), e);
            return null;
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
    // 修复 CR-1：题库写入只清空 questionSearch（列表缓存），不再 allEntries 清空 questions，
    // 避免跨课程、跨题目的详情缓存（questionDetail）被无辜击穿；详情缓存由 updateCorrectRate 精确失效。
    @CacheEvict(value = "questionSearch", allEntries = true)
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
    // 修复 CR-1：题库写入只清空 questionSearch（列表缓存），不再 allEntries 清空 questions，
    // 避免跨课程、跨题目的详情缓存（questionDetail）被无辜击穿；详情缓存由 updateCorrectRate 精确失效。
    @CacheEvict(value = "questionSearch", allEntries = true)
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
    // 修复 CR-1/CR-12：列表缓存独立为 questionSearch；key 中 null 参数归一为 ""，
    // 避免 (null) 与 ("") 生成不同 key 导致语义相同查询重复缓存。
    @Cacheable(value = "questionSearch", key = "'search_' + T(java.util.Objects).toString(#courseId,'') + '_' + T(java.util.Objects).toString(#type,'') + '_' + T(java.util.Objects).toString(#difficulty,'') + '_' + T(java.util.Objects).toString(#keyword,'') + '_' + #page + '_' + #size")
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
    // 修复 CR-1：详情缓存独立为 questionDetail（与列表缓存解耦，写入不击穿）。
    @Cacheable(value = "questionDetail", key = "'detail_' + #questionId")
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
    // 搜索列表中的 correctRate 允许 ≤10min（questionSearch TTL）的短暂滞后
    @CacheEvict(value = "questionDetail", key = "'detail_' + #questionId")
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
    public QuestionStatsVO getQuestionStats(Long questionId) {
        Question question = this.getById(questionId);
        if (question == null) {
            throw new BusinessException("题目不存在");
        }

        return new QuestionStatsVO(
                question.getId(), question.getUsageCount(), question.getCorrectRate(),
                question.getDifficulty(), question.getType(), question.getScore(),
                question.getIsActive(), question.getCreatedAt()
        );
    }

    /**
     * 题库总览统计：总题数（含无课程归属的历史题）与今日新增（本机时区当日 00:00 起）。
     */
    @Override
    public QuestionBankStatisticsVO getBankStatistics() {
        long total = this.count();
        long today = this.count(new LambdaQueryWrapper<Question>()
                .ge(Question::getCreatedAt, LocalDate.now().atStartOfDay()));
        return new QuestionBankStatisticsVO(total, today);
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
