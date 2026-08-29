package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.User;
import com.aicourse.exception.GlobalExceptionHandler;
import com.aicourse.service.AuthService;
import com.aicourse.service.PermissionService;
import com.aicourse.service.UserService;
import com.aicourse.common.CookieUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖率测试：AuthController（独立 MockMvc，不加载 Spring 上下文）。
 * 覆盖登录/注册/刷新/登出/改密/重置/个人资料/权限等端点的成功与未登录/异常分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 覆盖率测试")
class AuthControllerCoverageTest {

    @Mock
    private AuthService authService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private UserService userService;
    @Mock
    private CookieUtils cookieUtils;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        AuthController controller = new AuthController(authService, permissionService, userService, cookieUtils);
        // 独立 MockMvc 需手动注册全局异常处理器，使 BusinessException 被统一转换为 4xx
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        // 注意：控制器会对返回的 data 执行 Map.remove("token"/"refreshToken")，
        // 因此必须用可变 Map，否则 Map.of() 不可变会抛 UnsupportedOperationException
        Map<String, Object> tokens = new java.util.HashMap<>();
        tokens.put("token", "t");
        tokens.put("refreshToken", "r");
        lenient().when(authService.login(any(), any(), any(), any()))
                .thenReturn(ApiResponse.success(new java.util.HashMap<>(tokens)));
        lenient().when(authService.register(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ApiResponse.success(new java.util.HashMap<>(tokens)));
        lenient().when(authService.refreshToken(any()))
                .thenReturn(ApiResponse.success(new java.util.HashMap<>(tokens)));
        lenient().when(authService.logout(any(), any())).thenReturn(ApiResponse.success());
        lenient().when(authService.changePassword(any(), any(), any())).thenReturn(ApiResponse.success());
        lenient().when(authService.getCurrentUser(any())).thenReturn(ApiResponse.success(Map.of("id", 1L)));
        lenient().when(authService.resetPassword(any(), any(), any())).thenReturn(ApiResponse.success());
        lenient().when(authService.resetPasswordByEmail(any(), any(), any())).thenReturn(ApiResponse.success());
        lenient().when(authService.sendVerificationCode(any(), any(), any())).thenReturn(ApiResponse.success());
        lenient().when(authService.verifyCode(any(), any())).thenReturn(ApiResponse.success());

        lenient().when(userService.getById(any())).thenReturn(new User());
        lenient().when(userService.updateById(any())).thenReturn(true);

        lenient().when(permissionService.getUserPermissions(any())).thenReturn(Map.of("roles", List.of("STUDENT")));
        lenient().when(cookieUtils.readCookie(any(), any())).thenReturn(null);
        lenient().doNothing().when(cookieUtils).addAuthCookies(any(), any(), any(), anyBoolean());
        lenient().doNothing().when(cookieUtils).clearAuthCookies(any());
    }

    private static RequestPostProcessor withAuth(Long userId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        return request -> {
            request.setUserPrincipal(auth);
            if (userId != null) request.setRemoteUser(String.valueOf(userId));
            return request;
        };
    }

    // ===================== 登录 / 注册 =====================

    @Test
    @DisplayName("POST /auth/login 成功")
    void login() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"alice\",\"password\":\"Password123\",\"rememberMe\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /auth/register 成功")
    void register() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"alice\",\"fullName\":\"Alice\",\"password\":\"Password123\","
                                + "\"confirmPassword\":\"Password123\",\"role\":\"STUDENT\",\"agreement\":true,"
                                + "\"email\":\"alice@example.com\",\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk());
    }

    // ===================== 刷新 / 登出 =====================

    @Test
    @DisplayName("POST /auth/refresh 成功 / 缺失令牌(异常)")
    void refreshToken() throws Exception {
        when(cookieUtils.readCookie(any(), any())).thenReturn("refresh-token-value");
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh")).andExpect(status().isOk());
        // 缺失令牌 -> BusinessException 刷新令牌缺失或已失效 -> 400
        when(cookieUtils.readCookie(any(), any())).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/logout 已登录 / 未登录")
    void logout() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/logout").with(withAuth(1L, "STUDENT")))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/logout")).andExpect(status().isOk());
    }

    // ===================== 改密 =====================

    @Test
    @DisplayName("POST /auth/change-password 与 PUT /auth/password 已登录 / 未登录")
    void changePassword() throws Exception {
        String body = "{\"oldPassword\":\"OldPass123\",\"newPassword\":\"NewPass123\",\"confirmPassword\":\"NewPass123\"}";
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/change-password").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content(body)).andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.put("/auth/password").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content(body)).andExpect(status().isOk());
        // 未登录 -> BusinessException UNAUTHORIZED -> 401
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/change-password")
                        .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
    }

    // ===================== 重置密码 =====================

    @Test
    @DisplayName("POST /auth/reset-password 邮箱 / 手机 / 均无")
    void resetPassword() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/reset-password")
                        .contentType("application/json")
                        .content("{\"email\":\"alice@example.com\",\"code\":\"123456\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/reset-password")
                        .contentType("application/json")
                        .content("{\"phone\":\"13800138000\",\"code\":\"123456\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isOk());
        // 均无 email/phone -> 控制器返回 ApiResponse.error(BAD_REQUEST)（HTTP 200 + body code 400）
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/reset-password")
                        .contentType("application/json")
                        .content("{\"code\":\"123456\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(400));
    }

    // ===================== 当前用户 =====================

    @Test
    @DisplayName("GET /auth/me 已登录 / 未登录")
    void getCurrentUser() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/auth/me").with(withAuth(1L, "STUDENT")))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/auth/me")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /auth/me 成功 / 用户不存在 / 未登录")
    void updateCurrentUser() throws Exception {
        String body = "{\"fullName\":\"Bob\",\"email\":\"bob@example.com\",\"avatar\":\"http://x/a.png\","
                + "\"bio\":\"hi\",\"phone\":\"13800138000\"}";
        mockMvc.perform(MockMvcRequestBuilders.put("/auth/me").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content(body)).andExpect(status().isOk());
        when(userService.getById(999L)).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.put("/auth/me").with(withAuth(999L, "STUDENT"))
                        .contentType("application/json").content(body)).andExpect(status().isOk());
        // 未登录 -> requireLogin 抛 BusinessException(UNAUTHORIZED) -> 401
        mockMvc.perform(MockMvcRequestBuilders.put("/auth/me")
                        .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /auth/permissions 已登录")
    void getPermissions() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/auth/permissions").with(withAuth(1L, "STUDENT")))
                .andExpect(status().isOk());
    }

    // ===================== 验证码 =====================

    @Test
    @DisplayName("验证码发送/邮箱重置/校验")
    void codes() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/send-code")
                        .contentType("application/json").content("{\"email\":\"alice@example.com\",\"type\":\"REGISTER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/reset-password-by-email")
                        .contentType("application/json")
                        .content("{\"email\":\"alice@example.com\",\"code\":\"123456\",\"newPassword\":\"NewPass123!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.post("/auth/verify-code")
                        .contentType("application/json").content("{\"email\":\"alice@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk());
    }
}
