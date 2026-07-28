package com.aicourse.controller;
import com.aicourse.controller.UserSessionController;
import com.aicourse.service.UserSessionService;
import com.aicourse.entity.UserSession;
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
 * UserSessionController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 UserSessionService 方法打桩返回合理业务对象，断言 200/code=200，并校验关键字段透传、verify 路由委托。
 * 对带有 @PreAuthorize 的端点新增越权 403 测试（含 ADMIN/TEACHER 限定、以及 owner-or-principal 限定）。
 */
@WebMvcTest(UserSessionController.class)
class UserSessionControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.UserSessionService userSessionService;

    private UserSession sampleSession(Long id, Long userId) {
        UserSession s = new UserSession();
        s.setId(id);
        s.setUserId(userId);
        s.setDeviceName("Chrome");
        return s;
    }

    @Test
    @DisplayName("GET /user-sessions/online - 返回200且在线会话列表透传")
    void getOnlineUsers_authorized_returns_200() throws Exception {
        when(userSessionService.list()).thenReturn(List.of(sampleSession(1L, 1L)));
        mockMvc.perform(get("/user-sessions/online").with(withUser(1L, "ADMIN", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(userSessionService).list();
    }


    @Test
    @DisplayName("GET /user-sessions/online - STUDENT 越权返回403")
    void getOnlineUsers_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/user-sessions/online").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /user-sessions/online - 未认证返回401")
    void getOnlineUsers_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/user-sessions/online"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /user-sessions/user/1 - 本人访问返回200且会话历史透传")
    void getSessionHistory_authorized_returns_200() throws Exception {
        when(userSessionService.getSessionHistory(1L)).thenReturn(List.of(sampleSession(2L, 1L)));
        mockMvc.perform(get("/user-sessions/user/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(2));
        verify(userSessionService).getSessionHistory(eq(1L));
    }


    @Test
    @DisplayName("GET /user-sessions/user/1 - 非本人且非ADMIN/TEACHER返回403")
    void getSessionHistory_forbidden_403() throws Exception {
        mockMvc.perform(get("/user-sessions/user/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /user-sessions/user/1 - 未认证返回401")
    void getSessionHistory_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/user-sessions/user/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("DELETE /user-sessions/1/force-logout - 返回200且强制下线委托被调用")
    void forceLogout_authorized_returns_200() throws Exception {
        mockMvc.perform(delete("/user-sessions/1/force-logout").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(userSessionService).forceLogout(eq(1L));
    }


    @Test
    @DisplayName("DELETE /user-sessions/1/force-logout - STUDENT 越权返回403")
    void forceLogout_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/user-sessions/1/force-logout").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("DELETE /user-sessions/1/force-logout - 未认证返回401")
    void forceLogout_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/user-sessions/1/force-logout"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /user-sessions/stats - 返回200且会话统计透传")
    void getSessionStats_authorized_returns_200() throws Exception {
        when(userSessionService.getOnlineUserCount()).thenReturn(5L);
        when(userSessionService.cleanExpiredSessions()).thenReturn(2);
        mockMvc.perform(get("/user-sessions/stats").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.onlineUserCount").value(5))
                .andExpect(jsonPath("$.data.cleanedExpiredSessions").value(2));
        verify(userSessionService).getOnlineUserCount();
        verify(userSessionService).cleanExpiredSessions();
    }


    @Test
    @DisplayName("GET /user-sessions/stats - STUDENT 越权返回403")
    void getSessionStats_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/user-sessions/stats").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /user-sessions/stats - 未认证返回401")
    void getSessionStats_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/user-sessions/stats"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /user-sessions/abnormal/1 - 本人访问返回200且异常会话透传")
    void detectAbnormal_authorized_returns_200() throws Exception {
        when(userSessionService.detectAbnormalSessions(1L)).thenReturn(List.of(sampleSession(3L, 1L)));
        mockMvc.perform(get("/user-sessions/abnormal/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(3));
        verify(userSessionService).detectAbnormalSessions(eq(1L));
    }


    @Test
    @DisplayName("GET /user-sessions/abnormal/1 - 非本人且非ADMIN返回403")
    void detectAbnormal_forbidden_403() throws Exception {
        mockMvc.perform(get("/user-sessions/abnormal/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /user-sessions/abnormal/1 - 未认证返回401")
    void detectAbnormal_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/user-sessions/abnormal/1"))
                .andExpect(status().isUnauthorized());
    }

}
