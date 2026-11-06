package com.aicourse.controller;
import com.aicourse.controller.CourseController;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.User;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import com.aicourse.security.AdminScopeGuard;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CourseController 接口测试（升级版：真实业务断言）。
 * 对每个 endpoint 打桩 service 并断言 PagedResponse / 实体字段透传，verify 确认路由正确委托。
 */
@WebMvcTest(CourseController.class)
@Import(AdminScopeGuard.class)
class CourseControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.CourseService courseService;
    @MockitoBean
    private com.aicourse.service.CourseEnrollmentService enrollmentService;
    @MockitoBean
    private UserService userService;

    @BeforeEach
    void registerEntityMetadata() {
        // LambdaQueryWrapper 的 select 会立即解析列名，@WebMvcTest 切片需先注册实体元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Course.class);
    }

    private Page<Course> coursePageWith(Long id) {
        Course c = new Course();
        c.setId(id);
        Page<Course> p = new Page<>();
        p.setRecords(List.of(c));
        p.setTotal(1L);
        return p;
    }

    private Page<CourseEnrollment> enrollmentPageWith(Long id) {
        CourseEnrollment e = new CourseEnrollment();
        e.setId(id);
        Page<CourseEnrollment> p = new Page<>();
        p.setRecords(List.of(e));
        p.setTotal(1L);
        return p;
    }

    @Test
    @DisplayName("GET /courses - 分页查询课程返回200，列表与总数透传")
    void list_authorized_passes_security() throws Exception {
        when(courseService.page(any(), any())).thenReturn(coursePageWith(1L));
        mockMvc.perform(get("/courses?page=1&size=1&keyword=test&status=test").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
        verify(courseService).page(any(), any());
    }

    @Test
    @DisplayName("GET /courses?page=1&size=1&keyword=test&status=test - 未认证返回401")
    void list_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/courses?page=1&size=1&keyword=test&status=test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /courses/1 - 查询课程详情返回200，ID透传")
    void getById_authorized_passes_security() throws Exception {
        Course course = new Course();
        course.setId(1L);
        when(courseService.getById(eq(1L))).thenReturn(course);
        mockMvc.perform(get("/courses/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /courses/1 - 未认证返回401")
    void getById_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/courses/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /courses - 教师创建课程返回200，teacherId透传")
    void create_authorized_passes_security() throws Exception {
        Course created = new Course();
        created.setId(1L);
        created.setTeacherId(1L);
        when(courseService.createCourse(any(), eq(1L))).thenReturn(created);
        mockMvc.perform(post("/courses").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.teacherId").value(1));
        verify(courseService).createCourse(any(), eq(1L));
    }

    @Test
    @DisplayName("POST /courses - 未认证返回401")
    void create_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /courses - STUDENT角色返回403")
    void create_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/courses").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /courses/1 - 教师更新课程返回200，ID透传")
    void update_authorized_passes_security() throws Exception {
        Course course = new Course();
        course.setId(1L);
        course.setTeacherId(1L);
        when(courseService.updateCourse(eq(1L), any(), eq(1L), anyBoolean())).thenReturn(course);
        mockMvc.perform(put("/courses/1").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(courseService).updateCourse(eq(1L), any(), eq(1L), anyBoolean());
    }

    @Test
    @DisplayName("PUT /courses/1 - 未认证返回401")
    void update_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/courses/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /courses/1 - STUDENT角色返回403")
    void update_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/courses/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /courses/1 - 教师删除课程返回200")
    void delete_authorized_passes_security() throws Exception {
        mockMvc.perform(delete("/courses/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(courseService).deleteCourseById(eq(1L));
    }

    @Test
    @DisplayName("DELETE /courses/1 - 未认证返回401")
    void delete_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/courses/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /courses/1 - STUDENT角色返回403")
    void delete_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/courses/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /courses/1 - 教师删除其他教师课程返回403")
    void delete_otherTeachersCourse_forbidden_403() throws Exception {
        Course course = new Course();
        course.setId(1L);
        course.setTeacherId(20L);
        when(courseService.getById(eq(1L))).thenReturn(course);
        mockMvc.perform(delete("/courses/1").with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(courseService, never()).deleteCourseById(anyLong());
    }

    @Test
    @DisplayName("POST /courses/1/approve - 管理员审批返回200，ID透传")
    void approve_authorized_passes_security() throws Exception {
        Course course = new Course();
        course.setId(1L);
        when(courseService.approveCourse(eq(1L))).thenReturn(course);
        mockMvc.perform(post("/courses/1/approve").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(courseService).approveCourse(eq(1L));
    }

    @Test
    @DisplayName("POST /courses/1/approve - 未认证返回401")
    void approve_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/courses/1/approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /courses/1/approve - STUDENT角色返回403")
    void approve_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/courses/1/approve").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /courses/1/toggle - 教师切换状态返回200，ID透传")
    void toggle_authorized_passes_security() throws Exception {
        Course course = new Course();
        course.setId(1L);
        when(courseService.toggleCourseStatus(eq(1L))).thenReturn(course);
        mockMvc.perform(patch("/courses/1/toggle").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(courseService).toggleCourseStatus(eq(1L));
    }

    @Test
    @DisplayName("PATCH /courses/1/toggle - 未认证返回401")
    void toggle_unauthorized_returns_401() throws Exception {
        mockMvc.perform(patch("/courses/1/toggle"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /courses/1/toggle - STUDENT角色返回403")
    void toggle_student_forbidden_403() throws Exception {
        mockMvc.perform(patch("/courses/1/toggle").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /courses/1/toggle - 教师切换其他教师课程状态返回403")
    void toggle_otherTeachersCourse_forbidden_403() throws Exception {
        Course course = new Course();
        course.setId(1L);
        course.setTeacherId(20L);
        when(courseService.getById(eq(1L))).thenReturn(course);
        mockMvc.perform(patch("/courses/1/toggle").with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(courseService, never()).toggleCourseStatus(anyLong());
    }

    @Test
    @DisplayName("POST /courses/1/reject - 管理员驳回返回200，ID透传")
    void reject_authorized_passes_security() throws Exception {
        Course course = new Course();
        course.setId(1L);
        when(courseService.rejectCourse(eq(1L), any())).thenReturn(course);
        mockMvc.perform(post("/courses/1/reject").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(courseService).rejectCourse(eq(1L), any());
    }

    @Test
    @DisplayName("POST /courses/1/reject - 未认证返回401")
    void reject_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/courses/1/reject").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /courses/1/reject - STUDENT角色返回403")
    void reject_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/courses/1/reject").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /courses/enrolled - 查询已报名课程返回200，列表透传")
    void enrolledCourses_authorized_passes_security() throws Exception {
        when(enrollmentService.page(any(), any())).thenReturn(enrollmentPageWith(1L));
        mockMvc.perform(get("/courses/enrolled?page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(1));
        verify(enrollmentService).page(any(), any());
    }

    @Test
    @DisplayName("GET /courses/enrolled?page=1&size=1 - 未认证返回401")
    void enrolledCourses_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/courses/enrolled?page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /courses/my - 教师查询我的课程返回200，列表透传")
    void myCourses_authorized_passes_security() throws Exception {
        when(courseService.page(any(), any())).thenReturn(coursePageWith(1L));
        mockMvc.perform(get("/courses/my?page=1&size=1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(1));
        verify(courseService).page(any(), any());
    }

    @Test
    @DisplayName("GET /courses/my?page=1&size=1 - 未认证返回401")
    void myCourses_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/courses/my?page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /courses/my - STUDENT角色返回403")
    void myCourses_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/courses/my?page=1&size=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /courses/1/students - 查询课程学生返回200，列表透传")
    void students_authorized_passes_security() throws Exception {
        when(enrollmentService.page(any(), any())).thenReturn(enrollmentPageWith(1L));
        mockMvc.perform(get("/courses/1/students?page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list[0].id").value(1));
        verify(enrollmentService).page(any(), any());
    }

    @Test
    @DisplayName("GET /courses/1/students?page=1&size=1 - 未认证返回401")
    void students_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/courses/1/students?page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /courses/1/students - 教师查看其他教师课程学生列表返回403")
    void students_otherTeachersCourse_forbidden_403() throws Exception {
        Course course = new Course();
        course.setId(1L);
        course.setTeacherId(20L);
        when(courseService.getById(eq(1L))).thenReturn(course);
        mockMvc.perform(get("/courses/1/students?page=1&size=1").with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(enrollmentService, never()).page(any(), any());
    }

    @Test
    @DisplayName("GET /courses/1/stats - 查询课程统计返回200，字段透传")
    void getStats_authorized_passes_security() throws Exception {
        Course course = new Course();
        course.setId(1L);
        course.setName("数学");
        course.setStatus("ACTIVE");
        course.setApprovalStatus("APPROVED");
        when(courseService.getById(eq(1L))).thenReturn(course);
        mockMvc.perform(get("/courses/1/stats").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.courseName").value("数学"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /courses/1/stats - 未认证返回401")
    void getStats_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/courses/1/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /courses/1/stats - 教师查看其他教师课程统计返回403")
    void getStats_otherTeachersCourse_forbidden_403() throws Exception {
        Course course = new Course();
        course.setId(1L);
        course.setTeacherId(20L);
        when(courseService.getById(eq(1L))).thenReturn(course);
        mockMvc.perform(get("/courses/1/stats").with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    // ── WP2：院系管理员课程边界（学院归属按授课教师推导） ──

    private com.aicourse.entity.User user(Long id, Long collegeId, String role) {
        com.aicourse.entity.User u = new com.aicourse.entity.User();
        u.setId(id);
        u.setCollegeId(collegeId);
        u.setRole(role);
        return u;
    }

    private Course course(Long id, Long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        return c;
    }

    @Test
    @DisplayName("GET /courses/1 - 院系管理员访问本院教师课程 200")
    void getById_collegeAdmin_sameCollege_200() throws Exception {
        when(userService.getById(3L)).thenReturn(user(3L, 10L, "ADMIN"));
        when(courseService.getById(1L)).thenReturn(course(1L, 20L));
        when(userService.getById(20L)).thenReturn(user(20L, 10L, "TEACHER"));

        mockMvc.perform(get("/courses/1").with(withCollegeAdmin(3L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /courses/1 - 院系管理员访问他院教师课程 403")
    void getById_collegeAdmin_otherCollege_403() throws Exception {
        when(userService.getById(3L)).thenReturn(user(3L, 10L, "ADMIN"));
        when(courseService.getById(1L)).thenReturn(course(1L, 20L));
        when(userService.getById(20L)).thenReturn(user(20L, 11L, "TEACHER"));

        mockMvc.perform(get("/courses/1").with(withCollegeAdmin(3L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("POST /courses/1/approve - 院系管理员审批他院课程 403")
    void approve_collegeAdmin_otherCollege_403() throws Exception {
        when(userService.getById(3L)).thenReturn(user(3L, 10L, "ADMIN"));
        when(courseService.getById(1L)).thenReturn(course(1L, 20L));
        when(userService.getById(20L)).thenReturn(user(20L, 11L, "TEACHER"));

        mockMvc.perform(post("/courses/1/approve").with(withCollegeAdmin(3L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(courseService, never()).approveCourse(anyLong());
    }

    @Test
    @DisplayName("GET /courses - 院系管理员列表按本院教师过滤")
    void list_collegeAdmin_filtersByCollegeTeachers() throws Exception {
        when(userService.getById(3L)).thenReturn(user(3L, 10L, "ADMIN"));
        when(userService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(user(20L, 10L, "TEACHER")));
        when(courseService.page(any(), any())).thenReturn(coursePageWith(2L));

        mockMvc.perform(get("/courses?page=1&size=10").with(withCollegeAdmin(3L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].id").value(2));
        verify(courseService).page(any(), any());
    }
}
