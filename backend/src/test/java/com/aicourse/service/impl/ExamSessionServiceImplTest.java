package com.aicourse.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.UserService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.service.component.exam.ExamSessionCheatingComponent;
import com.aicourse.service.component.exam.ExamSessionLifecycleComponent;
import com.aicourse.service.component.exam.ExamSessionQueryComponent;
import com.aicourse.service.impl.exam.ExamSessionGradingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aicourse.vo.ActiveExamSessionVO;
import com.aicourse.vo.AutoGradeResultVO;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 考试会话服务单元测试。
 * <p>本测试验证门面各接口方法的实际行为（正常路径 + 关键分支）。
 * 阅卷逻辑（autoGrade / manualGrade / calculateTotalScore）已统一收口到
 * {@link ExamSessionGradingService}（逻辑细节由 ExamSessionGradingServiceTest 覆盖），
 * 此处验证门面委托行为与异常传播。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExamSessionServiceImplTest {

    @Mock private ExamPaperService examPaperService;
    @Mock private ExamQuestionService examQuestionService;
    @Mock private QuestionService questionService;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private ExamSessionGradingService gradingService;
    @Mock private ExamSessionMapper examSessionMapper;

    private ExamSessionServiceImpl examSessionService;

    private ExamPaper publishedPaper;
    private ExamSession startedSession;

    @BeforeEach
    void setUp() {
        // 原兼容构造器已移除：内联其组件组装逻辑，再走主构造器（3 组件 + gradingService）
        ObjectMapper objectMapper = new ObjectMapper();
        ExamSessionLifecycleComponent lifecycleComponent =
                new ExamSessionLifecycleComponent(examPaperService, null, objectMapper);
        ExamSessionCheatingComponent cheatingComponent =
                new ExamSessionCheatingComponent(examPaperService, null, objectMapper);
        ExamSessionQueryComponent queryComponent =
                new ExamSessionQueryComponent(examPaperService, examQuestionService, null, objectMapper,
                        mock(CourseService.class), mock(UserService.class));
        examSessionService = new ExamSessionServiceImpl(lifecycleComponent, cheatingComponent,
                queryComponent, gradingService);
        ReflectionTestUtils.setField(examSessionService, "baseMapper", examSessionMapper);
        for (String field : new String[]{"lifecycleComponent", "cheatingComponent", "queryComponent"}) {
            ReflectionTestUtils.setField(ReflectionTestUtils.getField(examSessionService, field), "examSessionMapper", examSessionMapper);
        }
        // 注册 ExamSession 的 lambda 元数据，使 LambdaUpdateWrapper 在无 Spring 上下文时可用
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamSession.class);

        publishedPaper = new ExamPaper();
        publishedPaper.setId(1L);
        publishedPaper.setTitle("期末测试");
        publishedPaper.setStatus("PUBLISHED");
        publishedPaper.setStartTime(LocalDateTime.now().minusHours(1));
        publishedPaper.setEndTime(LocalDateTime.now().plusHours(1));
        publishedPaper.setTotalScore(new BigDecimal("100"));
        publishedPaper.setMaxAttempts(3);
        publishedPaper.setDuration(60);

        startedSession = new ExamSession();
        startedSession.setId(1L);
        startedSession.setExamPaperId(1L);
        startedSession.setStudentId(2L);
        startedSession.setStatus("STARTED");
        startedSession.setStartTime(LocalDateTime.now().minusMinutes(5));
        startedSession.setAttemptNumber(1);
        startedSession.setIpAddress("192.168.1.1");
        startedSession.setUserAgent("Mozilla/5.0");
    }

    // ===================== startExam =====================

    @Test
    @DisplayName("startExam - 成功创建考试会话")
    void testStartExam_Success() {
        when(examPaperService.getById(1L)).thenReturn(publishedPaper);
        when(examSessionMapper.selectCount(any())).thenReturn(0L, 0L);
        when(examSessionMapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(examSessionMapper.insert(any(ExamSession.class))).thenReturn(1);

        ExamSession result = examSessionService.startExam(1L, 2L, "192.168.1.1", "Mozilla/5.0");

        assertNotNull(result);
        assertEquals(1L, result.getExamPaperId());
        assertEquals(2L, result.getStudentId());
        assertEquals("STARTED", result.getStatus());
        assertEquals(1, result.getAttemptNumber());
        assertEquals("192.168.1.1", result.getIpAddress());
        assertEquals("Mozilla/5.0", result.getUserAgent());
        assertNotNull(result.getStartTime());
        verify(examSessionMapper).insert(any(ExamSession.class));
    }

    @Test
    @DisplayName("startExam - 试卷不存在")
    void testStartExam_PaperNotFound() {
        when(examPaperService.getById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.startExam(999L, 2L, "ip", "ua"));
        assertEquals("试卷不存在", ex.getMessage());
    }

    @Test
    @DisplayName("startExam - 试卷未发布")
    void testStartExam_NotPublished() {
        publishedPaper.setStatus("DRAFT");
        when(examPaperService.getById(1L)).thenReturn(publishedPaper);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.startExam(1L, 2L, "ip", "ua"));
        assertEquals("试卷尚未发布，无法开始考试", ex.getMessage());
    }

    @Test
    @DisplayName("startExam - 未到开始时间")
    void testStartExam_BeforeStartTime() {
        publishedPaper.setStartTime(LocalDateTime.now().plusHours(1));
        when(examPaperService.getById(1L)).thenReturn(publishedPaper);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.startExam(1L, 2L, "ip", "ua"));
        assertEquals("考试尚未开始", ex.getMessage());
    }

    @Test
    @DisplayName("startExam - 已过结束时间")
    void testStartExam_AfterEndTime() {
        publishedPaper.setEndTime(LocalDateTime.now().minusMinutes(1));
        when(examPaperService.getById(1L)).thenReturn(publishedPaper);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.startExam(1L, 2L, "ip", "ua"));
        assertEquals("考试已结束", ex.getMessage());
    }

    @Test
    @DisplayName("startExam - 达到最大尝试次数")
    void testStartExam_MaxAttemptsReached() {
        when(examPaperService.getById(1L)).thenReturn(publishedPaper);
        when(examSessionMapper.selectCount(any())).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.startExam(1L, 2L, "ip", "ua"));
        assertEquals("已达到最大尝试次数限制（3次）", ex.getMessage());
    }

    @Test
    @DisplayName("startExam - 存在进行中的考试")
    void testStartExam_ActiveSessionExists() {
        when(examPaperService.getById(1L)).thenReturn(publishedPaper);
        when(examSessionMapper.selectCount(any())).thenReturn(0L);
        when(examSessionMapper.selectOne(any(), anyBoolean())).thenReturn(startedSession);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.startExam(1L, 2L, "ip", "ua"));
        assertEquals("您有正在进行的考试，请先完成或结束当前考试", ex.getMessage());
    }

    // ===================== autoSaveAnswers =====================

    @Test
    @DisplayName("autoSaveAnswers - 将答案序列化为JSON保存")
    void testAutoSaveAnswers_SavesJson() {
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);

        Map<String, Object> answers = new HashMap<>();
        answers.put("101", "A");
        examSessionService.autoSaveAnswers(1L, answers);

        assertEquals("{\"101\":\"A\"}", startedSession.getAnswers());
        verify(examSessionMapper).updateById(startedSession);
    }

    @Test
    @DisplayName("autoSaveAnswers - 会话不存在")
    void testAutoSaveAnswers_SessionNotFound() {
        when(examSessionMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.autoSaveAnswers(999L, new HashMap<>()));
        assertEquals("考试会话不存在", ex.getMessage());
    }

    @Test
    @DisplayName("autoSaveAnswers - 非进行中状态不允许保存")
    void testAutoSaveAnswers_NotStarted() {
        startedSession.setStatus("SUBMITTED");
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.autoSaveAnswers(1L, new HashMap<>()));
        assertEquals("当前考试状态不允许保存答案", ex.getMessage());
    }

    // ===================== submitExam =====================

    @Test
    @DisplayName("submitExam - 提交成功并返回最新会话")
    void testSubmitExam_Success() {
        when(examSessionMapper.update(isNull(), any())).thenReturn(1);

        ExamSession submitted = new ExamSession();
        submitted.setId(1L);
        submitted.setStatus("SUBMITTED");
        submitted.setAnswers("{\"101\":\"A\"}");
        submitted.setTimeSpent(300);
        submitted.setSubmitTime(LocalDateTime.now());
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession, submitted);

        Map<String, Object> answers = new HashMap<>();
        answers.put("101", "A");
        ExamSession result = examSessionService.submitExam(1L, answers);

        assertNotNull(result);
        assertEquals("SUBMITTED", result.getStatus());
        assertEquals("{\"101\":\"A\"}", result.getAnswers());
        assertNotNull(result.getSubmitTime());
        verify(examSessionMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("submitExam - 会话不存在")
    void testSubmitExam_SessionNotFound() {
        when(examSessionMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.submitExam(999L, new HashMap<>()));
        assertEquals("考试会话不存在", ex.getMessage());
    }

    @Test
    @DisplayName("submitExam - 重复提交被拒绝")
    void testSubmitExam_DuplicateSubmission() {
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);
        when(examSessionMapper.update(isNull(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.submitExam(1L, new HashMap<>()));
        assertEquals("该试卷已提交或已结束，无法重复提交", ex.getMessage());
    }

    // ===================== forceSubmit =====================

    @Test
    @DisplayName("forceSubmit - 强制交卷成功")
    void testForceSubmit_Success() {
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);

        ExamSession result = examSessionService.forceSubmit(1L);

        assertEquals("FORCE_SUBMITTED", result.getStatus());
        assertNotNull(result.getSubmitTime());
        assertNotNull(result.getTimeSpent());
        verify(examSessionMapper).updateById(startedSession);
    }

    @Test
    @DisplayName("forceSubmit - 已提交试卷无需强制交卷")
    void testForceSubmit_AlreadySubmitted() {
        startedSession.setStatus("SUBMITTED");
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.forceSubmit(1L));
        assertEquals("该试卷已经提交，无需强制交卷", ex.getMessage());
    }

    // ===================== getSessionQuestions =====================

    @Test
    @DisplayName("getSessionQuestions - 返回会话题目列表")
    void testGetSessionQuestions_ReturnsMappedQuestions() {
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);

        ExamQuestion eq1 = new ExamQuestion();
        eq1.setQuestionId(101L);
        eq1.setOrderIndex(1);
        eq1.setPoints(new BigDecimal("5"));
        ExamQuestion eq2 = new ExamQuestion();
        eq2.setQuestionId(102L);
        eq2.setOrderIndex(2);
        eq2.setPoints(new BigDecimal("10"));
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(java.util.Arrays.asList(eq1, eq2));

        List<Map<String, Object>> questions = examSessionService.getSessionQuestions(1L);

        assertEquals(2, questions.size());
        assertEquals(101L, questions.get(0).get("questionId"));
        assertEquals(1, questions.get(0).get("orderIndex"));
        assertEquals(new BigDecimal("5"), questions.get(0).get("score"));
        assertEquals(102L, questions.get(1).get("questionId"));
    }

    @Test
    @DisplayName("getSessionQuestions - 会话不存在返回空列表")
    void testGetSessionQuestions_SessionNotFound() {
        when(examSessionMapper.selectById(999L)).thenReturn(null);

        List<Map<String, Object>> questions = examSessionService.getSessionQuestions(999L);

        assertTrue(questions.isEmpty());
    }

    // ===================== timeoutSubmit =====================

    @Test
    @DisplayName("timeoutSubmit - 超时自动收卷：仅流转状态，保留已存答案，不覆盖 answers")
    void testTimeoutSubmit_AutoSubmits() {
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);
        when(examSessionMapper.update(isNull(), any())).thenReturn(1);

        examSessionService.timeoutSubmit(1L);

        // 修复 P0-2：超时收卷只更新状态/提交时间（LambdaUpdateWrapper），
        // 不得调用 submitExam 清空学生已保存的 answers，也不再二次 updateById
        verify(examSessionMapper).update(isNull(), any());
        verify(examSessionMapper, never()).updateById(any(ExamSession.class));
    }

    @Test
    @DisplayName("timeoutSubmit - 非进行中会话不处理")
    void testTimeoutSubmit_NoopWhenNotStarted() {
        startedSession.setStatus("SUBMITTED");
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);

        examSessionService.timeoutSubmit(1L);

        verify(examSessionMapper, never()).update(isNull(), any());
        verify(examSessionMapper, never()).updateById(any(ExamSession.class));
    }
    // ===================== autoGrade（委托 GradingService） =====================

    @Test
    @DisplayName("autoGrade - 委托 GradingService 并原样返回结果")
    void testAutoGrade_Delegates() {
        AutoGradeResultVO expected = new AutoGradeResultVO(1L, new BigDecimal("5"), null, null, 0, 0, 0, Map.of(), null);
        when(gradingService.autoGrade(1L)).thenReturn(expected);

        AutoGradeResultVO result = examSessionService.autoGrade(1L);

        assertSame(expected, result);
        verify(gradingService).autoGrade(1L);
    }

    @Test
    @DisplayName("autoGrade - GradingService 校验失败异常原样传播")
    void testAutoGrade_PropagatesBusinessException() {
        when(gradingService.autoGrade(1L))
                .thenThrow(new BusinessException("试卷尚未提交，无法进行自动阅卷"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.autoGrade(1L));
        assertEquals("试卷尚未提交，无法进行自动阅卷", ex.getMessage());
        verify(gradingService).autoGrade(1L);
    }

    // ===================== manualGrade（委托 GradingService） =====================

    @Test
    @DisplayName("manualGrade - 委托 GradingService")
    void testManualGrade_Delegates() {
        examSessionService.manualGrade(1L, 102L, new BigDecimal("8"), "评语");

        verify(gradingService).manualGrade(1L, 102L, new BigDecimal("8"), "评语");
    }

    @Test
    @DisplayName("manualGrade - GradingService 校验失败异常原样传播")
    void testManualGrade_PropagatesBusinessException() {
        doThrow(new BusinessException("请先完成自动阅卷"))
                .when(gradingService).manualGrade(eq(1L), eq(102L), any(BigDecimal.class), anyString());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.manualGrade(1L, 102L, BigDecimal.TEN, "评语"));
        assertEquals("请先完成自动阅卷", ex.getMessage());
        verify(gradingService).manualGrade(eq(1L), eq(102L), any(BigDecimal.class), anyString());
    }

    // ===================== calculateTotalScore（委托 GradingService） =====================

    @Test
    @DisplayName("calculateTotalScore - 委托 GradingService 并原样返回结果")
    void testCalculateTotalScore_Delegates() {
        when(gradingService.calculateTotalScore(1L)).thenReturn(new BigDecimal("5"));

        BigDecimal total = examSessionService.calculateTotalScore(1L);

        assertEquals(new BigDecimal("5"), total);
        verify(gradingService).calculateTotalScore(1L);
    }

    @Test
    @DisplayName("calculateTotalScore - GradingService 异常原样传播")
    void testCalculateTotalScore_PropagatesBusinessException() {
        when(gradingService.calculateTotalScore(999L))
                .thenThrow(new BusinessException("考试会话不存在"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.calculateTotalScore(999L));
        assertEquals("考试会话不存在", ex.getMessage());
    }


    // ===================== generateScoreReport =====================

    @Test
    @DisplayName("generateScoreReport - 生成含等级评定的成绩报告")
    void testGenerateScoreReport_ContainsReportFields() {
        startedSession.setStatus("GRADED");
        startedSession.setStartTime(LocalDateTime.now().minusMinutes(30));
        startedSession.setSubmitTime(LocalDateTime.now());
        startedSession.setTimeSpent(1800);
        startedSession.setTotalScore(new BigDecimal("90"));
        startedSession.setPercentage(new BigDecimal("90.00"));
        startedSession.setQuestionScores("{\"101\":90}");
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);
        when(examPaperService.getById(1L)).thenReturn(publishedPaper);

        Map<String, Object> report = examSessionService.generateScoreReport(1L);

        assertEquals(1L, report.get("sessionId"));
        assertEquals(1L, report.get("examPaperId"));
        assertEquals("期末测试", report.get("examPaperTitle"));
        assertEquals(2L, report.get("studentId"));
        assertEquals("GRADED", report.get("status"));
        assertEquals(new BigDecimal("90"), report.get("totalScore"));
        assertEquals("优秀", report.get("gradeLevel"));

        @SuppressWarnings("unchecked")
        Map<Long, BigDecimal> questionDetails = (Map<Long, BigDecimal>) report.get("questionDetails");
        assertEquals(new BigDecimal("90"), questionDetails.get(101L));
    }

    @Test
    @DisplayName("generateScoreReport - 会话不存在")
    void testGenerateScoreReport_SessionNotFound() {
        when(examSessionMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> examSessionService.generateScoreReport(999L));
        assertEquals("考试会话不存在", ex.getMessage());
    }

    // ===================== getActiveExams =====================

    @Test
    @DisplayName("getActiveExams - 返回进行中会话列表")
    void testGetActiveExams_ReturnsStartedSessions() {
        ExamSession s2 = new ExamSession();
        s2.setId(2L);
        s2.setStatus("STARTED");
        when(examSessionMapper.selectList(any())).thenReturn(java.util.Arrays.asList(startedSession, s2));

        List<ActiveExamSessionVO> active = examSessionService.getActiveExams();

        assertEquals(2, active.size());
        assertTrue(active.stream().allMatch(s -> "STARTED".equals(s.getStatus())));
        assertTrue(active.stream().allMatch(s -> s.getParticipantCount() != null));
        verify(examSessionMapper).selectList(any());
    }

    // ===================== detectCheating =====================

    @Test
    @DisplayName("detectCheating - 检测IP/设备变化与异常短时答题")
    void testDetectCheating_ReturnsSuspicions() {
        startedSession.setTimeSpent(30);
        startedSession.setSubmitTime(LocalDateTime.now());
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);

        ExamSession other = new ExamSession();
        other.setIpAddress("2.2.2.2");
        other.setUserAgent("OtherUA");
        when(examSessionMapper.selectList(any())).thenReturn(Collections.singletonList(other));

        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        paper.setEndTime(LocalDateTime.now().minusMinutes(1));
        when(examPaperService.getById(1L)).thenReturn(paper);

        List<String> suspicions = examSessionService.detectCheating(1L);

        assertEquals(4, suspicions.size());
        assertTrue(suspicions.stream().anyMatch(s -> s.contains("IP地址变化")));
        assertTrue(suspicions.stream().anyMatch(s -> s.contains("浏览器/设备变化")));
        assertTrue(suspicions.stream().anyMatch(s -> s.contains("答题时间过短")));
        assertTrue(suspicions.stream().anyMatch(s -> s.contains("在考试结束后提交")));
    }

    @Test
    @DisplayName("detectCheating - 无异常返回空列表")
    void testDetectCheating_NoSuspicion() {
        startedSession.setTimeSpent(120);
        startedSession.setSubmitTime(null);
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);
        when(examSessionMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<String> suspicions = examSessionService.detectCheating(1L);

        assertTrue(suspicions.isEmpty());
    }

    // ===================== getRemainingTime =====================

    @Test
    @DisplayName("getRemainingTime - 基于考试时长计算剩余秒数")
    void testGetRemainingTime_DurationBased() {
        startedSession.setStartTime(LocalDateTime.now().minusSeconds(30));
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        paper.setDuration(60);
        paper.setEndTime(null);
        when(examPaperService.getById(1L)).thenReturn(paper);

        Long remaining = examSessionService.getRemainingTime(1L);

        assertNotNull(remaining);
        assertTrue(remaining > 3500 && remaining <= 3600, "剩余时间应在 60 分钟减去已用时附近，实际=" + remaining);
    }

    @Test
    @DisplayName("getRemainingTime - 非进行中会话返回0")
    void testGetRemainingTime_NotStarted() {
        startedSession.setStatus("SUBMITTED");
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);

        assertEquals(0L, examSessionService.getRemainingTime(1L));
    }

    // ===================== getLatestSession =====================

    @Test
    @DisplayName("getLatestSession - 返回最新会话")
    void testGetLatestSession_ReturnsLatest() {
        when(examSessionMapper.selectOne(any(), anyBoolean())).thenReturn(startedSession);

        ExamSession result = examSessionService.getLatestSession(1L, 2L);

        assertSame(startedSession, result);
        verify(examSessionMapper).selectOne(any(), anyBoolean());
    }

    // ===================== checkCheating =====================

    @Test
    @DisplayName("checkCheating - 无嫌疑返回LOW风险且不写标记")
    void testCheckCheating_NoSuspicion() {
        startedSession.setTimeSpent(120);
        startedSession.setSubmitTime(null);
        startedSession.setAnswers("{\"101\":\"A\"}");
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);
        when(examSessionMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = examSessionService.checkCheating(1L);

        assertEquals(1L, result.get("sessionId"));
        assertFalse((Boolean) result.get("hasSuspicion"));
        assertEquals(0, result.get("suspicionCount"));
        assertEquals("LOW", result.get("riskLevel"));
        verify(examSessionMapper, never()).updateById(any(ExamSession.class));
    }

    @Test
    @DisplayName("checkCheating - 有嫌疑返回MEDIUM风险并写入标记")
    void testCheckCheating_WithSuspicion() {
        startedSession.setTimeSpent(30);
        startedSession.setSubmitTime(null);
        startedSession.setAnswers("{}");
        when(examSessionMapper.selectById(1L)).thenReturn(startedSession);
        when(examSessionMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = examSessionService.checkCheating(1L);

        assertTrue((Boolean) result.get("hasSuspicion"));
        assertEquals(1, result.get("suspicionCount"));
        assertEquals("MEDIUM", result.get("riskLevel"));
        assertTrue(startedSession.getAnswers().contains("cheatingFlags"));
        verify(examSessionMapper).updateById(startedSession);
    }
}
