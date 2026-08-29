package com.aicourse.scheduler;

import com.aicourse.mapper.LearningRecordMapper;
import com.aicourse.mapper.OperationLogMapper;
import com.aicourse.mapper.UserSessionMapper;
import com.aicourse.mapper.VerificationCodeMapper;
import com.aicourse.mapper.VideoWatchSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DataMaintenanceScheduler 单元测试（直接实例化，调用 @Scheduled 方法本体）。
 * 覆盖：enabled 开关、五个清理任务各自的成功（removed>0 / removed==0 日志分支）、
 * 单个任务异常被隔离（catch 后不影响其余任务）的分支。
 */
@ExtendWith(MockitoExtension.class)
class DataMaintenanceSchedulerTest {

    @Mock
    private VerificationCodeMapper verificationCodeMapper;
    @Mock
    private UserSessionMapper userSessionMapper;
    @Mock
    private OperationLogMapper operationLogMapper;
    @Mock
    private LearningRecordMapper learningRecordMapper;
    @Mock
    private VideoWatchSessionMapper videoWatchSessionMapper;

    private DataMaintenanceScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DataMaintenanceScheduler(
                verificationCodeMapper, userSessionMapper, operationLogMapper,
                learningRecordMapper, videoWatchSessionMapper);
        // @Value 在纯单元测试（无 Spring 上下文）下不会注入，enabled 字段默认 false，
        // 会导致 runCleanup() 直接提前返回、各清理任务 delete 永不执行。
        // 显式置为 true，使清理逻辑真正执行（runCleanup_disabled_returnsEarly 会再覆盖为 false）。
        ReflectionTestUtils.setField(scheduler, "enabled", true);
    }

    @Test
    void runCleanup_disabled_returnsEarly() {
        ReflectionTestUtils.setField(scheduler, "enabled", false);
        scheduler.runCleanup();
        verify(verificationCodeMapper, never()).delete(any());
        verify(userSessionMapper, never()).delete(any());
        verify(operationLogMapper, never()).delete(any());
        verify(learningRecordMapper, never()).delete(any());
        verify(videoWatchSessionMapper, never()).delete(any());
    }

    @Test
    void runCleanup_allTasksRemovedGreaterThanZero() {
        when(verificationCodeMapper.delete(any())).thenReturn(3);
        when(userSessionMapper.delete(any())).thenReturn(2);
        when(operationLogMapper.delete(any())).thenReturn(5);
        when(learningRecordMapper.delete(any())).thenReturn(1);
        when(videoWatchSessionMapper.delete(any())).thenReturn(4);

        scheduler.runCleanup();

        verify(verificationCodeMapper, times(1)).delete(any());
        verify(userSessionMapper, times(1)).delete(any());
        verify(operationLogMapper, times(1)).delete(any());
        verify(learningRecordMapper, times(1)).delete(any());
        verify(videoWatchSessionMapper, times(1)).delete(any());
    }

    @Test
    void runCleanup_removedZero_noInfoLogButProceeds() {
        when(verificationCodeMapper.delete(any())).thenReturn(0);
        when(userSessionMapper.delete(any())).thenReturn(0);
        when(operationLogMapper.delete(any())).thenReturn(0);
        when(learningRecordMapper.delete(any())).thenReturn(0);
        when(videoWatchSessionMapper.delete(any())).thenReturn(0);

        scheduler.runCleanup();

        verify(verificationCodeMapper).delete(any());
        verify(videoWatchSessionMapper).delete(any());
    }

    @Test
    void runCleanup_oneTaskThrows_isIsolated() {
        // 仅验证码清理抛异常，其余任务仍应执行（异常被各自 try/catch 隔离）
        when(verificationCodeMapper.delete(any())).thenThrow(new RuntimeException("db error"));
        when(userSessionMapper.delete(any())).thenReturn(0);
        when(operationLogMapper.delete(any())).thenReturn(0);
        when(learningRecordMapper.delete(any())).thenReturn(0);
        when(videoWatchSessionMapper.delete(any())).thenReturn(0);

        // 不应向外抛异常
        scheduler.runCleanup();

        verify(userSessionMapper).delete(any());
        verify(operationLogMapper).delete(any());
        verify(learningRecordMapper).delete(any());
        verify(videoWatchSessionMapper).delete(any());
    }
}
