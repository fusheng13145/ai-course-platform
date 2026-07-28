package com.aicourse.controller;
import com.aicourse.common.ApiResponse;
import com.aicourse.controller.CommonController;
import com.aicourse.service.AuthService;
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
 * CommonController 接口测试。
 * 升级自生成器产出的弱断言（status != 401 && status != 403），改为真实业务断言：
 * 对 service 方法打桩并用 jsonPath 校验响应体关键字段透传，verify 确认路由委托与参数透传。
 */
@WebMvcTest(CommonController.class)
class CommonControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.AuthService authService;

    @Test
    @DisplayName("POST /common/verify-code - 发送验证码成功并返回 code=200")
    void sendVerifyCode_authorized_passes_security() throws Exception {
        when(authService.sendVerificationCode(any(), any(), any())).thenReturn(ApiResponse.success());

        mockMvc.perform(post("/common/verify-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@example.com\",\"type\":\"FORGOT_PASSWORD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(authService).sendVerificationCode(eq("student@example.com"), eq("FORGOT_PASSWORD"), any());
    }


    @Test
    @DisplayName("GET /common/feature-roadmap - 返回功能路线图且 code=200")
    void featureRoadmap_authorized_passes_security() throws Exception {
        mockMvc.perform(get("/common/feature-roadmap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.features").isArray());
    }

}
