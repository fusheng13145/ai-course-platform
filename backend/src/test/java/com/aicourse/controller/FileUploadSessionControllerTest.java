package com.aicourse.controller;
import com.aicourse.controller.FileUploadSessionController;
import com.aicourse.service.FileUploadSessionService;
import com.aicourse.entity.FileUploadSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * FileUploadSessionController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 FileUploadSessionService 方法打桩返回合理业务对象，断言 200/code=200，并校验关键字段透传、verify 路由委托。
 * 该控制器无 @PreAuthorize，仅需认证，故不新增越权 403 测试。
 */
@WebMvcTest(FileUploadSessionController.class)
class FileUploadSessionControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.FileUploadSessionService fileUploadSessionService;

    private FileUploadSession sampleSession(Long id, String filename) {
        FileUploadSession s = new FileUploadSession();
        s.setId(id);
        s.setFilename(filename);
        s.setStatus("UPLOADING");
        return s;
    }

    @Test
    @DisplayName("GET /upload-sessions - 管理员返回200且会话列表透传")
    void listSessions_admin_returns_200() throws Exception {
        when(fileUploadSessionService.list(
                any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(java.util.List.of(sampleSession(1L, "a.pdf")));
        mockMvc.perform(get("/upload-sessions").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].filename").value("a.pdf"));
    }

    @Test
    @DisplayName("GET /upload-sessions - 非管理员返回403")
    void listSessions_nonAdmin_returns_403() throws Exception {
        mockMvc.perform(get("/upload-sessions").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /upload-sessions - 返回200且上传会话透传")
    void createUploadSession_authorized_returns_200() throws Exception {
        when(fileUploadSessionService.createUploadSession("a.pdf", 1024L, 2L, 1L)).thenReturn(sampleSession(1L, "a.pdf"));
        mockMvc.perform(post("/upload-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"a.pdf\",\"fileSize\":1024,\"totalChunks\":2}")
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.filename").value("a.pdf"));
        verify(fileUploadSessionService).createUploadSession(eq("a.pdf"), eq(1024L), eq(2L), eq(1L));
    }


    @Test
    @DisplayName("POST /upload-sessions - 未认证返回401")
    void createUploadSession_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/upload-sessions").contentType(MediaType.APPLICATION_JSON).content("{\"filename\":\"a.pdf\",\"fileSize\":1024,\"totalChunks\":2}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /upload-sessions/1/chunks/1 - 返回200且分片上传后会话透传")
    void uploadChunk_authorized_returns_200() throws Exception {
        when(fileUploadSessionService.uploadChunk(1L, 1, 1L)).thenReturn(sampleSession(1L, "a.pdf"));
        mockMvc.perform(post("/upload-sessions/1/chunks/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(fileUploadSessionService).uploadChunk(eq(1L), eq(1), eq(1L));
    }


    @Test
    @DisplayName("POST /upload-sessions/1/chunks/1 - 未认证返回401")
    void uploadChunk_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/upload-sessions/1/chunks/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /upload-sessions/1/merge - 返回200且合并后会话透传")
    void mergeChunks_authorized_returns_200() throws Exception {
        FileUploadSession merged = sampleSession(1L, "a.pdf");
        merged.setStatus("COMPLETED");
        when(fileUploadSessionService.mergeChunks(1L, 1L)).thenReturn(merged);
        mockMvc.perform(post("/upload-sessions/1/merge").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        verify(fileUploadSessionService).mergeChunks(eq(1L), eq(1L));
    }


    @Test
    @DisplayName("POST /upload-sessions/1/merge - 未认证返回401")
    void mergeChunks_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/upload-sessions/1/merge"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /upload-sessions/1/progress - 返回200且进度透传")
    void getUploadProgress_authorized_returns_200() throws Exception {
        when(fileUploadSessionService.getUploadProgress(1L, 1L)).thenReturn(Map.of("uploaded", 1, "total", 2, "percentage", 50));
        mockMvc.perform(get("/upload-sessions/1/progress").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.percentage").value(50));
        verify(fileUploadSessionService).getUploadProgress(eq(1L), eq(1L));
    }


    @Test
    @DisplayName("GET /upload-sessions/1/progress - 未认证返回401")
    void getUploadProgress_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/upload-sessions/1/progress"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("DELETE /upload-sessions/1/cancel - 返回200且取消委托被调用")
    void cancelUpload_authorized_returns_200() throws Exception {
        mockMvc.perform(delete("/upload-sessions/1/cancel").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(fileUploadSessionService).cancelUpload(eq(1L), eq(1L));
    }


    @Test
    @DisplayName("DELETE /upload-sessions/1/cancel - 未认证返回401")
    void cancelUpload_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/upload-sessions/1/cancel"))
                .andExpect(status().isUnauthorized());
    }

}
