package com.aicourse.service.impl.exam;

import com.aicourse.constant.ExamSessionStatus;

import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.util.WrongQuestionSnapshotUtil;
import com.aicourse.vo.AutoGradeResultVO;
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
    public AutoGradeResultVO autoGrade(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }
        if (!ExamSessionStatus.SUBMITTED.equals(session.getStatus()) && !ExamSessionStatus.FORCE_SUBMITTED.equals(session.getStatus())
                && !ExamSessionStatus.TIMEOUT.equals(session.getStatus()) && !ExamSessionStatus.AUTO_SUBMITTED.equals(session.getStatus())) {
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
            String studentAnswer = normalizeAnswer(stringifyAnswer(studentAnswers.get(questionKey)));
            String correctAnswer = normalizeAnswer(question.getCorrectAnswers());

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
            } else if (score.compareTo(BigDecimal.ZERO) == 0 && !studentAnswer.isEmpty()) {
                // 修复 D5（零分判定）：BigDecimal.equals 按 scale 比较，0.00 与 0 不等 → 错题数漏计；
                // 改用 compareTo 做数值零判定，保证「得分恰好为 0 且学生已作答」必被计入错题。
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
        session.setStatus(ExamSessionStatus.GRADED);
        examSessionMapper.updateById(session);

        // 记录错题到错题本（可选）
        for (Long qId : wrongQuestionIds) {
            String wa = studentAnswers.get(String.valueOf(qId)) != null
                    ? studentAnswers.get(String.valueOf(qId)).toString() : null;
            saveWrongQuestion(session.getStudentId(), qId, session.getExamPaperId(), wa, "EXAM");
        }

        // 构建返回结果
        return new AutoGradeResultVO(
                session.getId(), totalScore, fullScore, percentage,
                correctCount, wrongCount, pendingReviewCount,
                questionScoreMap, ExamSessionStatus.GRADED);
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
        if (!ExamSessionStatus.GRADED.equals(session.getStatus())) {
            throw new BusinessException("请先完成自动阅卷");
        }

        // 修复 D5（数值越界）：人工阅卷分数必须做边界校验，否则可写入负分/超满分导致 percentage>100，
        // 或 score=null 时后续 calculateTotalScoreFromScores 拆箱炸裂。
        // 策略选择「拒绝」而非「clamp」，与本项目既有 BusinessException 风格一致，让教师明确感知录入错误。
        if (score == null) {
            throw new BusinessException("人工阅卷分数不能为空");
        }
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("人工阅卷分数不能为负数");
        }
        // 该题满分来源：按 (examPaperId, questionId) 定位 ExamQuestion.points。
        // 定位不到（历史数据/未入库）时跳过上界校验，避免误伤既有可阅卷场景。
        ExamQuestion examQuestion = examQuestionService.getOne(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, session.getExamPaperId())
                .eq(ExamQuestion::getQuestionId, questionId)
                .last("LIMIT 1"));
        if (examQuestion != null && examQuestion.getPoints() != null
                && score.compareTo(examQuestion.getPoints()) > 0) {
            throw new BusinessException("人工阅卷分数不能超过该题满分：" + examQuestion.getPoints());
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
            log.error("考试会话Grading相关操作失败: {}", e.getMessage(), e);

            return result;
        }
    }

    /**
     * 归一化答案字面量，消除「JSON 存储形态」与「用户作答字符串」之间的表示差异。
     *
     * <p><b>修复记录（2026-09-14，P0）</b>：{@code questions.correct_answers} 是 JSON 列，
     * 单值会被持久化为带引号的 JSON 字符串（如 {@code "B"}）。MyBatis 按 String 读回时得到的是
     * 含引号的文本，与学生会提交的 {@code B} 直接做 {@code equalsIgnoreCase} 比较**必然不相等**，
     * 导致「作答全部正确却得 0 分」（实测 total_score=0.00 / 卷面满分 25.00）。</p>
     *
     * <p>归一化规则：JSON 字符串（{@code "X"}）→ 去引号标量 {@code X}；
     * JSON 数组/对象（{@code [...]}/{@code {...}}）→ 原样保留，交由 {@link #parseArrayToStringSet} 处理；
     * 其余字面量原样返回。</p>
     */
    private String normalizeAnswer(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        // 数组 / 对象形态交由集合解析，避免破坏多选题语义
        if (value.startsWith("[") || value.startsWith("{")) {
            return value;
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            try {
                return objectMapper.readValue(value, String.class);
            } catch (Exception e) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
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
            // 非 JSON 数组（如 "A,B" / "A，B" 这类分隔符写法）：按分隔符拆分，
            // 与 DDL 注释「多答案按规则分隔/JSON存储」保持一致，避免整串对比必然失配。
            for (String token : arrayStr.replace("\"", "").replace("'", "")
                    .split("[,，;；]")) {
                String t = token.trim();
                if (!t.isEmpty()) {
                    set.add(t);
                }
            }
            return set;
        }
    }

    /**
     * 把单条作答值规范成可比对的字符串。
     *
     * <p>多选题前端送的是 JSON 数组，{@code Map<String,Object>} 反序列化后是 {@code List}；
     * 原先直接 {@code value.toString()} 会得到 {@code "[A, C]"} —— 既不是合法 JSON（元素无引号），
     * 也不是纯分隔串，于是 {@link #parseArrayToStringSet(String)} 退化切分出 {@code "[A"} / {@code "C]"}，
     * 多选题无论是否答对都判错。集合一律按逗号连接后再交给 normalizeAnswer。</p>
     */
    private String stringifyAnswer(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof Collection<?> collection) {
            StringJoiner joiner = new StringJoiner(",");
            for (Object item : collection) {
                if (item != null) {
                    joiner.add(String.valueOf(item));
                }
            }
            return joiner.toString();
        }
        return String.valueOf(raw);
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
            log.error("考试会话Grading相关操作失败: {}", e.getMessage(), e);

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
            log.error("考试会话Grading相关操作失败: {}", e.getMessage(), e);

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
                // 快照填充（2026-09-23 修复）：questionContent / correctAnswer 等快照字段
                // 此前无人写入，导致错题本列表题干为空（前端 useWrongQuestions 依赖 questionContent）。
                WrongQuestionSnapshotUtil.fillFromQuestion(wq, questionService.getById(questionId));
                wrongQuestionService.save(wq);
            } else {
                // 更新错误次数与最新错误答案
                WrongQuestion existing = wrongQuestionService.getOne(wrapper);
                existing.setCourseId(realCourseId);
                existing.setWrongAnswer(wrongAnswer);
                // 修复 D6（拆箱 NPE）：practiceCount 可能为 null（历史数据/未初始化），
                // 直接 +1 会拆箱炸裂并被外层 catch 静默吞掉，导致练习次数永不累加。
                // 与 WrongQuestionServiceImpl 的既有判空口径保持一致。
                existing.setPracticeCount(Optional.ofNullable(existing.getPracticeCount()).orElse(0) + 1);
                existing.setLastPracticedAt(java.time.LocalDateTime.now());
                // 再次答错时一并刷新快照，保证展示与当前题库一致
                WrongQuestionSnapshotUtil.fillFromQuestion(existing, questionService.getById(questionId));
                wrongQuestionService.updateById(existing);
            }
        } catch (Exception e) {
            log.warn("考试错题记录失败: studentId={}, questionId={}", studentId, questionId, e);
        }
    }
}
