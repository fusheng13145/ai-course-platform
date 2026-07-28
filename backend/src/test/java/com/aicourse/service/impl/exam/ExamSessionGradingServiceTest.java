package com.aicourse.service.impl.exam;

import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 考试会话阅卷协作者单元测试（迁移自原 ExamSessionServiceImplTest 的自动阅卷用例，并补充人工阅卷/总分计算）。
 * 直接针对 {@link ExamSessionGradingService}，保持等价断言强度。
 */
@ExtendWith(MockitoExtension.class)
class ExamSessionGradingServiceTest {

    @Mock private ExamQuestionService examQuestionService;
    @Mock private QuestionService questionService;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private ExamPaperService examPaperService;
    @Mock private ObjectMapper objectMapper;
    @Mock private ExamSessionMapper examSessionMapper;

    @InjectMocks private ExamSessionGradingService gradingService;

    private ExamPaper testExamPaper;
    private ExamSession testSession;

    @BeforeEach
    void setUp() {
        testExamPaper = new ExamPaper();
        testExamPaper.setId(1L);
        testExamPaper.setTotalScore(new BigDecimal("100"));

        testSession = new ExamSession();
        testSession.setId(1L);
        testSession.setExamPaperId(1L);
        testSession.setStudentId(100L);
        testSession.setStatus("SUBMITTED");
        testSession.setStartTime(java.time.LocalDateTime.now().minusMinutes(30));
        testSession.setAttemptNumber(1);
    }

    // ==================== autoGrade ====================

    @Test
    @DisplayName("自动阅卷 - 单选题完全匹配得分")
    void testAutoGrade_SingleChoice() throws Exception {
        testSession.setStatus("SUBMITTED");
        testSession.setAnswers("{\"101\":\"B\"}");

        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(101L);
        eq.setExamPaperId(1L);
        eq.setPoints(new BigDecimal("5"));
        eq.setOrderIndex(1);

        Question q = new Question();
        q.setId(101L);
        q.setType("SINGLE_CHOICE");
        q.setCorrectAnswers("B");

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Map.of("101", "B"));
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(any())).thenReturn(List.of(q));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);

        Map<String, Object> result = gradingService.autoGrade(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("5"), result.get("totalScore"));
        assertEquals(1, result.get("correctCount"));
        assertEquals(0, result.get("wrongCount"));
        assertEquals("GRADED", result.get("status"));
    }

    @Test
    @DisplayName("自动阅卷 - 多选题完全匹配得分")
    void testAutoGrade_MultipleChoiceFullMatch() throws Exception {
        testSession.setStatus("SUBMITTED");
        testSession.setAnswers("{\"102\":\"[\\\"A\\\",\\\"B\\\"]\"}");

        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(102L);
        eq.setExamPaperId(1L);
        eq.setPoints(new BigDecimal("10"));
        eq.setOrderIndex(1);

        Question q = new Question();
        q.setId(102L);
        q.setType("MULTIPLE_CHOICE");
        q.setCorrectAnswers("[\"A\",\"B\"]");

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenAnswer(invocation -> {
                    String input = invocation.getArgument(0);
                    if (input.startsWith("{")) {
                        return Map.of("102", "[\"A\",\"B\"]");
                    }
                    String cleaned = input.replace("[", "").replace("]", "").replace("\"", "").trim();
                    if (cleaned.isEmpty()) return new HashSet<String>();
                    return new HashSet<>(Arrays.asList(cleaned.split("\\s*,\\s*")));
                });
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(any())).thenReturn(List.of(q));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);

        Map<String, Object> result = gradingService.autoGrade(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("10"), result.get("totalScore"));
        assertEquals(1, result.get("correctCount"));
    }

    @Test
    @DisplayName("自动阅卷 - 多选题部分匹配得一半分")
    void testAutoGrade_MultipleChoicePartialMatch() throws Exception {
        testSession.setStatus("SUBMITTED");
        testSession.setAnswers("{\"102\":\"[\\\"A\\\"]\"}");

        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(102L);
        eq.setExamPaperId(1L);
        eq.setPoints(new BigDecimal("10"));
        eq.setOrderIndex(1);

        Question q = new Question();
        q.setId(102L);
        q.setType("MULTIPLE_CHOICE");
        q.setCorrectAnswers("[\"A\",\"B\"]");

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenAnswer(invocation -> {
                    String input = invocation.getArgument(0);
                    if (input.startsWith("{")) {
                        return Map.of("102", "[\"A\"]");
                    }
                    String cleaned = input.replace("[", "").replace("]", "").replace("\"", "").trim();
                    if (cleaned.isEmpty()) return new HashSet<String>();
                    return new HashSet<>(Arrays.asList(cleaned.split("\\s*,\\s*")));
                });
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(any())).thenReturn(List.of(q));
        when(wrongQuestionService.count(any())).thenReturn(0L);
        when(wrongQuestionService.save(any(WrongQuestion.class))).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);

        Map<String, Object> result = gradingService.autoGrade(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("2.50"), result.get("totalScore"));
        assertEquals(1, result.get("wrongCount"));
    }

    @Test
    @DisplayName("自动阅卷 - 判断题正确得分")
    void testAutoGrade_TrueFalse() throws Exception {
        testSession.setStatus("SUBMITTED");
        testSession.setAnswers("{\"103\":\"true\"}");

        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(103L);
        eq.setExamPaperId(1L);
        eq.setPoints(new BigDecimal("5"));
        eq.setOrderIndex(1);

        Question q = new Question();
        q.setId(103L);
        q.setType("TRUE_FALSE");
        q.setCorrectAnswers("true");

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Map.of("103", "true"));
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(any())).thenReturn(List.of(q));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);

        Map<String, Object> result = gradingService.autoGrade(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("5"), result.get("totalScore"));
        assertEquals(1, result.get("correctCount"));
    }

    @Test
    @DisplayName("自动阅卷 - 简答题待人工批阅")
    void testAutoGrade_ShortAnswerPendingReview() throws Exception {
        testSession.setStatus("SUBMITTED");
        testSession.setAnswers("{\"104\":\"这是一段简答题答案\"}");

        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(104L);
        eq.setExamPaperId(1L);
        eq.setPoints(new BigDecimal("10"));
        eq.setOrderIndex(1);

        Question q = new Question();
        q.setId(104L);
        q.setType("SHORT_ANSWER");
        q.setCorrectAnswers("参考答案");

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Map.of("104", "这是一段简答题答案"));
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(any())).thenReturn(List.of(q));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);

        Map<String, Object> result = gradingService.autoGrade(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("0"), result.get("totalScore"));
        assertEquals(1, result.get("pendingReviewCount"));
    }

    @Test
    @DisplayName("自动阅卷 - 会话不存在")
    void testAutoGrade_NotFound() {
        when(examSessionMapper.selectById(999L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> gradingService.autoGrade(999L));
        assertEquals("考试会话不存在", ex.getMessage());
    }

    // ==================== manualGrade ====================

    @Test
    @DisplayName("人工阅卷 - 更新分数并重算总分/百分比")
    void testManualGrade_Success() throws Exception {
        testSession.setStatus("GRADED");
        testSession.setQuestionScores("{\"101\":\"5\"}");
        testSession.setTotalScore(new BigDecimal("5"));
        testSession.setPercentage(new BigDecimal("5.00"));

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(new HashMap<>(Map.of(101L, new BigDecimal("5"))));
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);

        gradingService.manualGrade(1L, 101L, new BigDecimal("8"), "good");

        assertEquals(new BigDecimal("8"), testSession.getTotalScore());
        assertEquals(new BigDecimal("8.00"), testSession.getPercentage());
        verify(examSessionMapper).updateById((ExamSession) any());
    }

    @Test
    @DisplayName("人工阅卷 - 未自动阅卷拒绝")
    void testManualGrade_NotGraded() {
        testSession.setStatus("SUBMITTED");
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> gradingService.manualGrade(1L, 101L, BigDecimal.TEN, "good"));
        assertEquals("请先完成自动阅卷", ex.getMessage());
    }

    // ==================== calculateTotalScore ====================

    @Test
    @DisplayName("计算总分 - 汇总所有非负得分")
    void testCalculateTotalScore_Success() throws Exception {
        testSession.setQuestionScores("{\"101\":\"5\",\"102\":\"10\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(new HashMap<>(Map.of(101L, new BigDecimal("5"), 102L, new BigDecimal("10"))));

        BigDecimal total = gradingService.calculateTotalScore(1L);
        assertEquals(new BigDecimal("15"), total);
    }

    @Test
    @DisplayName("计算总分 - 无得分记录返回0")
    void testCalculateTotalScore_Empty() {
        testSession.setQuestionScores("");
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        assertEquals(BigDecimal.ZERO, gradingService.calculateTotalScore(1L));
    }
}
