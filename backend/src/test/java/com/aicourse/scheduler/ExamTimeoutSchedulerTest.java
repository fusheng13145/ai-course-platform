package com.aicourse.scheduler;

import com.aicourse.entity.ExamSession;
import com.aicourse.service.ExamSessionService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 考试超时收卷调度器单元测试。
 * 直接实例化（不走 Spring 代理），验证“每个 STARTED 会话都触发一次收卷”的核心逻辑；
 * 分布式防重由 ShedLock 在调度切面保障，此处不依赖 Redis。
 */
@ExtendWith(MockitoExtension.class)
class ExamTimeoutSchedulerTest {

    @Mock
    private ExamSessionService examSessionService;

    @InjectMocks
    private ExamTimeoutScheduler scheduler;

    @Test
    void autoSubmitExpiredExams_submitsEachStartedSession() {
        ExamSession s1 = new ExamSession();
        s1.setId(1L);
        ExamSession s2 = new ExamSession();
        s2.setId(2L);
        when(examSessionService.list(any(Wrapper.class))).thenReturn(List.of(s1, s2));

        scheduler.autoSubmitExpiredExams();

        verify(examSessionService, times(1)).timeoutSubmit(1L);
        verify(examSessionService, times(1)).timeoutSubmit(2L);
    }

    @Test
    void autoSubmitExpiredExams_noActiveSessions_noSubmit() {
        when(examSessionService.list(any(Wrapper.class))).thenReturn(List.of());

        scheduler.autoSubmitExpiredExams();

        verify(examSessionService, never()).timeoutSubmit(any());
    }
}
