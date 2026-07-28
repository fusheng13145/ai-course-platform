package com.aicourse.service.impl.exam;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 考试会话查询/统计/防作弊协作者单元测试（迁移自原 ExamSessionServiceImplTest 的对应用例，并补充未覆盖方法）。
 * 直接针对 {@link ExamSessionQueryService}，保持等价断言强度。
 */
@ExtendWith(MockitoExtension.class)
class ExamSessionQueryServiceTest {

    @Mock private ExamPaperService examPaperService;
    @Mock private ExamQuestionService examQuestionService;
    @Mock private QuestionService questionService;
    @Mock private ObjectMapper objectMapper;
    @Mock private ExamSessionMapper examSessionMapper;

    @InjectMocks private ExamSessionQueryService queryService;

    private ExamPaper testExamPaper;
    private ExamSession testSession;

    @BeforeEach
    void setUp() {
        testExamPaper = new ExamPaper();
        testExamPaper.setId(1L);
        testExamPaper.setTitle("期末测试");
        testExamPaper.setTotalScore(new BigDecimal("100"));
        testExamPaper.setDuration(120);

        testSession = new ExamSession();
        testSession.setId(1L);
        testSession.setExamPaperId(1L);
        testSession.setStudentId(100L);
        testSession.setStatus("GRADED");
        testSession.setStartTime(LocalDateTime.now().minusMinutes(30));
        testSession.setAttemptNumber(1);
        testSession.setIpAddress("192.168.1.1");
        testSession.setUserAgent("Mozilla/5.0");
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

        Map<String, Object> report = queryService.generateScoreReport(1L);

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

        Map<String, Object> report = queryService.generateScoreReport(1L);
        assertEquals("良好", report.get("gradeLevel"));
    }

    @Test
    @DisplayName("生成成绩报告 - 中等等级")
    void testGenerateScoreReport_Medium() {
        testSession.setStatus("GRADED");
        testSession.setPercentage(new BigDecimal("75.00"));
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        Map<String, Object> report = queryService.generateScoreReport(1L);
        assertEquals("中等", report.get("gradeLevel"));
    }

    @Test
    @DisplayName("生成成绩报告 - 及格等级")
    void testGenerateScoreReport_Pass() {
        testSession.setStatus("GRADED");
        testSession.setPercentage(new BigDecimal("65.00"));
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        Map<String, Object> report = queryService.generateScoreReport(1L);
        assertEquals("及格", report.get("gradeLevel"));
    }

    @Test
    @DisplayName("生成成绩报告 - 不及格等级")
    void testGenerateScoreReport_Fail() {
        testSession.setStatus("GRADED");
        testSession.setPercentage(new BigDecimal("45.00"));
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper);

        Map<String, Object> report = queryService.generateScoreReport(1L);
        assertEquals("不及格", report.get("gradeLevel"));
    }

    @Test
    @DisplayName("生成成绩报告 - 会话不存在")
    void testGenerateScoreReport_NotFound() {
        when(examSessionMapper.selectById(999L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> queryService.generateScoreReport(999L));
        assertEquals("考试会话不存在", ex.getMessage());
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

        List<String> suspicions = queryService.detectCheating(1L);

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

        List<String> suspicions = queryService.detectCheating(1L);

        assertNotNull(suspicions);
        assertTrue(suspicions.stream().anyMatch(s -> s.contains("答题时间过短")));
    }

    @Test
    @DisplayName("作弊检测 - 无作弊嫌疑")
    void testDetectCheating_NoCheating() {
        testSession.setTimeSpent(3600);
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<String> suspicions = queryService.detectCheating(1L);

        assertNotNull(suspicions);
        assertTrue(suspicions.isEmpty());
    }

    // ==================== 新增覆盖 ====================

    @Test
    @DisplayName("进行中列表 - 返回 STARTED 会话")
    void testGetActiveExams() {
        ExamSession s = new ExamSession();
        s.setId(9L);
        when(examSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));

        List<ExamSession> result = queryService.getActiveExams();

        assertEquals(1, result.size());
        assertEquals(9L, result.get(0).getId());
        verify(examSessionMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("剩余时间 - 未开始状态返回0")
    void testGetRemainingTime_NotStarted() {
        testSession.setStatus("SUBMITTED");
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);

        assertEquals(0L, queryService.getRemainingTime(1L));
    }

    @Test
    @DisplayName("剩余时间 - 进行中按考试时长计算")
    void testGetRemainingTime_Started() {
        testSession.setStatus("STARTED");
        testSession.setStartTime(LocalDateTime.now().minusMinutes(30));
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examPaperService.getById(1L)).thenReturn(testExamPaper); // duration=120

        // 120*60 - 30*60 = 5400 秒
        assertEquals(5400L, queryService.getRemainingTime(1L));
    }

    @Test
    @DisplayName("最新会话 - 返回最新一条")
    void testGetLatestSession() {
        when(examSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testSession);
        ExamSession result = queryService.getLatestSession(1L, 100L);
        assertSame(testSession, result);
        verify(examSessionMapper).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("题目列表 - 应用会话维度题目与缺省选项")
    void testGetSessionQuestions() {
        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(101L);
        eq.setExamPaperId(1L);
        eq.setPoints(new BigDecimal("5"));
        eq.setOrderIndex(1);

        Question q = new Question();
        q.setId(101L);
        q.setType("SINGLE_CHOICE");
        q.setContent("1+1=?");
        q.setOptions("[\"1\",\"2\",\"3\"]");

        testSession.setQuestionOrder(null); // 回退到试卷原始顺序
        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(eq));
        when(questionService.listByIds(any())).thenReturn(List.of(q));

        List<Map<String, Object>> result = queryService.getSessionQuestions(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).get("questionId"));
        assertEquals(new BigDecimal("5"), result.get(0).get("points"));
        // optionOrders 为空 -> 选项按原样返回
        assertEquals("[\"1\",\"2\",\"3\"]", result.get(0).get("options"));
    }

    @Test
    @DisplayName("作弊标记 - 无嫌疑写入 LOW")
    void testCheckCheating_NoSuspicion() throws Exception {
        testSession.setStatus("STARTED");
        testSession.setTimeSpent(3600); // 不触发短时
        testSession.setSubmitTime(null);

        when(examSessionMapper.selectById(1L)).thenReturn(testSession);
        when(examSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examSessionMapper.updateById((ExamSession) any())).thenReturn(1);

        Map<String, Object> result = queryService.checkCheating(1L);

        assertNotNull(result);
        assertEquals(false, result.get("hasSuspicion"));
        assertEquals("LOW", result.get("riskLevel"));
        verify(examSessionMapper).updateById((ExamSession) any());
    }
}
