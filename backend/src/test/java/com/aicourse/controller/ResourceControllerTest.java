package com.aicourse.controller;
import com.aicourse.controller.ResourceController;
import com.aicourse.service.ResourceService;
import com.aicourse.entity.Course;
import com.aicourse.entity.Resource;
import com.aicourse.vo.ChapterResourceTreeVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * ResourceController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 ResourceService 方法打桩返回合理业务对象，断言 200/code=200，并校验关键字段透传、verify 路由委托。
 * 说明：downloadResource 返回 ResponseEntity（非 ApiResponse），且依赖磁盘文件，故断言资源不存在时返回 404 并校验委托。
 * 对带有 @PreAuthorize("hasAnyRole('TEACHER','ADMIN')") 的端点新增/保留 STUDENT 越权 403 测试。
 */
@WebMvcTest(ResourceController.class)
class ResourceControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.ResourceService resourceService;

    // 修复 P1-6：ResourceController 新增课程归属校验依赖，@WebMvcTest 需显式 mock
    @MockitoBean
    private com.aicourse.service.CourseService courseService;
    @MockitoBean
    private com.aicourse.service.CourseEnrollmentService courseEnrollmentService;
    // B-13㉑：上传成功后需落首版 resource_versions，@WebMvcTest 需显式 mock
    @MockitoBean
    private com.aicourse.service.ResourceVersionService resourceVersionService;

    private Resource sampleResource(Long id, String fileName) {
        Resource r = new Resource();
        r.setId(id);
        r.setFileName(fileName);
        r.setUrl("/uploads/" + id + ".txt");
        r.setCourseId(1L);
        r.setChapterId(1L);
        return r;
    }

    @Test
    @DisplayName("GET /resources/course/1 - 返回200且课程资源列表透传")
    void listByCourse_authorized_returns_200() throws Exception {
        when(resourceService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(sampleResource(1L, "课件.pdf")));
        mockMvc.perform(get("/resources/course/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(resourceService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }


    @Test
    @DisplayName("GET /resources/course/1 - 未认证返回401")
    void listByCourse_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/resources/course/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /resources/1 - 返回200且资源详情透传")
    void getResourceById_authorized_returns_200() throws Exception {
        when(resourceService.getById(1L)).thenReturn(sampleResource(1L, "课件.pdf"));
        mockMvc.perform(get("/resources/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.fileName").value("课件.pdf"));
        verify(resourceService).getById(eq(1L));
    }


    @Test
    @DisplayName("GET /resources/1 - 未认证返回401")
    void getResourceById_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/resources/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /resources/tree/1 - 返回200且资源树透传")
    void getResourceTree_authorized_returns_200() throws Exception {
        when(resourceService.getResourceTree(1L)).thenReturn(
                List.of(new ChapterResourceTreeVO(1L, Collections.emptyList())));
        mockMvc.perform(get("/resources/tree/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].chapterId").value(1));
        verify(resourceService).getResourceTree(eq(1L));
    }


    @Test
    @DisplayName("GET /resources/tree/1 - 未认证返回401")
    void getResourceTree_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/resources/tree/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /resources/search - 返回200且搜索分页结果透传")
    void searchResources_authorized_returns_200() throws Exception {
        Page<Resource> p = new Page<>(1, 1);
        p.setRecords(List.of(sampleResource(2L, "习题.pdf")));
        p.setTotal(1);
        when(resourceService.searchResources("test", 1L, 1, 1)).thenReturn(p);
        mockMvc.perform(get("/resources/search?keyword=test&courseId=1&page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(2));
        verify(resourceService).searchResources(eq("test"), eq(1L), eq(1), eq(1));
    }


    @Test
    @DisplayName("GET /resources/search?keyword=test&courseId=1&page=1&size=1 - 未认证返回401")
    void searchResources_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/resources/search?keyword=test&courseId=1&page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /resources/stats/course/1 - 返回200且资源统计透传")
    void getResourceStats_authorized_returns_200() throws Exception {
        when(resourceService.getResourceStats(1L)).thenReturn(new com.aicourse.vo.ResourceStatsVO(5L, 1024L, Map.of()));
        mockMvc.perform(get("/resources/stats/course/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.totalCount").value(5));
        verify(resourceService).getResourceStats(eq(1L));
    }


    @Test
    @DisplayName("GET /resources/stats/course/1 - 未认证返回401")
    void getResourceStats_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/resources/stats/course/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PATCH /resources/1/visibility - 返回200且可见性切换委托被调用")
    void toggleVisibility_authorized_returns_200() throws Exception {
        // 修复越权改造回归：toggleVisibility 先查资源存在性，需 stub getById
        when(resourceService.getById(1L)).thenReturn(sampleResource(1L, "课件.pdf"));
        mockMvc.perform(patch("/resources/1/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibleToAll\":true}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(resourceService).toggleVisibility(eq(1L), eq(Boolean.TRUE));
    }


    @Test
    @DisplayName("PATCH /resources/1/visibility - STUDENT 越权返回403")
    void toggleVisibility_student_forbidden_403() throws Exception {
        mockMvc.perform(patch("/resources/1/visibility").contentType(MediaType.APPLICATION_JSON).content("{\"visibleToAll\":true}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("PATCH /resources/1/visibility - 未认证返回401")
    void toggleVisibility_unauthorized_returns_401() throws Exception {
        mockMvc.perform(patch("/resources/1/visibility").contentType(MediaType.APPLICATION_JSON).content("{\"visibleToAll\":true}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /resources/chapter/1?courseId=1 - 返回200且章节资源列表透传")
    void getResourcesByChapter_authorized_returns_200() throws Exception {
        when(resourceService.getResourcesByChapter(1L, 1L)).thenReturn(List.of(sampleResource(3L, "视频.mp4")));
        mockMvc.perform(get("/resources/chapter/1?courseId=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(3));
        verify(resourceService).getResourcesByChapter(eq(1L), eq(1L));
    }


    @Test
    @DisplayName("GET /resources/chapter/1?courseId=1 - 未认证返回401")
    void getResourcesByChapter_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/resources/chapter/1?courseId=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /resources/upload?file=test&courseId=1&description=test - 未认证返回401")
    void uploadResource_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/resources/upload?file=test&courseId=1&description=test"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /resources/upload?file=test&courseId=1&description=test - STUDENT角色返回403")
    void uploadResource_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/resources/upload?file=test&courseId=1&description=test").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /resources/upload - 教师上传成功后落 resource_versions 首版（含大小与上传人）")
    void uploadResource_successWritesInitialVersion() throws Exception {
        Course course = new Course();
        course.setId(1L);
        course.setTeacherId(1L);
        when(courseService.getById(1L)).thenReturn(course);
        doAnswer(inv -> {
            inv.getArgument(0, Resource.class).setId(9L);
            return true;
        }).when(resourceService).save(any(Resource.class));

        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf",
                "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));

        MvcResult result = mockMvc.perform(multipart("/resources/upload").file(file)
                        .param("courseId", "1").param("description", "讲义")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        // 首版必须带资源ID、文件名、类型、地址、上传人与真实字节数，否则版本历史无法溯源大小
        verify(resourceVersionService).createNewVersion(eq(9L), eq("a.pdf"), eq("application/pdf"),
                anyString(), eq("初始上传"), eq(1L), eq(8L));

        JsonNode node = new ObjectMapper().readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String url = node.path("data").path("url").asText();
        if (url.startsWith("/uploads/")) {
            Files.deleteIfExists(Paths.get("uploads", url.substring("/uploads/".length())));
        }
    }


    @Test
    @DisplayName("GET /resources/download/1 - 资源不存在返回404且委托getById")
    void downloadResource_authorized_resource_not_found() throws Exception {
        when(resourceService.getById(1L)).thenReturn(null);
        mockMvc.perform(get("/resources/download/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isNotFound());
        verify(resourceService).getById(eq(1L));
    }


    @Test
    @DisplayName("GET /resources/download/1 - 未认证返回401")
    void downloadResource_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/resources/download/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("DELETE /resources/1 - 返回200且删除委托被调用")
    void deleteResource_authorized_returns_200() throws Exception {
        when(resourceService.getById(1L)).thenReturn(sampleResource(1L, "课件.pdf"));
        when(resourceService.removeById(1L)).thenReturn(true);
        mockMvc.perform(delete("/resources/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(resourceService).getById(eq(1L));
        verify(resourceService).removeById(eq(1L));
    }


    @Test
    @DisplayName("DELETE /resources/1 - STUDENT 越权返回403")
    void deleteResource_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/resources/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("DELETE /resources/1 - 未认证返回401")
    void deleteResource_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/resources/1"))
                .andExpect(status().isUnauthorized());
    }

}
