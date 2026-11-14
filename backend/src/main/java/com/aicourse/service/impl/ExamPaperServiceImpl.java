package com.aicourse.service.impl;


import com.aicourse.constant.ExamSessionStatus;
import com.aicourse.common.ApiCode;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamPaperStatus;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamPaperMapper;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.QuestionService;
import com.aicourse.vo.ExamPaperDetailVO;
import com.aicourse.vo.ExamPaperQuestionItemVO;
import com.aicourse.vo.ExamPaperStatsPaperVO;
import com.aicourse.vo.ExamPaperStatsVO;
import com.aicourse.dto.AutoComposeDTO;
import com.aicourse.dto.ExamQuestionBatchDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ExamSessionMapper examSessionMapper;

    public ExamPaperServiceImpl(ExamQuestionService examQuestionService, QuestionService questionService,
                                NotificationService notificationService, ExamSessionMapper examSessionMapper) {
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
        this.notificationService = notificationService;
        this.examSessionMapper = examSessionMapper;
    }

    /**
     * 更新试卷基础信息（PUT 更新路径专用）。
     *
     * <p><b>修复 A-02 #5</b>：状态字段白名单——status / isActive / creatorId 一律置 null 剔除出
     * UPDATE SET（MyBatis-Plus 默认按非空字段更新），请求体携带 {@code status:"PUBLISHED"}
     * 无法再绕过审核直发，审核流转只能走 submitExamPaperForReview / approve / reject 专用端点。</p>
     *
     * <p><b>修复 A-02 #8</b>：显式传入 totalScore 时校验其与题目分值合计一致；卷面尚未配置题目
     * （create 后先建卷后配题的既有流程）时跳过校验，总分以教师申报值为准。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "examPapers", key = "#paperId")
    public ExamPaper updateExamPaper(Long paperId, ExamPaper patch) {
        ExamPaper existing = this.getById(paperId);
        if (existing == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "试卷不存在：" + paperId);
        }
        // 状态/归属字段不透传：置 null 后由 updateById 的非空更新策略从 SET 子句剔除
        patch.setId(paperId);
        patch.setStatus(null);
        patch.setIsActive(null);
        patch.setCreatorId(null);
        if (patch.getTotalScore() != null) {
            assertTotalScoreMatchesQuestions(paperId, patch.getTotalScore());
        }
        this.updateById(patch);
        return this.getById(paperId);
    }

    /**
     * 卷面总分一致性校验：已配置题目的试卷，显式传入的 totalScore 必须等于各题分值合计，
     * 否则人工阅卷重算百分比时以 paper.totalScore 为分母，错位总分导致成绩失真。
     */
    private void assertTotalScoreMatchesQuestions(Long paperId, BigDecimal declaredTotalScore) {
        List<ExamQuestion> questions = examQuestionService.list(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, paperId));
        if (questions.isEmpty()) {
            return;
        }
        BigDecimal questionSum = BigDecimal.ZERO;
        for (ExamQuestion eq : questions) {
            if (eq.getPoints() != null) {
                questionSum = questionSum.add(eq.getPoints());
            }
        }
        if (questionSum.compareTo(declaredTotalScore) != 0) {
            throw new BusinessException(ApiCode.BAD_REQUEST,
                    "试卷总分与题目分值合计不一致：总分=" + declaredTotalScore.stripTrailingZeros().toPlainString()
                            + "，题目合计=" + questionSum.stripTrailingZeros().toPlainString());
        }
    }

    /**
     * 获取试卷完整详情（含题目列表）
     */
    @Override
    @Cacheable(cacheNames = "examPapers", key = "#paperId")
    public ExamPaperDetailVO getExamPaperDetail(Long paperId) {
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

        // 构建题目详情列表（结构化 VO，消除嵌套 Map）
        List<ExamPaperQuestionItemVO> questionList = new ArrayList<>();
        for (ExamQuestion eq : examQuestions) {
            Question question = questionMap.get(eq.getQuestionId());
            if (question == null) {
                continue;
            }
            questionList.add(new ExamPaperQuestionItemVO(eq, question));
        }

        return new ExamPaperDetailVO(examPaper, questionList, questionList.size());
    }

    /**
     * 发布试卷（管理员直接发布）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "examPapers", key = "#paperId")
    public void publishExamPaper(Long paperId) {
        ExamPaper examPaper = this.getById(paperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }
        if (ExamPaperStatus.PUBLISHED.name().equals(examPaper.getStatus())) {
            throw new BusinessException("试卷已发布");
        }
        if (ExamPaperStatus.PENDING_REVIEW.name().equals(examPaper.getStatus())) {
            throw new BusinessException("试卷待审核，请走审核通过");
        }
        markPublished(examPaper);
    }

    /**
     * 提交审核（教师）：学生仍不可见，管理员通过后才发布。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "examPapers", key = "#paperId")
    public void submitExamPaperForReview(Long paperId) {
        ExamPaper examPaper = this.getById(paperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }
        String status = examPaper.getStatus();
        if (ExamPaperStatus.PUBLISHED.name().equals(status)) {
            throw new BusinessException("试卷已发布");
        }
        if (ExamPaperStatus.PENDING_REVIEW.name().equals(status)) {
            throw new BusinessException("试卷已在审核中");
        }
        examPaper.setStatus(ExamPaperStatus.PENDING_REVIEW.name());
        this.updateById(examPaper);
    }

    /**
     * 审核通过（管理员）：仅待审核态可操作。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "examPapers", key = "#paperId")
    public void approveExamPaper(Long paperId) {
        ExamPaper examPaper = this.getById(paperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }
        if (!ExamPaperStatus.PENDING_REVIEW.name().equals(examPaper.getStatus())) {
            throw new BusinessException("试卷不在待审核状态");
        }
        markPublished(examPaper);
    }

    /**
     * 审核驳回（管理员）：仅待审核态可操作；原因仅进通知与日志，不落库。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "examPapers", key = "#paperId")
    public void rejectExamPaper(Long paperId, String reason) {
        ExamPaper examPaper = this.getById(paperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }
        if (!ExamPaperStatus.PENDING_REVIEW.name().equals(examPaper.getStatus())) {
            throw new BusinessException("试卷不在待审核状态");
        }
        examPaper.setStatus(ExamPaperStatus.REJECTED.name());
        this.updateById(examPaper);
        log.info("试卷审核驳回: examPaperId={}, reason={}", paperId, reason);
        String trimmed = reason == null ? "" : reason.trim();
        try {
            if (examPaper.getCreatorId() != null) {
                notificationService.sendNotification(examPaper.getCreatorId(), "试卷审核未通过",
                        "试卷「" + examPaper.getTitle() + "」已被驳回" + (trimmed.isEmpty() ? "" : "：" + trimmed),
                        "EXAM", "EXAM_REJECTED", "MEDIUM", "/teacher/exams", null, paperId, "exam_paper");
            }
        } catch (Exception e) {
            log.warn("试卷驳回通知发送失败: examPaperId={}", paperId, e);
        }
    }

    /**
     * 置为已发布并推送选课学生通知（发布与审核通过共用）。
     */
    private void markPublished(ExamPaper examPaper) {
        examPaper.setStatus(ExamPaperStatus.PUBLISHED.name());
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
        newPaper.setStatus(ExamPaperStatus.DRAFT.name());
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
    public ExamPaperStatsVO getPaperStatistics(Long paperId) {
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

        ExamPaperStatsVO stats = new ExamPaperStatsVO(
                new ExamPaperStatsPaperVO(examPaper.getId(), examPaper.getTitle(), examPaper.getStatus()),
                questionCount, totalPoints, typeDistribution, scoreDistribution);
        fillScoreAggregates(paperId, stats);
        return stats;
    }

    /**
     * 填充成绩聚合（均分/及格率）。口径：只统计已阅卷且 percentage 非空的会话，按百分制；
     * 无样本时 averageScore/passRate 保持 null，前端显示「—」而不是 0。
     */
    private void fillScoreAggregates(Long paperId, ExamPaperStatsVO stats) {
        List<ExamSession> graded = examSessionMapper.selectList(new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, paperId)
                .eq(ExamSession::getStatus, ExamSessionStatus.GRADED)
                .isNotNull(ExamSession::getPercentage));
        int count = graded.size();
        stats.setSubmittedCount(count);
        stats.setPassLine(60);
        if (count == 0) {
            stats.setAverageScore(null);
            stats.setPassRate(null);
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        long passed = 0;
        for (ExamSession session : graded) {
            BigDecimal percentage = session.getPercentage();
            sum = sum.add(percentage);
            if (percentage.compareTo(new BigDecimal("60")) >= 0) {
                passed++;
            }
        }
        stats.setAverageScore(sum.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP));
        stats.setPassRate(BigDecimal.valueOf(passed * 100L)
                .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP));
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

        // 修复（2026-09-14）：组卷后必须回填卷面总分——原实现直接透传 dto.totalScore，
        // 未传该字段的调用方会得到 total_score=NULL 的试卷（前端试卷列表/统计缺总分，
        // 人工阅卷重算百分比也会因除数为空而跳过）。此处以「各题分值合计」兜底。
        BigDecimal computedTotal = BigDecimal.ZERO;
        for (Map<String, Object> item : selectedQuestions) {
            BigDecimal points = (BigDecimal) item.get("points");
            if (points != null) {
                computedTotal = computedTotal.add(points);
            }
        }
        // 修复 A-02 #8：dto.totalScore 显式传入时必须与题目分值合计一致，
        // 否则落库总分与卷面题目脱节，人工阅卷百分比随之错算——拒绝而非静默覆盖。
        if (dto.getTotalScore() != null && dto.getTotalScore().compareTo(computedTotal) != 0) {
            throw new BusinessException(ApiCode.BAD_REQUEST,
                    "总分与题目分值合计不一致：传入=" + dto.getTotalScore().stripTrailingZeros().toPlainString()
                            + "，题目合计=" + computedTotal.stripTrailingZeros().toPlainString());
        }
        BigDecimal paperTotalScore = computedTotal;

        ExamPaper examPaper = new ExamPaper();
        examPaper.setTitle(paperTitle);
        examPaper.setDescription(dto.getDescription() != null ? dto.getDescription() : "由系统自动生成");
        examPaper.setCourseId(dto.getCourseId());
        examPaper.setCreatorId(creatorId);
        examPaper.setStatus(dto.getStatus() != null ? dto.getStatus() : ExamPaperStatus.DRAFT.name());
        examPaper.setTotalScore(paperTotalScore);
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

    /**
     * 批量替换试卷题目（手动选题保存通道）。
     *
     * <p>参照 {@link #autoComposeExamPaper} 的题目写入逻辑：清空旧关联行 → 按清单写入，
     * points 缺省回落题库题目分值（再缺省为 5，与自动组卷兜底一致），orderIndex 缺省按清单顺序。
     * 写入后回填卷面 questionCount / totalScore，保证列表与统计口径一致。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "examPapers", key = "#paperId")
    public List<ExamQuestion> replaceExamQuestions(Long paperId,
            List<ExamQuestionBatchDTO.ExamQuestionItem> questions) {
        ExamPaper examPaper = this.getById(paperId);
        if (examPaper == null) {
            throw new BusinessException("试卷不存在");
        }
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException("题目清单不能为空");
        }
        // 修复 A-02 #7：状态门禁——仅 DRAFT/REJECTED 允许替换题目。
        // PUBLISHED 试卷可能已有进行中的考试会话（startExam 仅放行 PUBLISHED），
        // 换题改分会篡改在考学生的题目与判分口径；PENDING_REVIEW 冻结待审内容，同样禁止改题。
        String paperStatus = examPaper.getStatus();
        if (!ExamPaperStatus.DRAFT.name().equals(paperStatus) && !ExamPaperStatus.REJECTED.name().equals(paperStatus)) {
            throw new BusinessException("试卷当前状态（" + paperStatus + "）不允许替换题目，仅草稿/已驳回试卷可编辑");
        }

        // 题目合法性校验：题目必须存在，且属于该试卷课程题库（防跨课程拼卷）
        List<Long> questionIds = questions.stream()
                .map(ExamQuestionBatchDTO.ExamQuestionItem::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (questionIds.size() != questions.size()) {
            throw new BusinessException("题目ID不能为空");
        }
        Map<Long, Question> questionById = questionService.listByIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        for (Long questionId : questionIds) {
            Question question = questionById.get(questionId);
            if (question == null) {
                throw new BusinessException("部分题目不存在，请刷新后重试");
            }
            if (!Objects.equals(question.getCourseId(), examPaper.getCourseId())) {
                throw new BusinessException("存在不属于该课程题库的题目，请重新选择");
            }
        }

        // 批量替换：先清空旧关联行，再写入新清单
        LambdaQueryWrapper<ExamQuestion> removeWrapper = new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, paperId);
        examQuestionService.remove(removeWrapper);

        List<ExamQuestion> rows = new ArrayList<>(questions.size());
        BigDecimal totalScore = BigDecimal.ZERO;
        for (int i = 0; i < questions.size(); i++) {
            ExamQuestionBatchDTO.ExamQuestionItem item = questions.get(i);
            Question question = questionById.get(item.getQuestionId());
            BigDecimal points = item.getPoints() != null ? item.getPoints()
                    : (question.getScore() != null ? question.getScore() : BigDecimal.valueOf(5));
            ExamQuestion eq = new ExamQuestion();
            eq.setExamPaperId(paperId);
            eq.setQuestionId(item.getQuestionId());
            eq.setOrderIndex(item.getOrderIndex() != null ? item.getOrderIndex() : i);
            eq.setPoints(points);
            rows.add(eq);
            totalScore = totalScore.add(points);
        }
        examQuestionService.saveBatch(rows);

        // 回填卷面题目数与总分（与 autoComposeExamPaper 落库口径一致）
        examPaper.setQuestionCount(rows.size());
        examPaper.setTotalScore(totalScore);
        this.updateById(examPaper);

        log.info("批量替换试卷题目: paperId={}, count={}, totalScore={}", paperId, rows.size(), totalScore);
        return rows;
    }

    // ===================== 缓存一致性（2026-09-14 修复） =====================

    /**
     * 试卷更新后失效详情缓存。
     *
     * <p>缺陷背景（与 courses 缓存同类）：{@link #getExamPaperDetail(Long)} 标注
     * {@code @Cacheable(cacheNames = "examPapers", key = "#paperId")}，
     * 但修改/删除试卷走的是 {@code IService.updateById/removeById}，**全无失效点**，
     * 导致「改完试卷（改时限/改分/改状态/改标题）后，详情接口在缓存 TTL 内仍返回旧内容」。
     * 这里把 evict 挂到同一写路径上，使读写缓存成对出现。</p>
     */
    @Override
    @CacheEvict(cacheNames = "examPapers", key = "#entity.id")
    public boolean updateById(ExamPaper entity) {
        return super.updateById(entity);
    }

    /** 试卷删除后失效详情缓存（否则已删除试卷在 TTL 内仍可命中详情）。 */
    @Override
    @CacheEvict(cacheNames = "examPapers", key = "#id")
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }

}
