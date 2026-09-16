package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.AuthService;
import com.aicourse.service.PermissionService;
import com.aicourse.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 认证授权控制器接口测试（公开端点范例）。
 * /auth/** 在 SecurityConfig 中整体 permitAll，故以下端点无需认证即可访问；
 * 其中需要 Authentication 参数的端点（如 /auth/me）通过 withUser(...) 模拟已登录用户。
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest extends ControllerTestBase {

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private PermissionService permissionService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private com.aicourse.common.CookieUtils cookieUtils;
    @MockitoBean
    private com.aicourse.util.ClientInfoResolver clientInfoResolver;

    @Test
    @DisplayName("登录-正常路径返回200，令牌写入HttpOnly Cookie并从响应体剥离")
    void login_success() throws Exception {
        // 注意：登录成功后 AuthController 会调用 data.remove("token")/remove("refreshToken")
        // 将令牌从响应体剥离并写入 HttpOnly Cookie。生产 AuthServiceImpl 返回可变 HashMap，
        // 故此处必须使用可变 Map（不可用 Map.of() 的不可变实现，否则 remove 抛 UnsupportedOperationException）。
        java.util.Map<String, Object> loginData = new java.util.HashMap<>();
        loginData.put("token", "jwt-token");
        loginData.put("refreshToken", "refresh-jwt");
        when(authService.login(anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(ApiResponse.success(loginData));
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // 令牌不应再出现在响应体（已迁移到 HttpOnly Cookie）
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
        verify(authService).login(eq("test@example.com"), eq("password123"), eq(false), any());
        // 验证令牌确实经由 Cookie 通道下发
        verify(cookieUtils).addAuthCookies(any(), eq("jwt-token"), eq("refresh-jwt"), eq(false));
    }

    @Test
    @DisplayName("刷新令牌-从HttpOnly Cookie读取刷新令牌，轮换访问令牌")
    void refresh_success() throws Exception {
        // 刷新令牌不再经请求体传递，而是从 HttpOnly Cookie（ai_course_refresh）读取，
        // 前端 JS 无法触碰，消除刷新令牌被 XSS 窃取复用的风险。
        when(cookieUtils.readCookie(any(), eq(com.aicourse.util.JwtUtils.REFRESH_TOKEN_COOKIE)))
                .thenReturn("rt");
        java.util.Map<String, Object> refreshData = new java.util.HashMap<>();
        refreshData.put("token", "new-jwt-token");
        when(authService.refreshToken(anyString()))
                .thenReturn(ApiResponse.success(refreshData));
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").doesNotExist());
        verify(authService).refreshToken("rt");
        // 轮换后的新访问令牌应写入 Cookie
        verify(cookieUtils).addAuthCookies(any(), eq("new-jwt-token"), isNull(), eq(true));
    }

    @Test
    @DisplayName("获取当前用户-已认证返回200")
    void me_authenticated() throws Exception {
        when(authService.getCurrentUser(1L)).thenReturn(ApiResponse.success(Map.of("id", 1L)));
        mockMvc.perform(get("/auth/me").with(withUser(1L)))
                .andExpect(status().isOk());
        verify(authService).getCurrentUser(1L);
    }

    @Test
    @DisplayName("获取权限列表-已认证返回200")
    void permissions_authenticated() throws Exception {
        when(permissionService.getUserPermissions(1L)).thenReturn(Map.of("roles", java.util.List.of("STUDENT")));
        mockMvc.perform(get("/auth/permissions").with(withUser(1L)))
                .andExpect(status().isOk());
        verify(permissionService).getUserPermissions(1L);
    }

    @Test
    @DisplayName("登录-缺少密码应被@Valid拦截返回400")
    void login_missing_password_returns_400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("注册-缺少必填字段应被@Valid拦截返回400")
    void register_missing_required_returns_400() throws Exception {
        // 缺 fullName、confirmPassword、role，且密码过短
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("注册-仅缺注册验证码(code)也必须被拦截返回400（B1 回归：原为可选分支）")
    void register_missing_code_returns_400() throws Exception {
        // 缺陷背景（2026-09-16 上线前全检阻塞项 B1）：UserRegisterDTO#code 原无 @NotBlank，
        // 且 AuthSessionComponent#register 为 `if (code != null)` 可选分支——省略 code 即可
        // 跳过验证码校验并注册成功。本用例锁定「除 code 外全部字段合法时仍须 400」。
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newbie\",\"fullName\":\"新同学\","
                                + "\"email\":\"newbie@aicourse.com\",\"password\":\"Password123\","
                                + "\"confirmPassword\":\"Password123\",\"role\":\"STUDENT\","
                                + "\"agreement\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("注册-缺少邮箱也必须被拦截返回400（email 在库中为 NOT NULL）")
    void register_missing_email_returns_400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newbie\",\"fullName\":\"新同学\","
                                + "\"password\":\"Password123\",\"confirmPassword\":\"Password123\","
                                + "\"role\":\"STUDENT\",\"agreement\":true,\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("修改密码-缺少旧密码应被@Valid拦截返回400")
    void change_password_missing_old_returns_400() throws Exception {
        mockMvc.perform(post("/auth/change-password").with(withUser(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NewPassword123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("刷新令牌-缺少refreshToken应返回400")
    void refresh_missing_token_returns_400() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
