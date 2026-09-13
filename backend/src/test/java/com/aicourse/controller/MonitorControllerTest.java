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
 * 覆盖：登录态下正常上报返回 200 / 空 body 容忍 / 未认证请求被拦截（端点已收敛为 isAuthenticated()）。
 */
@WebMvcTest(MonitorController.class)
class MonitorControllerTest extends ControllerTestBase {

    @Test
    @DisplayName("POST /api/monitor/frontend-error - 登录态正常上报返回 code=200")
    void reportFrontendError_ok() throws Exception {
        mockMvc.perform(withUser(post("/api/monitor/frontend-error"), 1L, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"TypeError: x is undefined\"," +
                                "\"component\":\"Dashboard\",\"info\":\"render\"," +
                                "\"url\":\"/student/dashboard\",\"userAgent\":\"Mozilla/5.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/monitor/frontend-error - 登录态空 body 容忍返回 200")
    void reportFrontendError_emptyBody_ok() throws Exception {
        mockMvc.perform(withUser(post("/api/monitor/frontend-error"), 1L, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
