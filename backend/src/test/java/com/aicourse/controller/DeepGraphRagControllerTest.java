package com.aicourse.controller;

import com.aicourse.service.DeepGraphRagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DeepGraphRagController 接口测试：路由委托、参数透传与鉴权拦截（与 RAGControllerTest 同口径）。
 * 服务层替身返回合法 JSON 字符串，由控制器反序列化为 JsonNode 透传；降级语义（503 JSON）
 * 由服务层字符串原样承载，控制器不做二次加工。
 */
@WebMvcTest(DeepGraphRagController.class)
class DeepGraphRagControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeepGraphRagService deepGraphRagService;

    @MockitoBean
    private com.aicourse.security.CourseAccessControl courseAccessControl;

    @Test
    @DisplayName("POST /rag/deepgraph/course-graph/sync - 教师同步课程图谱落库透传")
    void syncCourseGraph_teacher_passesThrough() throws Exception {
        when(deepGraphRagService.syncCourseKnowledgeGraph(anyLong(), anyString()))
                .thenReturn("{\"addedPoints\":495,\"addedRelations\":548,\"skippedRelations\":23}");

        mockMvc.perform(post("/rag/deepgraph/course-graph/sync?courseId=1&documentName=教材")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addedPoints").value(495));

        verify(deepGraphRagService).syncCourseKnowledgeGraph(eq(1L), eq("教材"));
    }

    @Test
    @DisplayName("POST /rag/deepgraph/course-graph/sync - 引擎降级必须 HTTP 503 且响应码一致")
    void syncCourseGraph_engineUnavailable_returns503() throws Exception {
        when(deepGraphRagService.syncCourseKnowledgeGraph(anyLong(), anyString()))
                .thenReturn("{\"mode\":\"UNAVAILABLE\",\"message\":\"文档图谱引擎未启用\",\"code\":503}");

        mockMvc.perform(post("/rag/deepgraph/course-graph/sync?courseId=1&documentName=教材")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("文档图谱引擎未启用"))
                .andExpect(jsonPath("$.data.mode").value("UNAVAILABLE"));
    }

    @Test
    @DisplayName("POST /rag/deepgraph/documents - 引擎降级不得包装为成功")
    void addDocument_engineUnavailable_returns503() throws Exception {
        when(deepGraphRagService.ingest(anyLong(), anyString(), anyString()))
                .thenReturn("{\"success\":false,\"mode\":\"UNAVAILABLE\",\"code\":503,"
                        + "\"message\":\"文档图谱引擎未启用\"}");

        mockMvc.perform(post("/rag/deepgraph/documents?courseId=1&documentName=教材")
                        .contentType(MediaType.APPLICATION_JSON).content("TCP 正文")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.data.success").value(false));
    }

    @Test
    @DisplayName("POST /rag/deepgraph/documents/upload - PDF 魔数通过并原样转发字节")
    void uploadDocument_pdf_passesBytesToService() throws Exception {
        when(deepGraphRagService.ingestFile(anyLong(), anyString(), any(byte[].class), anyString()))
                .thenReturn("{\"success\":true,\"mode\":\"ENGINE\",\"code\":200}");
        MockMultipartFile file = new MockMultipartFile(
                "file", "教材.pdf", "application/pdf", "%PDF-1.4\nbody".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/rag/deepgraph/documents/upload?courseId=1&documentName=教材.pdf")
                        .file(file)
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        verify(deepGraphRagService).ingestFile(eq(1L), eq("教材.pdf"), any(byte[].class),
                eq("application/pdf"));
    }

    @Test
    @DisplayName("POST /rag/deepgraph/documents/upload - 扩展名与魔数不一致返回 400")
    void uploadDocument_magicMismatch_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "教材.pdf", "application/pdf", "not-a-pdf".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/rag/deepgraph/documents/upload?courseId=1")
                        .file(file)
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /rag/deepgraph/course-graph/sync - 未认证返回 401")
    void syncCourseGraph_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/rag/deepgraph/course-graph/sync?courseId=1&documentName=教材"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /rag/deepgraph/health - 返回引擎健康裸 JSON")
    void health_returnsEngineHealth() throws Exception {
        when(deepGraphRagService.healthJson()).thenReturn("{\"enabled\":true,\"reachable\":true}");

        mockMvc.perform(get("/rag/deepgraph/health").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reachable").value(true));
    }

    @Test
    @DisplayName("POST /rag/deepgraph/documents - TEACHER 入库文档返回 200 且透传")
    void addDocument_teacher_passesThrough() throws Exception {
        when(deepGraphRagService.ingest(anyLong(), anyString(), anyString()))
                .thenReturn("{\"success\":true,\"stored_as\":\"course1_教材.txt\",\"chunk_count\":9}");

        mockMvc.perform(post("/rag/deepgraph/documents?courseId=1&documentName=教材")
                        .contentType(MediaType.APPLICATION_JSON).content("TCP 三次握手正文")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.chunk_count").value(9));

        verify(deepGraphRagService).ingest(eq(1L), eq("教材"), anyString());
    }

    @Test
    @DisplayName("POST /rag/deepgraph/documents - 空正文返回 400")
    void addDocument_blankContent_returns400() throws Exception {
        mockMvc.perform(post("/rag/deepgraph/documents?courseId=1&documentName=教材")
                        .contentType(MediaType.APPLICATION_JSON).content("  ")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /rag/deepgraph/documents - 未认证返回 401")
    void addDocument_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/rag/deepgraph/documents?courseId=1&documentName=教材")
                        .contentType(MediaType.APPLICATION_JSON).content("x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /rag/deepgraph/documents - 带 courseId 透传课程文档列表")
    void listDocuments_withCourse_passesThrough() throws Exception {
        when(deepGraphRagService.listDocuments(1L)).thenReturn(
                "{\"documents\":[{\"document_name\":\"course1_教材.txt\"}]}");

        mockMvc.perform(get("/rag/deepgraph/documents?courseId=1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documents[0].document_name").value("course1_教材.txt"));

        verify(deepGraphRagService).listDocuments(eq(1L));
    }

    @Test
    @DisplayName("GET /rag/deepgraph/documents - 学生不带 courseId 返回 403")
    void listDocuments_withoutCourse_studentForbidden() throws Exception {
        mockMvc.perform(get("/rag/deepgraph/documents").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /rag/deepgraph/documents/{name} - 教师删除透传")
    void deleteDocument_teacher_passesThrough() throws Exception {
        when(deepGraphRagService.deleteDocument(anyLong(), anyString()))
                .thenReturn("{\"success\":true}");

        // 路径变量必须走 URI 模板展开：字面量写 %E6.. 会被 MockMvc 二次编码（%25E6）且丢 query
        mockMvc.perform(delete("/rag/deepgraph/documents/{name}?courseId=1", "course1_教材.txt")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        verify(deepGraphRagService).deleteDocument(eq(1L), eq("course1_教材.txt"));
    }

    @Test
    @DisplayName("POST /rag/deepgraph/retrieve - 课程成员可检索且参数透传")
    void retrieve_courseMember_passesThrough() throws Exception {
        when(deepGraphRagService.retrieve(anyString(), anyLong(), anyInt()))
                .thenReturn("{\"results\":[],\"mode\":\"ENGINE\",\"code\":200}");

        mockMvc.perform(post("/rag/deepgraph/retrieve?query=TCP&courseId=1&topK=3")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("ENGINE"));

        verify(deepGraphRagService).retrieve(eq("TCP"), eq(1L), eq(3));
    }

    @Test
    @DisplayName("POST /rag/deepgraph/retrieve - 未带 courseId 返回 400（取数边界）")
    void retrieve_withoutCourse_returns400() throws Exception {
        mockMvc.perform(post("/rag/deepgraph/retrieve?query=TCP").with(withUser(1L, "STUDENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /rag/deepgraph/chapter-graph - 缺章节名返回 400")
    void chapterGraph_missingChapterName_returns400() throws Exception {
        mockMvc.perform(post("/rag/deepgraph/chapter-graph?courseId=1&documentName=教材")
                        .contentType(MediaType.APPLICATION_JSON).content("章节正文")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /rag/deepgraph/chapter-graph - 教师构建章节图谱透传")
    void chapterGraph_teacher_passesThrough() throws Exception {
        when(deepGraphRagService.chapterGraph(anyLong(), anyString(), anyString(), any()))
                .thenReturn("{\"success\":true,\"nodes\":12,\"edges\":18}");

        mockMvc.perform(post("/rag/deepgraph/chapter-graph?courseId=1&documentName=教材&chapterName=第1章")
                        .contentType(MediaType.APPLICATION_JSON).content("章节正文")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes").value(12));

        verify(deepGraphRagService).chapterGraph(eq(1L), eq("教材"), eq("第1章"), eq("章节正文"));
    }

    @Test
    @DisplayName("POST /rag/deepgraph/learning-path - 课程成员可用且参数透传")
    void learningPath_courseMember_passesThrough() throws Exception {
        when(deepGraphRagService.learningPath(anyString(), anyLong(), anyInt(), any(Boolean.class)))
                .thenReturn("{\"target\":\"TCP协议\",\"path_text\":\"## 路径\"}");

        mockMvc.perform(post("/rag/deepgraph/learning-path?target=TCP协议&courseId=1&maxDepth=2")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target").value("TCP协议"));

        verify(deepGraphRagService).learningPath(eq("TCP协议"), eq(1L), eq(2), eq(true));
    }

    @Test
    @DisplayName("POST /rag/deepgraph/learning-path - 不带 courseId 时透传 null 课程")
    void learningPath_withoutCourse_allowsNull() throws Exception {
        when(deepGraphRagService.learningPath(anyString(), isNull(), anyInt(), any(Boolean.class)))
                .thenReturn("{\"target\":\"TCP协议\",\"path_text\":\"## 路径\"}");

        mockMvc.perform(post("/rag/deepgraph/learning-path?target=TCP协议")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk());

        verify(deepGraphRagService).learningPath(eq("TCP协议"), isNull(), eq(3), eq(true));
    }
}
