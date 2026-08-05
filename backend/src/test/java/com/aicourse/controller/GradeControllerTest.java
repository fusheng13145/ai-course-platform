package com.aicourse.controller;
import com.aicourse.controller.GradeController;
import com.aicourse.entity.Course;
import com.aicourse.entity.GradeItem;
import com.aicourse.entity.GradePolicy;
import com.aicourse.service.CourseService;
import com.aicourse.service.GradeService;
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
 * GradeController 接口测试（升级版：真实业务断言）。
 * 所有 endpoint 均限定教师/管理员，已为每一个新增越权 403 测试。
 * 对每个 endpoint 打桩 service 并断言实体 / Map / 列表字段透传，verify 确认路由委托。
 */
@WebMvcTest(GradeController.class)
class GradeControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.GradeService gradeService;

    @MockitoBean
    private CourseService courseService;

    private Course course(Long id, Long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        return c;
    }

    @Test
    @DisplayName("POST /grade/policy - 教师保存成绩方案返回200，ID透传")
    void savePolicy_authorized_passes_security() throws Exception {
        GradePolicy policy = new GradePolicy();
        policy.setId(1L);
        when(gradeService.savePolicy(any(), eq(1L))).thenReturn(policy);
        mockMvc.perform(post("/grade/policy").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(gradeService).savePolicy(any(), eq(1L));
    }

    @Test
    @DisplayName("POST /grade/policy - 授课教师保存本课程方案返回200")
    void savePolicy_ownCourse_teacher_ok() throws Exception {
        GradePolicy policy = new GradePolicy();
        policy.setId(1L);
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        when(gradeService.savePolicy(any(), eq(20L))).thenReturn(policy);
        mockMvc.perform(post("/grade/policy").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":7,\"name\":\"平时成绩\"}")
                        .with(withUser(20L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("POST /grade/policy - 教师为其他教师课程保存方案返回403")
    void savePolicy_otherTeachersCourse_forbidden_403() throws Exception {
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        mockMvc.perform(post("/grade/policy").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":7,\"name\":\"平时成绩\"}")
                        .with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(gradeService, never()).savePolicy(any(), any());
    }

    @Test
    @DisplayName("POST /grade/policy - 未认证返回401")
    void savePolicy_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/grade/policy").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /grade/policy - STUDENT角色返回403")
    void savePolicy_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/grade/policy").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /grade/policy/course/1 - 教师查询成绩方案返回200，ID透传")
    void getPolicy_authorized_passes_security() throws Exception {
        GradePolicy policy = new GradePolicy();
        policy.setId(1L);
        when(gradeService.getPolicy(eq(1L))).thenReturn(policy);
        mockMvc.perform(get("/grade/policy/course/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(gradeService).getPolicy(eq(1L));
    }

    @Test
    @DisplayName("GET /grade/policy/course/7 - 授课教师查询本课程方案返回200")
    void getPolicy_ownCourse_teacher_ok() throws Exception {
        GradePolicy policy = new GradePolicy();
        policy.setId(1L);
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        when(gradeService.getPolicy(eq(7L))).thenReturn(policy);
        mockMvc.perform(get("/grade/policy/course/7").with(withUser(20L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /grade/policy/course/7 - 教师查询其他教师课程方案返回403")
    void getPolicy_otherTeachersCourse_forbidden_403() throws Exception {
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        mockMvc.perform(get("/grade/policy/course/7").with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(gradeService, never()).getPolicy(anyLong());
    }

    @Test
    @DisplayName("GET /grade/policy/course/1 - 未认证返回401")
    void getPolicy_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/grade/policy/course/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /grade/policy/course/1 - STUDENT角色返回403")
    void getPolicy_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/grade/policy/course/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /grade/item - 教师保存成绩项返回200，ID透传")
    void upsertItem_authorized_passes_security() throws Exception {
        GradeItem item = new GradeItem();
        item.setId(1L);
        GradePolicy policy = new GradePolicy();
        policy.setId(1L);
        policy.setCourseId(1L);
        when(gradeService.getPolicyById(eq(1L))).thenReturn(policy);
        when(gradeService.upsertItem(any(), any(), any(), any(), any(), any(), eq(1L), any()))
                .thenReturn(item);
        mockMvc.perform(post("/grade/item").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":1,\"studentId\":1,\"componentKey\":\"quiz\",\"score\":90,\"rawScore\":90,\"sourceId\":1,\"remark\":\"x\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(gradeService).upsertItem(any(), any(), any(), any(), any(), any(), eq(1L), any());
    }

    @Test
    @DisplayName("POST /grade/item - 教师为其他教师课程方案录入成绩返回403")
    void upsertItem_otherTeachersPolicy_forbidden_403() throws Exception {
        GradePolicy policy = new GradePolicy();
        policy.setId(5L);
        policy.setCourseId(7L);
        when(gradeService.getPolicyById(eq(5L))).thenReturn(policy);
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        mockMvc.perform(post("/grade/item").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":5,\"studentId\":1,\"componentKey\":\"quiz\",\"score\":90,\"rawScore\":90,\"sourceId\":1,\"remark\":\"x\"}")
                        .with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(gradeService, never()).upsertItem(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /grade/item - 未认证返回401")
    void upsertItem_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/grade/item").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /grade/item - STUDENT角色返回403")
    void upsertItem_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/grade/item").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /grade/final/1/student/1 - 教师计算最终成绩返回200，字段透传")
    void computeFinal_authorized_passes_security() throws Exception {
        GradePolicy policy = new GradePolicy();
        policy.setId(1L);
        policy.setCourseId(1L);
        when(gradeService.getPolicyById(eq(1L))).thenReturn(policy);
        when(gradeService.computeFinal(eq(1L), eq(1L))).thenReturn(Map.of("finalScore", 88));
        mockMvc.perform(get("/grade/final/1/student/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.finalScore").value(88));
        verify(gradeService).computeFinal(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /grade/final/5/student/1 - 教师计算其他教师课程学生成绩返回403")
    void computeFinal_otherTeachersPolicy_forbidden_403() throws Exception {
        GradePolicy policy = new GradePolicy();
        policy.setId(5L);
        policy.setCourseId(7L);
        when(gradeService.getPolicyById(eq(5L))).thenReturn(policy);
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        mockMvc.perform(get("/grade/final/5/student/1").with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(gradeService, never()).computeFinal(anyLong(), anyLong());
    }

    @Test
    @DisplayName("GET /grade/final/1/student/1 - 未认证返回401")
    void computeFinal_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/grade/final/1/student/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /grade/final/1/student/1 - STUDENT角色返回403")
    void computeFinal_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/grade/final/1/student/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /grade/report/course/1 - 教师查询成绩报告返回200，数组透传")
    void courseReport_authorized_passes_security() throws Exception {
        when(gradeService.courseReport(eq(1L))).thenReturn(List.of(Map.of("studentId", 1)));
        mockMvc.perform(get("/grade/report/course/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].studentId").value(1));
        verify(gradeService).courseReport(eq(1L));
    }

    @Test
    @DisplayName("GET /grade/report/course/7 - 教师查看其他教师课程成绩报告返回403")
    void courseReport_otherTeachersCourse_forbidden_403() throws Exception {
        when(courseService.getById(7L)).thenReturn(course(7L, 20L));
        mockMvc.perform(get("/grade/report/course/7").with(withUser(21L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(gradeService, never()).courseReport(anyLong());
    }

    @Test
    @DisplayName("GET /grade/report/course/1 - 未认证返回401")
    void courseReport_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/grade/report/course/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /grade/report/course/1 - STUDENT角色返回403")
    void courseReport_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/grade/report/course/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }
}
