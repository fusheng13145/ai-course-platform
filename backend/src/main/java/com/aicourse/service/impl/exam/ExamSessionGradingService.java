package com.aicourse.service.impl.exam;

import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 考试会话阅卷与成绩计算协作者（委托模式）。
 * 负责：自动阅卷（客观题）/ 人工阅卷 / 总分计算 / 错题本记录。
 * 由 {@code ExamSessionServiceImpl} 门面委托调用，本类只持有自己需要的依赖。
 */
@Component
public class ExamSessionGradingService {

    private static final Logger log = LoggerFactory.getLogger(ExamSessionGradingService.class);

    private final ExamQuestionService examQuestionService;
    private final QuestionService questionService;
    private final WrongQuestionService wrongQuestionService;
    private final ExamPaperService examPaperService;
    private final ObjectMapper objectMapper;
    private final ExamSessionMapper examSessionMapper;

    public ExamSessionGradingService(ExamQuestionService examQuestionService,
                                     QuestionService questionService,
                                     WrongQuestionService wrongQuestionService,
                                     ExamPaperService examPaperService,
                                     ObjectMapper objectMapper,
                                     ExamSessionMapper examSessionMapper) {
        this.examQuestionService = examQuestionService;
        this.questionService = questionService;
        this.wrongQuestionService = wrongQuestionService;
        this.examPaperService = examPaperService;
        this.objectMapper = objectMapper;
        this.examSessionMapper = examSessionMapper;
    }

    /**
     * 自动阅卷（客观题：单选/多选/判断）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> autoGrade(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (!"SUBMITTED".equals(session.getStatus()) && !"FORCE_SUBMITTED".equals(session.getStatus())
                && !"TIMEOUT".equals(session.getStatus())) {
            throw new BusinessException("试卷尚未提交，无法进行自动阅卷");
        }

        // 解析学生答案
        Map<String, Object> studentAnswers = parseJsonToMap(session.getAnswers());
        if (studentAnswers == null || studentAnswers.isEmpty()) {
            throw new BusinessException("未找到学生作答记录");
        }

        // 查询试卷关联的所有题目
        List<ExamQuestion> examQuestions = examQuestionService.list(
                new LambdaQueryWrapper<ExamQuestion>()
                        .eq(ExamQuestion::getExamPaperId, session.getExamPaperId())
                        .orderByAsc(ExamQuestion::getOrderIndex)
        );

        // 每题得分记录
        Map<Long, BigDecimal> questionScoreMap = new LinkedHashMap<>();
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal fullScore = BigDecimal.ZERO;
        int correctCount = 0;
        int wrongCount = 0;
        int pendingReviewCount = 0;
        List<Long> wrongQuestionIds = new ArrayList<>();

        // 批量取出题目，避免逐题 questionService.getById 的 N+1（一次 listByIds 替代 N 次 getById）
        List<Long> questionIds = new ArrayList<>();
        for (ExamQuestion eq : examQuestions) questionIds.add(eq.getQuestionId());
        Map<Long, Question> questionMap = new HashMap<>();
        List<Question> gradedQuestions = questionService.listByIds(questionIds);
        if (gradedQuestions != null) {
            for (Question q : gradedQuestions) questionMap.put(q.getId(), q);
        }

        for (ExamQuestion eq : examQuestions) {
            Question question = questionMap.get(eq.getQuestionId());
            if (question == null) {
                continue;
            }

            BigDecimal points = eq.getPoints() != null ? eq.getPoints() : BigDecimal.ZERO;
            fullScore = fullScore.add(points);

            String questionKey = String.valueOf(eq.getQuestionId());
            String studentAnswer = studentAnswers.get(questionKey) != null ?
                    studentAnswers.get(questionKey).toString().trim() : "";
            String correctAnswer = question.getCorrectAnswers();

            BigDecimal score = BigDecimal.ZERO;
            boolean isCorrect = false;

            switch (question.getType()) {
                case "SINGLE_CHOICE":
                case "SINGLE":
                    // 单选题：完全匹配得满分
                    isCorrect = studentAnswer.equalsIgnoreCase(correctAnswer);
                    score = isCorrect ? points : BigDecimal.ZERO;
                    break;

                case "MULTIPLE_CHOICE":
                case "MULTIPLE":
                    // 多选题：完全匹配得满分，部分匹配得一半分
                    if (correctAnswer != null && !correctAnswer.isEmpty()) {
                        Set<String> correctSet = parseArrayToStringSet(correctAnswer);
                        Set<String> answerSet = parseArrayToStringSet(studentAnswer);
                        if (answerSet.equals(correctSet)) {
                            // 完全匹配
                            isCorrect = true;
                            score = points;
                        } else {
                            // 部分匹配：计算交集比例
                            Set<String> intersection = new HashSet<>(answerSet);
                            intersection.retainAll(correctSet);
                            if (!intersection.isEmpty()) {
                                double ratio = (double) intersection.size() / correctSet.size();
                                score = points.multiply(BigDecimal.valueOf(ratio * 0.5))
                                        .setScale(2, RoundingMode.HALF_UP);
                            } else {
                                score = BigDecimal.ZERO;
                            }
                        }
                    }
                    break;

                case "TRUE_FALSE":
                case "JUDGE":
                    // 判断题：忽略大小写比较
                    isCorrect = studentAnswer.equalsIgnoreCase(correctAnswer);
                    score = isCorrect ? points : BigDecimal.ZERO;
                    break;

                case "SHORT_ANSWER":
                case "ESSAY":
                case "FILL_BLANK":
                case "FILL":
                    // 主观题：不自动评分，标记为待人工批阅
                    pendingReviewCount++;
                    score = BigDecimal.valueOf(-1); // -1表示待人工评分
                    break;

                default:
                    // 其他题型暂不自动评分
                    pendingReviewCount++;
                    score = BigDecimal.valueOf(-1);
                    break;
            }

            questionScoreMap.put(eq.getQuestionId(), score);
            totalScore = totalScore.add(score.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : score);

            if (isCorrect) {
                correctCount++;
            } else if (score.compareTo(BigDecimal.ZERO) > 0) {
                // 部分正确也算错误
                wrongCount++;
                wrongQuestionIds.add(question.getId());
            } else if (score.equals(BigDecimal.ZERO) && !studentAnswer.isEmpty()) {
                wrongCount++;
                wrongQuestionIds.add(question.getId());
            }
        }

        // 计算百分比
        BigDecimal percentage = BigDecimal.ZERO;
        if (fullScore.compareTo(BigDecimal.ZERO) > 0) {
            percentage = totalScore.divide(fullScore, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // 将每题得分写入JSON
        String questionScoresJson = scoresMapToJson(questionScoreMap);
        session.setQuestionScores(questionScoresJson);
        session.setTotalScore(totalScore);
        session.setPercentage(percentage);
        session.setStatus("GRADED");
        examSessionMapper.updateById(session);

        // 记录错题到错题本（可选）
        for (Long qId : wrongQuestionIds) {
            String wa = studentAnswers.get(String.valueOf(qId)) != null
                    ? studentAnswers.get(String.valueOf(qId)).toString() : null;
            saveWrongQuestion(session.getStudentId(), qId, session.getExamPaperId(), wa, "EXAM");
        }

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", session.getId());
        result.put("totalScore", totalScore);
        result.put("fullScore", fullScore);
        result.put("percentage", percentage);
        result.put("correctCount", correctCount);
        result.put("wrongCount", wrongCount);
        result.put("pendingReviewCount", pendingReviewCount);
        result.put("questionScores", questionScoreMap);
        result.put("status", "GRADED");

        return result;
    }

    /**
     * 人工阅卷（主观题）
     */
    @Transactional(rollbackFor = Exception.class)
    public void manualGrade(Long sessionId, Long questionId, BigDecimal score, String comment) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (!"GRADED".equals(session.getStatus())) {
            throw new BusinessException("请先完成自动阅卷");
        }

        // 解析现有的questionScores
        Map<Long, BigDecimal> currentScores = parseScoresJson(session.getQuestionScores());
        if (currentScores == null) {
            currentScores = new HashMap<>();
        }

        // 更新该题分数
        currentScores.put(questionId, score);
        String updatedScoresJson = scoresMapToJson(currentScores);
        session.setQuestionScores(updatedScoresJson);

        // 重新计算总分
        BigDecimal newTotalScore = calculateTotalScoreFromScores(currentScores);
        session.setTotalScore(newTotalScore);

        // 重新计算百分比
        ExamPaper paper = examPaperService.getById(session.getExamPaperId());
        if (paper != null && paper.getTotalScore() != null &&
                paper.getTotalScore().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newPercentage = newTotalScore.divide(paper.getTotalScore(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
            session.setPercentage(newPercentage);
        }

        examSessionMapper.updateById(session);
    }

    /**
     * 计算试卷总分
     */
    public BigDecimal calculateTotalScore(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (session.getQuestionScores() == null || session.getQuestionScores().isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<Long, BigDecimal> scores = parseScoresJson(session.getQuestionScores());
        return calculateTotalScoreFromScores(scores);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 解析简单JSON字符串为Map
     */
    private Map<String, Object> parseJsonToMap(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isEmpty() || "{}".equals(json.trim())) {
            return result;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return result;
        }
    }

    /**
     * 解析JSON数组字符串为Set
     */
    private Set<String> parseArrayToStringSet(String arrayStr) {
        Set<String> set = new HashSet<>();
        if (arrayStr == null || arrayStr.isEmpty()) {
            return set;
        }
        try {
            return objectMapper.readValue(arrayStr, new TypeReference<Set<String>>() {});
        } catch (Exception e) {
            // 解析失败时尝试直接添加
            set.add(arrayStr.replace("\"", "").replace("'", "").trim());
            return set;
        }
    }

    /**
     * 将题目得分Map转换为JSON字符串
     */
    private String scoresMapToJson(Map<Long, BigDecimal> scores) {
        if (scores == null || scores.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(scores);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 解析题目得分JSON字符串为Map
     */
    private Map<Long, BigDecimal> parseScoresJson(String json) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (json == null || json.isEmpty() || "{}".equals(json.trim())) {
            return result;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<Long, BigDecimal>>() {});
        } catch (Exception e) {
            return result;
        }
    }

    /**
     * 从得分Map中计算总分（排除-1的待评阅项）
     */
    private BigDecimal calculateTotalScoreFromScores(Map<Long, BigDecimal> scores) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal score : scores.values()) {
            if (score != null && score.compareTo(BigDecimal.ZERO) >= 0) {
                total = total.add(score);
            }
        }
        return total;
    }

    /**
     * 保存错题到错题本
     * 修复 P0-2：courseId 必须是"真实课程ID"（由试卷解析），而不是把 examPaperId 当 courseId 写入。
     *
     * @param wrongAnswer 学生的错误作答（可为 null）
     * @param sourceType  来源类型 EXAM / QUIZ / HOMEWORK / PRACTICE
     */
    private void saveWrongQuestion(Long studentId, Long questionId, Long examPaperId,
                                   String wrongAnswer, String sourceType) {
        try {
            // 关键修复：从试卷解析真实课程ID，绝不直接用 examPaperId 当作 courseId
            Long realCourseId = null;
            ExamPaper paper = examPaperService.getById(examPaperId);
            if (paper != null) {
                realCourseId = paper.getCourseId();
            }

            // 检查是否已存在（同一学生+题目，不分课程，错题本按学生维度去重）
            LambdaQueryWrapper<WrongQuestion> wrapper = new LambdaQueryWrapper<WrongQuestion>()
                    .eq(WrongQuestion::getStudentId, studentId)
                    .eq(WrongQuestion::getQuestionId, questionId);
            long count = wrongQuestionService.count(wrapper);
            if (count == 0) {
                WrongQuestion wq = new WrongQuestion();
                wq.setStudentId(studentId);
                wq.setQuestionId(questionId);
                wq.setCourseId(realCourseId);
                wq.setWrongAnswer(wrongAnswer);
                wq.setSourceType(sourceType != null ? sourceType : "EXAM");
                wq.setPracticeCount(1);
                wq.setLastPracticedAt(java.time.LocalDateTime.now());
                wrongQuestionService.save(wq);
            } else {
                // 更新错误次数与最新错误答案
                WrongQuestion existing = wrongQuestionService.getOne(wrapper);
                existing.setCourseId(realCourseId);
                existing.setWrongAnswer(wrongAnswer);
                existing.setPracticeCount(existing.getPracticeCount() + 1);
                existing.setLastPracticedAt(java.time.LocalDateTime.now());
                wrongQuestionService.updateById(existing);
            }
        } catch (Exception e) {
            log.warn("考试错题记录失败: studentId={}, questionId={}", studentId, questionId, e);
        }
    }
}
