package com.aicourse.controller;
import com.aicourse.controller.CourseChapterController;
import com.aicourse.entity.CourseChapter;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseService;
import com.aicourse.service.TeacherCourseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CourseChapterController 接口测试（升级版：真实业务断言）。
 * 对每个 endpoint 打桩 service 并断言实体 / Map 字段透传，verify 确认路由委托。
 * create/update/delete/batchReorder 均限定教师/管理员，已包含越权 403 测试。
 */
@WebMvcTest(CourseChapterController.class)
class CourseChapterControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.CourseChapterService chapterService;
    @MockitoBean
    private com.aicourse.service.CourseService courseService;
    @MockitoBean
    private com.aicourse.service.TeacherCourseService teachingService;

    @Test
    @DisplayName("GET /chapters/course/1 - 查询章节树返回200，数组透传")
    void getByCourse_authorized_passes_security() throws Exception {
        when(teachingService.getFullChapterTree(eq(1L))).thenReturn(List.of(Map.of("id", 1)));
        mockMvc.perform(get("/chapters/course/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(teachingService).getFullChapterTree(eq(1L));
    }

    @Test
    @DisplayName("GET /chapters/course/1 - 未认证返回401")
    void getByCourse_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/chapters/course/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /courses/1/chapters - 备用章节树查询返回200，数组透传")
    void getByCourseAlt_authorized_passes_security() throws Exception {
        when(teachingService.getFullChapterTree(eq(1L))).thenReturn(List.of(Map.of("id", 1)));
        mockMvc.perform(get("/courses/1/chapters").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(teachingService).getFullChapterTree(eq(1L));
    }

    @Test
    @DisplayName("GET /courses/1/chapters - 未认证返回401")
    void getByCourseAlt_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/courses/1/chapters"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /chapters/1 - 查询章节详情返回200，ID透传")
    void getById_authorized_passes_security() throws Exception {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(1L);
        when(chapterService.getById(eq(1L))).thenReturn(chapter);
        mockMvc.perform(get("/chapters/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(chapterService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /chapters/1 - 未认证返回401")
    void getById_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/chapters/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /chapters - 管理员创建章节返回200")
    void create_authorized_passes_security() throws Exception {
        CourseChapter saved = new CourseChapter();
        saved.setId(1L);
        when(chapterService.createChapter(any(), eq(1L), anyBoolean())).thenReturn(saved);
        mockMvc.perform(post("/chapters").contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":1,\"title\":\"测试章节\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(chapterService).createChapter(any(), eq(1L), anyBoolean());
    }

    @Test
    @DisplayName("POST /chapters - 未认证返回401")
    void create_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/chapters").contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /chapters - STUDENT角色返回403")
    void create_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/chapters").contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":1}")
                        .with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /chapters/1 - 管理员更新章节返回200，ID透传")
    void update_authorized_passes_security() throws Exception {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(1L);
        chapter.setCourseId(1L);
        when(chapterService.updateChapter(eq(1L), any(), eq(1L), anyBoolean())).thenReturn(chapter);
        mockMvc.perform(put("/chapters/1").contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":1,\"title\":\"测试章节\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(chapterService).updateChapter(eq(1L), any(), eq(1L), anyBoolean());
    }

    @Test
    @DisplayName("PUT /chapters/1 - 未认证返回401")
    void update_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/chapters/1").contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /chapters/1 - STUDENT角色返回403")
    void update_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/chapters/1").contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":1}")
                        .with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /chapters/1 - 管理员删除章节返回200")
    void delete_authorized_passes_security() throws Exception {
        mockMvc.perform(delete("/chapters/1").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(chapterService).deleteChapter(eq(1L), eq(1L), anyBoolean());
    }

    @Test
    @DisplayName("DELETE /chapters/1 - 未认证返回401")
    void delete_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/chapters/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /chapters/1 - STUDENT角色返回403")
    void delete_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/chapters/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /batch-reorder - 管理员批量排序返回200")
    void batchReorder_authorized_passes_security() throws Exception {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(1L);
        chapter.setCourseId(1L);
        when(chapterService.listByIds(any())).thenReturn(List.of(chapter));
        mockMvc.perform(put("/batch-reorder").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderList\":[{\"id\":1,\"orderIndex\":0}]}").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(chapterService).updateById(any());
    }

    @Test
    @DisplayName("PUT /batch-reorder - 未认证返回401")
    void batchReorder_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/batch-reorder").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderList\":[{\"id\":1,\"orderIndex\":0}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /batch-reorder - STUDENT角色返回403")
    void batchReorder_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/batch-reorder").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderList\":[{\"id\":1,\"orderIndex\":0}]}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }
}
