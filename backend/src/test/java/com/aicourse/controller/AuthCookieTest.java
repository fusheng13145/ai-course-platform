package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.CookieUtils;
import com.aicourse.service.AuthService;
import com.aicourse.service.PermissionService;
import com.aicourse.service.UserService;
import com.aicourse.util.JwtUtils;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证 Cookie 下发与剥离测试。
 *
 * <p>验证：登录/注册/刷新将令牌写入 HttpOnly Cookie 且从响应体移除；退出清除 Cookie。
 * 目标是确认前端永远拿不到原始令牌字符串，令牌仅存在于 HttpOnly Cookie 中（XSS 不可读）。</p>
 */
@WebMvcTest(AuthController.class)
@Import(CookieUtils.class)
class AuthCookieTest extends ControllerTestBase {

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private PermissionService permissionService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private JwtUtils jwtUtils;
    @MockitoBean
    private com.aicourse.util.ClientInfoResolver clientInfoResolver;

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    private Map<String, Object> loginResultMap() {
        Map<String, Object> data = new HashMap<>(4);
        data.put("token", "access-jwt-value");
        data.put("refreshToken", "refresh-uuid-value");
        Map<String, Object> user = new HashMap<>(2);
        user.put("id", 1L);
        user.put("username", "alice");
        data.put("user", user);
        return data;
    }

    @Test
    void login_issuesHttpOnlyCookiesAndStripsTokensFromBody() throws Exception {
        when(jwtUtils.getRemainingSeconds(anyString())).thenReturn(3600L);
        when(authService.login(anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(ApiResponse.success("登录成功", loginResultMap()));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret\",\"rememberMe\":true}"))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(JwtUtils.ACCESS_TOKEN_COOKIE, true))
                .andExpect(cookie().value(JwtUtils.ACCESS_TOKEN_COOKIE, "access-jwt-value"))
                .andExpect(cookie().path(JwtUtils.ACCESS_TOKEN_COOKIE, "/"))
                .andExpect(cookie().httpOnly(JwtUtils.REFRESH_TOKEN_COOKIE, true))
                .andExpect(cookie().value(JwtUtils.REFRESH_TOKEN_COOKIE, "refresh-uuid-value"))
                .andExpect(cookie().path(JwtUtils.REFRESH_TOKEN_COOKIE, "/api/auth/refresh"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = body.get("data");
        assertNotNull(data);
        // 响应体不得再携带令牌字段
        assertFalse(data.has("token"), "响应体不应包含 token 字段");
        assertFalse(data.has("refreshToken"), "响应体不应包含 refreshToken 字段");
        assertTrue(data.has("user"), "响应体应包含非敏感 user 字段");
    }

    @Test
    void refresh_readsRefreshCookieAndRotatesAccessToken() throws Exception {
        when(jwtUtils.getRemainingSeconds(anyString())).thenReturn(3600L);
        Map<String, Object> data = new HashMap<>(2);
        data.put("token", "new-access-jwt");
        data.put("userId", 1L);
        when(authService.refreshToken("refresh-uuid-value"))
                .thenReturn(ApiResponse.success(data));

        MvcResult result = mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie(JwtUtils.REFRESH_TOKEN_COOKIE, "refresh-uuid-value")))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(JwtUtils.ACCESS_TOKEN_COOKIE, true))
                .andExpect(cookie().value(JwtUtils.ACCESS_TOKEN_COOKIE, "new-access-jwt"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(body.get("data").has("token"), "刷新响应体不应包含 token 字段");
    }

    @Test
    void logout_clearsAuthCookies() throws Exception {
        when(authService.logout(anyLong(), any())).thenReturn(ApiResponse.success("退出成功", null));

        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie(JwtUtils.ACCESS_TOKEN_COOKIE, "access-jwt-value"))
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(JwtUtils.ACCESS_TOKEN_COOKIE, 0))
                .andExpect(cookie().maxAge(JwtUtils.REFRESH_TOKEN_COOKIE, 0));
    }
}
