package com.aicourse.controller;
import com.aicourse.controller.LoginLogController;
import com.aicourse.service.LoginLogService;
import com.aicourse.entity.LoginLog;
import com.aicourse.vo.DailyLoginTrendVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * LoginLogController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 LoginLogService 方法打桩返回合理业务对象，断言 200/code=200，并校验关键字段透传、verify 路由委托。
 * 对带有 @PreAuthorize 的端点新增越权 403 测试（含 ADMIN 限定、ADMIN/TEACHER 限定、以及 owner-or-principal 限定）。
 */
@WebMvcTest(LoginLogController.class)
class LoginLogControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.LoginLogService loginLogService;

    private LoginLog sampleLog(Long id) {
        LoginLog log = new LoginLog();
        log.setId(id);
        return log;
    }

    @Test
    @DisplayName("GET /login-logs/user/1 - 返回200且用户登录历史分页透传")
    void getUserLoginHistory_authorized_returns_200() throws Exception {
        Page<LoginLog> p = new Page<>(1, 1);
        p.setRecords(List.of(sampleLog(1L)));
        p.setTotal(1);
        when(loginLogService.getUserLoginHistory(1L, 1, 1)).thenReturn(p);
        mockMvc.perform(get("/login-logs/user/1?page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
        verify(loginLogService).getUserLoginHistory(eq(1L), eq(1), eq(1));
    }


    @Test
    @DisplayName("GET /login-logs/user/1?page=1&size=1 - 未认证返回401")
    void getUserLoginHistory_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/login-logs/user/1?page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /login-logs/user/1/stats - 返回200且登录统计透传")
    void getLoginStats_authorized_returns_200() throws Exception {
        when(loginLogService.getLoginStats(1L)).thenReturn(new com.aicourse.vo.LoginStatsVO(0L, 10L, 0L, 0.0, null, null, null, 0L));
        mockMvc.perform(get("/login-logs/user/1/stats").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.successCount").value(10));
        verify(loginLogService).getLoginStats(eq(1L));
    }


    @Test
    @DisplayName("GET /login-logs/user/1/stats - 未认证返回401")
    void getLoginStats_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/login-logs/user/1/stats"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /login-logs/suspicious/1 - 本人访问返回200且可疑登录列表透传")
    void getSuspiciousLogins_authorized_returns_200() throws Exception {
        when(loginLogService.detectSuspiciousLogins(1L)).thenReturn(List.of(sampleLog(5L)));
        mockMvc.perform(get("/login-logs/suspicious/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(5));
        verify(loginLogService).detectSuspiciousLogins(eq(1L));
    }


    @Test
    @DisplayName("GET /login-logs/suspicious/1 - 非本人且非ADMIN返回403")
    void getSuspiciousLogins_forbidden_403() throws Exception {
        mockMvc.perform(get("/login-logs/suspicious/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /login-logs/suspicious/1 - 未认证返回401")
    void getSuspiciousLogins_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/login-logs/suspicious/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /login-logs - 返回200且登录日志分页透传")
    void getList_authorized_returns_200() throws Exception {
        Page<LoginLog> p = new Page<>(1, 1);
        p.setRecords(List.of(sampleLog(3L)));
        p.setTotal(1);
        when(loginLogService.getLoginLogList(1, 1, "test", 1L)).thenReturn(p);
        mockMvc.perform(get("/login-logs?page=1&size=1&status=test&userId=1").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.total").value(1));
        verify(loginLogService).getLoginLogList(eq(1), eq(1), eq("test"), eq(1L));
    }


    @Test
    @DisplayName("GET /login-logs - STUDENT 越权返回403")
    void getList_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/login-logs?page=1&size=1&status=test&userId=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /login-logs?page=1&size=1&status=test&userId=1 - 未认证返回401")
    void getList_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/login-logs?page=1&size=1&status=test&userId=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /login-logs/stats - 返回200且全局统计透传")
    void getGlobalStats_authorized_returns_200() throws Exception {
        when(loginLogService.getGlobalLoginStats()).thenReturn(new com.aicourse.vo.LoginGlobalStatsVO(
                1000L, 950L, 50L, 95.0, 10L, 60L, java.util.Map.of("SUCCESS", 950L)));
        mockMvc.perform(get("/login-logs/stats").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                // 原桩键为 totalLogins，与真实契约键 totalCount 不符；VO 化后按真实键断言
                .andExpect(jsonPath("$.data.totalCount").value(1000));
        verify(loginLogService).getGlobalLoginStats();
    }


    @Test
    @DisplayName("GET /login-logs/stats - STUDENT 越权返回403")
    void getGlobalStats_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/login-logs/stats").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /login-logs/stats - 未认证返回401")
    void getGlobalStats_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/login-logs/stats"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /login-logs/recent?limit=1 - 返回200且最近登录列表透传")
    void getRecentLogs_authorized_returns_200() throws Exception {
        when(loginLogService.getRecentLogs(1)).thenReturn(List.of(sampleLog(8L)));
        mockMvc.perform(get("/login-logs/recent?limit=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(8));
        verify(loginLogService).getRecentLogs(eq(1));
    }


    @Test
    @DisplayName("GET /login-logs/recent?limit=1 - 未认证返回401")
    void getRecentLogs_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/login-logs/recent?limit=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /login-logs/failures - 返回200且失败日志分页透传")
    void getFailureLogs_authorized_returns_200() throws Exception {
        Page<LoginLog> p = new Page<>(1, 1);
        p.setRecords(List.of(sampleLog(4L)));
        p.setTotal(1);
        when(loginLogService.getFailureLogs(1, 1, 1L)).thenReturn(p);
        mockMvc.perform(get("/login-logs/failures?page=1&size=1&userId=1").with(withUser(1L, "ADMIN", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.total").value(1));
        verify(loginLogService).getFailureLogs(eq(1), eq(1), eq(1L));
    }


    @Test
    @DisplayName("GET /login-logs/failures - STUDENT 越权返回403")
    void getFailureLogs_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/login-logs/failures?page=1&size=1&userId=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /login-logs/failures?page=1&size=1&userId=1 - 未认证返回401")
    void getFailureLogs_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/login-logs/failures?page=1&size=1&userId=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /login-logs/abnormal/1 - 本人访问返回200且异常检测结果透传")
    void getAbnormalLogin_authorized_returns_200() throws Exception {
        when(loginLogService.detectAbnormalLogin(1L)).thenReturn(new com.aicourse.vo.AbnormalLoginVO(
                1L, 2, true, java.util.List.of("多地登录"), 2, 2, 0L, 2L, java.time.LocalDateTime.now()));
        mockMvc.perform(get("/login-logs/abnormal/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                // VO 以 @JsonProperty 钉死 isAbnormal 键（原桩的 abnormal 与真实契约不符）
                .andExpect(jsonPath("$.data.isAbnormal").value(true));
        verify(loginLogService).detectAbnormalLogin(eq(1L));
    }


    @Test
    @DisplayName("GET /login-logs/abnormal/1 - 非本人且非ADMIN返回403")
    void getAbnormalLogin_forbidden_403() throws Exception {
        mockMvc.perform(get("/login-logs/abnormal/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /login-logs/abnormal/1 - 未认证返回401")
    void getAbnormalLogin_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/login-logs/abnormal/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /login-logs/trends?days=1 - 返回200且登录趋势列表透传")
    void getLoginTrends_authorized_returns_200() throws Exception {
        when(loginLogService.getLoginTrends(any(), any())).thenReturn(
                List.of(new DailyLoginTrendVO("2024-01-01", 5, 2, 7)));
        mockMvc.perform(get("/login-logs/trends?days=1").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].totalCount").value(7));
        verify(loginLogService).getLoginTrends(any(), any());
    }


    @Test
    @DisplayName("GET /login-logs/trends?days=1 - STUDENT 越权返回403")
    void getLoginTrends_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/login-logs/trends?days=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /login-logs/trends?days=1 - 未认证返回401")
    void getLoginTrends_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/login-logs/trends?days=1"))
                .andExpect(status().isUnauthorized());
    }

}
