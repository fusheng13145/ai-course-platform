package com.aicourse.controller;
import com.aicourse.controller.CourseEnrollmentController;
import com.aicourse.entity.Course;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.entity.CourseEnrollment;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * CourseEnrollmentController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 CourseEnrollmentService 方法打桩返回合理业务对象，断言 200/code=200，
 * 并校验关键字段透传、verify 路由委托。
 * enroll/unenroll 业务逻辑已下沉至服务层（enroll/enrollStudent/unenroll 事务化方法）。
 * 仅 approve/reject 带 @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")，新增 STUDENT 越权 403 测试。
 */
@WebMvcTest(CourseEnrollmentController.class)
class CourseEnrollmentControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.CourseEnrollmentService enrollmentService;

    @MockitoBean
    private CourseService courseService;

    private Course course(Long id, Long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        return c;
    }

    private CourseEnrollment sampleEnrollment(Long id, Long studentId, Long courseId) {
        CourseEnrollment e = new CourseEnrollment();
        e.setId(id);
        e.setStudentId(studentId);
        e.setCourseId(courseId);
        e.setApprovalStatus("APPROVED");
        return e;
    }

    @Test
    @DisplayName("POST /enrollments - 返回200且报名记录透传")
    void enroll_authorized_returns_200() throws Exception {
        when(enrollmentService.enroll(eq(1L), eq(1L), isNull())).thenReturn(sampleEnrollment(1L, 1L, 1L));
        mockMvc.perform(post("/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1}")
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.courseId").value(1))
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));
        verify(enrollmentService).enroll(eq(1L), eq(1L), isNull());
    }

    @Test
    @DisplayName("POST /enrollments - 指定教学班时透传 teachingClassId")
    void enroll_withTeachingClass_passes_id() throws Exception {
        when(enrollmentService.enroll(eq(1L), eq(1L), eq(100L))).thenReturn(sampleEnrollment(1L, 1L, 1L));
        mockMvc.perform(post("/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"teachingClassId\":100}")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(enrollmentService).enroll(1L, 1L, 100L);
    }


    @Test
    @DisplayName("POST /enrollments - 未认证返回401")
    void enroll_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/enrollments").contentType(MediaType.APPLICATION_JSON).content("{\"courseId\":1}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /enrollments/my - 返回200且我的报名分页透传")
    void myEnrollments_authorized_returns_200() throws Exception {
        Page<CourseEnrollment> p = new Page<>(1, 100);
        p.setRecords(List.of(sampleEnrollment(1L, 1L, 1L)));
        p.setTotal(1);
        when(enrollmentService.page(any(), any())).thenReturn(p);
        mockMvc.perform(get("/enrollments/my").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(1));
        verify(enrollmentService).page(any(), any());
    }


    @org.junit.jupiter.api.BeforeEach
    void initMybatisMeta() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                CourseEnrollment.class);
    }

    @Test
    @DisplayName("GET /enrollments/my - 仅查询有效且 APPROVED 的选课（WP5）")
    void myEnrollments_filtersApprovedOnly() throws Exception {
        Page<CourseEnrollment> p = new Page<>(1, 100);
        p.setRecords(List.of());
        when(enrollmentService.page(any(), any())).thenReturn(p);

        mockMvc.perform(get("/enrollments/my").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk());

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(enrollmentService).page(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("approval_status"), "我的课程必须过滤审批状态：" + sql);
        assertTrue(sql.contains("is_active"), "我的课程必须过滤有效记录：" + sql);
    }


    @Test
    @DisplayName("GET /enrollments/my - 未认证返回401")
    void myEnrollments_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/enrollments/my"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("DELETE /enrollments/1 - 返回200且退课委托被调用")
    void unenroll_authorized_returns_200() throws Exception {
        mockMvc.perform(delete("/enrollments/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(enrollmentService).unenroll(eq(1L), eq(1L));
    }


    @Test
    @DisplayName("DELETE /enrollments/1 - 未认证返回401")
    void unenroll_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/enrollments/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /enrollments/1/approve - 返回200且审核通过透传")
    void approve_authorized_returns_200() throws Exception {
        when(enrollmentService.getById(1L)).thenReturn(sampleEnrollment(1L, 1L, 1L));
        when(enrollmentService.updateById(any())).thenReturn(true);
        mockMvc.perform(post("/enrollments/1/approve").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(enrollmentService).updateById(any(CourseEnrollment.class));
    }


    @Test
    @DisplayName("POST /enrollments/1/approve - STUDENT 越权返回403")
    void approve_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/enrollments/1/approve").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /enrollments/1/approve - 教师审批其他教师课程的选课返回403")
    void approve_otherTeachersCourse_forbidden_403() throws Exception {
        when(enrollmentService.getById(1L)).thenReturn(sampleEnrollment(1L, 1L, 1L));
        when(courseService.getById(1L)).thenReturn(course(1L, 20L));
        mockMvc.perform(post("/enrollments/1/approve").with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(enrollmentService, never()).updateById(any());
    }


    @Test
    @DisplayName("POST /enrollments/1/approve - 未认证返回401")
    void approve_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/enrollments/1/approve"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /enrollments/1/reject - 返回200且审核驳回透传")
    void reject_authorized_returns_200() throws Exception {
        when(enrollmentService.getById(1L)).thenReturn(sampleEnrollment(1L, 1L, 1L));
        when(enrollmentService.updateById(any())).thenReturn(true);
        mockMvc.perform(post("/enrollments/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"不符合\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(enrollmentService).updateById(any(CourseEnrollment.class));
    }


    @Test
    @DisplayName("POST /enrollments/1/reject - STUDENT 越权返回403")
    void reject_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/enrollments/1/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /enrollments/1/reject - 教师驳回其他教师课程的选课返回403")
    void reject_otherTeachersCourse_forbidden_403() throws Exception {
        when(enrollmentService.getById(1L)).thenReturn(sampleEnrollment(1L, 1L, 1L));
        when(courseService.getById(1L)).thenReturn(course(1L, 20L));
        mockMvc.perform(post("/enrollments/1/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}").with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(enrollmentService, never()).updateById(any());
    }


    @Test
    @DisplayName("POST /enrollments/1/reject - 未认证返回401")
    void reject_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/enrollments/1/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

}
