package com.aicourse.service.impl;

import com.aicourse.constant.ExamSessionStatus;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamQuestion;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.impl.exam.ExamSessionGradingService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamQuestionService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
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
 * ExamSessionServiceImpl 指令覆盖率强化测试。
 * 覆盖开始/自动保存/提交/强制交卷/阅卷委托/成绩报告/活跃列表/作弊检测/剩余时间/超时收卷/会话题目，
 * 以及私有 mapToJson / parseScoresJson / saveWrongQuestion / determineRiskLevel。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamSessionServiceBoostTest {

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
    @Mock
    private ExamSessionMapper examSessionMapper;

    private ExamSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExamSessionServiceImpl(examPaperService, examQuestionService, questionService,
                wrongQuestionService, gradingService);
        ReflectionTestUtils.setField(service, "baseMapper", examSessionMapper);
        // 同步 mapper 到内部组件（兼容构造器组装的真实组件初始持有 null mapper）
        for (String field : new String[]{"lifecycleComponent", "cheatingComponent", "queryComponent"}) {
            ReflectionTestUtils.setField(ReflectionTestUtils.getField(service, field), "examSessionMapper", examSessionMapper);
        }
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), ExamSession.class);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), WrongQuestion.class);
    }

    private ExamPaper publishedPaper() {
        ExamPaper p = new ExamPaper();
        p.setId(1L);
        p.setStatus("PUBLISHED");
        p.setTitle("期中");
        return p;
    }

    private ExamSession startedSession(Long id) {
        ExamSession s = new ExamSession();
        s.setId(id);
        s.setExamPaperId(1L);
        s.setStudentId(7L);
        s.setStatus(ExamSessionStatus.STARTED);
        s.setStartTime(LocalDateTime.now().minusMinutes(5));
        return s;
    }

    // ==================== startExam ====================

    @Test
    @DisplayName("startExam: 试卷不存在/未发布")
    void startExam_paperInvalid() {
        when(examPaperService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.startExam(1L, 7L, "ip", "ua"));

        ExamPaper p = new ExamPaper(); p.setId(1L); p.setStatus("DRAFT");
        when(examPaperService.getById(1L)).thenReturn(p);
        assertThrows(BusinessException.class, () -> service.startExam(1L, 7L, "ip", "ua"));
    }

    @Test
    @DisplayName("startExam: 时间窗口校验（未开始/已结束）")
    void startExam_timeWindow() {
        ExamPaper future = publishedPaper();
        future.setStartTime(LocalDateTime.now().plusDays(1));
        when(examPaperService.getById(1L)).thenReturn(future);
        assertThrows(BusinessException.class, () -> service.startExam(1L, 7L, "ip", "ua"));

        ExamPaper past = publishedPaper();
        past.setEndTime(LocalDateTime.now().minusDays(1));
        when(examPaperService.getById(1L)).thenReturn(past);
        assertThrows(BusinessException.class, () -> service.startExam(1L, 7L, "ip", "ua"));
    }

    @Test
    @DisplayName("startExam: 达最大尝试次数 / 已有进行中会话")
    void startExam_attemptsAndActive() {
        ExamPaper p = publishedPaper();
        p.setMaxAttempts(1);
        when(examPaperService.getById(1L)).thenReturn(p);
        when(examSessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.startExam(1L, 7L, "ip", "ua"));

        ExamPaper p2 = publishedPaper();
        when(examPaperService.getById(1L)).thenReturn(p2);
        when(examSessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        ExamSession active = startedSession(9L);
        when(examSessionMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(active);
        assertThrows(BusinessException.class, () -> service.startExam(1L, 7L, "ip", "ua"));
    }

    @Test
    @DisplayName("startExam: 成功创建会话")
    void startExam_success() {
        ExamPaper p = publishedPaper();
        when(examPaperService.getById(1L)).thenReturn(p);
        when(examSessionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L); // attempts & attemptNumber
        when(examSessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null); // no active
        ExamSession r = service.startExam(1L, 7L, "ip", "ua");
        assertEquals(ExamSessionStatus.STARTED, r.getStatus());
        assertEquals(1, r.getAttemptNumber());
        verify(examSessionMapper).insert(any(ExamSession.class));
    }

    // ==================== autoSaveAnswers ====================

    @Test
    @DisplayName("autoSaveAnswers: 不存在/状态不允许")
    void autoSaveAnswers_invalid() {
        when(examSessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.autoSaveAnswers(1L, new HashMap<>()));

        ExamSession s = startedSession(1L);
        s.setStatus(ExamSessionStatus.SUBMITTED);
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.autoSaveAnswers(1L, new HashMap<>()));
    }

    @Test
    @DisplayName("autoSaveAnswers: 成功保存")
    void autoSaveAnswers_success() {
        ExamSession s = startedSession(1L);
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        Map<String, Object> answers = new HashMap<>();
        answers.put("q1", "a");
        service.autoSaveAnswers(1L, answers);
        verify(examSessionMapper).updateById(any(ExamSession.class));
    }

    // ==================== submitExam ====================

    @Test
    @DisplayName("submitExam: 不存在 / 重复提交")
    void submitExam_invalid() {
        when(examSessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitExam(1L, new HashMap<>()));

        ExamSession s = startedSession(1L);
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        when(examSessionMapper.update(any())).thenReturn(0); // 未更新 -> 已提交
        assertThrows(BusinessException.class, () -> service.submitExam(1L, new HashMap<>()));
    }

    @Test
    @DisplayName("submitExam: 成功提交")
    void submitExam_success() {
        ExamSession s = startedSession(1L);
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        when(examSessionMapper.update(any(), any())).thenReturn(1);
        ExamSession r = service.submitExam(1L, new HashMap<>());
        assertNotNull(r);
        verify(examSessionMapper, atLeast(1)).selectById(1L);
    }

    // ==================== forceSubmit ====================

    @Test
    @DisplayName("forceSubmit: 不存在/已提交")
    void forceSubmit_invalid() {
        when(examSessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.forceSubmit(1L));

        ExamSession s = startedSession(1L);
        s.setStatus(ExamSessionStatus.SUBMITTED);
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.forceSubmit(1L));
    }

    @Test
    @DisplayName("forceSubmit: 成功强制交卷")
    void forceSubmit_success() {
        ExamSession s = startedSession(1L);
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        ExamSession r = service.forceSubmit(1L);
        assertEquals(ExamSessionStatus.FORCE_SUBMITTED, r.getStatus());
        verify(examSessionMapper).updateById(any(ExamSession.class));
    }

    // ==================== 阅卷委托 ====================

    @Test
    @DisplayName("autoGrade / manualGrade / calculateTotalScore 委托 gradingService")
    void gradingDelegation() {
        Map<String, Object> map = new HashMap<>();
        when(gradingService.autoGrade(1L)).thenReturn(map);
        assertSame(map, service.autoGrade(1L));

        service.manualGrade(1L, 2L, new BigDecimal("10"), "good");
        verify(gradingService).manualGrade(1L, 2L, new BigDecimal("10"), "good");

        when(gradingService.calculateTotalScore(1L)).thenReturn(new BigDecimal("88"));
        assertEquals(new BigDecimal("88"), service.calculateTotalScore(1L));
    }

    // ==================== generateScoreReport ====================

    @Test
    @DisplayName("generateScoreReport: 不存在抛异常")
    void scoreReport_notFound() {
        when(examSessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.generateScoreReport(1L));
    }

    @Test
    @DisplayName("generateScoreReport: 等级评定 + 题目得分解析")
    void scoreReport_grades() {
        // 试卷为空 -> 未知试卷
        ExamSession s = startedSession(1L);
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        when(examPaperService.getById(1L)).thenReturn(null);
        Map<String, Object> r = service.generateScoreReport(1L);
        assertEquals("未知试卷", r.get("examPaperTitle"));

        // 各等级
        BigDecimal[] pcts = {new BigDecimal("95"), new BigDecimal("85"), new BigDecimal("75"), new BigDecimal("65"), new BigDecimal("50")};
        String[] levels = {"优秀", "良好", "中等", "及格", "不及格"};
        for (int i = 0; i < pcts.length; i++) {
            ExamSession ss = startedSession(1L);
            ss.setPercentage(pcts[i]);
            ss.setQuestionScores("{\"1\":10}");
            when(examSessionMapper.selectById(1L)).thenReturn(ss);
            when(examPaperService.getById(1L)).thenReturn(publishedPaper());
            Map<String, Object> rr = service.generateScoreReport(1L);
            assertEquals(levels[i], rr.get("gradeLevel"));
            assertNotNull(rr.get("questionDetails"));
        }
    }

    // ==================== getActiveExams ====================

    @Test
    @DisplayName("getActiveExams: 返回进行中考试")
    void getActiveExams() {
        when(examSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(startedSession(1L)));
        assertEquals(1, service.getActiveExams().size());
    }

    // ==================== detectCheating ====================

    @Test
    @DisplayName("detectCheating: 不存在抛异常")
    void detectCheating_notFound() {
        when(examSessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.detectCheating(1L));
    }

    @Test
    @DisplayName("detectCheating: IP/UA 变化、短时、超时提交")
    void detectCheating_signs() {
        ExamSession s = startedSession(1L);
        s.setIpAddress("1.1.1.1");
        s.setUserAgent("A");
        s.setTimeSpent(30); // < 60
        s.setSubmitTime(LocalDateTime.now()); // 提交时间晚于结束时间 -> 超时提交嫌疑
        when(examSessionMapper.selectById(1L)).thenReturn(s);

        ExamSession other = new ExamSession();
        other.setIpAddress("2.2.2.2");
        other.setUserAgent("B");
        when(examSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(other));

        ExamPaper paper = publishedPaper();
        paper.setEndTime(LocalDateTime.now().minusMinutes(1)); // submitTime 在结束后
        when(examPaperService.getById(1L)).thenReturn(paper);

        List<String> sus = service.detectCheating(1L);
        assertEquals(4, sus.size()); // IP变化 + UA变化 + 短时 + 超时提交
    }

    // ==================== checkCheating ====================

    @Test
    @DisplayName("checkCheating: 不存在抛异常")
    void checkCheating_notFound() {
        when(examSessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.checkCheating(1L));
    }

    @Test
    @DisplayName("checkCheating: 有嫌疑写标记；无嫌疑不写；风险等级")
    void checkCheating_branches() {
        // 有嫌疑：timeSpent<60
        ExamSession s = startedSession(1L);
        s.setTimeSpent(30);
        s.setAnswers("{\"k\":1}");
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        when(examSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        Map<String, Object> r = service.checkCheating(1L);
        assertTrue((Boolean) r.get("hasSuspicion"));
        assertEquals("MEDIUM", r.get("riskLevel"));
        verify(examSessionMapper).updateById(any(ExamSession.class));

        // 无嫌疑
        ExamSession s2 = startedSession(1L);
        s2.setTimeSpent(600);
        s2.setAnswers("{}");
        when(examSessionMapper.selectById(1L)).thenReturn(s2);
        Map<String, Object> r2 = service.checkCheating(1L);
        assertFalse((Boolean) r2.get("hasSuspicion"));
        assertEquals("LOW", r2.get("riskLevel"));
    }

    @Test
    @DisplayName("checkCheating: answers 非法 JSON 走兜底")
    void checkCheating_invalidAnswers() {
        ExamSession s = startedSession(1L);
        s.setTimeSpent(30);
        s.setAnswers("not json {{{");
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        when(examSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        Map<String, Object> r = service.checkCheating(1L);
        assertTrue((Boolean) r.get("hasSuspicion"));
        verify(examSessionMapper).updateById(any(ExamSession.class));
    }

    // ==================== getRemainingTime ====================

    @Test
    @DisplayName("getRemainingTime: 不存在/非进行中/试卷为空")
    void remainingTime_simple() {
        when(examSessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getRemainingTime(1L));

        ExamSession s = startedSession(1L);
        s.setStatus(ExamSessionStatus.SUBMITTED);
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        assertEquals(0L, service.getRemainingTime(1L));

        ExamSession s2 = startedSession(1L);
        when(examSessionMapper.selectById(1L)).thenReturn(s2);
        when(examPaperService.getById(1L)).thenReturn(null);
        assertEquals(0L, service.getRemainingTime(1L));
    }

    @Test
    @DisplayName("getRemainingTime: 基于时长 / 基于结束时间 / 二者取最小")
    void remainingTime_calc() {
        // 基于时长
        ExamSession s = startedSession(1L);
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        ExamPaper p = publishedPaper();
        p.setDuration(60); // 60分钟
        when(examPaperService.getById(1L)).thenReturn(p);
        Long r1 = service.getRemainingTime(1L);
        assertTrue(r1 > 0 && r1 <= 3600);

        // 基于结束时间
        ExamSession s2 = startedSession(1L);
        when(examSessionMapper.selectById(1L)).thenReturn(s2);
        ExamPaper p2 = publishedPaper();
        p2.setDuration(null);
        p2.setEndTime(LocalDateTime.now().plusMinutes(10));
        when(examPaperService.getById(1L)).thenReturn(p2);
        Long r2 = service.getRemainingTime(1L);
        assertTrue(r2 > 0 && r2 <= 600);

        // 二者取最小（超时->0）
        ExamSession s3 = startedSession(1L);
        when(examSessionMapper.selectById(1L)).thenReturn(s3);
        ExamPaper p3 = publishedPaper();
        p3.setDuration(60);
        p3.setEndTime(LocalDateTime.now().minusMinutes(1));
        when(examPaperService.getById(1L)).thenReturn(p3);
        assertEquals(0L, service.getRemainingTime(1L));
    }

    // ==================== getLatestSession ====================

    @Test
    @DisplayName("getLatestSession: 返回最新会话")
    void getLatestSession() {
        when(examSessionMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(startedSession(5L));
        ExamSession r = service.getLatestSession(1L, 7L);
        assertEquals(5L, r.getId());
    }

    // ==================== getSessionQuestions ====================

    @Test
    @DisplayName("getSessionQuestions: 会话为空返回空，否则返回题目映射")
    void getSessionQuestions() {
        when(examSessionMapper.selectById(1L)).thenReturn(null);
        assertTrue(service.getSessionQuestions(1L).isEmpty());

        ExamSession s = startedSession(1L);
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        ExamQuestion eq = new ExamQuestion();
        eq.setQuestionId(3L);
        eq.setOrderIndex(1);
        eq.setPoints(new BigDecimal("10"));
        when(examQuestionService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(eq));
        List<Map<String, Object>> r = service.getSessionQuestions(1L);
        assertEquals(1, r.size());
        assertEquals(3L, r.get(0).get("questionId"));
    }

    // ==================== timeoutSubmit ====================

    @Test
    @DisplayName("timeoutSubmit: 不存在/非进行中直接返回；进行中则流转")
    void timeoutSubmit() {
        when(examSessionMapper.selectById(1L)).thenReturn(null);
        service.timeoutSubmit(1L); // 无异常

        ExamSession s = startedSession(1L);
        s.setStatus(ExamSessionStatus.SUBMITTED);
        when(examSessionMapper.selectById(1L)).thenReturn(s);
        service.timeoutSubmit(1L); // 非 STARTED -> 直接返回
        verify(examSessionMapper, never()).update(any());

        ExamSession s2 = startedSession(1L);
        when(examSessionMapper.selectById(1L)).thenReturn(s2);
        service.timeoutSubmit(1L);
        verify(examSessionMapper).update(any(), any());
    }

    // ==================== 私有方法反射（迁移至组件后） ====================

    @Test
    @DisplayName("mapToJson: 空/非空")
    void mapToJson() throws Exception {
        Object lifecycle = ReflectionTestUtils.getField(service, "lifecycleComponent");
        Method m = lifecycle.getClass().getDeclaredMethod("mapToJson", Map.class);
        m.setAccessible(true);
        assertEquals("{}", m.invoke(lifecycle, (Object) null));
        assertEquals("{}", m.invoke(lifecycle, Collections.emptyMap()));
        Map<String, Object> map = new HashMap<>();
        map.put("q1", "a");
        assertTrue(m.invoke(lifecycle, map).toString().contains("q1"));
    }

    @Test
    @DisplayName("parseScoresJson: 空/合法/非法")
    void parseScoresJson() throws Exception {
        Object query = ReflectionTestUtils.getField(service, "queryComponent");
        Method m = query.getClass().getDeclaredMethod("parseScoresJson", String.class);
        m.setAccessible(true);
        assertTrue(((Map<?, ?>) m.invoke(query, (Object) null)).isEmpty());
        assertTrue(((Map<?, ?>) m.invoke(query, "{}")).isEmpty());
        @SuppressWarnings("unchecked")
        Map<Long, BigDecimal> parsed = (Map<Long, BigDecimal>) m.invoke(query, "{\"1\":10}");
        assertEquals(new BigDecimal("10"), parsed.get(1L));
        assertTrue(((Map<?, ?>) m.invoke(query, "not json")).isEmpty());
    }

    @Test
    @DisplayName("determineRiskLevel: 3->HIGH, 1->MEDIUM, 0->LOW")
    void determineRiskLevel() throws Exception {
        Object cheating = ReflectionTestUtils.getField(service, "cheatingComponent");
        Method m = cheating.getClass().getDeclaredMethod("determineRiskLevel", List.class);
        m.setAccessible(true);
        assertEquals("HIGH", m.invoke(cheating, Arrays.asList("a", "b", "c")));
        assertEquals("MEDIUM", m.invoke(cheating, Collections.singletonList("a")));
        assertEquals("LOW", m.invoke(cheating, Collections.emptyList()));
    }
}
