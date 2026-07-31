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

import org.springframework.security.test.context.support.WithMockUser;

/**
 * API 契约测试
 * 验证所有 Controller 的关键端点返回正确的 HTTP 状态码
 *
 * <p>使用 @SpringBootTest + @AutoConfigureMockMvc 端到端验证 HTTP 层的契约正确性。
 * 启用 contract-test profile，复用本机 MySQL/Redis（sql.init 关闭，不触碰数据）。
 * 集成测试（Testcontainers）场景由继承 AbstractIntegrationTest 的类覆盖。
 *
 * <p>注意：MockMvc 请求路径不含 context-path（/api）前缀——MockMvc 环境下
 * contextPath 为空，Spring Security 的请求匹配与 Controller 映射均按去前缀路径生效，
 * 与实际部署中 Servlet 视图一致。
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("contract-test")
@DisplayName("API 契约测试")
class ApiContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ===================== 认证接口契约 =====================

    @Test
    @DisplayName("GET /auth/me - 未认证返回 200 且业务码 401")
    void getAuthMe_WithoutAuthentication_ShouldReturnError() throws Exception {
        // /auth/me 位于 permitAll 的 /auth/** 下，未认证请求会到达 Controller，
        // 由 Controller 返回 HTTP 200 + 业务码 401（HTTP 401 仅由安全层对受保护路径产生）
        mockMvc.perform(get("/auth/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("POST /auth/login - 有效输入返回200")
    void postAuthLogin_ValidInput_ShouldReturn200() throws Exception {
        Map<String, Object> loginBody = Map.of(
                "username", "student",
                "password", "123456",
                "rememberMe", false
        );

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /auth/login - 空请求体返回400")
    void postAuthLogin_EmptyBody_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register - 无效数据返回400")
    void postAuthRegister_InvalidData_ShouldReturn400() throws Exception {
        Map<String, Object> registerBody = Map.of(
                "username", "",
                "password", "short",
                "fullName", "",
                "role", "STUDENT",
                "email", "invalid-email"
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerBody))
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    // ===================== 课程接口契约 =====================

    @Test
    @DisplayName("GET /courses - 未认证应返回401")
    void getCourses_WithoutAuth_ShouldReturn401() throws Exception {
        // 课程资源仅 POST/PUT/DELETE/PATCH 限教师/管理员，但 GET 仍要求登录认证
        mockMvc.perform(get("/courses")
                        .param("page", "1")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("GET /courses - 已登录用户分页查询返回200")
    void getCourses_WithAuth_ShouldReturn200() throws Exception {
        mockMvc.perform(get("/courses")
                        .param("page", "1")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    @DisplayName("GET /courses/{id} - 查询不存在的课程返回 200 且无 data 字段")
    void getCourseById_NotFound_ShouldReturn200WithNullData() throws Exception {
        mockMvc.perform(get("/courses/99999")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                // 统一 ApiResponse 包装：code=200（未找到返回 success(null) 契约）、message 存在；
                // Jackson 全局 non_null 序列化会省略值为 null 的 data 字段
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ===================== 考试接口契约 =====================

    @Test
    @DisplayName("GET /exam-sessions/active - 未认证应返回4xx")
    void getActiveExams_WithoutAuth_ShouldReturnError() throws Exception {
        // /exam-sessions/active 需要 @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
        mockMvc.perform(get("/exam-sessions/active")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    // ===================== 公共接口契约 =====================

    @Test
    @DisplayName("POST /common/verify-code - 公开接口返回200")
    void getVerifyCode_ShouldReturn200() throws Exception {
        // 每次运行使用唯一邮箱，避免触发「1分钟内仅允许一次」的按邮箱限流
        String uniqueEmail = "contract-test-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/common/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());
    }

    // ===================== 安全边界契约 =====================

    @Test
    @DisplayName("GET /admin/dashboard - 未认证返回4xx/5xx")
    void getAdminEndpoint_WithoutAuth_ShouldReturnError() throws Exception {
        // /admin/** 需要 ADMIN 角色
        mockMvc.perform(get("/admin/analytics")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(
                        status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /auth/refresh - 无刷新令牌Cookie返回4xx")
    void postRefreshToken_WithoutCookie_ShouldReturnError() throws Exception {
        // /auth/refresh 需要从 Cookie 读取刷新令牌
        MvcResult result = mockMvc.perform(post("/auth/refresh")
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
