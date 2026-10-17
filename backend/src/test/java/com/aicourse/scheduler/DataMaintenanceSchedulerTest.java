package com.aicourse.scheduler;

import com.aicourse.mapper.LearningRecordMapper;
import com.aicourse.mapper.OperationLogMapper;
import com.aicourse.mapper.UserSessionMapper;
import com.aicourse.mapper.VerificationCodeMapper;
import com.aicourse.mapper.VideoWatchSessionMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private com.aicourse.service.FileUploadSessionService fileUploadSessionService;

    private DataMaintenanceScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DataMaintenanceScheduler(
                verificationCodeMapper, userSessionMapper, operationLogMapper,
                learningRecordMapper, videoWatchSessionMapper, fileUploadSessionService);
        // @Value 在纯单元测试（无 Spring 上下文）下不会注入，enabled 字段默认 false，
        // 会导致 runCleanup() 直接提前返回、各清理任务 delete 永不执行。
        // 显式置为 true，使清理逻辑真正执行（runCleanup_disabled_returnsEarly 会再覆盖为 false）。
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        // 上传会话清理走 service（其内部查库+回收分片），本类只断言"被调度调用"，
        // 返回值与用例无关，故用 lenient 避免各分支的严格存根检查报错
        lenient().when(fileUploadSessionService.cleanExpiredSessions()).thenReturn(0);
        // 初始化各实体 TableInfo，使 LambdaQueryWrapper 能解析 lambda 列并在 getSqlSegment()
        // 时把参数值落入 paramNameValuePairs（供 runCleanup_usesDocumentedRetentionCutoffs 断言时间阈值）。
        for (Class<?> e : new Class<?>[]{com.aicourse.entity.VerificationCode.class,
                com.aicourse.entity.UserSession.class, com.aicourse.entity.OperationLog.class,
                com.aicourse.entity.LearningRecord.class, com.aicourse.entity.VideoWatchSession.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), e);
        }
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
        verify(fileUploadSessionService, never()).cleanExpiredSessions();
    }

    @Test
    @DisplayName("runCleanup 调用上传会话过期清理（分片回收的唯一入口）")
    void runCleanup_cleansExpiredUploadSessions() {
        when(verificationCodeMapper.delete(any())).thenReturn(0);
        when(userSessionMapper.delete(any())).thenReturn(0);
        when(operationLogMapper.delete(any())).thenReturn(0);
        when(learningRecordMapper.delete(any())).thenReturn(0);
        when(videoWatchSessionMapper.delete(any())).thenReturn(0);

        scheduler.runCleanup();

        verify(fileUploadSessionService).cleanExpiredSessions();
    }

    @Test
    @DisplayName("上传会话清理抛异常时不影响其余清理任务")
    void runCleanup_uploadSessionCleanupThrows_isIsolated() {
        when(fileUploadSessionService.cleanExpiredSessions())
                .thenThrow(new RuntimeException("chunk dir locked"));
        when(verificationCodeMapper.delete(any())).thenReturn(0);
        when(userSessionMapper.delete(any())).thenReturn(0);
        when(operationLogMapper.delete(any())).thenReturn(0);
        when(learningRecordMapper.delete(any())).thenReturn(0);
        when(videoWatchSessionMapper.delete(any())).thenReturn(0);

        scheduler.runCleanup();

        verify(verificationCodeMapper).delete(any());
        verify(learningRecordMapper).delete(any());
    }

    @Test
    void runCleanup_allTasksRemovedGreaterThanZero() {
        // 此处 any() 保留合理：这组用例断言的是「每个清理任务各被调用一次」与 removed>0 的日志分支，
        // 各任务 Wrapper 的具体条件（时间阈值 / 列名 / is_active 过滤）由
        // runCleanup_usesDocumentedRetentionCutoffs 与 runCleanup_deletesFilterOnDocumentedColumns 专测锁定。
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
        // 此处 any() 保留合理：断言目标是「异常隔离后其余任务仍被调用」，与各自的查询条件无关。
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

    /** 从捕获的 Wrapper 参数集合中提取 LocalDateTime 型时间阈值。 */
    private static LocalDateTime extractCutoff(AbstractWrapper<?, ?, ?> wrapper) {
        // 触发 SQL 片段构建：lambda 条件的参数值在 getSqlSegment() 时才落入 paramNameValuePairs。
        String sql = wrapper.getSqlSegment();
        Collection<Object> values = wrapper.getParamNameValuePairs().values();
        LocalDateTime found = null;
        for (Object v : values) {
            if (v instanceof LocalDateTime lt) {
                found = lt;
            }
        }
        assertTrue(found != null, "清理条件应包含时间阈值参数，实际参数集=" + values + " sql=" + sql);
        return found;
    }

    @Test
    void runCleanup_usesDocumentedRetentionCutoffs() {
        // 关键路径断言：用具体值匹配取代 any()，锁定各清理任务的「保留天数」时间参数与失效会话的
        // is_active=false 过滤条件——这些是数据保留策略的核心，一旦误改会造成提前删数据或漏删膨胀。
        when(verificationCodeMapper.delete(any())).thenReturn(0);
        when(userSessionMapper.delete(any())).thenReturn(0);
        when(operationLogMapper.delete(any())).thenReturn(0);
        when(learningRecordMapper.delete(any())).thenReturn(0);
        when(videoWatchSessionMapper.delete(any())).thenReturn(0);

        LocalDateTime before = LocalDateTime.now();
        scheduler.runCleanup();
        LocalDateTime after = LocalDateTime.now();

        // 验证码：保留 7 天
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aicourse.entity.VerificationCode>> vcCap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(verificationCodeMapper).delete(vcCap.capture());
        LocalDateTime vcCutoff = extractCutoff((AbstractWrapper<?, ?, ?>) vcCap.getValue());
        assertTrue(Duration.between(before.minusDays(7), vcCutoff).abs().compareTo(Duration.ofMinutes(5)) < 0,
                "验证码截止应≈now-7d，实际=" + vcCutoff);

        // 失效会话：保留 90 天，且必须带 is_active=false 过滤
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aicourse.entity.UserSession>> usCap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(userSessionMapper).delete(usCap.capture());
        AbstractWrapper<?, ?, ?> usWrapper = (AbstractWrapper<?, ?, ?>) usCap.getValue();
        LocalDateTime usCutoff = extractCutoff(usWrapper);
        // extractCutoff 已触发 getSqlSegment()，此时参数映射被填充，可校验 is_active=false 过滤存在
        assertTrue(usWrapper.getParamNameValuePairs().containsValue(Boolean.FALSE),
                "失效会话清理必须过滤 is_active=false");
        assertTrue(Duration.between(before.minusDays(90), usCutoff).abs().compareTo(Duration.ofMinutes(5)) < 0,
                "失效会话截止应≈now-90d，实际=" + usCutoff);

        // 操作日志：保留 90 天
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aicourse.entity.OperationLog>> olCap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(operationLogMapper).delete(olCap.capture());
        LocalDateTime olCutoff = extractCutoff((AbstractWrapper<?, ?, ?>) olCap.getValue());
        assertTrue(Duration.between(before.minusDays(90), olCutoff).abs().compareTo(Duration.ofMinutes(5)) < 0,
                "操作日志截止应≈now-90d，实际=" + olCutoff);

        // 学习记录 / 观看会话：保留 180 天
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aicourse.entity.LearningRecord>> lrCap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(learningRecordMapper).delete(lrCap.capture());
        LocalDateTime lrCutoff = extractCutoff((AbstractWrapper<?, ?, ?>) lrCap.getValue());
        assertTrue(Duration.between(after.minusDays(180), lrCutoff).abs().compareTo(Duration.ofMinutes(5)) < 0,
                "学习记录截止应≈now-180d，实际=" + lrCutoff);
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aicourse.entity.VideoWatchSession>> vwCap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(videoWatchSessionMapper).delete(vwCap.capture());
        LocalDateTime vwCutoff = extractCutoff((AbstractWrapper<?, ?, ?>) vwCap.getValue());
        assertTrue(Duration.between(after.minusDays(180), vwCutoff).abs().compareTo(Duration.ofMinutes(5)) < 0,
                "观看会话截止应≈now-180d，实际=" + vwCutoff);
    }

    /**
     * 取 wrapper 渲染出的 SQL 条件片段。
     * <p>时间阈值对但「比较的列选错」或「比较方向写反」同样会让上面的断言全绿——
     * 列名与操作符只有在 SQL 片段里才可见，故单独断言。</p>
     */
    private static String sqlSegment(com.baomidou.mybatisplus.core.conditions.Wrapper<?> wrapper) {
        AbstractWrapper<?, ?, ?> aw = (AbstractWrapper<?, ?, ?>) wrapper;
        return aw.getSqlSegment();
    }

    @Test
    void runCleanup_deletesFilterOnDocumentedColumns() {
        when(verificationCodeMapper.delete(any())).thenReturn(0);
        when(userSessionMapper.delete(any())).thenReturn(0);
        when(operationLogMapper.delete(any())).thenReturn(0);
        when(learningRecordMapper.delete(any())).thenReturn(0);
        when(videoWatchSessionMapper.delete(any())).thenReturn(0);

        scheduler.runCleanup();

        // 验证码：按 created_at 取「早于阈值」的记录（< 写成 > 会删掉最新的验证码而留下过期的）
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aicourse.entity.VerificationCode>> vcCap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(verificationCodeMapper).delete(vcCap.capture());
        String vcSql = sqlSegment(vcCap.getValue());
        assertTrue(vcSql.contains("created_at <"), "验证码应按 created_at 早于阈值删除，实际 SQL=" + vcSql);

        // 失效会话：必须 is_active 等值过滤 + created_at 范围过滤同时成立（漏掉 is_active 会删掉活跃会话）
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aicourse.entity.UserSession>> usCap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(userSessionMapper).delete(usCap.capture());
        String usSql = sqlSegment(usCap.getValue());
        assertTrue(usSql.contains("is_active ="), "失效会话清理必须过滤 is_active，实际 SQL=" + usSql);
        assertTrue(usSql.contains("created_at <"), "失效会话应按 created_at 早于阈值删除，实际 SQL=" + usSql);
        assertTrue(usSql.contains(" AND "), "is_active 与 created_at 两个条件必须同时成立，实际 SQL=" + usSql);

        // 操作日志：按 created_at
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aicourse.entity.OperationLog>> olCap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(operationLogMapper).delete(olCap.capture());
        String olSql = sqlSegment(olCap.getValue());
        assertTrue(olSql.contains("created_at <"), "操作日志应按 created_at 早于阈值删除，实际 SQL=" + olSql);

        // 学习记录：按 created_at
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aicourse.entity.LearningRecord>> lrCap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(learningRecordMapper).delete(lrCap.capture());
        String lrSql = sqlSegment(lrCap.getValue());
        assertTrue(lrSql.contains("created_at <"), "学习记录应按 created_at 早于阈值删除，实际 SQL=" + lrSql);

        // 观看会话：该表没有 created_at，保留策略只能落在 started_at 上（写错列会直接抛 SQL 异常或漏删）
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aicourse.entity.VideoWatchSession>> vwCap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(videoWatchSessionMapper).delete(vwCap.capture());
        String vwSql = sqlSegment(vwCap.getValue());
        assertTrue(vwSql.contains("started_at <"), "观看会话应按 started_at 早于阈值删除，实际 SQL=" + vwSql);
    }
}
