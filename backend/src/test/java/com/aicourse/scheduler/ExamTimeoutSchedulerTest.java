package com.aicourse.scheduler;

import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 考试超时收卷调度器单元测试。
 *
 * <p><b>语义变更（2026-09-14，P0 修复）</b>：原实现取出全部 STARTED 会话后不经任何时限判定
 * 就逐场收卷，等价于「每 30 秒把所有进行中的考试强制收卷」，本测试原用例
 * {@code autoSubmitExpiredExams_submitsEachStartedSession} 恰好固化了该缺陷行为。
 * 现按正确语义重写：<b>仅到期的会话被收卷</b>，未到期必须原样放过；
 * 且无作答时跳过自动阅卷（不再产生周期性 ERROR）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ExamTimeoutSchedulerTest {

    @Mock
    private ExamSessionService examSessionService;
    @Mock
    private ExamPaperService examPaperService;

    @InjectMocks
    private ExamTimeoutScheduler scheduler;

    private ExamSession started(Long id, LocalDateTime startTime, String answers) {
        ExamSession s = new ExamSession();
        s.setId(id);
        s.setExamPaperId(100L);
        s.setStudentId(9L);
        s.setStatus("STARTED");
        s.setStartTime(startTime);
        s.setAnswers(answers);
        return s;
    }

    private ExamPaper paper(Integer timeLimit, Integer duration, LocalDateTime endTime) {
        ExamPaper p = new ExamPaper();
        p.setId(100L);
        p.setTimeLimit(timeLimit);
        p.setDuration(duration);
        p.setEndTime(endTime);
        return p;
    }

    @Test
    void expiredSession_submittedAndGraded() {
        // 开考 70 分钟前，时限 45 分钟 → 已到期 → 收卷 + 自动阅卷
        when(examSessionService.list(any(Wrapper.class)))
                .thenReturn(List.of(started(1L, LocalDateTime.now().minusMinutes(70), "{\"1\":\"B\"}")));
        when(examPaperService.getById(100L)).thenReturn(paper(45, 45, null));

        scheduler.autoSubmitExpiredExams();

        verify(examSessionService, times(1)).timeoutSubmit(1L);
        verify(examSessionService, times(1)).autoGrade(1L);
    }

    /** 回归护栏（R-02）：未到期的进行中考试绝不能被收卷。 */
    @Test
    void notExpiredSession_notSubmitted() {
        when(examSessionService.list(any(Wrapper.class)))
                .thenReturn(List.of(started(1L, LocalDateTime.now(), "{\"1\":\"B\"}")));
        when(examPaperService.getById(100L)).thenReturn(paper(45, 45, null));

        scheduler.autoSubmitExpiredExams();

        verify(examSessionService, never()).timeoutSubmit(any());
        verify(examSessionService, never()).autoGrade(any());
    }

    /** 回归护栏（R-03）：无作答时收卷但跳过自动阅卷，避免周期性 ERROR 刷屏。 */
    @Test
    void expiredSessionWithoutAnswers_submittedButNotGraded() {
        when(examSessionService.list(any(Wrapper.class)))
                .thenReturn(List.of(started(1L, LocalDateTime.now().minusMinutes(70), "{}")));
        when(examPaperService.getById(100L)).thenReturn(paper(45, 45, null));

        scheduler.autoSubmitExpiredExams();

        verify(examSessionService, times(1)).timeoutSubmit(1L);
        verify(examSessionService, never()).autoGrade(any());
    }

    /** 时限取「开始时间+时长」与「试卷结束时间」中较早者：结束时间更早则按结束时间判定。 */
    @Test
    void endTimeEarlierThanTimeLimit_usesEarlierDeadline() {
        when(examSessionService.list(any(Wrapper.class)))
                .thenReturn(List.of(started(1L, LocalDateTime.now().minusMinutes(10), "{\"1\":\"B\"}")));
        when(examPaperService.getById(100L)).thenReturn(paper(180, 180, LocalDateTime.now().minusMinutes(1)));

        scheduler.autoSubmitExpiredExams();

        verify(examSessionService, times(1)).timeoutSubmit(1L);
    }

    /** 兜底时长配置为 0（显式关闭）+ 无任何期限依据时，保守不收卷，交人工处理。 */
    @Test
    void noDeadlineAndFallbackDisabled_notSubmitted() {
        when(examSessionService.list(any(Wrapper.class)))
                .thenReturn(List.of(started(1L, LocalDateTime.now().minusDays(2), null)));
        when(examPaperService.getById(100L)).thenReturn(paper(null, null, null));

        scheduler.autoSubmitExpiredExams();

        verify(examSessionService, never()).timeoutSubmit(any());
    }

    /** 无期限依据但兜底时长启用（默认 120 分钟）：超过兜底时长应收卷，避免悬空会话。 */
    @Test
    void noDeadlineAndFallbackEnabled_submitAfterFallback() {
        org.springframework.test.util.ReflectionTestUtils.setField(scheduler, "defaultTimeLimitMinutes", 120);
        when(examSessionService.list(any(Wrapper.class)))
                .thenReturn(List.of(started(1L, LocalDateTime.now().minusMinutes(180), "{\"1\":\"B\"}")));
        when(examPaperService.getById(100L)).thenReturn(paper(null, null, null));

        scheduler.autoSubmitExpiredExams();

        verify(examSessionService, times(1)).timeoutSubmit(1L);
        verify(examSessionService, times(1)).autoGrade(1L);
    }

    /** 兜底时长启用但尚未到时：不得收卷（回归护栏）。 */
    @Test
    void noDeadlineAndFallbackEnabled_notYetExpired_notSubmitted() {
        org.springframework.test.util.ReflectionTestUtils.setField(scheduler, "defaultTimeLimitMinutes", 120);
        when(examSessionService.list(any(Wrapper.class)))
                .thenReturn(List.of(started(1L, LocalDateTime.now().minusMinutes(10), "{\"1\":\"B\"}")));
        when(examPaperService.getById(100L)).thenReturn(paper(null, null, null));

        scheduler.autoSubmitExpiredExams();

        verify(examSessionService, never()).timeoutSubmit(any());
    }

    @Test
    void noActiveSessions_noSubmit() {
        when(examSessionService.list(any(Wrapper.class))).thenReturn(List.of());

        scheduler.autoSubmitExpiredExams();

        verify(examSessionService, never()).timeoutSubmit(any());
    }
}
