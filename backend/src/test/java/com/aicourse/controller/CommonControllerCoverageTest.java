package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.AuthService;
import com.aicourse.service.FileStorageService;
import com.aicourse.util.ClientInfoResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖率测试：CommonController（独立 MockMvc，不加载 Spring 上下文）。
 * 覆盖验证码、单文件上传（空/非法类型/成功/异常）、批量上传（跳过空/非法/成功/异常）、功能路线图等分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommonController 覆盖率测试")
class CommonControllerCoverageTest {

    @Mock
    private AuthService authService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ClientInfoResolver clientInfoResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        CommonController controller = new CommonController(authService, fileStorageService, clientInfoResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        lenient().when(authService.sendVerificationCode(any(), any(), any())).thenReturn(ApiResponse.success());
        lenient().when(fileStorageService.upload(anyString(), any(InputStream.class), any(), anyLong()))
                .thenReturn("test-uuid.png");
    }

    @Test
    @DisplayName("POST /common/verify-code 成功 / 邮箱为空")
    void sendVerifyCode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/common/verify-code")
                        .contentType("application/json")
                        .content("{\"email\":\"student@example.com\",\"type\":\"FORGOT_PASSWORD\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.post("/common/verify-code")
                        .contentType("application/json").content("{\"email\":\"\",\"type\":\"FORGOT_PASSWORD\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /common/upload 空文件 / 非法类型 / 成功 / 存储异常")
    void uploadFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        mockMvc.perform(MockMvcRequestBuilders.multipart("/common/upload").file(empty)).andExpect(status().isOk());

        MockMultipartFile evil = new MockMultipartFile("file", "evil.html", "text/html",
                "<script>alert(1)</script>".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/common/upload").file(evil)).andExpect(status().isOk());

        MockMultipartFile ok = new MockMultipartFile("file", "test.png", "image/png", "img".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/common/upload").file(ok)).andExpect(status().isOk());

        when(fileStorageService.upload(anyString(), any(InputStream.class), any(), anyLong()))
                .thenThrow(new RuntimeException("磁盘已满"));
        MockMultipartFile fail = new MockMultipartFile("file", "test.png", "image/png", "img".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/common/upload").file(fail)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /common/upload/batch 空文件跳过 / 非法类型 / 成功 / 异常")
    void uploadBatch() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("files", "empty.txt", "text/plain", new byte[0]);
        MockMultipartFile evil = new MockMultipartFile("files", "evil.html", "text/html",
                "<script>alert(1)</script>".getBytes());
        MockMultipartFile ok = new MockMultipartFile("files", "test.png", "image/png", "img".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/common/upload/batch")
                        .file(empty).file(evil).file(ok)).andExpect(status().isOk());

        when(fileStorageService.upload(anyString(), any(InputStream.class), any(), anyLong()))
                .thenThrow(new RuntimeException("磁盘已满"));
        MockMultipartFile ok2 = new MockMultipartFile("files", "test2.png", "image/png", "img".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/common/upload/batch").file(ok2)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /common/feature-roadmap")
    void featureRoadmap() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/common/feature-roadmap")).andExpect(status().isOk());
    }
}
