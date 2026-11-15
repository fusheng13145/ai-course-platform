package com.aicourse.controller;
import com.aicourse.controller.RAGController;
import com.aicourse.service.RAGService;
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
 * RAGController 接口测试。
 * 升级自生成器产出的弱断言（status != 401 && status != 403），改为真实业务断言：
 * 对 RAGService 的相关方法打桩（返回合法 JSON 字符串，由控制器反序列化为 JsonNode 透传），
 * 并用 jsonPath 校验响应体 data 关键字段，verify 确认路由委托与参数透传。
 */
@WebMvcTest(RAGController.class)
class RAGControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.RAGService ragService;

    /**
     * 2026-11-06 验收批：控制器新增课程归属/成员关系断言（B-05）。本切片只验路由与信封透传，
     * 替身默认不拦截；归属判定语义见 RAGControllerScopeTest。
     */
    @MockitoBean
    private com.aicourse.security.CourseAccessControl courseAccessControl;

    @Test
    @DisplayName("POST /rag/ask - 已认证用户问答返回 code=200 且答案透传")
    void ask_authorized_passes_security() throws Exception {
        when(ragService.ask(anyString(), any(), anyInt())).thenReturn("{\"answer\":\"hello\",\"sources\":[]}");

        mockMvc.perform(post("/rag/ask?question=test&courseId=1&topK=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").value("hello"));

        verify(ragService).ask(eq("test"), eq(1L), eq(1));
    }


    @Test
    @DisplayName("POST /rag/ask - 未认证返回401")
    void ask_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/rag/ask?question=test&courseId=1&topK=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /rag/documents - TEACHER 添加文档返回 code=200 且字段透传")
    void addDocument_authorized_passes_security() throws Exception {
        when(ragService.addDocument(anyLong(), anyString(), anyString()))
                .thenReturn("{\"id\":1,\"documentName\":\"test\"}");

        mockMvc.perform(post("/rag/documents?courseId=1&documentName=test")
                        .contentType(MediaType.APPLICATION_JSON).content("document body text")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(ragService).addDocument(eq(1L), eq("test"), anyString());
    }


    @Test
    @DisplayName("POST /rag/documents - 未认证返回401")
    void addDocument_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/rag/documents?courseId=1&documentName=test")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /rag/documents - STUDENT角色返回403")
    void addDocument_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/rag/documents?courseId=1&documentName=test")
                        .contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /rag/documents - 已认证用户列出文档返回 code=200 且为数组")
    void listDocuments_authorized_passes_security() throws Exception {
        when(ragService.listDocuments(any())).thenReturn("{\"documents\":[{\"id\":1,\"name\":\"a\"}]}");

        mockMvc.perform(get("/rag/documents?courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.documents").isArray())
                .andExpect(jsonPath("$.data.documents[0].id").value(1));

        verify(ragService).listDocuments(eq(1L));
    }


    @Test
    @DisplayName("GET /rag/documents - 未认证返回401")
    void listDocuments_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/rag/documents?courseId=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PUT /rag/documents/1 - TEACHER 更新文档返回 code=200 且字段透传")
    void updateDocument_authorized_passes_security() throws Exception {
        // 2026-11-13 对象级收口：更新前先经 listDocuments 反查文档归属课程（引擎契约），再过课程管理权
        when(ragService.listDocuments(null))
                .thenReturn("{\"documents\":[{\"id\":1,\"document_name\":\"a\",\"course_id\":9}]}");
        when(ragService.updateDocument(anyLong(), anyString())).thenReturn("{\"id\":1,\"updated\":true}");

        mockMvc.perform(put("/rag/documents/1").contentType(MediaType.APPLICATION_JSON).content("new content")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(ragService).listDocuments(null);
        verify(ragService).updateDocument(eq(1L), anyString());
    }


    @Test
    @DisplayName("PUT /rag/documents/1 - 未认证返回401")
    void updateDocument_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/rag/documents/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PUT /rag/documents/1 - STUDENT角色返回403")
    void updateDocument_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/rag/documents/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("DELETE /rag/documents/1 - TEACHER 删除文档返回 code=200 且字段透传")
    void deleteDocument_authorized_passes_security() throws Exception {
        // 2026-11-13 对象级收口：删除前先经 listDocuments 反查文档归属课程，再过课程管理权
        when(ragService.listDocuments(null))
                .thenReturn("{\"documents\":[{\"id\":1,\"document_name\":\"a\",\"course_id\":9}]}");
        when(ragService.deleteDocument(anyLong())).thenReturn("{\"deleted\":true}");

        mockMvc.perform(delete("/rag/documents/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deleted").value(true));

        verify(ragService).listDocuments(null);
        verify(ragService).deleteDocument(eq(1L));
    }


    @Test
    @DisplayName("DELETE /rag/documents/1 - 未认证返回401")
    void deleteDocument_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/rag/documents/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("DELETE /rag/documents/1 - STUDENT角色返回403")
    void deleteDocument_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/rag/documents/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /rag/index/build/1 - TEACHER 构建索引返回 code=200 且字段透传")
    void buildIndex_authorized_passes_security() throws Exception {
        when(ragService.buildIndex(anyLong())).thenReturn("{\"built\":true}");

        mockMvc.perform(post("/rag/index/build/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.built").value(true));

        verify(ragService).buildIndex(eq(1L));
    }


    @Test
    @DisplayName("POST /rag/index/build/1 - 未认证返回401")
    void buildIndex_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/rag/index/build/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /rag/index/build/1 - STUDENT角色返回403")
    void buildIndex_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/rag/index/build/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("DELETE /rag/index/1 - TEACHER 清理索引返回 code=200 且字段透传")
    void clearIndex_authorized_passes_security() throws Exception {
        when(ragService.clearIndex(anyLong())).thenReturn("{\"cleared\":true}");

        mockMvc.perform(delete("/rag/index/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cleared").value(true));

        verify(ragService).clearIndex(eq(1L));
    }


    @Test
    @DisplayName("DELETE /rag/index/1 - 未认证返回401")
    void clearIndex_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/rag/index/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("DELETE /rag/index/1 - STUDENT角色返回403")
    void clearIndex_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/rag/index/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /rag/retrieve - 已认证用户检索返回 code=200 且字段透传")
    void retrieve_authorized_passes_security() throws Exception {
        when(ragService.retrieve(anyString(), any(), anyInt())).thenReturn("{\"results\":[]}");

        mockMvc.perform(post("/rag/retrieve?query=test&courseId=1&topK=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.results").isArray());

        verify(ragService).retrieve(eq("test"), eq(1L), eq(1));
    }


    @Test
    @DisplayName("POST /rag/retrieve - 未认证返回401")
    void retrieve_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/rag/retrieve?query=test&courseId=1&topK=1"))
                .andExpect(status().isUnauthorized());
    }

}
