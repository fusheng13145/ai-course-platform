package com.aicourse.controller;
import com.aicourse.controller.AdminAiController;
import com.aicourse.llm.RoutingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * AdminAiController 接口测试。
 * 升级自生成器产出的弱断言（status != 401 && status != 403），改为真实业务断言：
 * 对 RoutingLLMProvider 的相关方法打桩并用 jsonPath 校验响应体关键字段透传，verify 确认路由委托与参数透传。
 */
@WebMvcTest(AdminAiController.class)
class AdminAiControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.llm.RoutingLLMProvider routingLLMProvider;

    @Test
    @DisplayName("GET /admin/ai/current - ADMIN 查询当前供应商返回 code=200 且字段透传")
    void current_authorized_passes_security() throws Exception {
        when(routingLLMProvider.name()).thenReturn("router(deepseek)");
        when(routingLLMProvider.isConfigured()).thenReturn(true);

        mockMvc.perform(get("/admin/ai/current").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.provider").value("deepseek"))
                .andExpect(jsonPath("$.data.llmName").value("router(deepseek)"))
                .andExpect(jsonPath("$.data.configured").value(true));

        verify(routingLLMProvider, atLeastOnce()).name();
        verify(routingLLMProvider).isConfigured();
    }


    @Test
    @DisplayName("GET /admin/ai/current - 未认证返回401")
    void current_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/admin/ai/current"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /admin/ai/current - STUDENT角色返回403")
    void current_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/admin/ai/current").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /admin/ai/provider - ADMIN 切换供应商返回 code=200 且委托 switchTo")
    void switchProvider_authorized_passes_security() throws Exception {
        when(routingLLMProvider.name()).thenReturn("router(qwen)");

        mockMvc.perform(post("/admin/ai/provider").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"qwen\"}").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.provider").value("qwen"))
                .andExpect(jsonPath("$.data.llmName").value("router(qwen)"));

        verify(routingLLMProvider).switchTo(eq("qwen"));
    }


    @Test
    @DisplayName("POST /admin/ai/provider - 未认证返回401")
    void switchProvider_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/admin/ai/provider").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /admin/ai/provider - STUDENT角色返回403")
    void switchProvider_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/admin/ai/provider").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /admin/ai/test - ADMIN 连通性测试返回 code=200 且 ok=true")
    void test_authorized_passes_security() throws Exception {
        when(routingLLMProvider.testConnection()).thenReturn(true);
        when(routingLLMProvider.name()).thenReturn("router(deepseek)");
        when(routingLLMProvider.isConfigured()).thenReturn(true);

        mockMvc.perform(get("/admin/ai/test").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.configured").value(true));

        verify(routingLLMProvider).testConnection();
    }


    @Test
    @DisplayName("GET /admin/ai/test - 未认证返回401")
    void test_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/admin/ai/test"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /admin/ai/test - STUDENT角色返回403")
    void test_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/admin/ai/test").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

}
