package com.aicourse.controller;

import com.aicourse.entity.Course;
import com.aicourse.entity.Resource;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ResourceService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ResourceController 资源列表端点 ACL 否定场景回归测试（DR-16）。
 *
 * <p>既有的 {@link ResourceControllerTest} 使用三角色合一用户（含 ADMIN），
 * {@code canAccessCourse} 恒真，无法暴露越权。本类改用单一角色用户，专测否定路径：
 * 学生未报名 / 教师非本课程 / 匿名 / 学生无 courseId 跨课程搜索 均须被拒。</p>
 */
@WebMvcTest(ResourceController.class)
class ResourceEndpointAclTest extends ControllerTestBase {

    @MockitoBean
    private ResourceService resourceService;
    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private CourseEnrollmentService courseEnrollmentService;
    // B-13㉑：ResourceController 新增的版本服务依赖，@WebMvcTest 需显式 mock
    @MockitoBean
    private com.aicourse.service.ResourceVersionService resourceVersionService;

    private static final Long COURSE_ID = 1L;
    private static final Long TEACHER_ID = 10L;
    private static final Long OTHER_TEACHER = 99L;
    private static final Long STUDENT_ID = 20L;

    private Course courseOf(Long teacherId) {
        Course c = new Course();
        c.setId(COURSE_ID);
        c.setTeacherId(teacherId);
        return c;
    }

    @Test
    @DisplayName("listByCourse：未报名学生 -> 403")
    void listByCourse_studentNotEnrolled_forbidden() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));
        when(courseEnrollmentService.hasApprovedEnrollment(STUDENT_ID, COURSE_ID)).thenReturn(false);

        mockMvc.perform(get("/resources/course/{id}", COURSE_ID).with(withUser(STUDENT_ID, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listByCourse：本课程教师 -> 200")
    void listByCourse_ownTeacher_ok() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));
        when(resourceService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(new Resource()));

        mockMvc.perform(get("/resources/course/{id}", COURSE_ID).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listByCourse：非本课程教师 -> 403")
    void listByCourse_otherTeacher_forbidden() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));

        mockMvc.perform(get("/resources/course/{id}", COURSE_ID).with(withUser(OTHER_TEACHER, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listByCourse：ADMIN -> 200（不受课程归属限制）")
    void listByCourse_admin_ok() throws Exception {
        when(resourceService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(new Resource()));

        mockMvc.perform(get("/resources/course/{id}", COURSE_ID).with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listByCourse：匿名 -> 401")
    void listByCourse_anonymous_unauthorized() throws Exception {
        mockMvc.perform(get("/resources/course/{id}", COURSE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getResourceTree：未报名学生 -> 403")
    void tree_studentNotEnrolled_forbidden() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));
        when(courseEnrollmentService.hasApprovedEnrollment(STUDENT_ID, COURSE_ID)).thenReturn(false);

        mockMvc.perform(get("/resources/tree/{id}", COURSE_ID).with(withUser(STUDENT_ID, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getResourceStats：本课程教师 -> 200")
    void stats_ownTeacher_ok() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));
        when(resourceService.getResourceStats(COURSE_ID))
                .thenReturn(new com.aicourse.vo.ResourceStatsVO(0L, 0L, Map.of()));

        mockMvc.perform(get("/resources/stats/course/{id}", COURSE_ID).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getResourcesByChapter：非本课程教师 -> 403")
    void chapter_otherTeacher_forbidden() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));

        mockMvc.perform(get("/resources/chapter/{cid}", 5L)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(OTHER_TEACHER, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("searchResources：未指定 courseId 的学生 -> 403（禁止跨课程枚举）")
    void search_noCourseId_student_forbidden() throws Exception {
        mockMvc.perform(get("/resources/search").param("keyword", "ppt")
                        .with(withUser(STUDENT_ID, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("searchResources：未指定 courseId 的 ADMIN -> 200（允许全站搜索）")
    void search_noCourseId_admin_ok() throws Exception {
        when(resourceService.searchResources(eq("ppt"), eq(null), eq(1), eq(10)))
                .thenReturn(new Page<>());

        mockMvc.perform(get("/resources/search").param("keyword", "ppt")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("searchResources：指定 courseId 且已报名学生 -> 200")
    void search_withCourseId_studentEnrolled_ok() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));
        when(courseEnrollmentService.hasApprovedEnrollment(STUDENT_ID, COURSE_ID)).thenReturn(true);
        when(resourceService.searchResources(eq("ppt"), eq(COURSE_ID), eq(1), eq(10)))
                .thenReturn(new Page<>());

        mockMvc.perform(get("/resources/search").param("keyword", "ppt")
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(STUDENT_ID, "STUDENT")))
                .andExpect(status().isOk());
    }

    // ===================== 上传对象级 ACL（#5 回归） =====================

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("uploadResource：非本课程教师 -> 403（#5 对象级 ACL 回归）")
    void upload_otherTeacher_forbidden() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));

        mockMvc.perform(multipart("/resources/upload")
                        .file(sampleFile())
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(OTHER_TEACHER, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("uploadResource：courseId 指向不存在的课程 -> 404（防孤儿资源）")
    void upload_courseNotFound_notFound() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(null);

        mockMvc.perform(multipart("/resources/upload")
                        .file(sampleFile())
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("uploadResource：本课程教师 -> ACL 放行（文件内容非法由后续校验拦截，非 403/404）")
    void upload_ownTeacher_aclPasses() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));

        mockMvc.perform(multipart("/resources/upload")
                        .file(sampleFile())
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                // 业务校验失败以 200 + code 返回（文件魔数不合法 → code=400），但不应为 403/404
                .andExpect(status().isOk());
    }
}
