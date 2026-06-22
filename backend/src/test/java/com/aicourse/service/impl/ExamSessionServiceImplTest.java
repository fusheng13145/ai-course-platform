package com.aicourse.service.impl;

import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 考试会话服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class ExamSessionServiceImplTest {

    @Mock private ExamPaperService examPaperService;
    @Mock private ExamQuestionService examQuestionService;
    @Mock private QuestionService questionService;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private ObjectMapper objectMapper;
    @Mock private ExamSessionMapper examSessionMapper;

    @InjectMocks private ExamSessionServiceImpl examSessionService;

    private ExamPaper testExamPaper;
    private ExamSession testSession;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(examSessionService, "baseMapper", examSessionMapper);

        testExamPaper = new ExamPaper();
        testExamPaper.setId(1L);
        testExamPaper.setTitle("期末测试");
        testExamPaper.setStatus("PUBLISHED");
        testExamPaper.setStartTime(LocalDateTime.now().minusDays(1));
        testExamPaper.setEndTime(LocalDateTime.now().plusDays(1));
        testExamPaper.setTotalScore(new BigDecimal("100"));
        testExamPaper.setMaxAttempts(3);
        testExamPaper.setDuration(120);

        testSession = new ExamSession();
        testSession.setId(1L);
        testSession.setExamPaperId(1L);
        testSession.setStudentId(100L);
        testSession.setStatus("STARTED");
        testSession.setStartTime(LocalDateTime.now().minusMinutes(30));
        testSession.setAttemptNumber(1);
        testSession.setIpAddress("192.168.1.1");
        testSession.setUserAgent("Mozilla/5.0");
    }

    // ==================== startExam ====================

    @Test
    @DisplayName("开始考试 - 成功开始")
    void testStartExam_Success() {
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);
        when(examSessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 0L);
        when(examSessionMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(examSessionMapper.insert(any(ExamSession.class))).thenReturn(1);

        ExamSession result = examSessionService.startExam(1L, 100L, "192.168.1.1", "Mozilla/5.0");

        assertNotNull(result);
        assertEquals("STARTED", result.getStatus());
        assertEquals(1, result.getAttemptNumber());
        verify(examSessionMapper).insert(any(ExamSession.class));
    }

    @Test
    @DisplayName("开始考试 - 试卷不存在")
    void testStartExam_ExamNotFound() {
        when(examPaperService.getById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examSessionService.startExam(999L, 100L, "192.168.1.1", "Mozilla/5.0"));
        assertEquals("试卷不存在", exception.getMessage());
    }

    @Test
    @DisplayName("开始考试 - 试卷未发布")
    void testStartExam_NotPublished() {
        testExamPaper.setStatus("DRAFT");
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examSessionService.startExam(1L, 100L, "192.168.1.1", "Mozilla/5.0"));
        assertEquals("试卷尚未发布，无法开始考试", exception.getMessage());
    }

    @Test
    @DisplayName("开始考试 - 考试尚未开始")
    void testStartExam_BeforeStartTime() {
        testExamPaper.setStartTime(LocalDateTime.now().plusHours(1));
        testExamPaper.setEndTime(null);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examSessionService.startExam(1L, 100L, "192.168.1.1", "Mozilla/5.0"));
        assertEquals("考试尚未开始", exception.getMessage());
    }

    @Test
    @DisplayName("开始考试 - 考试已结束")
    void testStartExam_AfterEndTime() {
        testExamPaper.setStartTime(LocalDateTime.now().minusDays(2));
        testExamPaper.setEndTime(LocalDateTime.now().minusDays(1));
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examSessionService.startExam(1L, 100L, "192.168.1.1", "Mozilla/5.0"));
        assertEquals("考试已结束", exception.getMessage());
    }

    @Test
    @DisplayName("开始考试 - 超过最大尝试次数")
    void testStartExam_MaxAttemptsExceeded() {
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);
        when(examSessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examSessionService.startExam(1L, 100L, "192.168.1.1", "Mozilla/5.0"));
        assertTrue(exception.getMessage().contains("已达到最大尝试次数限制"));
    }

    @Test
    @DisplayName("开始考试 - 已有进行中的考试")
    void testStartExam_DuplicateActiveSession() {
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);
        when(examSessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(examSessionMapper.selectOne(any(), anyBoolean())).thenReturn(testSession);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examSessionService.startExam(1L, 100L, "192.168.1.1", "Mozilla/5.0"));
        assertEquals("您有正在进行的考试，请先完成或结束当前考试", exception.getMessage());
    }

    // ==================== submitExam ====================

    @Test
    @DisplayName("提交试卷 - 成功提交")
    void testSubmitExam_Success() {
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);

        Map<String, Object> answers = new HashMap<>();
        answers.put("1", "B");

        ExamSession result = examSessionService.submitExam(1L, answers);

        assertNotNull(result);
        assertEquals("SUBMITTED", result.getStatus());
        assertNotNull(result.getSubmitTime());
        assertNotNull(result.getTimeSpent());
        verify(examSessionMapper).updateById(any(ExamSession.class));
    }

    @Test
    @DisplayName("提交试卷 - 重复提交防重")
    void testSubmitExam_DuplicateSubmit() {
        testSession.setStatus("SUBMITTED");
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examSessionService.submitExam(1L, new HashMap<>()));
        assertTrue(exception.getMessage().contains("无法重复提交"));
    }

    @Test
    @DisplayName("提交试卷 - 会话不存在")
    void testSubmitExam_NotFound() {
        when(examSessionMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> examSessionService.submitExam(999L, new HashMap<>()));
        assertEquals("考试会话不存在", exception.getMessage());
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
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Map.of("101", "B"));
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(eq));
        when(questionService.getById(101L)).thenReturn(q);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);

        Map<String, Object> result = examSessionService.autoGrade(1L);

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
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
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
        when(questionService.getById(102L)).thenReturn(q);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);

        Map<String, Object> result = examSessionService.autoGrade(1L);

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
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
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
        when(questionService.getById(102L)).thenReturn(q);
        when(wrongQuestionService.count(any())).thenReturn(0L);
        when(wrongQuestionService.save(any(WrongQuestion.class))).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);

        Map<String, Object> result = examSessionService.autoGrade(1L);

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
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Map.of("103", "true"));
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(eq));
        when(questionService.getById(103L)).thenReturn(q);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);

        Map<String, Object> result = examSessionService.autoGrade(1L);

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
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Map.of("104", "这是一段简答题答案"));
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(eq));
        when(questionService.getById(104L)).thenReturn(q);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);

        Map<String, Object> result = examSessionService.autoGrade(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("0"), result.get("totalScore"));
        assertEquals(1, result.get("pendingReviewCount"));
    }

    // ==================== generateScoreReport ====================

    @Test
    @DisplayName("生成成绩报告 - 优秀等级")
    void testGenerateScoreReport_Excellent() {
        testSession.setStatus("GRADED");
        testSession.setTotalScore(new BigDecimal("95"));
        testSession.setPercentage(new BigDecimal("95.00"));
        testSession.setTimeSpent(3600);
        testSession.setSubmitTime(LocalDateTime.now());

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        Map<String, Object> report = examSessionService.generateScoreReport(1L);

        assertNotNull(report);
        assertEquals("优秀", report.get("gradeLevel"));
        assertEquals(new BigDecimal("95"), report.get("totalScore"));
    }

    @Test
    @DisplayName("生成成绩报告 - 良好等级")
    void testGenerateScoreReport_Good() {
        testSession.setStatus("GRADED");
        testSession.setPercentage(new BigDecimal("85.00"));

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        Map<String, Object> report = examSessionService.generateScoreReport(1L);

        assertEquals("良好", report.get("gradeLevel"));
    }

    @Test
    @DisplayName("生成成绩报告 - 中等等级")
    void testGenerateScoreReport_Medium() {
        testSession.setStatus("GRADED");
        testSession.setPercentage(new BigDecimal("75.00"));

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        Map<String, Object> report = examSessionService.generateScoreReport(1L);

        assertEquals("中等", report.get("gradeLevel"));
    }

    @Test
    @DisplayName("生成成绩报告 - 及格等级")
    void testGenerateScoreReport_Pass() {
        testSession.setStatus("GRADED");
        testSession.setPercentage(new BigDecimal("65.00"));

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        Map<String, Object> report = examSessionService.generateScoreReport(1L);

        assertEquals("及格", report.get("gradeLevel"));
    }

    @Test
    @DisplayName("生成成绩报告 - 不及格等级")
    void testGenerateScoreReport_Fail() {
        testSession.setStatus("GRADED");
        testSession.setPercentage(new BigDecimal("45.00"));

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        Map<String, Object> report = examSessionService.generateScoreReport(1L);

        assertEquals("不及格", report.get("gradeLevel"));
    }

    // ==================== detectCheating ====================

    @Test
    @DisplayName("作弊检测 - IP地址变化检测")
    void testDetectCheating_IPChange() {
        ExamSession otherSession = new ExamSession();
        otherSession.setId(2L);
        otherSession.setIpAddress("10.0.0.1");
        otherSession.setUserAgent("Mozilla/5.0");

        testSession.setTimeSpent(600);

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(otherSession));

        List<String> suspicions = examSessionService.detectCheating(1L);

        assertNotNull(suspicions);
        assertFalse(suspicions.isEmpty());
        assertTrue(suspicions.stream().anyMatch(s -> s.contains("IP地址变化")));
    }

    @Test
    @DisplayName("作弊检测 - 答题时间过短")
    void testDetectCheating_ShortTime() {
        testSession.setTimeSpent(30);

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<String> suspicions = examSessionService.detectCheating(1L);

        assertNotNull(suspicions);
        assertTrue(suspicions.stream().anyMatch(s -> s.contains("答题时间过短")));
    }

    @Test
    @DisplayName("作弊检测 - 无作弊嫌疑")
    void testDetectCheating_NoCheating() {
        testSession.setTimeSpent(3600);

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<String> suspicions = examSessionService.detectCheating(1L);

        assertNotNull(suspicions);
        assertTrue(suspicions.isEmpty());
    }
}