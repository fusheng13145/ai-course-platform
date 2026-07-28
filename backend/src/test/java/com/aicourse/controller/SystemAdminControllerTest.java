package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.AIService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.LoginLogService;
import com.aicourse.service.OperationLogService;
import com.aicourse.service.SystemService;
import com.aicourse.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 系统管理控制器接口测试（受保护 + 角色鉴权范例）。
 * /admin/** 在 SecurityConfig 中要求 ADMIN 角色，且类上标注 @PreAuthorize("hasRole('ADMIN')")，
 * 故可验证：未认证 -> 401、非管理员(STUDENT) -> 403、管理员(ADMIN) -> 200。
 *
 * <p>注意：PasswordEncoder 由 SecurityConfig 提供的真实 bean 注入，无需 mock。
 */
@WebMvcTest(SystemAdminController.class)
class SystemAdminControllerTest extends ControllerTestBase {

    @MockitoBean
    private SystemService systemService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private AnalyticsService analyticsService;
    @MockitoBean
    private CourseEnrollmentService enrollmentService;
    @MockitoBean
    private AIService aiService;
    @MockitoBean
    private LoginLogService loginLogService;
    @MockitoBean
    private OperationLogService operationLogService;

    @Test
    @DisplayName("未认证访问管理员接口-返回401")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("非管理员(学生)访问管理员接口-返回403")
    void forbidden_for_student() throws Exception {
        mockMvc.perform(get("/admin/users").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员访问用户列表-返回200并调用service")
    void admin_list_users() throws Exception {
        when(userService.page(any(), any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());
        mockMvc.perform(get("/admin/users").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk());
        verify(userService).page(any(), any());
    }

    @Test
    @DisplayName("管理员获取系统健康状态-返回200")
    void admin_health() throws Exception {
        when(systemService.getHealth()).thenReturn(java.util.Map.of("status", "UP"));
        mockMvc.perform(get("/admin/health").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk());
        verify(systemService).getHealth();
    }
}
