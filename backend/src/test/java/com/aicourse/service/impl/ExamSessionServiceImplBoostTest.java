package com.aicourse.service.impl;

import com.aicourse.constant.ExamSessionStatus;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Question;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.service.impl.exam.ExamSessionGradingService;
import com.aicourse.vo.AutoGradeResultVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 纯 Mockito 单元测试：补充覆盖 ExamSessionServiceImpl 的考试流控、阅卷委托、报告/检测/计时等分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamSessionServiceImplBoostTest {

    @Mock
    private ExamSessionMapper examSessionMapper;
    @Mock
    private ExamPaperService examPaperService;
    @Mock
    private ExamQuestionService examQuestionService;
    @Mock
    private QuestionService questionService;
    @Mock
    private WrongQuestionService wrongQuestionService;
    @Mock
    private ExamSessionGradingService gradingService;

    private ExamSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExamSession.class);
        service = new ExamSessionServiceImpl(examPaperService, examQuestionService,
                questionService, wrongQuestionService, gradingService);
        ReflectionTestUtils.setField(service, "baseMapper", examSessionMapper);
        // 同步 mapper 到内部组件（兼容构造器组装的真实组件初始持有 null mapper）
        for (String field : new String[]{"lifecycleComponent", "cheatingComponent", "queryComponent"}) {
            ReflectionTestUtils.setField(ReflectionTestUtils.getField(service, field), "examSessionMapper", examSessionMapper);
        }
    }

    private ExamPaper publishedPaper() {
        ExamPaper p = new ExamPaper();
        p.setId(1L);
        p.setStatus("PUBLISHED");
        return p;
    }

    // ===== startExam =====

    @Test
    @DisplayName("startExam 试卷不存在抛异常")
    void start_notFound() {
        when(examPaperService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.startExam(1L, 2L, "ip", "ua"));
    }

    @Test
    @DisplayName("startExam 未发布抛异常")
    void start_notPublished() {
        ExamPaper p = publishedPaper();
        p.setStatus("DRAFT");
        when(examPaperService.getById(1L)).thenReturn(p);
        assertThrows(BusinessException.class, () -> service.startExam(1L, 2L, "ip", "ua"));
    }

    @Test
    @DisplayName("startExam 考试尚未开始")
    void start_beforeStart() {
        ExamPaper p = publishedPaper();
        p.setStartTime(LocalDateTime.now().plusDays(1));
        when(examPaperService.getById(1L)).thenReturn(p);
        assertThrows(BusinessException.class, () -> service.startExam(1L, 2L, "ip", "ua"));
    }

    @Test
    @DisplayName("startExam 考试已结束")
    void start_afterEnd() {
        ExamPaper p = publishedPaper();
        p.setEndTime(LocalDateTime.now().minusDays(1));
        when(examPaperService.getById(1L)).thenReturn(p);
        assertThrows(BusinessException.class, () -> service.startExam(1L, 2L, "ip", "ua"));
    }

    @Test
    @DisplayName("startExam 超过最大尝试次数")
    void start_maxAttempts() {
        ExamPaper p = publishedPaper();
        p.setMaxAttempts(1);
        when(examPaperService.getById(1L)).thenReturn(p);
        when(examSessionMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.startExam(1L, 2L, "ip", "ua"));
    }

    @Test
    @DisplayName("startExam 存在进行中会话")
    void start_activeExists() {
        ExamPaper p = publishedPaper();
        when(examPaperService.getById(1L)).thenReturn(p);
        ExamSession active = new ExamSession();
        active.setId(9L);
        active.setStatus(ExamSessionStatus.STARTED);
        when(examSessionMapper.selectOne(any(), anyBoolean())).thenReturn(active);
        when(examSessionMapper.selectCount(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () -> service.startExam(1L, 2L, "ip", "ua"));
    }

    @Test
    @DisplayName("startExam 成功创建会话")
    void start_ok() {
        ExamPaper p = publishedPaper();
        when(examPaperService.getById(1L)).thenReturn(p);
        when(examSessionMapper.selectOne(any())).thenReturn(null);
        when(examSessionMapper.selectCount(any())).thenReturn(0L);
        when(examSessionMapper.insert(any(ExamSession.class))).thenReturn(1);
        ExamSession r = service.startExam(1L, 2L, "ip", "ua");
        assertEquals(ExamSessionStatus.STARTED, r.getStatus());
        assertEquals(1, r.getAttemptNumber());
    }

    // ===== autoSaveAnswers =====

    @Test
    @DisplayName("autoSaveAnswers 会话不存在抛异常")
    void autoSave_notFound() {
        when(examSessionMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.autoSaveAnswers(1L, Map.<String, Object>of("a", "1")));
    }

    @Test
    @DisplayName("autoSaveAnswers 状态不允许抛异常")
    void autoSave_wrongStatus() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.SUBMITTED);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.autoSaveAnswers(1L, Map.<String, Object>of("a", "1")));
    }

    @Test
    @DisplayName("autoSaveAnswers 成功")
    void autoSave_ok() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.STARTED);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);
        service.autoSaveAnswers(1L, Map.<String, Object>of("q1", "A"));
        assertNotNull(s.getAnswers());
    }

    // ===== submitExam =====

    @Test
    @DisplayName("submitExam 会话不存在抛异常")
    void submit_notFound() {
        when(examSessionMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitExam(1L, Map.<String, Object>of("a", "1")));
    }

    @Test
    @DisplayName("submitExam 重复提交返回 false 抛异常")
    void submit_duplicate() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.STARTED);
        s.setStartTime(LocalDateTime.now().minusMinutes(5));
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.update(any(), any())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.submitExam(1L, Map.<String, Object>of("a", "1")));
    }

    @Test
    @DisplayName("submitExam 成功")
    void submit_ok() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.STARTED);
        s.setStartTime(LocalDateTime.now().minusMinutes(5));
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.update(any(), any())).thenReturn(1);
        ExamSession after = new ExamSession();
        after.setId(1L);
        after.setStatus(ExamSessionStatus.SUBMITTED);
        when(examSessionMapper.selectById(any())).thenReturn(s).thenReturn(after);
        ExamSession r = service.submitExam(1L, Map.<String, Object>of("a", "1"));
        assertEquals(ExamSessionStatus.SUBMITTED, r.getStatus());
    }

    // ===== forceSubmit =====

    @Test
    @DisplayName("forceSubmit 会话不存在抛异常")
    void force_notFound() {
        when(examSessionMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.forceSubmit(1L));
    }

    @Test
    @DisplayName("forceSubmit 已提交/已阅卷抛异常")
    void force_already() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.SUBMITTED);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.forceSubmit(1L));
    }

    @Test
    @DisplayName("forceSubmit 成功")
    void force_ok() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.STARTED);
        s.setStartTime(LocalDateTime.now().minusMinutes(5));
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);
        ExamSession r = service.forceSubmit(1L);
        assertEquals(ExamSessionStatus.FORCE_SUBMITTED, r.getStatus());
    }

    // ===== 阅卷委托 =====

    @Test
    @DisplayName("autoGrade 委托 gradingService")
    void autoGrade_delegate() {
        AutoGradeResultVO m = new AutoGradeResultVO(1L, null, null, null, 0, 0, 0, Map.of(), null);
        when(gradingService.autoGrade(1L)).thenReturn(m);
        assertEquals(m, service.autoGrade(1L));
    }

    @Test
    @DisplayName("manualGrade 委托 gradingService")
    void manualGrade_delegate() {
        service.manualGrade(1L, 2L, BigDecimal.TEN, "good");
        verify(gradingService).manualGrade(1L, 2L, BigDecimal.TEN, "good");
    }

    @Test
    @DisplayName("calculateTotalScore 委托 gradingService")
    void totalScore_delegate() {
        when(gradingService.calculateTotalScore(1L)).thenReturn(BigDecimal.TEN);
        assertEquals(0, BigDecimal.TEN.compareTo(service.calculateTotalScore(1L)));
    }

    // ===== generateScoreReport =====

    @Test
    @DisplayName("generateScoreReport 会话不存在抛异常")
    void report_notFound() {
        when(examSessionMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.generateScoreReport(1L));
    }

    @Test
    @DisplayName("generateScoreReport 试卷为 null 用未知试卷")
    void report_paperNull() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStudentId(2L);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examPaperService.getById(1L)).thenReturn(null);
        Map<String, Object> r = service.generateScoreReport(1L);
        assertEquals("未知试卷", r.get("examPaperTitle"));
    }

    @Test
    @DisplayName("generateScoreReport 各等级分支")
    void report_gradeLevels() {
        for (double pct : new double[]{95, 85, 75, 65, 50}) {
            ExamSession s = new ExamSession();
            s.setId(1L);
            s.setExamPaperId(1L);
            s.setStudentId(2L);
            s.setPercentage(BigDecimal.valueOf(pct));
            when(examSessionMapper.selectById(any())).thenReturn(s);
            ExamPaper p = new ExamPaper();
            p.setTitle("卷");
            when(examPaperService.getById(1L)).thenReturn(p);
            Map<String, Object> r = service.generateScoreReport(1L);
            assertNotNull(r.get("gradeLevel"));
        }
    }

    @Test
    @DisplayName("generateScoreReport 解析题目得分明细")
    void report_questionScores() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStudentId(2L);
        s.setQuestionScores("{\"1\":5}");
        s.setPercentage(BigDecimal.valueOf(90));
        when(examSessionMapper.selectById(any())).thenReturn(s);
        ExamPaper p = new ExamPaper();
        p.setTitle("卷");
        when(examPaperService.getById(1L)).thenReturn(p);
        Map<String, Object> r = service.generateScoreReport(1L);
        assertNotNull(r.get("questionDetails"));
    }

    @Test
    @DisplayName("generateScoreReport 题目得分JSON非法容错")
    void report_questionScoresBadJson() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStudentId(2L);
        s.setQuestionScores("{bad");
        s.setPercentage(BigDecimal.valueOf(90));
        when(examSessionMapper.selectById(any())).thenReturn(s);
        ExamPaper p = new ExamPaper();
        p.setTitle("卷");
        when(examPaperService.getById(1L)).thenReturn(p);
        Map<String, Object> r = service.generateScoreReport(1L);
        assertNotNull(r.get("sessionId"));
    }

    // ===== getActiveExams =====

    @Test
    @DisplayName("getActiveExams 返回列表")
    void activeExams_ok() {
        when(examSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new ExamSession()));
        assertEquals(1, service.getActiveExams().size());
    }

    // ===== detectCheating =====

    @Test
    @DisplayName("detectCheating 会话不存在抛异常")
    void detect_notFound() {
        when(examSessionMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.detectCheating(1L));
    }

    @Test
    @DisplayName("detectCheating IP/UA变化与短时作答")
    void detect_ipUaTime() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStudentId(2L);
        s.setIpAddress("1.1.1.1");
        s.setUserAgent("A");
        s.setTimeSpent(10);
        ExamSession other = new ExamSession();
        other.setId(2L);
        other.setIpAddress("2.2.2.2");
        other.setUserAgent("B");
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(other));
        List<String> r = service.detectCheating(1L);
        assertEquals(3, r.size());
    }

    @Test
    @DisplayName("detectCheating 超时后提交")
    void detect_timeoutSubmit() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStudentId(2L);
        s.setSubmitTime(LocalDateTime.now().plusHours(1));
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        ExamPaper p = new ExamPaper();
        p.setEndTime(LocalDateTime.now());
        when(examPaperService.getById(1L)).thenReturn(p);
        List<String> r = service.detectCheating(1L);
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("detectCheating 无嫌疑")
    void detect_none() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStudentId(2L);
        s.setIpAddress("1.1.1.1");
        s.setUserAgent("A");
        s.setTimeSpent(600);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertTrue(service.detectCheating(1L).isEmpty());
    }

    // ===== checkCheating =====

    @Test
    @DisplayName("checkCheating 会话不存在抛异常")
    void check_notFound() {
        when(examSessionMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.checkCheating(1L));
    }

    @Test
    @DisplayName("checkCheating 无嫌疑 LOW")
    void check_low() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStudentId(2L);
        s.setIpAddress("1.1.1.1");
        s.setUserAgent("A");
        s.setTimeSpent(600);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        Map<String, Object> r = service.checkCheating(1L);
        assertEquals("LOW", r.get("riskLevel"));
        assertFalse((Boolean) r.get("hasSuspicion"));
    }

    @Test
    @DisplayName("checkCheating 有嫌疑 MEDIUM 且 answers 为空")
    void check_mediumAnswersNull() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStudentId(2L);
        s.setTimeSpent(10);
        s.setAnswers(null);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);
        Map<String, Object> r = service.checkCheating(1L);
        assertEquals("MEDIUM", r.get("riskLevel"));
        verify(examSessionMapper).updateById(any(ExamSession.class));
    }

    @Test
    @DisplayName("checkCheating answers 为合法 JSON")
    void check_validJson() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStudentId(2L);
        s.setTimeSpent(10);
        s.setAnswers("{\"q1\":\"A\"}");
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);
        service.checkCheating(1L);
        assertTrue(s.getAnswers().contains("cheatingFlags"));
    }

    @Test
    @DisplayName("checkCheating answers 为损坏 JSON 走兜底")
    void check_corruptJson() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStudentId(2L);
        s.setTimeSpent(10);
        s.setAnswers("{bad");
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);
        service.checkCheating(1L);
        assertTrue(s.getAnswers().contains("cheatingFlags"));
    }

    @Test
    @DisplayName("checkCheating 多项嫌疑 HIGH")
    void check_high() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        s.setStudentId(2L);
        s.setIpAddress("1.1.1.1");
        s.setUserAgent("A");
        s.setTimeSpent(10);
        s.setSubmitTime(LocalDateTime.now().plusHours(1));
        s.setAnswers(null);
        ExamSession other = new ExamSession();
        other.setId(2L);
        other.setIpAddress("2.2.2.2");
        other.setUserAgent("B");
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(other));
        ExamPaper p = new ExamPaper();
        p.setEndTime(LocalDateTime.now());
        when(examPaperService.getById(1L)).thenReturn(p);
        when(examSessionMapper.updateById(any(ExamSession.class))).thenReturn(1);
        Map<String, Object> r = service.checkCheating(1L);
        assertEquals("HIGH", r.get("riskLevel"));
    }

    // ===== getRemainingTime =====

    @Test
    @DisplayName("getRemainingTime 会话不存在抛异常")
    void remain_notFound() {
        when(examSessionMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getRemainingTime(1L));
    }

    @Test
    @DisplayName("getRemainingTime 非进行中返回 0")
    void remain_notStarted() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.SUBMITTED);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        assertEquals(0L, service.getRemainingTime(1L));
    }

    @Test
    @DisplayName("getRemainingTime 试卷为 null 返回 0")
    void remain_paperNull() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.STARTED);
        s.setStartTime(LocalDateTime.now().minusMinutes(1));
        s.setExamPaperId(1L);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examPaperService.getById(1L)).thenReturn(null);
        assertEquals(0L, service.getRemainingTime(1L));
    }

    @Test
    @DisplayName("getRemainingTime 按时长计算")
    void remain_byDuration() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.STARTED);
        s.setStartTime(LocalDateTime.now().minusMinutes(1));
        s.setExamPaperId(1L);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        ExamPaper p = new ExamPaper();
        p.setDuration(60);
        when(examPaperService.getById(1L)).thenReturn(p);
        long r = service.getRemainingTime(1L);
        assertTrue(r > 0 && r <= 60 * 60L);
    }

    @Test
    @DisplayName("getRemainingTime 按结束时间计算")
    void remain_byEndTime() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.STARTED);
        s.setStartTime(LocalDateTime.now().minusMinutes(1));
        s.setExamPaperId(1L);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        ExamPaper p = new ExamPaper();
        p.setEndTime(LocalDateTime.now().plusMinutes(30));
        when(examPaperService.getById(1L)).thenReturn(p);
        long r = service.getRemainingTime(1L);
        assertTrue(r > 0 && r <= 30 * 60L + 1);
    }

    @Test
    @DisplayName("getRemainingTime 时长与结束时间取最小值")
    void remain_min() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.STARTED);
        s.setStartTime(LocalDateTime.now().minusMinutes(1));
        s.setExamPaperId(1L);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        ExamPaper p = new ExamPaper();
        p.setDuration(60);
        p.setEndTime(LocalDateTime.now().plusMinutes(10));
        when(examPaperService.getById(1L)).thenReturn(p);
        long r = service.getRemainingTime(1L);
        assertTrue(r <= 10 * 60L + 1);
    }

    // ===== getLatestSession =====

    @Test
    @DisplayName("getLatestSession 返回会话")
    void latest_ok() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        when(examSessionMapper.selectOne(any(), anyBoolean())).thenReturn(s);
        assertEquals(s, service.getLatestSession(1L, 2L));
    }

    // ===== getSessionQuestions =====

    @Test
    @DisplayName("getSessionQuestions 会话不存在返回空")
    void sessionQuestions_null() {
        when(examSessionMapper.selectById(any())).thenReturn(null);
        assertTrue(service.getSessionQuestions(1L).isEmpty());
    }

    @Test
    @DisplayName("getSessionQuestions 成功")
    void sessionQuestions_ok() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setExamPaperId(1L);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(100L);
        eq.setOrderIndex(1);
        eq.setPoints(BigDecimal.TEN);
        when(examQuestionService.list(any(Wrapper.class))).thenReturn(List.of(eq));
        assertEquals(1, service.getSessionQuestions(1L).size());
    }

    // ===== timeoutSubmit =====

    @Test
    @DisplayName("timeoutSubmit 会话不存在直接返回")
    void timeout_null() {
        when(examSessionMapper.selectById(any())).thenReturn(null);
        service.timeoutSubmit(1L);
        verify(examSessionMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("timeoutSubmit 非进行中直接返回")
    void timeout_notStarted() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.SUBMITTED);
        when(examSessionMapper.selectById(any())).thenReturn(s);
        service.timeoutSubmit(1L);
        verify(examSessionMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("timeoutSubmit 成功流转状态（经 LambdaUpdateWrapper 更新 DB，实体对象不变）")
    void timeout_ok() {
        ExamSession s = new ExamSession();
        s.setId(1L);
        s.setStatus(ExamSessionStatus.STARTED);
        s.setStartTime(LocalDateTime.now().minusMinutes(5));
        when(examSessionMapper.selectById(any())).thenReturn(s);
        when(examSessionMapper.update(any(), any())).thenReturn(1);
        service.timeoutSubmit(1L);
        // 生产实现经 LambdaUpdateWrapper 更新数据库（不修改传入实体对象），断言 wrapper 更新已发出
        // ServiceImpl.update(Wrapper) 委托 baseMapper.update(entity=null, wrapper)
        verify(examSessionMapper).update(any(),
                any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class));
    }

    // ===== mapToJson / parseScoresJson (private，迁移至组件后) =====

    @Test
    @DisplayName("mapToJson 空/ null 返回 {}")
    void mapToJson_empty() throws Exception {
        Object lifecycle = ReflectionTestUtils.getField(service, "lifecycleComponent");
        Method m = lifecycle.getClass().getDeclaredMethod("mapToJson", Map.class);
        m.setAccessible(true);
        assertEquals("{}", m.invoke(lifecycle, (Map<String, Object>) null));
        assertEquals("{}", m.invoke(lifecycle, new HashMap<String, Object>()));
    }

    @Test
    @DisplayName("mapToJson 正常序列化")
    void mapToJson_ok() throws Exception {
        Object lifecycle = ReflectionTestUtils.getField(service, "lifecycleComponent");
        Method m = lifecycle.getClass().getDeclaredMethod("mapToJson", Map.class);
        m.setAccessible(true);
        Object r = m.invoke(lifecycle, Map.of("q1", "A"));
        assertTrue(r.toString().contains("q1"));
    }

    @Test
    @DisplayName("parseScoresJson 空/ null/ {} 返回空")
    void parseScores_empty() throws Exception {
        Object query = ReflectionTestUtils.getField(service, "queryComponent");
        Method m = query.getClass().getDeclaredMethod("parseScoresJson", String.class);
        m.setAccessible(true);
        assertTrue(((Map<?, ?>) m.invoke(query, (String) null)).isEmpty());
        assertTrue(((Map<?, ?>) m.invoke(query, "")).isEmpty());
        assertTrue(((Map<?, ?>) m.invoke(query, "{}")).isEmpty());
    }

    @Test
    @DisplayName("parseScoresJson 合法解析")
    void parseScores_ok() throws Exception {
        Object query = ReflectionTestUtils.getField(service, "queryComponent");
        Method m = query.getClass().getDeclaredMethod("parseScoresJson", String.class);
        m.setAccessible(true);
        Map<?, ?> r = (Map<?, ?>) m.invoke(query, "{\"1\":5}");
        assertFalse(r.isEmpty());
    }

    @Test
    @DisplayName("parseScoresJson 非法 JSON 容错")
    void parseScores_bad() throws Exception {
        Object query = ReflectionTestUtils.getField(service, "queryComponent");
        Method m = query.getClass().getDeclaredMethod("parseScoresJson", String.class);
        m.setAccessible(true);
        assertTrue(((Map<?, ?>) m.invoke(query, "{bad")).isEmpty());
    }
}
