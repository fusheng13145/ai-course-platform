package com.aicourse.service.impl;

import com.aicourse.entity.ExamSession;
import com.aicourse.service.impl.exam.ExamSessionGradingService;
import com.aicourse.service.impl.exam.ExamSessionLifecycleService;
import com.aicourse.service.impl.exam.ExamSessionQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 考试会话服务门面委托测试。
 * 仅验证 {@link ExamSessionServiceImpl} 作为薄门面，将每个接口方法正确委托给对应协作者，
 * 并透传返回值；不承载任何业务逻辑。
 */
@ExtendWith(MockitoExtension.class)
class ExamSessionServiceImplTest {

    @Mock private ExamSessionLifecycleService lifecycleService;
    @Mock private ExamSessionGradingService gradingService;
    @Mock private ExamSessionQueryService queryService;

    @InjectMocks private ExamSessionServiceImpl examSessionService;

    private final ExamSession sampleSession = new ExamSession();

    @Test
    @DisplayName("门面 - startExam 委托给生命周期协作者")
    void startExam_delegates() {
        when(lifecycleService.startExam(1L, 2L, "ip", "ua")).thenReturn(sampleSession);
        ExamSession r = examSessionService.startExam(1L, 2L, "ip", "ua");
        assertSame(sampleSession, r);
        verify(lifecycleService).startExam(1L, 2L, "ip", "ua");
    }

    @Test
    @DisplayName("门面 - autoSaveAnswers 委托给生命周期协作者")
    void autoSaveAnswers_delegates() {
        Map<String, Object> answers = new HashMap<>();
        examSessionService.autoSaveAnswers(1L, answers);
        verify(lifecycleService).autoSaveAnswers(1L, answers);
    }

    @Test
    @DisplayName("门面 - submitExam 委托给生命周期协作者")
    void submitExam_delegates() {
        when(lifecycleService.submitExam(1L, Collections.emptyMap())).thenReturn(sampleSession);
        ExamSession r = examSessionService.submitExam(1L, Collections.emptyMap());
        assertSame(sampleSession, r);
        verify(lifecycleService).submitExam(1L, Collections.emptyMap());
    }

    @Test
    @DisplayName("门面 - forceSubmit 委托给生命周期协作者")
    void forceSubmit_delegates() {
        when(lifecycleService.forceSubmit(1L)).thenReturn(sampleSession);
        assertSame(sampleSession, examSessionService.forceSubmit(1L));
        verify(lifecycleService).forceSubmit(1L);
    }

    @Test
    @DisplayName("门面 - getSessionQuestions 委托给查询协作者")
    void getSessionQuestions_delegates() {
        when(queryService.getSessionQuestions(1L)).thenReturn(Collections.emptyList());
        assertSame(Collections.emptyList(), examSessionService.getSessionQuestions(1L));
        verify(queryService).getSessionQuestions(1L);
    }

    @Test
    @DisplayName("门面 - timeoutSubmit 委托给生命周期协作者")
    void timeoutSubmit_delegates() {
        when(lifecycleService.timeoutSubmit(1L)).thenReturn(sampleSession);
        examSessionService.timeoutSubmit(1L);
        verify(lifecycleService).timeoutSubmit(1L);
    }

    @Test
    @DisplayName("门面 - autoGrade 委托给阅卷协作者")
    void autoGrade_delegates() {
        Map<String, Object> result = new HashMap<>();
        when(gradingService.autoGrade(1L)).thenReturn(result);
        assertSame(result, examSessionService.autoGrade(1L));
        verify(gradingService).autoGrade(1L);
    }

    @Test
    @DisplayName("门面 - manualGrade 委托给阅卷协作者")
    void manualGrade_delegates() {
        examSessionService.manualGrade(1L, 2L, BigDecimal.TEN, "comment");
        verify(gradingService).manualGrade(1L, 2L, BigDecimal.TEN, "comment");
    }

    @Test
    @DisplayName("门面 - calculateTotalScore 委托给阅卷协作者")
    void calculateTotalScore_delegates() {
        when(gradingService.calculateTotalScore(1L)).thenReturn(BigDecimal.TEN);
        assertEquals(BigDecimal.TEN, examSessionService.calculateTotalScore(1L));
        verify(gradingService).calculateTotalScore(1L);
    }

    @Test
    @DisplayName("门面 - generateScoreReport 委托给查询协作者")
    void generateScoreReport_delegates() {
        Map<String, Object> result = new HashMap<>();
        when(queryService.generateScoreReport(1L)).thenReturn(result);
        assertSame(result, examSessionService.generateScoreReport(1L));
        verify(queryService).generateScoreReport(1L);
    }

    @Test
    @DisplayName("门面 - getActiveExams 委托给查询协作者")
    void getActiveExams_delegates() {
        when(queryService.getActiveExams()).thenReturn(Collections.emptyList());
        assertSame(Collections.emptyList(), examSessionService.getActiveExams());
        verify(queryService).getActiveExams();
    }

    @Test
    @DisplayName("门面 - detectCheating 委托给查询协作者")
    void detectCheating_delegates() {
        when(queryService.detectCheating(1L)).thenReturn(Collections.emptyList());
        assertSame(Collections.emptyList(), examSessionService.detectCheating(1L));
        verify(queryService).detectCheating(1L);
    }

    @Test
    @DisplayName("门面 - getRemainingTime 委托给查询协作者")
    void getRemainingTime_delegates() {
        when(queryService.getRemainingTime(1L)).thenReturn(60L);
        assertEquals(60L, examSessionService.getRemainingTime(1L));
        verify(queryService).getRemainingTime(1L);
    }

    @Test
    @DisplayName("门面 - getLatestSession 委托给查询协作者")
    void getLatestSession_delegates() {
        when(queryService.getLatestSession(1L, 2L)).thenReturn(sampleSession);
        assertSame(sampleSession, examSessionService.getLatestSession(1L, 2L));
        verify(queryService).getLatestSession(1L, 2L);
    }

    @Test
    @DisplayName("门面 - checkCheating 委托给查询协作者")
    void checkCheating_delegates() {
        Map<String, Object> result = new HashMap<>();
        when(queryService.checkCheating(1L)).thenReturn(result);
        assertSame(result, examSessionService.checkCheating(1L));
        verify(queryService).checkCheating(1L);
    }
}
