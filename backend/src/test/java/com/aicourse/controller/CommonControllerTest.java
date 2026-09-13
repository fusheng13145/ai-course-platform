package com.aicourse.controller;
import com.aicourse.common.ApiResponse;
import com.aicourse.controller.CommonController;
import com.aicourse.service.AuthService;
import com.aicourse.service.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * CommonController 接口测试。
 */
@WebMvcTest(CommonController.class)
class CommonControllerTest extends ControllerTestBase {

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private FileStorageService fileStorageService;
    @MockitoBean
    private com.aicourse.util.ClientInfoResolver clientInfoResolver;

    // ==================== 验证码 ====================

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
    @DisplayName("POST /common/verify-code - 邮箱为空返回 400")
    void sendVerifyCode_emptyEmail_returns400() throws Exception {
        mockMvc.perform(post("/common/verify-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"type\":\"FORGOT_PASSWORD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱不能为空"));
    }

    // ==================== 文件上传 ====================

    @Test
    @DisplayName("POST /common/upload - 上传合法图片文件成功")
    void uploadFile_validPng_success() throws Exception {
        when(fileStorageService.upload(anyString(), any(InputStream.class), any(), anyLong()))
                .thenReturn("test-uuid.png");

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/common/upload").file(file)
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.url").value("/uploads/test-uuid.png"))
                .andExpect(jsonPath("$.data.fileName").value("test.png"));
    }

    @Test
    @DisplayName("POST /common/upload - 上传非法扩展名被拒绝")
    void uploadFile_invalidExt_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.html", "text/html", "<script>alert(1)</script>".getBytes());

        mockMvc.perform(multipart("/common/upload").file(file)
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不支持的文件类型")));

        verify(fileStorageService, never()).upload(anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("POST /common/upload - 空文件返回错误")
    void uploadFile_emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/common/upload").file(file)
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("上传文件不能为空"));

        verify(fileStorageService, never()).upload(anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("POST /common/upload - FileStorageService 异常返回 500")
    void uploadFile_storageError_returns500() throws Exception {
        when(fileStorageService.upload(anyString(), any(InputStream.class), any(), anyLong()))
                .thenThrow(new RuntimeException("磁盘已满"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", "test".getBytes());

        mockMvc.perform(multipart("/common/upload").file(file)
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("上传失败")));
    }

    // ==================== 功能路线图 ====================

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
