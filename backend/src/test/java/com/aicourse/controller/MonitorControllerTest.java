package com.aicourse.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MonitorController 接口测试。
 * 覆盖：正常上报返回 200 / 空 body 容忍 / CSRF 与安全链路放行（引导型匿名端点）。
 */
@WebMvcTest(MonitorController.class)
class MonitorControllerTest extends ControllerTestBase {

    @Test
    @DisplayName("POST /api/monitor/frontend-error - 正常上报返回 code=200")
    void reportFrontendError_ok() throws Exception {
        mockMvc.perform(post("/api/monitor/frontend-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"TypeError: x is undefined\"," +
                                "\"component\":\"Dashboard\",\"info\":\"render\"," +
                                "\"url\":\"/student/dashboard\",\"userAgent\":\"Mozilla/5.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/monitor/frontend-error - 空 body 容忍返回 200")
    void reportFrontendError_emptyBody_ok() throws Exception {
        mockMvc.perform(post("/api/monitor/frontend-error")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
