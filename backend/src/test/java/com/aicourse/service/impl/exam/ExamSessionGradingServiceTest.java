package com.aicourse.service.impl.exam;

import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.vo.AutoGradeResultVO;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("5"), result.getTotalScore());
        assertEquals(1, result.getCorrectCount());
        assertEquals(0, result.getWrongCount());
        assertEquals("GRADED", result.getStatus());

        // 修复弱测试：原用例对 objectMapper 全部用 any() 打桩，未校验实际序列化/反序列化载荷。
        // 现用 ArgumentCaptor 断言：
        //  1) 反序列化入参确为会话原始作答 JSON（{"101":"B"}）；
        //  2) 序列化入参确为「题目得分映射」（含 101→5），确保写入 questionScores 的是逐题分数而非其它对象。
        ArgumentCaptor<String> readArg = ArgumentCaptor.forClass(String.class);
        verify(objectMapper, atLeastOnce()).readValue(readArg.capture(), any(TypeReference.class));
        assertTrue(readArg.getAllValues().contains(testSession.getAnswers()),
                "readValue 应解析会话作答 JSON");
        ArgumentCaptor<Object> writeArg = ArgumentCaptor.forClass(Object.class);
        verify(objectMapper).writeValueAsString(writeArg.capture());
        assertEquals(Map.of(101L, new BigDecimal("5")), writeArg.getValue(),
                "writeValueAsString 应序列化逐题得分映射");
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

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("10"), result.getTotalScore());
        assertEquals(1, result.getCorrectCount());
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

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("2.50"), result.getTotalScore());
        assertEquals(1, result.getWrongCount());
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

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("5"), result.getTotalScore());
        assertEquals(1, result.getCorrectCount());
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

        AutoGradeResultVO result = gradingService.autoGrade(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("0"), result.getTotalScore());
        assertEquals(1, result.getPendingReviewCount());
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

    // ==================== D5 人工阅卷分数边界校验 ====================

    @Test
    @DisplayName("人工阅卷 - 分数为空拒绝（D5 回归：null 不得写入导致后续拆箱炸裂）")
    void testManualGrade_nullScore_rejected() {
        testSession.setStatus("GRADED");
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> gradingService.manualGrade(1L, 101L, null, "good"));
        assertEquals("人工阅卷分数不能为空", ex.getMessage());
        verify(examSessionMapper, never()).updateById((ExamSession) any());
    }

    @Test
    @DisplayName("人工阅卷 - 负分拒绝（D5 回归：防止 percentage 越界为负）")
    void testManualGrade_negativeScore_rejected() {
        testSession.setStatus("GRADED");
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> gradingService.manualGrade(1L, 101L, new BigDecimal("-1"), "good"));
        assertEquals("人工阅卷分数不能为负数", ex.getMessage());
        verify(examSessionMapper, never()).updateById((ExamSession) any());
    }

    @Test
    @DisplayName("人工阅卷 - 超过该题满分拒绝（D5 回归：上界取 ExamQuestion.points）")
    void testManualGrade_exceedQuestionPoints_rejected() {
        testSession.setStatus("GRADED");
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(101L);
        eq.setExamPaperId(1L);
        eq.setPoints(new BigDecimal("5"));
        when(examQuestionService.getOne(any(LambdaQueryWrapper.class))).thenReturn(eq);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> gradingService.manualGrade(1L, 101L, new BigDecimal("6"), "good"));
        assertTrue(ex.getMessage().contains("不能超过该题满分"),
                "拒绝信息应包含满分上界，实际=" + ex.getMessage());
        verify(examSessionMapper, never()).updateById((ExamSession) any());
    }

    @Test
    @DisplayName("人工阅卷 - 0.00（带 scale 的零）合法放行（D5 回归：null/负值判定须用 compareTo）")
    void testManualGrade_zeroWithScale_accepted() throws Exception {
        testSession.setStatus("GRADED");
        testSession.setQuestionScores(null); // 走空分表路径，避免 readValue 依赖
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examQuestionService.getOne(any(LambdaQueryWrapper.class))).thenReturn(null); // 无上界来源→跳过上界
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);

        gradingService.manualGrade(1L, 101L, new BigDecimal("0.00"), "good");

        // 0.00 既非 null 也非负数，应正常落库（updateById 被调用），且总分为 0
        verify(examSessionMapper).updateById((ExamSession) any());
        assertEquals(0, testSession.getTotalScore().compareTo(BigDecimal.ZERO));
    }

    // ==================== D6 错题练习次数 null 拆箱 ====================

    @Test
    @DisplayName("自动阅卷 - 已有错题 practiceCount 为 null 时再次答错次数变 1 且不丢更新（D6 回归）")
    void testAutoGrade_practiceCountNull_incrementsTo1() throws Exception {
        testSession.setStatus("SUBMITTED");
        testSession.setAnswers("{\"101\":\"A\"}"); // 学生答 A

        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(101L);
        eq.setExamPaperId(1L);
        eq.setPoints(new BigDecimal("5"));
        eq.setOrderIndex(1);

        Question q = new Question();
        q.setId(101L);
        q.setType("SINGLE_CHOICE");
        q.setCorrectAnswers("B"); // 正确答案 B → 答错

        // 已存在的错题记录：practiceCount 为 null（历史/未初始化数据）
        WrongQuestion existing = new WrongQuestion();
        existing.setId(500L);
        existing.setStudentId(100L);
        existing.setQuestionId(101L);
        existing.setPracticeCount(null);

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(Map.of("101", "A"));
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(any())).thenReturn(List.of(q));
        when(questionService.getById(101L)).thenReturn(q);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);
        ExamPaper paperWithCourse = new ExamPaper();
        paperWithCourse.setId(1L);
        paperWithCourse.setCourseId(7L);
        when(examPaperService.getById(1L)).thenReturn(paperWithCourse);
        when(wrongQuestionService.count(any())).thenReturn(1L); // 命中已存在分支
        when(wrongQuestionService.getOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        assertDoesNotThrow(() -> gradingService.autoGrade(1L));

        // D6 核心断言：更新被真正执行且次数由 null→1（修复前 NPE 被外层 catch 吞掉，updateById 从不触发）
        ArgumentCaptor<WrongQuestion> captor = ArgumentCaptor.forClass(WrongQuestion.class);
        verify(wrongQuestionService).updateById(captor.capture());
        assertEquals(1, captor.getValue().getPracticeCount(),
                "practiceCount 为 null 时再次答错应累加为 1");
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
