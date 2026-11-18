package com.aicourse.service.impl.exam;

import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.vo.AutoGradeResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExamSessionGradingService 覆盖率补测（I-04 第四批）。
 *
 * <p>覆盖状态放行矩阵、空作答与损坏 JSON、题型别名与未知题型、多选不交集、
 * GRADED 人工分守卫、人工阅卷边界、总分空值及错题落库异常吞掉。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamSessionGradingServiceCoverageTest {

    @Mock private ExamQuestionService examQuestionService;
    @Mock private QuestionService questionService;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private ExamPaperService examPaperService;
    @Mock private ExamSessionMapper examSessionMapper;

    private ExamSessionGradingService gradingService;

    @BeforeEach
    void setUp() {
        gradingService = new ExamSessionGradingService(
                examQuestionService, questionService, wrongQuestionService,
                examPaperService, new ObjectMapper(), examSessionMapper);
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);
        when(wrongQuestionService.count(any())).thenReturn(0L);
        when(wrongQuestionService.save(any(WrongQuestion.class))).thenReturn(true);
    }

    private ExamSession session(String status, String answers) {
        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setExamPaperId(1L);
        session.setStudentId(100L);
        session.setStatus(status);
        session.setAnswers(answers);
        return session;
    }

    private ExamQuestion examQuestion(long questionId, String type, String correctAnswers, String points) {
        ExamQuestion examQuestion = new ExamQuestion();
        examQuestion.setQuestionId(questionId);
        examQuestion.setExamPaperId(1L);
        examQuestion.setPoints(points == null ? null : new BigDecimal(points));
        examQuestion.setOrderIndex((int) questionId);
        return examQuestion;
    }

    private Question question(long questionId, String type, String correctAnswers) {
        Question question = new Question();
        question.setId(questionId);
        question.setType(type);
        question.setCorrectAnswers(correctAnswers);
        return question;
    }

    private ExamPaper paper(String totalScore) {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        paper.setCourseId(7L);
        paper.setTotalScore(totalScore == null ? null : new BigDecimal(totalScore));
        return paper;
    }

    private ExamSessionGradingService graderWith(ObjectMapper mapper) {
        return new ExamSessionGradingService(
                examQuestionService, questionService, wrongQuestionService,
                examPaperService, mapper, examSessionMapper);
    }

    // ===================== autoGrade：状态与作答 =====================

    @Test
    @DisplayName("自动阅卷：FORCE/TIMEOUT/AUTO 提交均可放行，DRAFT 拒绝")
    void autoGrade_statusBranches() {
        ExamQuestion eq = examQuestion(101L, "SINGLE", "B", "5");
        Question q = question(101L, "SINGLE", "B");
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(any())).thenReturn(List.of(q));

        ExamSession force = session("FORCE_SUBMITTED", "{\"101\":\"B\"}");
        ExamSession timeout = session("TIMEOUT", "{\"101\":\"B\"}");
        ExamSession auto = session("AUTO_SUBMITTED", "{\"101\":\"B\"}");
        ExamSession draft = session("DRAFT", "{\"101\":\"B\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(force, timeout, auto, draft);

        assertEquals("GRADED", gradingService.autoGrade(1L).getStatus());
        assertEquals("GRADED", gradingService.autoGrade(1L).getStatus());
        assertEquals("GRADED", gradingService.autoGrade(1L).getStatus());
        assertThrows(BusinessException.class, () -> gradingService.autoGrade(1L));
    }

    @Test
    @DisplayName("自动阅卷：answers 为 null/空串/{}/空白均按无作答拒绝")
    void autoGrade_emptyAnswerForms() {
        ExamSession nullAnswers = session("SUBMITTED", null);
        ExamSession emptyAnswers = session("SUBMITTED", "");
        ExamSession braceAnswers = session("SUBMITTED", "{}");
        ExamSession blankAnswers = session("SUBMITTED", "   ");
        when(examSessionMapper.selectById(1L)).thenReturn(nullAnswers, emptyAnswers, braceAnswers, blankAnswers);

        for (int i = 0; i < 4; i++) {
            BusinessException error = assertThrows(BusinessException.class, () -> gradingService.autoGrade(1L));
            assertTrue(error.getMessage().contains("未找到学生作答记录"));
        }
    }

    @Test
    @DisplayName("自动阅卷：题目批量查询为 null 时不落题且总分为 0")
    void autoGrade_listByIdsNull() {
        ExamSession session = session("SUBMITTED", "{\"101\":\"B\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(examQuestion(101L, "SINGLE", "B", "5")));
        when(questionService.listByIds(any())).thenReturn(null);

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertEquals(0, result.getTotalScore().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.getCorrectCount());
    }

    @Test
    @DisplayName("自动阅卷：GRADED 守卫遇到题库缺失题时跳过，不阻断重算")
    void autoGrade_gradedMissingQuestionInGuard() {
        ExamSession session = session("GRADED", "{\"101\":\"B\"}");
        session.setQuestionScores("{\"101\":5}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                examQuestion(101L, "SINGLE", "B", "5"),
                examQuestion(999L, "SINGLE", "C", "5")));
        when(questionService.listByIds(any())).thenReturn(List.of(question(101L, "SINGLE", "B")));

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertEquals(0, result.getTotalScore().compareTo(new BigDecimal("5")));
    }

    @Test
    @DisplayName("自动阅卷：题目未配置分值且无题目时满分/百分比均为 0")
    void autoGrade_nullPointsAndNoQuestions() {
        ExamSession noPointsSession = session("SUBMITTED", "{\"101\":\"B\"}");
        ExamSession noQuestionSession = session("SUBMITTED", "{\"101\":\"B\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(noPointsSession, noQuestionSession);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(
                List.of(examQuestion(101L, "SINGLE", "B", null)), List.of());
        when(questionService.listByIds(any())).thenReturn(List.of(question(101L, "SINGLE", "B")));

        AutoGradeResultVO first = gradingService.autoGrade(1L);
        assertEquals(0, first.getTotalScore().compareTo(BigDecimal.ZERO));
        assertEquals(0, first.getFullScore().compareTo(BigDecimal.ZERO));

        AutoGradeResultVO second = gradingService.autoGrade(1L);
        assertEquals(0, second.getFullScore().compareTo(BigDecimal.ZERO));
        assertEquals("{}", noQuestionSession.getQuestionScores());
    }

    // ===================== 题型与多选 =====================

    @Test
    @DisplayName("自动阅卷：SINGLE/MULTIPLE/JUDGE 别名与 ESSAY/FILL/未知题型分支")
    void autoGrade_typeAliasesAndDefault() {
        ExamSession session = session("SUBMITTED",
                "{\"101\":\"A\",\"102\":[\"A\",\"B\"],\"103\":\"true\","
                        + "\"104\":\"essay\",\"105\":\"fill\",\"106\":\"fill2\",\"107\":\"unknown\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                examQuestion(101L, "SINGLE", "A", "5"),
                examQuestion(102L, "MULTIPLE", "[\"A\",\"B\"]", "5"),
                examQuestion(103L, "JUDGE", "false", "5"),
                examQuestion(104L, "ESSAY", null, "5"),
                examQuestion(105L, "FILL_BLANK", null, "5"),
                examQuestion(106L, "FILL", null, "5"),
                examQuestion(107L, "UNKNOWN", null, "5")));
        when(questionService.listByIds(any())).thenReturn(List.of(
                question(101L, "SINGLE", "A"),
                question(102L, "MULTIPLE", "[\"A\",\"B\"]"),
                question(103L, "JUDGE", "false"),
                question(104L, "ESSAY", null),
                question(105L, "FILL_BLANK", null),
                question(106L, "FILL", null),
                question(107L, "UNKNOWN", null)));

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertEquals(0, result.getTotalScore().compareTo(new BigDecimal("10")));
        assertEquals(2, result.getCorrectCount());
        assertEquals(1, result.getWrongCount());
        assertEquals(4, result.getPendingReviewCount());
    }

    @Test
    @DisplayName("自动阅卷：多选不交集、空正确答案、分隔符回退")
    void autoGrade_multipleDisjointEmptyAndFallback() {
        ExamSession session = session("SUBMITTED",
                "{\"102\":[\"C\"],\"103\":[\"A\"],\"104\":\"A,B\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                examQuestion(102L, "MULTIPLE", "[\"A\",\"B\"]", "10"),
                examQuestion(103L, "MULTIPLE", "", "10"),
                examQuestion(104L, "MULTIPLE", "A,,B", "10")));
        when(questionService.listByIds(any())).thenReturn(List.of(
                question(102L, "MULTIPLE", "[\"A\",\"B\"]"),
                question(103L, "MULTIPLE", ""),
                question(104L, "MULTIPLE", "A,,B")));

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertEquals(0, result.getTotalScore().compareTo(new BigDecimal("10")));
        assertEquals(1, result.getCorrectCount());
        assertEquals(2, result.getWrongCount());
    }

    @Test
    @DisplayName("自动阅卷：未作答时不记错题")
    void autoGrade_noAnswerNotWrong() {
        ExamSession session = session("SUBMITTED", "{\"999\":\"x\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(examQuestion(101L, "SINGLE", "B", "5")));
        when(questionService.listByIds(any())).thenReturn(List.of(question(101L, "SINGLE", "B")));

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertEquals(0, result.getTotalScore().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.getWrongCount());
        verify(wrongQuestionService, never()).save(any(WrongQuestion.class));
    }

    @Test
    @DisplayName("自动阅卷：正确答案为 null 且有作答时判错并落错题")
    void autoGrade_nullCorrectWithAnswerIsWrong() {
        ExamSession session = session("SUBMITTED", "{\"101\":\"X\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(examQuestion(101L, "SINGLE", null, "5")));
        when(questionService.listByIds(any())).thenReturn(List.of(question(101L, "SINGLE", null)));
        when(questionService.getById(101L)).thenReturn(question(101L, "SINGLE", null));

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertEquals(0, result.getTotalScore().compareTo(BigDecimal.ZERO));
        assertEquals(1, result.getWrongCount());
        verify(wrongQuestionService).save(any(WrongQuestion.class));
    }

    // ===================== GRADED 守卫 =====================

    @Test
    @DisplayName("自动阅卷：GRADED 守卫中历史分为空/负数时不拒绝")
    void autoGrade_gradedGuardNullAndNegativePrevious() {
        ExamQuestion eq = examQuestion(101L, "SHORT_ANSWER", "ref", "5");
        Question q = question(101L, "SHORT_ANSWER", "ref");
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(any())).thenReturn(List.of(q));

        ExamSession noScore = session("GRADED", "{\"101\":\"answer\"}");
        noScore.setQuestionScores("{}");
        ExamSession negative = session("GRADED", "{\"101\":\"answer\"}");
        negative.setQuestionScores("{\"101\":-1}");
        when(examSessionMapper.selectById(1L)).thenReturn(noScore, negative);

        assertEquals(1, gradingService.autoGrade(1L).getPendingReviewCount());
        assertEquals(1, gradingService.autoGrade(1L).getPendingReviewCount());
    }

    @Test
    @DisplayName("自动阅卷：GRADED 且题型为 null 时按人工可评拒绝")
    void autoGrade_gradedNullTypeRejected() {
        ExamSession session = session("GRADED", "{\"101\":\"answer\"}");
        session.setQuestionScores("{\"101\":5}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(examQuestion(101L, "SINGLE", "B", "5")));
        when(questionService.listByIds(any())).thenReturn(List.of(question(101L, null, "B")));

        assertThrows(BusinessException.class, () -> gradingService.autoGrade(1L));
        verify(examSessionMapper, never()).updateById(any(ExamSession.class));
    }

    // ===================== manualGrade 边界 =====================

    @Test
    @DisplayName("人工阅卷：会话不存在、题目分值为 null 时跳过上界")
    void manualGrade_notFoundAndNullPoints() {
        assertThrows(BusinessException.class, () -> gradingService.manualGrade(999L, 101L, BigDecimal.ONE, "x"));

        ExamSession session = session("GRADED", null);
        session.setQuestionScores("");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.getOne(any())).thenReturn(examQuestion(101L, "SHORT_ANSWER", "ref", null));
        when(examPaperService.getById(1L)).thenReturn(paper("100"));

        gradingService.manualGrade(1L, 101L, new BigDecimal("8"), "good");

        assertEquals(new BigDecimal("8"), session.getTotalScore());
    }

    @Test
    @DisplayName("人工阅卷：试卷总分为 null/0 时只更新总分不更新百分比")
    void manualGrade_paperTotalNullAndZero() {
        ExamSession nullTotalSession = session("GRADED", null);
        nullTotalSession.setQuestionScores("");
        nullTotalSession.setPercentage(new BigDecimal("9.00"));
        ExamSession zeroTotalSession = session("GRADED", null);
        zeroTotalSession.setQuestionScores("");
        zeroTotalSession.setPercentage(new BigDecimal("9.00"));
        when(examSessionMapper.selectById(1L)).thenReturn(nullTotalSession, zeroTotalSession);
        when(examQuestionService.getOne(any())).thenReturn(null);
        when(examPaperService.getById(1L)).thenReturn(paper(null), paper("0"));

        gradingService.manualGrade(1L, 101L, new BigDecimal("4"), "good");
        gradingService.manualGrade(1L, 101L, new BigDecimal("4"), "good");

        assertEquals(new BigDecimal("9.00"), nullTotalSession.getPercentage());
        assertEquals(new BigDecimal("9.00"), zeroTotalSession.getPercentage());
        assertEquals(new BigDecimal("4"), nullTotalSession.getTotalScore());
    }

    @Test
    @DisplayName("自动阅卷：引号边界与对象形态答案不破坏归一化")
    void autoGrade_quoteBoundaryAndObjectAnswer() {
        ExamSession session = session("SUBMITTED",
                "{\"101\":\"X\",\"102\":\"X\",\"103\":\"X\",\"104\":\"B\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                examQuestion(101L, "SINGLE", "\"", "5"),
                examQuestion(102L, "SINGLE", "\"B", "5"),
                examQuestion(103L, "SINGLE", "B\"", "5"),
                examQuestion(104L, "SINGLE", "{}", "5")));
        when(questionService.listByIds(any())).thenReturn(List.of(
                question(101L, "SINGLE", "\""),
                question(102L, "SINGLE", "\"B"),
                question(103L, "SINGLE", "B\""),
                question(104L, "SINGLE", "{}")));

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertEquals(0, result.getTotalScore().compareTo(BigDecimal.ZERO));
        assertEquals(4, result.getWrongCount());
    }

    @Test
    @DisplayName("自动阅卷：带引号答案走 JSON 字符串反序列化后判对")
    void autoGrade_quotedAnswerNormalized() {
        ExamSession session = session("SUBMITTED", "{\"101\":\"B\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                examQuestion(101L, "SINGLE", "\"B\"", "5")));
        when(questionService.listByIds(any())).thenReturn(List.of(question(101L, "SINGLE", "\"B\"")));

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertEquals(0, result.getTotalScore().compareTo(new BigDecimal("5")));
        assertEquals(1, result.getCorrectCount());
    }

    @Test
    @DisplayName("自动阅卷：带引号答案反序列化失败时回退去引号")
    void autoGrade_quotedAnswerParseFailureFallsBack() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenReturn(Map.of("101", "B"));
        when(mapper.readValue(anyString(), eq(String.class))).thenThrow(new RuntimeException("bad string"));
        when(mapper.writeValueAsString(any())).thenReturn("{}");
        ExamSession session = session("SUBMITTED", "{\"101\":\"B\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                examQuestion(101L, "SINGLE", "\"B\"", "5")));
        when(questionService.listByIds(any())).thenReturn(List.of(question(101L, "SINGLE", "\"B\"")));

        AutoGradeResultVO result = graderWith(mapper).autoGrade(1L);

        assertEquals(0, result.getTotalScore().compareTo(new BigDecimal("5")));
        assertEquals(1, result.getCorrectCount());
    }

    @Test
    @DisplayName("自动阅卷：多选作答中的 null 元素被跳过")
    void autoGrade_collectionNullItemSkipped() {
        ExamSession session = session("SUBMITTED", "{\"101\":[null,\"A\"]}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                examQuestion(101L, "MULTIPLE", "[\"A\"]", "5")));
        when(questionService.listByIds(any())).thenReturn(List.of(question(101L, "MULTIPLE", "[\"A\"]")));

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertEquals(0, result.getTotalScore().compareTo(new BigDecimal("5")));
        assertEquals(1, result.getCorrectCount());
    }

    // ===================== 计算总分 =====================

    @Test
    @DisplayName("计算总分：会话不存在、分数为 null、负分或 JSON null 值")
    void calculateTotalScore_nullBranches() {
        assertThrows(BusinessException.class, () -> gradingService.calculateTotalScore(999L));

        ExamSession nullScores = session("GRADED", null);
        nullScores.setQuestionScores(null);
        ExamSession nullValue = session("GRADED", null);
        nullValue.setQuestionScores("{\"101\":null}");
        ExamSession negative = session("GRADED", null);
        negative.setQuestionScores("{\"101\":-1}");
        ExamSession brace = session("GRADED", null);
        brace.setQuestionScores("{}");
        when(examSessionMapper.selectById(1L)).thenReturn(nullScores, nullValue, negative, brace);

        assertEquals(0, gradingService.calculateTotalScore(1L).compareTo(BigDecimal.ZERO));
        assertEquals(0, gradingService.calculateTotalScore(1L).compareTo(BigDecimal.ZERO));
        assertEquals(0, gradingService.calculateTotalScore(1L).compareTo(BigDecimal.ZERO));
        assertEquals(0, gradingService.calculateTotalScore(1L).compareTo(BigDecimal.ZERO));
    }

    // ===================== JSON 异常路径 =====================

    @Test
    @DisplayName("自动阅卷：作答 JSON 反序列化失败时按无作答拒绝")
    void autoGrade_answerParseFailure() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(new RuntimeException("bad json"));
        ExamSession session = session("SUBMITTED", "{bad");
        when(examSessionMapper.selectById(1L)).thenReturn(session);

        assertThrows(BusinessException.class, () -> graderWith(mapper).autoGrade(1L));
    }

    @Test
    @DisplayName("自动阅卷：得分序列化失败时落 {} 并继续")
    void autoGrade_scoreSerializeFailure() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenReturn(Map.of("101", "B"));
        when(mapper.writeValueAsString(any())).thenThrow(new RuntimeException("write down"));
        ExamSession session = session("SUBMITTED", "{\"101\":\"B\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(examQuestion(101L, "SINGLE", "B", "5")));
        when(questionService.listByIds(any())).thenReturn(List.of(question(101L, "SINGLE", "B")));

        AutoGradeResultVO result = graderWith(mapper).autoGrade(1L);

        assertEquals(0, result.getTotalScore().compareTo(new BigDecimal("5")));
        assertEquals("{}", session.getQuestionScores());
    }

    @Test
    @DisplayName("人工阅卷：分数/评语解析与评语序列化异常均降级为空")
    void manualGrade_jsonFailureFallsBack() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(new RuntimeException("bad"));
        when(mapper.writeValueAsString(any())).thenThrow(new RuntimeException("write down"));
        ExamSession session = session("GRADED", null);
        session.setQuestionScores("{bad");
        session.setQuestionComments("{bad");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.getOne(any())).thenReturn(null);
        when(examPaperService.getById(1L)).thenReturn(paper("100"));

        assertDoesNotThrow(() -> graderWith(mapper).manualGrade(1L, 101L, new BigDecimal("4"), "good"));

        assertEquals("{}", session.getQuestionScores());
        assertEquals("{}", session.getQuestionComments());
    }

    @Test
    @DisplayName("自动阅卷：错题落库异常被吞掉，不影响阅卷结果")
    void autoGrade_wrongQuestionFailureIsSwallowed() {
        ExamSession session = session("SUBMITTED", "{\"101\":\"A\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(examQuestion(101L, "SINGLE", "B", "5")));
        when(questionService.listByIds(any())).thenReturn(List.of(question(101L, "SINGLE", "B")));
        when(questionService.getById(101L)).thenReturn(question(101L, "SINGLE", "B"));
        when(wrongQuestionService.count(any())).thenThrow(new RuntimeException("db down"));

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertEquals(1, result.getWrongCount());
        verify(wrongQuestionService, never()).save(any(WrongQuestion.class));
    }

    @Test
    @DisplayName("人工阅卷：评语 JSON 为空串/{} 时按空 Map 起算")
    void manualGrade_emptyCommentJson() {
        ExamSession empty = session("GRADED", null);
        empty.setQuestionScores("");
        empty.setQuestionComments("");
        ExamSession brace = session("GRADED", null);
        brace.setQuestionScores("{}");
        brace.setQuestionComments("{}");
        when(examSessionMapper.selectById(1L)).thenReturn(empty, brace);
        when(examQuestionService.getOne(any())).thenReturn(null);
        when(examPaperService.getById(1L)).thenReturn(paper("100"));

        gradingService.manualGrade(1L, 101L, new BigDecimal("4"), "note");
        gradingService.manualGrade(1L, 101L, new BigDecimal("5"), "note2");

        assertTrue(empty.getQuestionComments().contains("note"));
        assertTrue(brace.getQuestionComments().contains("note2"));
    }

    @Test
    @DisplayName("人工阅卷：评语为 null 时不落评语键")
    void manualGrade_nullCommentSkipped() {
        ExamSession session = session("GRADED", null);
        session.setQuestionScores("");
        session.setQuestionComments("{\"101\":\"old\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(session);
        when(examQuestionService.getOne(any())).thenReturn(null);
        when(examPaperService.getById(1L)).thenReturn(paper("100"));

        gradingService.manualGrade(1L, 101L, new BigDecimal("4"), null);

        assertEquals("{\"101\":\"old\"}", session.getQuestionComments());
        assertEquals(new BigDecimal("4"), session.getTotalScore());
    }
}
