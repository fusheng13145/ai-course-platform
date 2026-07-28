package com.aicourse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API 契约测试
 * 验证所有 Controller 的关键端点返回正确的 HTTP 状态码
 *
 * <p>使用 @SpringBootTest + @AutoConfigureMockMvc 端到端验证 HTTP 层的契约正确性。
 * 启用 test profile 以使用测试容器（Testcontainers）的 MySQL/Redis 环境。
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API 契约测试")
class ApiContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ===================== 认证接口契约 =====================

    @Test
    @DisplayName("GET /api/auth/me - 未认证应返回非200状态码")
    void getAuthMe_WithoutAuthentication_ShouldReturnError() throws Exception {
        // /auth/** 为 permitAll 路径，但 /me 接口的 Authentication 参数依赖认证态。
        // 无有效令牌时，SecurityContext 中无 Authentication，Controller 无法注入。
        mockMvc.perform(get("/api/auth/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").isNumber());
    }

    @Test
    @DisplayName("POST /api/auth/login - 有效输入返回200")
    void postAuthLogin_ValidInput_ShouldReturn200() throws Exception {
        Map<String, Object> loginBody = Map.of(
                "username", "testuser",
                "password", "Password123",
                "rememberMe", false
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/auth/login - 空请求体返回400")
    void postAuthLogin_EmptyBody_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - 无效数据返回400")
    void postAuthRegister_InvalidData_ShouldReturn400() throws Exception {
        Map<String, Object> registerBody = Map.of(
                "username", "",
                "password", "short",
                "fullName", "",
                "role", "STUDENT",
                "email", "invalid-email"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    // ===================== 课程接口契约 =====================

    @Test
    @DisplayName("GET /api/courses - 分页查询课程返回200")
    void getCourses_ShouldReturn200() throws Exception {
        mockMvc.perform(get("/api/courses")
                        .param("page", "1")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/courses/{id} - 查询不存在的课程返回200(null data)")
    void getCourseById_NotFound_ShouldReturn200WithNullData() throws Exception {
        mockMvc.perform(get("/api/courses/99999")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());
    }

    // ===================== 考试接口契约 =====================

    @Test
    @DisplayName("GET /api/exam-sessions/active - 未认证应返回4xx")
    void getActiveExams_WithoutAuth_ShouldReturnError() throws Exception {
        // /exam-sessions/active 需要 @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
        mockMvc.perform(get("/api/exam-sessions/active")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    // ===================== 公共接口契约 =====================

    @Test
    @DisplayName("GET /api/common/verify-code - 公开接口返回200")
    void getVerifyCode_ShouldReturn200() throws Exception {
        mockMvc.perform(get("/api/common/verify-code")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());
    }

    // ===================== 安全边界契约 =====================

    @Test
    @DisplayName("GET /api/admin/dashboard - 未认证返回4xx/5xx")
    void getAdminEndpoint_WithoutAuth_ShouldReturnError() throws Exception {
        // /admin/** 需要 ADMIN 角色
        mockMvc.perform(get("/api/admin/analytics")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(
                        status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /api/auth/refresh - 无刷新令牌Cookie返回4xx")
    void postRefreshToken_WithoutCookie_ShouldReturnError() throws Exception {
        // /auth/refresh 需要从 Cookie 读取刷新令牌
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().is4xxClientError())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(
                body.contains("缺失") || body.contains("失效"),
                "Response should indicate missing/invalid refresh token, got: " + body
        );
    }
}
