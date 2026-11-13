package com.aicourse.controller;

import com.aicourse.service.AuthService;
import com.aicourse.service.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通用上传控制器补充测试：{@code POST /common/upload/batch} 批量上传（此前零覆盖）
 * 与 {@code /common/upload} 的<b>未登录 401 越权</b>边界（此前仅覆盖已登录成功/失败分支）。
 *
 * <p>安全要点：控制器对文件名做「扩展名白名单 + 随机 UUID 落盘」处理——
 * {@code objectName = UUID + ext}，因此含 {@code ../} 的路径穿越文件名不会参与落盘路径，
 * 仅作为展示用 fileName 原样返回。测试捕获传入存储层的 objectName，断言其不含 {@code ..}。</p>
 */
@WebMvcTest(CommonController.class)
class CommonControllerUploadTest extends ControllerTestBase {

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private FileStorageService fileStorageService;
    @MockitoBean
    private com.aicourse.util.ClientInfoResolver clientInfoResolver;

    /** 10A 当前学期端点（/common/semester/active）注入的学期服务 */
    @MockitoBean
    private com.aicourse.service.SemesterService semesterService;

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("files", name, "image/png", "bytes".getBytes());
    }

    @Test
    @DisplayName("POST /common/upload/batch - 未登录返回401，绝不落盘")
    void batch_unauthenticated_returns401() throws Exception {
        mockMvc.perform(multipart("/common/upload/batch").file(png("a.png")))
                .andExpect(status().isUnauthorized());
        verify(fileStorageService, never()).upload(anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("POST /common/upload/batch - 两个合法文件全部成功入库，逐项含 url/fileName/size 且无 error 键")
    void batch_allValid_success() throws Exception {
        when(fileStorageService.upload(anyString(), any(InputStream.class), any(), anyLong()))
                .thenReturn("stored.png");
        mockMvc.perform(multipart("/common/upload/batch")
                        .file(png("one.png")).file(png("two.png"))
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].url").value("/uploads/stored.png"))
                .andExpect(jsonPath("$.data[0].fileName").value("one.png"))
                .andExpect(jsonPath("$.data[0].error").doesNotExist())
                .andExpect(jsonPath("$.data[1].fileName").value("two.png"));
        verify(fileStorageService, times(2)).upload(anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("POST /common/upload/batch - 非法扩展名(.exe)项标记失败且不落盘，成功项不受影响")
    void batch_mixedValidAndInvalidExt() throws Exception {
        when(fileStorageService.upload(anyString(), any(InputStream.class), any(), anyLong()))
                .thenReturn("stored.png");
        MockMultipartFile exe = new MockMultipartFile("files", "malware.exe",
                "application/octet-stream", "MZ".getBytes());
        mockMvc.perform(multipart("/common/upload/batch")
                        .file(png("ok.png")).file(exe)
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].error").doesNotExist())
                .andExpect(jsonPath("$.data[1].url").value(""))
                .andExpect(jsonPath("$.data[1].error")
                        .value(org.hamcrest.Matchers.containsString("不支持的文件类型")));
        // 仅合法文件落盘一次
        verify(fileStorageService, times(1)).upload(anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("POST /common/upload/batch - 空文件被跳过（不产生结果项，也不落盘）")
    void batch_emptyFileSkipped() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("files", "e.txt", "text/plain", new byte[0]);
        MockMultipartFile ok = png("ok.png");
        when(fileStorageService.upload(anyString(), any(InputStream.class), any(), anyLong()))
                .thenReturn("stored.png");
        mockMvc.perform(multipart("/common/upload/batch").file(empty).file(ok)
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fileName").value("ok.png"));
        verify(fileStorageService, times(1)).upload(anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("POST /common/upload/batch - 路径穿越文件名不会污染落盘路径（objectName 为 UUID，不含 ..）")
    void batch_pathTraversalFilename_neutralizedInStorageKey() throws Exception {
        when(fileStorageService.upload(anyString(), any(InputStream.class), any(), anyLong()))
                .thenReturn("stored.png");
        MockMultipartFile traversal = new MockMultipartFile("files",
                "../../etc/passwd.png", "image/png", "x".getBytes());
        mockMvc.perform(multipart("/common/upload/batch").file(traversal)
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileName").value("../../etc/passwd.png"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorageService).upload(keyCaptor.capture(), any(), any(), anyLong());
        String objectName = keyCaptor.getValue();
        assertFalse(objectName.contains(".."), "落盘对象名不得含路径穿越片段，实际=" + objectName);
        assertTrue(objectName.endsWith(".png"), "应保留合法扩展名，实际=" + objectName);
    }

    @Test
    @DisplayName("POST /common/upload - 未登录返回401（单文件同样需认证）")
    void single_unauthenticated_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "b".getBytes());
        mockMvc.perform(multipart("/common/upload").file(file))
                .andExpect(status().isUnauthorized());
        verify(fileStorageService, never()).upload(anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("POST /common/upload - 存储层收到不落盘扩展名时绝不调用（.html 白名单外）")
    void single_htmlExtension_neverUploaded() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.html", "text/html", "<b>".getBytes());
        mockMvc.perform(multipart("/common/upload").file(file).with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verify(fileStorageService, never()).upload(anyString(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("POST /common/upload/batch - files 参数缺失（无 part）返回400，不落盘")
    void batch_missingFiles_returns400() throws Exception {
        // 缺少必需的 files 多部件
        mockMvc.perform(multipart("/common/upload/batch").with(withUser(1L, "STUDENT")))
                .andExpect(status().isBadRequest());
        List<?> nothing = List.of();
        assertTrue(nothing.isEmpty());
        verify(fileStorageService, never()).upload(anyString(), any(), any(), anyLong());
    }
}
