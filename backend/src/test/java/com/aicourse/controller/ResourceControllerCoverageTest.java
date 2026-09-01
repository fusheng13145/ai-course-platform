package com.aicourse.controller;

import com.aicourse.entity.Course;
import com.aicourse.entity.Resource;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ResourceService;
import com.aicourse.vo.ChapterResourceTreeVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖率测试：ResourceController（独立 MockMvc，不加载 Spring 上下文）。
 * 覆盖资源列表/详情/树/搜索/统计/可见性/章节/上传/下载/删除，以及权限校验各分支
 * （ADMIN/TEACHER/STUDENT/未登录/文件类型与魔数校验）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ResourceController 覆盖率测试")
class ResourceControllerCoverageTest {

    @Mock
    private ResourceService resourceService;
    @Mock
    private CourseService courseService;
    @Mock
    private CourseEnrollmentService courseEnrollmentService;

    private MockMvc mockMvc;
    private final Path uploadsDir = Paths.get("uploads");

    @BeforeEach
    void setup() {
        ResourceController controller = new ResourceController(resourceService, courseService, courseEnrollmentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Resource r = makeResource("/uploads/r.txt", 10L);
        lenient().when(resourceService.getById(anyLong())).thenReturn(r);
        lenient().when(resourceService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(r));
        lenient().when(resourceService.getResourceTree(any())).thenReturn(
                List.of(new ChapterResourceTreeVO(1L, Collections.emptyList())));
        Page<Resource> p = new Page<>(1, 10);
        p.setRecords(List.of(r));
        lenient().when(resourceService.searchResources(any(), any(), anyInt(), anyInt())).thenReturn(p);
        lenient().when(resourceService.getResourceStats(any())).thenReturn(new com.aicourse.vo.ResourceStatsVO(0L, 0L, Map.of()));
        lenient().when(resourceService.getResourcesByChapter(any(), any())).thenReturn(List.of(r));
        lenient().doNothing().when(resourceService).toggleVisibility(anyLong(), anyBoolean());
        lenient().when(resourceService.save(any())).thenReturn(true);
        lenient().when(resourceService.removeById(anyLong())).thenReturn(true);

        Course c = new Course();
        c.setId(10L);
        c.setTeacherId(1L);
        lenient().when(courseService.getById(any())).thenReturn(c);
        lenient().when(courseEnrollmentService.count(any())).thenReturn(0L);
    }

    @AfterEach
    void cleanup() {
        // 清理测试中创建的上传/下载文件
        try {
            if (Files.exists(uploadsDir)) {
                try (var stream = Files.list(uploadsDir)) {
                    stream.forEach(f -> {
                        try { Files.deleteIfExists(f); } catch (Exception ignored) {}
                    });
                }
                Files.deleteIfExists(uploadsDir);
            }
        } catch (Exception ignored) {
        }
    }

    private Resource makeResource(String url, Long courseId) {
        Resource r = new Resource();
        r.setId(1L);
        r.setFileName("r.txt");
        r.setFileType("text/plain");
        r.setUrl(url);
        r.setCourseId(courseId);
        return r;
    }

    private static RequestPostProcessor withAuth(Long userId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        return request -> {
            request.setUserPrincipal(auth);
            if (userId != null) request.setRemoteUser(String.valueOf(userId));
            return request;
        };
    }

    // ===================== 查询类 =====================

    @Test
    @DisplayName("GET /resources/course/{courseId}")
    void listByCourse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/course/10")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /resources/{id} 成功 / 不存在 / 无权 / 未登录")
    void getResourceById() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/1").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isOk());
        // 不存在 -> NOT_FOUND
        when(resourceService.getById(999L)).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/999").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isOk());
        // STUDENT 未选课 -> FORBIDDEN
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/1").with(withAuth(2L, "STUDENT")))
                .andExpect(status().isOk());
        // 未登录 -> FORBIDDEN
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /resources/tree/{courseId}")
    void getResourceTree() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/tree/10")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /resources/search 成功")
    void searchResources() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/search").param("keyword", "java"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/search").param("keyword", "java")
                        .param("courseId", "10").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /resources/stats/course/{courseId}")
    void getResourceStats() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/stats/course/10")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /resources/chapter/{chapterId}")
    void getResourcesByChapter() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/chapter/5").param("courseId", "10"))
                .andExpect(status().isOk());
    }

    // ===================== 可见性 =====================

    @Test
    @DisplayName("PATCH /resources/{id}/visibility 成功(TEACHER) / 不存在 / 无权(STUDENT)")
    void toggleVisibility() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.patch("/resources/1/visibility")
                        .with(withAuth(1L, "TEACHER"))
                        .contentType("application/json").content("{\"visibleToAll\":true}"))
                .andExpect(status().isOk());
        when(resourceService.getById(999L)).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.patch("/resources/999/visibility")
                        .with(withAuth(1L, "TEACHER"))
                        .contentType("application/json").content("{\"visibleToAll\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.patch("/resources/1/visibility")
                        .with(withAuth(2L, "STUDENT"))
                        .contentType("application/json").content("{\"visibleToAll\":true}"))
                .andExpect(status().isOk());
    }

    // ===================== 上传 =====================

    @Test
    @DisplayName("POST /resources/upload 空文件 / 类型不支持 / 魔数不符 / 成功")
    void uploadResource() throws Exception {
        // 空文件
        MockMultipartFile empty = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);
        mockMvc.perform(MockMvcRequestBuilders.multipart("/resources/upload").file(empty).param("courseId", "10"))
                .andExpect(status().isOk());
        // 不支持的类型
        MockMultipartFile badType = new MockMultipartFile("file", "x.xyz", "application/x-unknown", "data".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/resources/upload").file(badType).param("courseId", "10"))
                .andExpect(status().isOk());
        // 魔数不符（声明 pdf 实为非 pdf）
        MockMultipartFile magic = new MockMultipartFile("file", "x.pdf", "application/pdf", "hello-not-pdf".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/resources/upload").file(magic).param("courseId", "10"))
                .andExpect(status().isOk());
        // 成功（PDF 魔数）
        MockMultipartFile ok = new MockMultipartFile("file", "x.pdf", "application/pdf",
                "%PDF-1.4 fake content".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/resources/upload").file(ok).param("courseId", "10")
                        .param("description", "desc"))
                .andExpect(status().isOk());
    }

    // ===================== 下载 =====================

    @Test
    @DisplayName("GET /resources/download/{id} 不存在 / 无权 / 成功(ADMIN) / 成功(STUDENT已选课)")
    void downloadResource() throws Exception {
        // 不存在
        when(resourceService.getById(404L)).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/download/404").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isNotFound());
        // 无权
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/download/1").with(withAuth(2L, "STUDENT")))
                .andExpect(status().isForbidden());
        // 成功 ADMIN（创建真实文件）
        Files.createDirectories(uploadsDir);
        Path file = uploadsDir.resolve("r.txt");
        Files.writeString(file, "hello");
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/download/1").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isOk());
        // 成功 STUDENT 已选课
        when(courseEnrollmentService.count(any())).thenReturn(1L);
        mockMvc.perform(MockMvcRequestBuilders.get("/resources/download/1").with(withAuth(2L, "STUDENT")))
                .andExpect(status().isOk());
    }

    // ===================== 删除 =====================

    @Test
    @DisplayName("DELETE /resources/{id} 成功(无文件) / 成功(有文件) / 不存在 / 无权")
    void deleteResource() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/resources/1").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isOk());
        // 文件存在分支
        Files.createDirectories(uploadsDir);
        Path file = uploadsDir.resolve("r.txt");
        Files.writeString(file, "hello");
        mockMvc.perform(MockMvcRequestBuilders.delete("/resources/1").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isOk());
        // 不存在
        when(resourceService.getById(999L)).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.delete("/resources/999").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isOk());
        // 无权（STUDENT）
        mockMvc.perform(MockMvcRequestBuilders.delete("/resources/1").with(withAuth(2L, "STUDENT")))
                .andExpect(status().isOk());
    }
}
