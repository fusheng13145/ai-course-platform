package com.aicourse.service.impl;

import com.aicourse.entity.LoginLog;
import com.aicourse.vo.DailyLoginTrendVO;
import com.aicourse.mapper.LoginLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 登录日志服务单元测试（B 第一批）。
 * 依赖仅 LoginLogMapper（baseMapper），按 write-tree 约定以 ReflectionTestUtils 注入 baseMapper。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginLogServiceImplTest {

    @Mock private LoginLogMapper mapper;
    @InjectMocks private LoginLogServiceImpl service;

    private LoginLog successLog;
    private LoginLog failedLog;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        successLog = new LoginLog();
        successLog.setId(1L);
        successLog.setUserId(1L);
        successLog.setLoginStatus("SUCCESS");
        successLog.setIpAddress("192.168.1.1");
        successLog.setLocation("Beijing");
        successLog.setCreatedAt(LocalDateTime.now());
        failedLog = new LoginLog();
        failedLog.setId(2L);
        failedLog.setUserId(1L);
        failedLog.setLoginStatus("FAILED");
        failedLog.setIpAddress("10.0.0.1");
        failedLog.setFailureReason("bad pwd");
        failedLog.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("用户登录历史-分页")
    void getUserLoginHistory() {
        Page<LoginLog> p = new Page<>(1, 10);
        p.setRecords(List.of(successLog));
        when(mapper.selectPage(any(Page.class), any())).thenReturn(p);
        Page<LoginLog> r = service.getUserLoginHistory(1L, 1, 10);
        assertEquals(1, r.getRecords().size());
    }

    @Test
    @DisplayName("登录统计")
    void getLoginStats() {
        when(mapper.selectList(any())).thenReturn(List.of(successLog, failedLog));
        com.aicourse.vo.LoginStatsVO s = service.getLoginStats(1L);
        assertEquals(2L, s.getTotalCount());
        assertEquals(1L, s.getSuccessCount());
        assertEquals(1L, s.getFailedCount());
        assertEquals(50.0, s.getSuccessRate(), 0.01);
        assertEquals("Beijing", s.getLastLoginLocation());
    }

    @Test
    @DisplayName("异常登录检测-无失败记录返回空")
    void detectSuspiciousLogins_empty() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(service.detectSuspiciousLogins(1L).isEmpty());
    }

    @Test
    @DisplayName("异常登录检测-同一IP失败>=3标记为可疑")
    void detectSuspiciousLogins_suspicious() {
        List<LoginLog> fails = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            LoginLog l = new LoginLog();
            l.setLoginStatus("FAILED");
            l.setIpAddress("8.8.8.8");
            l.setCreatedAt(LocalDateTime.now().minusDays(1));
            fails.add(l);
        }
        when(mapper.selectList(any())).thenReturn(fails);
        List<LoginLog> r = service.detectSuspiciousLogins(1L);
        assertEquals(3, r.size());
    }

    @Test
    @DisplayName("登录趋势-按日期统计")
    void getLoginTrends() {
        when(mapper.selectList(any())).thenReturn(List.of(successLog));
        List<DailyLoginTrendVO> t = service.getLoginTrends(LocalDate.now().minusDays(1), LocalDate.now());
        assertFalse(t.isEmpty());
        int total = t.stream().mapToInt(d -> (int) d.getTotalCount()).sum();
        assertEquals(1, total);
    }

    @Test
    @DisplayName("记录登录成功")
    void recordLoginSuccess() {
        when(mapper.insert(any(LoginLog.class))).thenReturn(1);
        service.recordLoginSuccess(1L, "u", "1.1.1.1",
                "Mozilla/5.0 (Windows NT 10.0) Chrome/120 Safari/537", "Shanghai");
        ArgumentCaptor<LoginLog> c = ArgumentCaptor.forClass(LoginLog.class);
        verify(mapper).insert(c.capture());
        assertEquals("SUCCESS", c.getValue().getLoginStatus());
        assertEquals("Chrome", c.getValue().getBrowser());
        assertEquals("Windows 10+", c.getValue().getOperatingSystem());
    }

    @Test
    @DisplayName("UserAgent 解析分支覆盖（浏览器/OS 全分支）")
    void extractBrowserOs_branches() {
        when(mapper.insert(any(LoginLog.class))).thenReturn(1);
        service.recordLoginSuccess(1L, "u", "1.1.1.1", "Mozilla/5.0 (Windows NT 10.0) Chrome/120 Safari/537", "X");
        service.recordLoginSuccess(1L, "u", "1.1.1.1", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) Firefox/121", "X");
        service.recordLoginSuccess(1L, "u", "1.1.1.1", "Mozilla/5.0 (iPhone; CPU iPhone OS 16) Safari/604", "X");
        service.recordLoginSuccess(1L, "u", "1.1.1.1", "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.1)", "X");
        service.recordLoginSuccess(1L, "u", "1.1.1.1", "curl/8.0", "X");
        service.recordLoginSuccess(1L, "u", null, null, "X");
        verify(mapper, times(6)).insert(any(LoginLog.class));
    }

    @Test
    @DisplayName("记录登录失败")
    void recordLoginFailure() {
        when(mapper.insert(any(LoginLog.class))).thenReturn(1);
        service.recordLoginFailure(1L, "u", "1.1.1.1", "ua", "bad");
        ArgumentCaptor<LoginLog> c = ArgumentCaptor.forClass(LoginLog.class);
        verify(mapper).insert(c.capture());
        assertEquals("FAILED", c.getValue().getLoginStatus());
        assertEquals("bad", c.getValue().getFailureReason());
    }

    @Test
    @DisplayName("按失败原因分组统计")
    void getFailuresGroupedByReason() {
        when(mapper.selectList(any())).thenReturn(List.of(failedLog));
        Map<String, Long> r = service.getFailuresGroupedByReason(1L);
        assertEquals(1L, r.getOrDefault("bad pwd", 0L));
    }

    @Test
    @DisplayName("异常登录检测-多地IP")
    void detectAbnormalLogin() {
        LoginLog l2 = new LoginLog();
        l2.setLoginStatus("SUCCESS");
        l2.setIpAddress("9.9.9.9");
        l2.setLocation("Shanghai");
        l2.setCreatedAt(LocalDateTime.now());
        when(mapper.selectList(any())).thenReturn(List.of(successLog, l2));
        com.aicourse.vo.AbnormalLoginVO r = service.detectAbnormalLogin(1L);
        assertEquals(true, r.isAbnormal());
    }

    @Test
    @DisplayName("登录日志列表-分页筛选")
    void getLoginLogList() {
        Page<LoginLog> p = new Page<>(1, 10);
        p.setRecords(List.of(successLog));
        when(mapper.selectPage(any(Page.class), any())).thenReturn(p);
        Page<LoginLog> r = service.getLoginLogList(1, 10, "SUCCESS", 1L);
        assertEquals(1, r.getRecords().size());
    }

    @Test
    @DisplayName("全局登录统计")
    void getGlobalLoginStats() {
        when(mapper.selectCount(any())).thenReturn(100L, 60L, 40L, 10L, 5L, 3L, 2L, 1L);
        com.aicourse.vo.LoginGlobalStatsVO s = service.getGlobalLoginStats();
        assertEquals(100L, s.getTotalCount());
        assertEquals(60L, s.getSuccessCount());
    }

    @Test
    @DisplayName("最近登录记录")
    void getRecentLogs() {
        when(mapper.selectList(any())).thenReturn(List.of(successLog));
        List<LoginLog> r = service.getRecentLogs(5);
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("失败登录记录-分页")
    void getFailureLogs() {
        Page<LoginLog> p = new Page<>(1, 10);
        p.setRecords(List.of(failedLog));
        when(mapper.selectPage(any(Page.class), any())).thenReturn(p);
        Page<LoginLog> r = service.getFailureLogs(1, 10, 1L);
        assertEquals(1, r.getRecords().size());
    }
}
