package com.aicourse.service.component.learning;

import com.aicourse.common.KnowledgePointNameUtils;
import com.aicourse.constant.ExamSessionStatus;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.mapper.ExamPaperMapper;
import com.aicourse.mapper.ExamQuestionMapper;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.vo.MasteryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识点掌握度计算组件（2026-11-16，阶段 4 P0-4）。
 *
 * <p><b>为什么单独拆组件</b>：掌握度同时依赖题库题目、错题本、考试会话与试卷题目分值。
 * 旧实现直接写在 {@code KnowledgePointServiceImpl} 中，题目查询漏 {@code course_id}，
 * 并把“没有错题”直接当成 100 分；本组件按课程一次性加载证据，避免逐知识点 N+1，也让
 * 单点查询与薄弱点快照共用同一套算法。</p>
 *
 * <p><b>证据优先级</b>：同一道题优先取该学生最近一次已阅卷考试的题目得分比例；
 * 没有考试记录时，取错题本中该题的 {@code mastered} 状态。两者都没有的题目视为
 * “未作答”，不计入分母，也不把缺失数据伪装成 0 分或 100 分。</p>
 */
@Component
public class KnowledgeMasteryComponent {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal FULL_SCORE = BigDecimal.ONE;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final QuestionService questionService;
    private final WrongQuestionService wrongQuestionService;
    private final ExamPaperMapper examPaperMapper;
    private final ExamSessionMapper examSessionMapper;
    private final ExamQuestionMapper examQuestionMapper;

    public KnowledgeMasteryComponent(QuestionService questionService,
                                     WrongQuestionService wrongQuestionService,
                                     ExamPaperMapper examPaperMapper,
                                     ExamSessionMapper examSessionMapper,
                                     ExamQuestionMapper examQuestionMapper) {
        this.questionService = questionService;
        this.wrongQuestionService = wrongQuestionService;
        this.examPaperMapper = examPaperMapper;
        this.examSessionMapper = examSessionMapper;
        this.examQuestionMapper = examQuestionMapper;
    }

    /**
     * 计算单个知识点掌握度。
     */
    public MasteryVO evaluate(KnowledgePoint point, Long studentId) {
        if (point == null) {
            return MasteryVO.unknown(0, "知识点不存在");
        }
        return evaluate(List.of(point), studentId).getOrDefault(point.getId(),
                MasteryVO.unknown(0, "知识点不存在"));
    }

    /**
     * 批量计算知识点掌握度，返回知识点 ID -> 结果。
     */
    public Map<Long, MasteryVO> evaluate(List<KnowledgePoint> points, Long studentId) {
        if (points == null || points.isEmpty() || studentId == null) {
            return Collections.emptyMap();
        }
        List<Long> courseIds = points.stream()
                .map(KnowledgePoint::getCourseId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Question> questions = safeList(questionService.list(
                new LambdaQueryWrapper<Question>().in(Question::getCourseId, courseIds)));
        Map<Long, Map<String, List<Long>>> questionIndex = indexQuestions(questions);
        Set<Long> allQuestionIds = questions.stream()
                .map(Question::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Boolean> wrongMasteredByQuestion = loadWrongBook(studentId, allQuestionIds);
        Map<Long, BigDecimal> latestExamRatioByQuestion = loadExamEvidence(studentId, courseIds);

        Map<Long, MasteryVO> result = new LinkedHashMap<>();
        for (KnowledgePoint point : points) {
            if (point == null || point.getId() == null) {
                continue;
            }
            List<Long> linkedQuestionIds = questionIndex
                    .getOrDefault(point.getCourseId(), Collections.emptyMap())
                    .getOrDefault(KnowledgePointNameUtils.normalize(point.getName()), Collections.emptyList());
            result.put(point.getId(), evaluatePoint(linkedQuestionIds, wrongMasteredByQuestion,
                    latestExamRatioByQuestion));
        }
        return result;
    }

    private MasteryVO evaluatePoint(List<Long> linkedQuestionIds,
                                    Map<Long, Boolean> wrongMasteredByQuestion,
                                    Map<Long, BigDecimal> latestExamRatioByQuestion) {
        if (linkedQuestionIds == null || linkedQuestionIds.isEmpty()) {
            return MasteryVO.unknown(0, "该知识点暂无关联题目");
        }

        List<BigDecimal> evidenceRatios = new ArrayList<>();
        for (Long questionId : linkedQuestionIds) {
            BigDecimal examRatio = latestExamRatioByQuestion.get(questionId);
            if (examRatio != null) {
                evidenceRatios.add(examRatio);
                continue;
            }
            Boolean mastered = wrongMasteredByQuestion.get(questionId);
            if (mastered != null) {
                evidenceRatios.add(Boolean.TRUE.equals(mastered) ? FULL_SCORE : BigDecimal.ZERO);
            }
        }

        if (evidenceRatios.isEmpty()) {
            return MasteryVO.unknown(linkedQuestionIds.size(), "该知识点暂无作答记录");
        }

        BigDecimal sum = evidenceRatios.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal score = sum.divide(BigDecimal.valueOf(evidenceRatios.size()), 4, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
        int correctQuestions = (int) evidenceRatios.stream()
                .filter(ratio -> ratio.compareTo(FULL_SCORE) >= 0)
                .count();
        int answeredQuestions = evidenceRatios.size();
        int wrongQuestions = answeredQuestions - correctQuestions;

        MasteryVO vo = new MasteryVO();
        vo.setScore(score);
        vo.setLevel(levelOf(score));
        vo.setAnswered(true);
        vo.setTotalQuestions(linkedQuestionIds.size());
        vo.setAnsweredQuestions(answeredQuestions);
        vo.setCorrectQuestions(correctQuestions);
        vo.setWrongQuestions(wrongQuestions);
        vo.setNote("共 " + linkedQuestionIds.size() + " 道关联题，已作答 " + answeredQuestions + " 道");
        return vo;
    }

    private Map<Long, Map<String, List<Long>>> indexQuestions(List<Question> questions) {
        Map<Long, Map<String, List<Long>>> index = new HashMap<>();
        for (Question question : questions) {
            if (question == null || question.getId() == null || question.getCourseId() == null) {
                continue;
            }
            for (String name : KnowledgePointNameUtils.split(question.getKnowledgePoints())) {
                String key = KnowledgePointNameUtils.normalize(name);
                if (key.isEmpty()) {
                    continue;
                }
                List<Long> ids = index
                        .computeIfAbsent(question.getCourseId(), ignored -> new HashMap<>())
                        .computeIfAbsent(key, ignored -> new ArrayList<>());
                if (!ids.contains(question.getId())) {
                    ids.add(question.getId());
                }
            }
        }
        return index;
    }

    private Map<Long, Boolean> loadWrongBook(Long studentId, Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<WrongQuestion> records = safeList(wrongQuestionService.list(
                new LambdaQueryWrapper<WrongQuestion>()
                        .eq(WrongQuestion::getStudentId, studentId)
                        .in(WrongQuestion::getQuestionId, questionIds)));
        Map<Long, Boolean> result = new HashMap<>();
        for (WrongQuestion record : records) {
            if (record != null && record.getQuestionId() != null) {
                result.put(record.getQuestionId(), Boolean.TRUE.equals(record.getMastered()));
            }
        }
        return result;
    }

    /**
     * 取该学生在这些课程中“最近一次已阅卷考试”的每题得分比例。
     * 同一道题只保留最近一次记录；待人工评分的 {@code -1} 不产生证据。
     */
    private Map<Long, BigDecimal> loadExamEvidence(Long studentId, List<Long> courseIds) {
        List<ExamPaper> papers = safeList(examPaperMapper.selectList(
                new LambdaQueryWrapper<ExamPaper>().in(ExamPaper::getCourseId, courseIds)));
        List<Long> paperIds = papers.stream()
                .map(ExamPaper::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (paperIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, BigDecimal> pointsByPaperQuestion = new HashMap<>();
        for (ExamQuestion examQuestion : safeList(examQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamQuestion>().in(ExamQuestion::getExamPaperId, paperIds)))) {
            if (examQuestion != null && examQuestion.getExamPaperId() != null
                    && examQuestion.getQuestionId() != null && examQuestion.getPoints() != null) {
                pointsByPaperQuestion.put(paperQuestionKey(examQuestion.getExamPaperId(),
                        examQuestion.getQuestionId()), examQuestion.getPoints());
            }
        }

        List<ExamSession> sessions = safeList(examSessionMapper.selectList(
                new LambdaQueryWrapper<ExamSession>()
                        .eq(ExamSession::getStudentId, studentId)
                        .in(ExamSession::getExamPaperId, paperIds)
                        .eq(ExamSession::getStatus, ExamSessionStatus.GRADED)
                        .isNotNull(ExamSession::getQuestionScores)))
                .stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        sessions.sort(latestSessionFirst());

        Map<Long, BigDecimal> latestRatio = new HashMap<>();
        for (ExamSession session : sessions) {
            if (session == null || session.getExamPaperId() == null) {
                continue;
            }
            Map<Long, BigDecimal> scores = parseQuestionScores(session.getQuestionScores());
            for (Map.Entry<Long, BigDecimal> entry : scores.entrySet()) {
                Long questionId = entry.getKey();
                if (latestRatio.containsKey(questionId)) {
                    continue;
                }
                BigDecimal score = entry.getValue();
                if (score == null || score.compareTo(BigDecimal.ZERO) < 0) {
                    continue;
                }
                BigDecimal points = pointsByPaperQuestion.get(
                        paperQuestionKey(session.getExamPaperId(), questionId));
                if (points == null || points.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal ratio = score.divide(points, 4, RoundingMode.HALF_UP);
                if (ratio.compareTo(FULL_SCORE) > 0) {
                    ratio = FULL_SCORE;
                }
                latestRatio.put(questionId, ratio);
            }
        }
        return latestRatio;
    }

    private Comparator<ExamSession> latestSessionFirst() {
        return (left, right) -> {
            LocalDateTime leftTime = left.getSubmitTime();
            LocalDateTime rightTime = right.getSubmitTime();
            if (leftTime != null && rightTime != null) {
                int compared = rightTime.compareTo(leftTime);
                if (compared != 0) {
                    return compared;
                }
            } else if (leftTime != null) {
                return -1;
            } else if (rightTime != null) {
                return 1;
            }
            Integer leftAttempt = left.getAttemptNumber();
            Integer rightAttempt = right.getAttemptNumber();
            if (leftAttempt != null && rightAttempt != null) {
                return Integer.compare(rightAttempt, leftAttempt);
            }
            if (leftAttempt != null) {
                return -1;
            }
            if (rightAttempt != null) {
                return 1;
            }
            return 0;
        };
    }

    private Map<Long, BigDecimal> parseQuestionScores(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            JsonNode root = JSON.readTree(rawJson);
            if (root == null || !root.isObject()) {
                return Collections.emptyMap();
            }
            Map<Long, BigDecimal> result = new HashMap<>();
            root.fields().forEachRemaining(entry -> {
                try {
                    Long questionId = Long.valueOf(entry.getKey());
                    JsonNode value = entry.getValue();
                    BigDecimal score = value != null && value.isNumber()
                            ? value.decimalValue()
                            : new BigDecimal(value == null ? "" : value.asText());
                    result.put(questionId, score);
                } catch (Exception ignored) {
                    // 单题分值脏数据不影响其余题目
                }
            });
            return result;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private static String paperQuestionKey(Long paperId, Long questionId) {
        return paperId + ":" + questionId;
    }

    private static <T> List<T> safeList(List<T> source) {
        return source == null ? Collections.emptyList() : source;
    }

    private static String levelOf(BigDecimal score) {
        if (score.compareTo(new BigDecimal("85")) >= 0) {
            return "优秀";
        }
        if (score.compareTo(new BigDecimal("70")) >= 0) {
            return "良好";
        }
        if (score.compareTo(new BigDecimal("60")) >= 0) {
            return "合格";
        }
        return "待巩固";
    }
}
