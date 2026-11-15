package com.aicourse.controller;
import com.aicourse.controller.CourseChapterController;
import com.aicourse.entity.Course;
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
    /** 2026-11-13 对象级收口：章节读端点的课程归属/学生成员判定走 CourseAccessControl。 */
    @MockitoBean
    private com.aicourse.security.CourseAccessControl courseAccessControl;
    @MockitoBean
    private com.aicourse.service.CourseEnrollmentService enrollmentService;

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
    @DisplayName("POST /chapters - 教师创建章节返回200；管理员 403（WP9 只读矩阵）")
    void create_authorized_passes_security() throws Exception {
        CourseChapter saved = new CourseChapter();
        saved.setId(1L);
        when(chapterService.createChapter(any(), eq(1L), anyBoolean())).thenReturn(saved);
        mockMvc.perform(post("/chapters").contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":1,\"title\":\"测试章节\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(chapterService).createChapter(any(), eq(1L), anyBoolean());
        mockMvc.perform(post("/chapters").contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":1,\"title\":\"测试章节\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isForbidden());
        verify(chapterService, times(1)).createChapter(any(), eq(1L), anyBoolean());
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
    @DisplayName("PUT /chapters/1 - 教师更新章节返回200；管理员 403（WP9 只读矩阵）")
    void update_authorized_passes_security() throws Exception {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(1L);
        chapter.setCourseId(1L);
        when(chapterService.updateChapter(eq(1L), any(), eq(1L), anyBoolean())).thenReturn(chapter);
        mockMvc.perform(put("/chapters/1").contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":1,\"title\":\"测试章节\"}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(chapterService).updateChapter(eq(1L), any(), eq(1L), anyBoolean());
        mockMvc.perform(put("/chapters/1").contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":1,\"title\":\"测试章节\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isForbidden());
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
    @DisplayName("DELETE /chapters/1 - 教师删除章节返回200；管理员 403（WP9 只读矩阵）")
    void delete_authorized_passes_security() throws Exception {
        mockMvc.perform(delete("/chapters/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(chapterService).deleteChapter(eq(1L), eq(1L), anyBoolean());
        mockMvc.perform(delete("/chapters/1").with(withUser(1L, "ADMIN")))
                .andExpect(status().isForbidden());
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
    @DisplayName("PUT /batch-reorder - 课程负责人教师排序返回200；管理员 403（WP9 只读矩阵）；他课教师 403")
    void batchReorder_authorized_passes_security() throws Exception {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(1L);
        chapter.setCourseId(1L);
        when(chapterService.listByIds(any())).thenReturn(List.of(chapter));
        Course owned = new Course();
        owned.setId(1L);
        owned.setTeacherId(1L);
        when(courseService.getById(1L)).thenReturn(owned);
        mockMvc.perform(put("/batch-reorder").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderList\":[{\"id\":1,\"orderIndex\":0}]}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(chapterService).updateById(any());
        mockMvc.perform(put("/batch-reorder").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderList\":[{\"id\":1,\"orderIndex\":0}]}").with(withUser(1L, "ADMIN")))
                .andExpect(status().isForbidden());
        // 他课教师：course 1 的负责人是 1L，换用户 7L 即 fail-closed 403
        Course notOwned = new Course();
        notOwned.setId(2L);
        notOwned.setTeacherId(7L);
        when(courseService.getById(2L)).thenReturn(notOwned);
        chapter.setCourseId(2L);
        mockMvc.perform(put("/batch-reorder").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderList\":[{\"id\":1,\"orderIndex\":0}]}").with(withUser(9L, "TEACHER")))
                .andExpect(status().isForbidden());
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

    // ==================== 2026-11-13 对象级收口：章节读端点 ACL ====================

    /** 构造可变章节树节点（学生视图过滤会在原地改写 children，须用可变集合）。 */
    private Map<String, Object> chapterNode(long id, String title, boolean published) {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("id", id);
        node.put("title", title);
        node.put("isPublished", published);
        node.put("submittedCount", 12L);
        node.put("completionRate", 85.5);
        Map<String, Object> taskPoint = new java.util.LinkedHashMap<>();
        taskPoint.put("id", 900L);
        taskPoint.put("title", "作业A");
        taskPoint.put("submittedCount", 12L);
        taskPoint.put("completionRate", 85.5);
        node.put("taskPoints", new java.util.ArrayList<>(List.of(taskPoint)));
        node.put("children", new java.util.ArrayList<Map<String, Object>>());
        return node;
    }

    private void stubActiveCourse(long courseId, long teacherId) {
        Course course = new Course();
        course.setId(courseId);
        course.setTeacherId(teacherId);
        course.setStatus("ACTIVE");
        course.setIsActive(true);
        when(courseService.getById(courseId)).thenReturn(course);
    }

    @Test
    @DisplayName("GET /chapters/course/1 - 他课教师（无课程管理权）返回403且不查树")
    void getByCourse_crossCourseTeacher_forbidden_403() throws Exception {
        stubActiveCourse(1L, 1L);
        doThrow(new com.aicourse.exception.BusinessException(com.aicourse.common.ApiCode.FORBIDDEN, "无权操作其他教师的课程"))
                .when(courseAccessControl).assertCanManageCourse(eq(1L), any());

        mockMvc.perform(get("/chapters/course/1").with(withUser(9L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(teachingService, never()).getFullChapterTree(anyLong());
    }

    @Test
    @DisplayName("GET /chapters/course/1 - 已选课学生可见：未发布章节隐藏、教师统计清零但键位保留")
    void getByCourse_studentView_filtersUnpublishedAndStripsStats() throws Exception {
        stubActiveCourse(1L, 1L);
        when(courseAccessControl.isStudent(any())).thenReturn(true);
        when(enrollmentService.hasApprovedEnrollment(5L, 1L)).thenReturn(true);
        Map<String, Object> published = chapterNode(11L, "第一章", true);
        Map<String, Object> unpublished = chapterNode(12L, "未发布章", false);
        when(teachingService.getFullChapterTree(eq(1L))).thenReturn(
                new java.util.ArrayList<>(List.of(published, unpublished)));

        mockMvc.perform(get("/chapters/course/1").with(withUser(5L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(11))
                // 教师侧提交率统计剥离数值但保留键位（前端结构兼容）
                .andExpect(jsonPath("$.data[0].submittedCount").value(0))
                .andExpect(jsonPath("$.data[0].completionRate").value(0))
                .andExpect(jsonPath("$.data[0].taskPoints[0].submittedCount").value(0))
                .andExpect(jsonPath("$.data[0].taskPoints[0].completionRate").value(0));

        verify(enrollmentService).hasApprovedEnrollment(eq(5L), eq(1L));
    }

    @Test
    @DisplayName("GET /chapters/course/1 - 未选课学生访问停用课程返回403")
    void getByCourse_unenrolledStudent_invisibleCourse_forbidden_403() throws Exception {
        Course inactive = new Course();
        inactive.setId(1L);
        inactive.setTeacherId(1L);
        inactive.setStatus("INACTIVE");
        inactive.setIsActive(false);
        when(courseService.getById(1L)).thenReturn(inactive);
        when(courseAccessControl.isStudent(any())).thenReturn(true);
        when(enrollmentService.hasApprovedEnrollment(5L, 1L)).thenReturn(false);

        mockMvc.perform(get("/chapters/course/1").with(withUser(5L, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(teachingService, never()).getFullChapterTree(anyLong());
    }

    @Test
    @DisplayName("GET /chapters/1 - 本课程教师读已发布章节详情返回200")
    void getById_courseTeacher_allowed() throws Exception {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(1L);
        chapter.setCourseId(1L);
        chapter.setIsPublished(true);
        when(chapterService.getById(eq(1L))).thenReturn(chapter);
        stubActiveCourse(1L, 1L);

        mockMvc.perform(get("/chapters/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(courseAccessControl).assertCanManageCourse(eq(1L), any());
    }

    @Test
    @DisplayName("GET /chapters/1 - 学生读未发布章节详情返回403")
    void getById_student_unpublishedChapter_forbidden_403() throws Exception {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(1L);
        chapter.setCourseId(1L);
        chapter.setIsPublished(false);
        when(chapterService.getById(eq(1L))).thenReturn(chapter);
        stubActiveCourse(1L, 1L);
        when(courseAccessControl.isStudent(any())).thenReturn(true);
        when(enrollmentService.hasApprovedEnrollment(5L, 1L)).thenReturn(true);

        mockMvc.perform(get("/chapters/1").with(withUser(5L, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
