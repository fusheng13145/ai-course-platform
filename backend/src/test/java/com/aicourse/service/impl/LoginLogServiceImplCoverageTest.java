package com.aicourse.service.impl;

import com.aicourse.entity.LoginLog;
import com.aicourse.mapper.LoginLogMapper;
import com.aicourse.vo.AbnormalLoginVO;
import com.aicourse.vo.DailyLoginTrendVO;
import com.aicourse.vo.LoginGlobalStatsVO;
import com.aicourse.vo.LoginStatsVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LoginLogServiceImpl 覆盖率补测（I-04 第七批）。
 *
 * <p>覆盖空统计、可疑登录 null IP 与截断、趋势查询异常、UA 空值、失败原因空值、
 * 异常登录各风险信号、筛选条件空值、范围过滤空列表、最近记录上下限。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginLogServiceImplCoverageTest {

    @Mock private LoginLogMapper mapper;
    private LoginLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new LoginLogServiceImpl());
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    private LoginLog login(String status, String ip, String location, LocalDateTime time) {
        LoginLog log = new LoginLog();
        log.setUserId(1L);
        log.setUsername("u");
        log.setLoginStatus(status);
        log.setIpAddress(ip);
        log.setLocation(location);
        log.setCreatedAt(time);
        return log;
    }

    @Test
    @DisplayName("登录统计：无日志时成功率为 0、最后登录字段为 null")
    void loginStats_empty() {
        doReturn(List.of()).when(service).list(any(LambdaQueryWrapper.class));

        LoginStatsVO stats = service.getLoginStats(1L);

        assertEquals(0L, stats.getTotalCount());
        assertEquals(0.0, stats.getSuccessRate(), 0.01);
        assertNull(stats.getLastLoginTime());
        assertNull(stats.getLastLoginIp());
        assertEquals(0L, stats.getUniqueIpCount());
    }

    @Test
    @DisplayName("可疑登录：IP 为 null 归入 unknown，超过 20 条截断")
    void suspicious_nullIpAndTruncate() {
        List<LoginLog> logs = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            logs.add(login("FAILED", null, null, LocalDateTime.now().minusMinutes(i)));
        }
        doReturn(logs).when(service).list(any(LambdaQueryWrapper.class));

        List<LoginLog> result = service.detectSuspiciousLogins(1L);

        assertEquals(20, result.size());
    }

    @Test
    @DisplayName("登录趋势：查询异常时返回空趋势而不是抛出")
    void trends_swallowsException() {
        doThrow(new RuntimeException("db down")).when(service).list(any(LambdaQueryWrapper.class));

        List<DailyLoginTrendVO> result = service.getLoginTrends(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("记录登录成功：UserAgent 为 null/空串时不解析浏览器与系统")
    void recordSuccess_emptyUserAgent() {
        service.recordLoginSuccess(1L, "u", "1.1.1.1", null, "loc");
        service.recordLoginSuccess(1L, "u", "1.1.1.1", "", "loc");

        verify(mapper, times(2)).insert(any(LoginLog.class));
    }

    @Test
    @DisplayName("记录登录失败：失败原因为 null 时写“未知原因”")
    void recordFailure_nullReason() {
        service.recordLoginFailure(1L, "u", "1.1.1.1", "Chrome/120", null);

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals("未知原因", captor.getValue().getFailureReason());
    }

    @Test
    @DisplayName("失败原因分组：userId 为 null 不加用户条件，原因为 null 归入 UNKNOWN")
    void failuresGrouped_nullUserAndReason() {
        doReturn(List.of(login("FAILED", "1", "x", null), login("FAILED", "1", "x", null)))
                .when(service).list(any(LambdaQueryWrapper.class));

        Map<String, Long> grouped = service.getFailuresGroupedByReason(null);

        assertEquals(2L, grouped.get("UNKNOWN"));
    }

    @Test
    @DisplayName("异常登录：多 IP、多地点、频繁失败后成功、凌晨登录均被标记")
    void abnormal_allSignals() {
        LocalDateTime night = LocalDateTime.of(2026, 11, 18, 3, 0);
        List<LoginLog> logs = List.of(
                login("FAILED", "1.1.1.1", "A", night.minusMinutes(1)),
                login("FAILED", "1.1.1.1", "A", night.minusMinutes(2)),
                login("FAILED", "2.2.2.2", "B", night.minusMinutes(3)),
                login("SUCCESS", "3.3.3.3", "C", night));
        doReturn(logs).when(service).list(any(LambdaQueryWrapper.class));

        AbnormalLoginVO vo = service.detectAbnormalLogin(1L);

        assertTrue(vo.isAbnormal());
        assertTrue(vo.getAbnormalDetails().stream().anyMatch(d -> d.contains("不同IP")));
        assertTrue(vo.getAbnormalDetails().stream().anyMatch(d -> d.contains("不同地点")));
        assertTrue(vo.getAbnormalDetails().stream().anyMatch(d -> d.contains("频繁失败")));
        assertTrue(vo.getAbnormalDetails().stream().anyMatch(d -> d.contains("凌晨")));
        assertEquals(3L, vo.getUniqueIpCount());
    }

    @Test
    @DisplayName("异常登录：无记录时无风险、详情为空")
    void abnormal_noRecords() {
        doReturn(List.of()).when(service).list(any(LambdaQueryWrapper.class));

        AbnormalLoginVO vo = service.detectAbnormalLogin(1L);

        assertFalse(vo.isAbnormal());
        assertTrue(vo.getAbnormalDetails().isEmpty());
        assertEquals(0L, vo.getRecentFailedCount());
    }

    @Test
    @DisplayName("日志列表：userId 为 null、status 为空串时不追加筛选条件")
    void loginLogList_blankFilters() {
        Page<LoginLog> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        Page<LoginLog> result = service.getLoginLogList(1, 10, "", null);

        assertEquals(0, result.getRecords().size());
    }

    @Test
    @DisplayName("全局统计：空范围按不可见处理、全零时状态分布为空")
    void globalStats_emptyScopeAndZero() {
        doReturn(0L).when(service).count(any());

        LoginGlobalStatsVO stats = service.getGlobalLoginStats(List.of());

        assertEquals(0L, stats.getTotalCount());
        assertEquals(0L, stats.getTodayCount());
        assertTrue(stats.getStatusDistribution().isEmpty());
    }

    @Test
    @DisplayName("全局统计：状态计数大于 0 才进入分布")
    void globalStats_statusDistribution() {
        // total/success/today/week + SUCCESS/FAILED/LOCKED/EXPIRED
        doReturn(10L, 6L, 2L, 4L, 6L, 4L, 0L, 0L).when(service).count(any());

        LoginGlobalStatsVO stats = service.getGlobalLoginStats(null);

        assertEquals(10L, stats.getTotalCount());
        assertEquals(6L, stats.getSuccessCount());
        assertEquals(4L, stats.getFailedCount());
        assertEquals(6L, stats.getStatusDistribution().get("SUCCESS"));
        assertEquals(4L, stats.getStatusDistribution().get("FAILED"));
    }

    @Test
    @DisplayName("最近登录：limit<=0 归 10、>100 归 100")
    void recentLogs_limits() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        service.getRecentLogs(0);
        service.getRecentLogs(200);

        verify(mapper, times(2)).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("失败日志分页：userId 为 null 不加用户条件")
    void failureLogs_nullUser() {
        Page<LoginLog> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        Page<LoginLog> result = service.getFailureLogs(1, 10, null);

        assertEquals(0, result.getRecords().size());
    }

    @Test
    @DisplayName("UA 解析：null 与未知 UA 返回兜底值")
    void extractUa_nullAndUnknown() {
        assertEquals("Unknown", ReflectionTestUtils.invokeMethod(service, "extractBrowser", (String) null));
        assertEquals("Other", ReflectionTestUtils.invokeMethod(service, "extractBrowser", "curl/8"));
        assertEquals("Unknown", ReflectionTestUtils.invokeMethod(service, "extractOS", (String) null));
        assertEquals("Other", ReflectionTestUtils.invokeMethod(service, "extractOS", "curl/8"));
    }
}

