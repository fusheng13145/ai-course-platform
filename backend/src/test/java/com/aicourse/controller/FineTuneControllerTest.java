package com.aicourse.controller;

import com.aicourse.service.LLMFineTuneService;
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
 * FineTuneController 接口测试（LoRA 微调编排）：角色边界（401/403）、健康检查、
 * 服务可达时的任务编排委托、服务不可达时的 503 降级语义。
 */
@WebMvcTest(FineTuneController.class)
class FineTuneControllerTest extends ControllerTestBase {

    @MockitoBean
    private LLMFineTuneService fineTuneService;

    @Test
    @DisplayName("GET /admin/fine-tune/health - 管理员返回200且available透传")
    void health_admin_returns_200() throws Exception {
        when(fineTuneService.healthCheck()).thenReturn(true);
        mockMvc.perform(get("/admin/fine-tune/health").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("GET /admin/fine-tune/health - 未认证返回401")
    void health_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/admin/fine-tune/health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /admin/fine-tune/health - 教师角色返回403（仅管理员）")
    void health_teacher_returns_403() throws Exception {
        mockMvc.perform(get("/admin/fine-tune/health").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/fine-tune/tasks - 服务可达时返回200且委托 startFineTune")
    void start_serviceAvailable_returns_200() throws Exception {
        when(fineTuneService.healthCheck()).thenReturn(true);
        when(fineTuneService.startFineTune(eq(1L), eq("qwen-7b"), anyString()))
                .thenReturn("{\"task_id\":\"ft_1\",\"status\":\"pending\"}");

        mockMvc.perform(post("/admin/fine-tune/tasks?courseId=1&baseModel=qwen-7b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"epochs\":3,\"lora_rank\":8}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("{\"task_id\":\"ft_1\",\"status\":\"pending\"}"));
        verify(fineTuneService).startFineTune(eq(1L), eq("qwen-7b"), contains("lora_rank"));
    }

    @Test
    @DisplayName("POST /admin/fine-tune/tasks - 服务不可达返回503降级且不委托")
    void start_serviceUnavailable_returns_503() throws Exception {
        when(fineTuneService.healthCheck()).thenReturn(false);
        mockMvc.perform(post("/admin/fine-tune/tasks?courseId=1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不可达")));
        verify(fineTuneService, never()).startFineTune(any(), any(), any());
    }

    @Test
    @DisplayName("GET /admin/fine-tune/tasks/ft_1 - 返回200且状态透传")
    void status_serviceAvailable_returns_200() throws Exception {
        when(fineTuneService.healthCheck()).thenReturn(true);
        when(fineTuneService.getTaskStatus("ft_1")).thenReturn("{\"status\":\"running\",\"progress\":42}");
        mockMvc.perform(get("/admin/fine-tune/tasks/ft_1").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("{\"status\":\"running\",\"progress\":42}"));
        verify(fineTuneService).getTaskStatus("ft_1");
    }

    @Test
    @DisplayName("GET /admin/fine-tune/tasks - 返回200且列表透传")
    void list_serviceAvailable_returns_200() throws Exception {
        when(fineTuneService.healthCheck()).thenReturn(true);
        when(fineTuneService.listTasks(1L)).thenReturn("{\"tasks\":[]}");
        mockMvc.perform(get("/admin/fine-tune/tasks?courseId=1").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(fineTuneService).listTasks(1L);
    }

    @Test
    @DisplayName("POST /admin/fine-tune/tasks/ft_1/cancel - 返回200且委托 cancelTask")
    void cancel_serviceAvailable_returns_200() throws Exception {
        when(fineTuneService.healthCheck()).thenReturn(true);
        when(fineTuneService.cancelTask("ft_1")).thenReturn("{\"status\":\"cancelled\"}");
        mockMvc.perform(post("/admin/fine-tune/tasks/ft_1/cancel").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(fineTuneService).cancelTask("ft_1");
    }

    @Test
    @DisplayName("POST /admin/fine-tune/tasks/ft_1/infer - 返回200且委托 inferWithFineTuned")
    void infer_serviceAvailable_returns_200() throws Exception {
        when(fineTuneService.healthCheck()).thenReturn(true);
        when(fineTuneService.inferWithFineTuned(eq("ft_1"), eq("什么是导数"), anyString()))
                .thenReturn("{\"content\":\"导数是...\"}");
        mockMvc.perform(post("/admin/fine-tune/tasks/ft_1/infer?prompt=什么是导数")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperature\":0.7}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(fineTuneService).inferWithFineTuned(eq("ft_1"), eq("什么是导数"), contains("temperature"));
    }
}
