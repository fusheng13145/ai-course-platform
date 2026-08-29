package com.aicourse.service.impl;


import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamPaperMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.QuestionService;
import com.aicourse.dto.AutoComposeDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;


/**
 * 考试试卷服务实现：相关业务逻辑入口，提供增删改查与编排能力。
 */
@Service
@Slf4j
public class ExamPaperServiceImpl extends ServiceImpl<ExamPaperMapper, ExamPaper> implements ExamPaperService {


    private final ExamQuestionService examQuestionService;
    private final QuestionService questionService;
    private final NotificationService notificationService;

    public ExamPaperServiceImpl(ExamQuestionService examQuestionService, QuestionService questionService,
                                NotificationService notificationService) {
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
        this.notificationService = notificationService;
    }

    /**
     * 获取试卷完整详情（含题目列表）
     */
    @Override
    @Cacheable(cacheNames = "examPapers", key = "#paperId")
    public Map<String, Object> getExamPaperDetail(Long paperId) {
        ExamPaper examPaper = this.getById(paperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }

        // 查询试卷关联的题目列表
        LambdaQueryWrapper<ExamQuestion> queryWrapper = new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, paperId)
                .orderByAsc(ExamQuestion::getOrderIndex);
        List<ExamQuestion> examQuestions = examQuestionService.list(queryWrapper);

        // 批量取回题目（避免循环内单查 N+1），按 ID 建索引
        List<Long> questionIds = examQuestions.stream()
                .map(ExamQuestion::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, Question> questionMap = questionIds.isEmpty() ? Collections.emptyMap()
                : questionService.listByIds(questionIds).stream()
                        .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));

        // 构建题目详情列表
        List<Map<String, Object>> questionList = new ArrayList<>();
        for (ExamQuestion eq : examQuestions) {
            Question question = questionMap.get(eq.getQuestionId());
            if (question == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", eq.getId());
            item.put("questionId", eq.getQuestionId());
            item.put("orderIndex", eq.getOrderIndex());
            item.put("points", eq.getPoints());
            item.put("type", question.getType());
            item.put("content", question.getContent());
            item.put("options", question.getOptions());
            item.put("difficulty", question.getDifficulty());
            questionList.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("paper", examPaper);
        result.put("questions", questionList);
        result.put("questionCount", questionList.size());
        return result;
    }

    /**
     * 发布试卷
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "examPapers", key = "#paperId")
    public void publishExamPaper(Long paperId) {
        ExamPaper examPaper = this.getById(paperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }
        if ("PUBLISHED".equals(examPaper.getStatus())) {
            throw new BusinessException("试卷已发布");
        }
        examPaper.setStatus("PUBLISHED");
        this.updateById(examPaper);

        // 修复 P1-1：考试发布后自动推送通知给课程选课学生
        try {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("courseId", examPaper.getCourseId());
            eventData.put("examId", examPaper.getId());
            eventData.put("examTitle", examPaper.getTitle());
            notificationService.onEvent("EXAM_PUBLISHED", eventData);
        } catch (Exception e) {
            log.warn("考试发布通知推送失败: examPaperId={}", examPaper.getId(), e);
        }
    }

    /**
     * 复制试卷
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamPaper copyExamPaper(Long sourcePaperId, String newTitle) {
        log.info("复制考试试卷");

        ExamPaper sourcePaper = this.getById(sourcePaperId);
        if (sourcePaper == null) {
            throw new BusinessException("源试卷不存在");
        }

        // 创建新试卷
        ExamPaper newPaper = new ExamPaper();
        newPaper.setTitle(newTitle != null ? newTitle : sourcePaper.getTitle() + "(副本)");
        newPaper.setDescription(sourcePaper.getDescription());
        newPaper.setCourseId(sourcePaper.getCourseId());
        newPaper.setCreatorId(sourcePaper.getCreatorId());
        newPaper.setStatus("DRAFT");
        newPaper.setInstructions(sourcePaper.getInstructions());
        newPaper.setStartTime(sourcePaper.getStartTime());
        newPaper.setEndTime(sourcePaper.getEndTime());
        newPaper.setTotalScore(sourcePaper.getTotalScore());
        newPaper.setDuration(sourcePaper.getDuration());
        newPaper.setShuffleQuestions(sourcePaper.getShuffleQuestions());
        newPaper.setShuffleOptions(sourcePaper.getShuffleOptions());
        newPaper.setShowScore(sourcePaper.getShowScore());
        newPaper.setShowCorrectAnswer(sourcePaper.getShowCorrectAnswer());
        newPaper.setMaxAttempts(sourcePaper.getMaxAttempts());
        newPaper.setTimeLimit(sourcePaper.getTimeLimit());
        newPaper.setAllowReview(sourcePaper.getAllowReview());
        newPaper.setGenerationRules(sourcePaper.getGenerationRules());
        newPaper.setIsActive(true);
        this.save(newPaper);

        // 复制试卷-题目关联关系
        LambdaQueryWrapper<ExamQuestion> queryWrapper = new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, sourcePaperId)
                .orderByAsc(ExamQuestion::getOrderIndex);
        List<ExamQuestion> sourceQuestions = examQuestionService.list(queryWrapper);

        for (ExamQuestion sq : sourceQuestions) {
            ExamQuestion newEq = new ExamQuestion();
            newEq.setExamPaperId(newPaper.getId());
            newEq.setQuestionId(sq.getQuestionId());
            newEq.setOrderIndex(sq.getOrderIndex());
            newEq.setPoints(sq.getPoints());
            examQuestionService.save(newEq);
        }

        // 更新新试卷的题目数量
        newPaper.setQuestionCount(sourceQuestions.size());
        this.updateById(newPaper);

        return newPaper;
    }

    /**
     * 获取试卷统计（题型分布、分值分布）
     */
    @Override
    public Map<String, Object> getPaperStatistics(Long paperId) {
        ExamPaper examPaper = this.getById(paperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }

        // 查询所有关联题目
        LambdaQueryWrapper<ExamQuestion> queryWrapper = new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, paperId);
        List<ExamQuestion> examQuestions = examQuestionService.list(queryWrapper);

        // 批量取回题目（避免循环内单查 N+1），按 ID 建索引
        List<Long> questionIds = examQuestions.stream()
                .map(ExamQuestion::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, Question> questionMap = questionIds.isEmpty() ? Collections.emptyMap()
                : questionService.listByIds(questionIds).stream()
                        .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));

        // 统计题型分布
        Map<String, Integer> typeDistribution = new HashMap<>();
        // 统计分值分布
        Map<String, Integer> scoreDistribution = new HashMap<>();
        BigDecimal totalPoints = BigDecimal.ZERO;
        int questionCount = examQuestions.size();

        for (ExamQuestion eq : examQuestions) {
            Question question = questionMap.get(eq.getQuestionId());
            if (question == null) {
                continue;
            }

            // 题型统计
            String type = question.getType();
            typeDistribution.merge(type, 1, Integer::sum);

            // 分值统计（按分数段）
            BigDecimal points = eq.getPoints();
            totalPoints = totalPoints.add(points);
            String scoreRange;
            if (points.compareTo(new BigDecimal("5")) <= 0) {
                scoreRange = "1-5分";
            } else if (points.compareTo(new BigDecimal("10")) <= 0) {
                scoreRange = "6-10分";
            } else if (points.compareTo(new BigDecimal("20")) <= 0) {
                scoreRange = "11-20分";
            } else {
                scoreRange = "20分以上";
            }
            scoreDistribution.merge(scoreRange, 1, Integer::sum);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("paperInfo", Map.of(
                "id", examPaper.getId(),
                "title", examPaper.getTitle(),
                "status", examPaper.getStatus()
        ));
        result.put("questionCount", questionCount);
        result.put("totalPoints", totalPoints);
        result.put("typeDistribution", typeDistribution);
        result.put("scoreDistribution", scoreDistribution);
        return result;
    }

    /**
     * 自动组卷：按题型数量和难度比例从题库随机抽取题目生成试卷
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamPaper autoComposeExamPaper(AutoComposeDTO dto, Long creatorId) {
        if (dto.getCourseId() == null) {
            throw new BusinessException("请指定课程");
        }
        if (dto.getTypeRules() == null || dto.getTypeRules().isEmpty()) {
            throw new BusinessException("请设置题型规则");
        }

        LambdaQueryWrapper<Question> questionWrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getCourseId, dto.getCourseId())
                .eq(Question::getIsActive, true);
        List<Question> allQuestions = questionService.list(questionWrapper);

        // 知识点覆盖增强：若指定了知识点，优先选择标记为这些知识点的题目
        // 不足时由同课程其他题目补充，确保试卷总量不变
        if (dto.getKnowledgePointNames() != null && !dto.getKnowledgePointNames().isEmpty() && !allQuestions.isEmpty()) {
            Set<String> targetKP = dto.getKnowledgePointNames().stream()
                    .map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());
            List<Question> kpQuestions = new ArrayList<>();
            List<Question> otherQuestions = new ArrayList<>();
            for (Question q : allQuestions) {
                if (q.getKnowledgePoints() != null && !q.getKnowledgePoints().isBlank()) {
                    boolean matched = Arrays.stream(q.getKnowledgePoints().split("[,，;；]"))
                            .map(String::trim).map(String::toLowerCase)
                            .anyMatch(targetKP::contains);
                    if (matched) kpQuestions.add(q);
                    else otherQuestions.add(q);
                } else {
                    otherQuestions.add(q);
                }
            }
            // 知识点相关题目优先，其余补充（保持总题量不减）
            List<Question> prioritized = new ArrayList<>(kpQuestions);
            prioritized.addAll(otherQuestions);
            allQuestions = prioritized;
            log.info("自动组卷知识点感知: courseId={}, targetKP={}, kpMatched={}, total={}",
                    dto.getCourseId(), targetKP.size(), kpQuestions.size(), allQuestions.size());
        }

        if (allQuestions.isEmpty()) {
            throw new BusinessException("该课程暂无可用题目，请先录入题库");
        }

        Map<String, List<Question>> questionsByType = allQuestions.stream()
                .collect(Collectors.groupingBy(Question::getType));

        List<Map<String, Object>> selectedQuestions = new ArrayList<>();
        Random random = new Random();
        int totalPicked = 0;

        for (Map.Entry<String, Integer> typeRule : dto.getTypeRules().entrySet()) {
            String type = typeRule.getKey();
            int count = typeRule.getValue();
            List<Question> candidates = questionsByType.getOrDefault(type, new ArrayList<>());
            if (candidates.isEmpty()) continue;

            if (dto.getDifficultyRatio() != null && !dto.getDifficultyRatio().isEmpty()) {
                Map<String, List<Question>> diffGroup = candidates.stream()
                        .collect(Collectors.groupingBy(q -> q.getDifficulty() != null ? q.getDifficulty() : "MEDIUM"));
                int totalRatio = dto.getDifficultyRatio().values().stream().mapToInt(Integer::intValue).sum();
                for (Map.Entry<String, Integer> diffEntry : dto.getDifficultyRatio().entrySet()) {
                    String diff = diffEntry.getKey();
                    int ratio = diffEntry.getValue();
                    int diffCount = totalRatio > 0 ? (int) Math.round((double) count * ratio / totalRatio) : 0;
                    List<Question> diffCandidates = new ArrayList<>(diffGroup.getOrDefault(diff, new ArrayList<>()));
                    Collections.shuffle(diffCandidates, random);
                    for (int i = 0; i < Math.min(diffCount, diffCandidates.size()); i++) {
                        Question q = diffCandidates.get(i);
                        BigDecimal points = dto.getScoreRules() != null && dto.getScoreRules().containsKey(type)
                                ? dto.getScoreRules().get(type)
                                : (q.getScore() != null ? q.getScore() : BigDecimal.valueOf(5));
                        Map<String, Object> item = new HashMap<>();
                        item.put("questionId", q.getId());
                        item.put("points", points);
                        item.put("type", q.getType());
                        selectedQuestions.add(item);
                        totalPicked++;
                    }
                }
            } else {
                List<Question> shuffled = new ArrayList<>(candidates);
                Collections.shuffle(shuffled, random);
                for (int i = 0; i < Math.min(count, shuffled.size()); i++) {
                    Question q = shuffled.get(i);
                    BigDecimal points = dto.getScoreRules() != null && dto.getScoreRules().containsKey(type)
                            ? dto.getScoreRules().get(type)
                            : (q.getScore() != null ? q.getScore() : BigDecimal.valueOf(5));
                    Map<String, Object> item = new HashMap<>();
                    item.put("questionId", q.getId());
                    item.put("points", points);
                    item.put("type", q.getType());
                    selectedQuestions.add(item);
                    totalPicked++;
                }
            }
        }

        if (selectedQuestions.isEmpty()) {
            throw new BusinessException("没有匹配到符合条件的题目，请调整组卷规则");
        }

        String paperTitle = dto.getTitle() != null ? dto.getTitle() : "自动组卷-" + System.currentTimeMillis();

        ExamPaper examPaper = new ExamPaper();
        examPaper.setTitle(paperTitle);
        examPaper.setDescription(dto.getDescription() != null ? dto.getDescription() : "由系统自动生成");
        examPaper.setCourseId(dto.getCourseId());
        examPaper.setCreatorId(creatorId);
        examPaper.setStatus(dto.getStatus() != null ? dto.getStatus() : "DRAFT");
        examPaper.setTotalScore(dto.getTotalScore());
        examPaper.setDuration(dto.getDuration());
        examPaper.setQuestionCount(selectedQuestions.size());
        examPaper.setShuffleQuestions(dto.getShuffleQuestions() != null ? dto.getShuffleQuestions() : true);
        examPaper.setShuffleOptions(dto.getShuffleOptions() != null ? dto.getShuffleOptions() : true);
        examPaper.setShowScore(dto.getShowScore() != null ? dto.getShowScore() : true);
        examPaper.setShowCorrectAnswer(dto.getShowCorrectAnswer() != null ? dto.getShowCorrectAnswer() : false);
        examPaper.setMaxAttempts(dto.getMaxAttempts() != null ? dto.getMaxAttempts() : 1);
        examPaper.setTimeLimit(dto.getTimeLimit() != null ? dto.getTimeLimit() : dto.getDuration());
        examPaper.setAllowReview(dto.getAllowReview() != null ? dto.getAllowReview() : true);
        examPaper.setIsActive(true);
        this.save(examPaper);

        int orderIndex = 0;
        for (Map<String, Object> item : selectedQuestions) {
            ExamQuestion eq = new ExamQuestion();
            eq.setExamPaperId(examPaper.getId());
            eq.setQuestionId((Long) item.get("questionId"));
            eq.setOrderIndex(orderIndex++);
            eq.setPoints((BigDecimal) item.get("points"));
            examQuestionService.save(eq);
        }

        return examPaper;
    }

}
