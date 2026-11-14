package com.aicourse.controller;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.Question;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseApprovalService;
import com.aicourse.service.CourseService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.TeacherCourseService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TeacherController 接口测试（升级版）。
 * 将「伪覆盖弱断言」改造为真实业务断言：打桩返回非空业务对象，
 * 断言 status().isOk() + jsonPath($.code)=200 + 至少一处 $.data 透传字段 + verify(...) 委托与参数透传。
 * 401 测试保持原样；所有接口（类级 @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")）均补 STUDENT→403 越权测试。
 */
@WebMvcTest(TeacherController.class)
class TeacherControllerTest extends ControllerTestBase {

    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private CourseApprovalService courseApprovalService;
    @MockitoBean
    private CourseChapterService chapterService;
    @MockitoBean
    private TaskService taskService;
    @MockitoBean
    private SubmissionService submissionService;
    @MockitoBean
    private QuestionService questionService;
    @MockitoBean
    private AnalyticsService analyticsService;
    @MockitoBean
    private CourseEnrollmentService enrollmentService;
    @MockitoBean
    private TeacherCourseService teachingService;
    @MockitoBean
    private KnowledgePointService knowledgePointService;
    /** 2026-11-13 对象级收口：/teacher/questions/import 逐题课程归属校验走 CourseAccessControl。 */
    @MockitoBean
    private com.aicourse.security.CourseAccessControl courseAccessControl;

    // ============ 归属校验桩（owner id 与 withUser(1L,...) 一致，越权校验才能放行） ============
    private Course ownerCourse() {
        Course c = new Course();
        c.setId(1L);
        c.setTeacherId(1L);
        return c;
    }

    private Task ownerTask() {
        Task t = new Task();
        t.setId(1L);
        t.setCourseId(1L);
        return t;
    }

    private Question ownerQuestion() {
        Question q = new Question();
        q.setId(1L);
        q.setCourseId(1L);
        return q;
    }

    /** 生成与导出列头一致的题库 xlsx 上传文件（表头 + 若干行题干） */
    private MockMultipartFile excelQuestionFile(String... contents) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("题库");
            String[] heads = {"题型", "题干", "选项(JSON)", "正确答案", "难度", "分值", "知识点", "解析"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) {
                header.createCell(i).setCellValue(heads[i]);
            }
            for (int r = 0; r < contents.length; r++) {
                Row row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue("SINGLE_CHOICE");
                row.createCell(1).setCellValue(contents[r]);
                row.createCell(3).setCellValue("A");
                row.createCell(4).setCellValue("MEDIUM");
                row.createCell(5).setCellValue("5");
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new MockMultipartFile("file", "questions.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
        }
    }

    private Submission ownerSubmission() {
        Submission s = new Submission();
        s.setId(1L);
        return s;
    }

    private <T> Page<T> pageOf(T record) {
        Page<T> p = new Page<>();
        p.setRecords(List.of(record));
        p.setTotal(1);
        p.setCurrent(1);
        p.setSize(1);
        return p;
    }

    // ==================== 课程管理 ====================

    @Test
    @DisplayName("POST /teacher/courses - 真实业务断言：创建课程并透传 teacherId")
    void createCourse_authorized_passes_security() throws Exception {
        when(courseService.createCourse(any(Course.class), eq(1L))).thenAnswer(inv -> {
            Course arg = inv.getArgument(0);
            arg.setId(1L);
            arg.setTeacherId(1L);
            return arg;
        });
        mockMvc.perform(post("/teacher/courses").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.teacherId").value(1));
        verify(courseService).createCourse(any(Course.class), eq(1L));
    }

    @Test
    @DisplayName("POST /teacher/courses - 未认证返回401")
    void createCourse_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/teacher/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /teacher/courses - STUDENT角色返回403")
    void createCourse_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/teacher/courses").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/courses - 真实业务断言：分页返回课程列表")
    void getCourses_authorized_passes_security() throws Exception {
        when(courseService.page(any(), any())).thenReturn(pageOf(ownerCourse()));
        mockMvc.perform(get("/teacher/courses?page=1&size=1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
        verify(courseService).page(any(), any());
    }

    @Test
    @DisplayName("GET /teacher/courses - 未认证返回401")
    void getCourses_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/courses?page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/courses - STUDENT角色返回403")
    void getCourses_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/courses?page=1&size=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/courses/1 - 真实业务断言：归属校验通过并透传课程字段")
    void getCourseDetail_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        mockMvc.perform(get("/teacher/courses/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.teacherId").value(1));
        verify(courseService, times(2)).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/courses/1 - 未认证返回401")
    void getCourseDetail_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/courses/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/courses/1 - STUDENT角色返回403")
    void getCourseDetail_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/courses/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /teacher/courses/1 - 真实业务断言：更新课程并透传字段")
    void updateCourse_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(courseService.updateCourseById(eq(1L), any(Course.class))).thenReturn(ownerCourse());
        mockMvc.perform(put("/teacher/courses/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(courseService).updateCourseById(eq(1L), any(Course.class));
        verify(courseService, atLeastOnce()).getById(eq(1L));
    }

    @Test
    @DisplayName("PUT /teacher/courses/1 - 未认证返回401")
    void updateCourse_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/teacher/courses/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /teacher/courses/1 - STUDENT角色返回403")
    void updateCourse_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/teacher/courses/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /teacher/courses/1 - 真实业务断言：归属校验通过并委托 delete")
    void deleteCourse_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        mockMvc.perform(delete("/teacher/courses/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").exists());
        verify(courseService).deleteCourseById(eq(1L));
    }

    @Test
    @DisplayName("DELETE /teacher/courses/1 - 未认证返回401")
    void deleteCourse_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/teacher/courses/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /teacher/courses/1 - STUDENT角色返回403")
    void deleteCourse_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/teacher/courses/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /teacher/courses/1/chapters - 真实业务断言：归属校验通过并透传 courseId")
    void addChapter_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(chapterService.save(any(CourseChapter.class))).thenReturn(true);
        mockMvc.perform(post("/teacher/courses/1/chapters").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"章节\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.courseId").value(1));
        verify(chapterService).save(any(CourseChapter.class));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("POST /teacher/courses/1/chapters - 未认证返回401")
    void addChapter_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/teacher/courses/1/chapters").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /teacher/courses/1/chapters - STUDENT角色返回403")
    void addChapter_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/teacher/courses/1/chapters").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /teacher/courses/1/chapters/1 - 真实业务断言：归属校验通过并透传字段")
    void updateChapter_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        CourseChapter ch = new CourseChapter();
        ch.setId(1L);
        ch.setCourseId(1L);
        when(chapterService.getById(eq(1L))).thenReturn(ch);
        when(chapterService.updateById(any(CourseChapter.class))).thenReturn(true);
        mockMvc.perform(put("/teacher/courses/1/chapters/1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"章节\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.courseId").value(1));
        verify(chapterService).updateById(any(CourseChapter.class));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("PUT /teacher/courses/1/chapters/1 - 未认证返回401")
    void updateChapter_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/teacher/courses/1/chapters/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /teacher/courses/1/chapters/1 - STUDENT角色返回403")
    void updateChapter_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/teacher/courses/1/chapters/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /teacher/courses/1/chapters/1 - 真实业务断言：归属校验通过并委托 delete")
    void deleteChapter_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        CourseChapter ch = new CourseChapter();
        ch.setId(1L);
        ch.setCourseId(1L);
        when(chapterService.getById(eq(1L))).thenReturn(ch);
        when(chapterService.removeById(any(Long.class))).thenReturn(true);
        mockMvc.perform(delete("/teacher/courses/1/chapters/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").exists());
        verify(chapterService).removeById(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("DELETE /teacher/courses/1/chapters/1 - 未认证返回401")
    void deleteChapter_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/teacher/courses/1/chapters/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /teacher/courses/1/chapters/1 - STUDENT角色返回403")
    void deleteChapter_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/teacher/courses/1/chapters/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    // ==================== 作业管理 ====================

    @Test
    @DisplayName("POST /teacher/assignments - 真实业务断言：创建作业并委托 save")
    void createAssignment_authorized_passes_security() throws Exception {
        when(taskService.save(any(Task.class))).thenReturn(true);
        mockMvc.perform(post("/teacher/assignments").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
        verify(taskService).save(any(Task.class));
    }

    @Test
    @DisplayName("POST /teacher/assignments - 未认证返回401")
    void createAssignment_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/teacher/assignments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /teacher/assignments - STUDENT角色返回403")
    void createAssignment_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/teacher/assignments").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/assignments - 真实业务断言：分页返回作业列表")
    void getAssignments_authorized_passes_security() throws Exception {
        when(taskService.page(any(), any())).thenReturn(pageOf(ownerTask()));
        mockMvc.perform(get("/teacher/assignments?page=1&size=1&courseId=1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
        verify(taskService).page(any(), any());
    }

    @Test
    @DisplayName("GET /teacher/assignments - 未认证返回401")
    void getAssignments_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/assignments?page=1&size=1&courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/assignments - STUDENT角色返回403")
    void getAssignments_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/assignments?page=1&size=1&courseId=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/assignments/1 - 真实业务断言：作业归属校验通过并透传字段")
    void getAssignmentDetail_authorized_passes_security() throws Exception {
        when(taskService.getById(eq(1L))).thenReturn(ownerTask());
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        mockMvc.perform(get("/teacher/assignments/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.courseId").value(1));
        verify(taskService, times(2)).getById(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/assignments/1 - 未认证返回401")
    void getAssignmentDetail_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/assignments/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/assignments/1 - STUDENT角色返回403")
    void getAssignmentDetail_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/assignments/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /teacher/assignments/1 - 真实业务断言：作业归属校验通过并透传字段")
    void updateAssignment_authorized_passes_security() throws Exception {
        when(taskService.getById(eq(1L))).thenReturn(ownerTask());
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(taskService.updateTaskById(eq(1L), any(Task.class))).thenReturn(ownerTask());
        mockMvc.perform(put("/teacher/assignments/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(taskService).updateTaskById(eq(1L), any(Task.class));
        verify(taskService, atLeastOnce()).getById(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("PUT /teacher/assignments/1 - 未认证返回401")
    void updateAssignment_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/teacher/assignments/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /teacher/assignments/1 - STUDENT角色返回403")
    void updateAssignment_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/teacher/assignments/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /teacher/assignments/1 - 真实业务断言：作业归属校验通过并委托 delete")
    void deleteAssignment_authorized_passes_security() throws Exception {
        when(taskService.getById(eq(1L))).thenReturn(ownerTask());
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(taskService.removeById(any(Long.class))).thenReturn(true);
        mockMvc.perform(delete("/teacher/assignments/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").exists());
        verify(taskService).removeById(eq(1L));
        verify(taskService).getById(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("DELETE /teacher/assignments/1 - 未认证返回401")
    void deleteAssignment_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/teacher/assignments/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /teacher/assignments/1 - STUDENT角色返回403")
    void deleteAssignment_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/teacher/assignments/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/assignments/1/submissions - 真实业务断言：作业归属校验通过并分页返回提交")
    void getSubmissions_authorized_passes_security() throws Exception {
        when(taskService.getById(eq(1L))).thenReturn(ownerTask());
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(submissionService.page(any(), any())).thenReturn(pageOf(ownerSubmission()));
        mockMvc.perform(get("/teacher/assignments/1/submissions?page=1&size=1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
        verify(submissionService).page(any(), any());
        verify(taskService).getById(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/assignments/1/submissions - 未认证返回401")
    void getSubmissions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/assignments/1/submissions?page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/assignments/1/submissions - STUDENT角色返回403")
    void getSubmissions_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/assignments/1/submissions?page=1&size=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /teacher/assignments/1/submissions/1 - 真实业务断言：批改提交并透传 status")
    void gradeSubmission_authorized_passes_security() throws Exception {
        when(taskService.getById(eq(1L))).thenReturn(ownerTask());
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        Submission graded = ownerSubmission();
        graded.setStatus("GRADED");
        when(submissionService.gradeSubmissionByTeacher(eq(1L), eq(1L), any(Submission.class), eq(1L))).thenReturn(graded);
        mockMvc.perform(put("/teacher/assignments/1/submissions/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":90,\"feedback\":\"good\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("GRADED"));
        verify(submissionService).gradeSubmissionByTeacher(eq(1L), eq(1L), any(Submission.class), eq(1L));
        verify(taskService).getById(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("PUT /teacher/assignments/1/submissions/1 - 未认证返回401")
    void gradeSubmission_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/teacher/assignments/1/submissions/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /teacher/assignments/1/submissions/1 - STUDENT角色返回403")
    void gradeSubmission_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/teacher/assignments/1/submissions/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /teacher/assignments/1/publish - 真实业务断言：作业归属校验通过并委托 publishTask")
    void publishAssignment_authorized_passes_security() throws Exception {
        when(taskService.getById(eq(1L))).thenReturn(ownerTask());
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(taskService.publishTask(eq(1L))).thenReturn(ownerTask());
        mockMvc.perform(put("/teacher/assignments/1/publish").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(taskService).publishTask(eq(1L));
        verify(taskService).getById(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("PUT /teacher/assignments/1/publish - 未认证返回401")
    void publishAssignment_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/teacher/assignments/1/publish"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /teacher/assignments/1/publish - STUDENT角色返回403")
    void publishAssignment_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/teacher/assignments/1/publish").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    // ==================== 题库管理 ====================

    @Test
    @DisplayName("POST /teacher/questions - 真实业务断言：创建题目并委托 createQuestion")
    void createQuestion_authorized_passes_security() throws Exception {
        // controller 返回的是入参 question（忽略 service 返回值），故用 thenAnswer 对入参 setId
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(questionService.createQuestion(any(Question.class))).thenAnswer(inv -> {
            Question arg = inv.getArgument(0);
            arg.setId(1L);
            return arg;
        });
        mockMvc.perform(post("/teacher/questions").contentType(MediaType.APPLICATION_JSON).content("{\"id\":1,\"content\":\"题干\",\"courseId\":1}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(questionService).createQuestion(any(Question.class));
    }

    @Test
    @DisplayName("POST /teacher/questions - 未认证返回401")
    void createQuestion_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/teacher/questions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /teacher/questions - STUDENT角色返回403")
    void createQuestion_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/teacher/questions").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/questions - 真实业务断言：课程归属校验通过并分页返回题目")
    void getQuestions_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(questionService.page(any(), any())).thenReturn(pageOf(ownerQuestion()));
        mockMvc.perform(get("/teacher/questions?page=1&size=1&courseId=1&type=test&difficulty=test").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
        verify(questionService).page(any(), any());
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/questions - 未认证返回401")
    void getQuestions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/questions?page=1&size=1&courseId=1&type=test&difficulty=test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/questions - STUDENT角色返回403")
    void getQuestions_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/questions?page=1&size=1&courseId=1&type=test&difficulty=test").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/questions/1 - 真实业务断言：题目归属校验通过并透传字段")
    void getQuestionDetail_authorized_passes_security() throws Exception {
        when(questionService.getById(eq(1L))).thenReturn(ownerQuestion());
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        mockMvc.perform(get("/teacher/questions/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.courseId").value(1));
        verify(questionService, times(2)).getById(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/questions/1 - 未认证返回401")
    void getQuestionDetail_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/questions/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/questions/1 - STUDENT角色返回403")
    void getQuestionDetail_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/questions/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /teacher/questions/1 - 真实业务断言：题目归属校验通过并透传字段")
    void updateQuestion_authorized_passes_security() throws Exception {
        when(questionService.getById(eq(1L))).thenReturn(ownerQuestion());
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(questionService.updateById(any(Question.class))).thenReturn(true);
        mockMvc.perform(put("/teacher/questions/1").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"题干\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(1));
        verify(questionService).updateById(any(Question.class));
        verify(questionService, atLeastOnce()).getById(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("PUT /teacher/questions/1 - 未认证返回401")
    void updateQuestion_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/teacher/questions/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /teacher/questions/1 - STUDENT角色返回403")
    void updateQuestion_student_forbidden_403() throws Exception {
        mockMvc.perform(put("/teacher/questions/1").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /teacher/questions/1 - 真实业务断言：题目归属校验通过并委托 delete")
    void deleteQuestion_authorized_passes_security() throws Exception {
        when(questionService.getById(eq(1L))).thenReturn(ownerQuestion());
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(questionService.removeById(any(Long.class))).thenReturn(true);
        mockMvc.perform(delete("/teacher/questions/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").exists());
        verify(questionService).removeById(eq(1L));
        verify(questionService).getById(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("DELETE /teacher/questions/1 - 未认证返回401")
    void deleteQuestion_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/teacher/questions/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /teacher/questions/1 - STUDENT角色返回403")
    void deleteQuestion_student_forbidden_403() throws Exception {
        mockMvc.perform(delete("/teacher/questions/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /teacher/questions/import - 真实业务断言：导入空列表返回 importedCount=0")
    void importQuestions_authorized_passes_security() throws Exception {
        when(questionService.save(any(Question.class))).thenReturn(true);
        mockMvc.perform(post("/teacher/questions/import").contentType(MediaType.APPLICATION_JSON).content("[]").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.importedCount").value(0));
    }

    @Test
    @DisplayName("POST /teacher/questions/import - 未认证返回401")
    void importQuestions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/teacher/questions/import").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /teacher/questions/import - STUDENT角色返回403")
    void importQuestions_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/teacher/questions/import").contentType(MediaType.APPLICATION_JSON).content("[]").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /teacher/questions/import - 逐题过课程归属校验（同 QuestionController.batchImport 口径）")
    void importQuestions_checksCourseOwnershipPerQuestion() throws Exception {
        when(questionService.save(any(Question.class))).thenReturn(true);
        mockMvc.perform(post("/teacher/questions/import").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"content\":\"题干一\",\"courseId\":1},{\"content\":\"题干二\",\"courseId\":1}]")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.importedCount").value(2));

        verify(courseAccessControl, times(2)).assertCanManageCourse(eq(1L), any());
        verify(questionService, times(2)).save(any(Question.class));
    }

    @Test
    @DisplayName("POST /teacher/questions/import - 向他人课程导入返回403且一条都不落库")
    void importQuestions_otherTeachersCourse_forbidden_403() throws Exception {
        doThrow(new com.aicourse.exception.BusinessException(com.aicourse.common.ApiCode.FORBIDDEN, "无权操作其他教师的课程"))
                .when(courseAccessControl).assertCanManageCourse(eq(2L), any());
        mockMvc.perform(post("/teacher/questions/import").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"content\":\"题干一\",\"courseId\":2}]")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(questionService, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("GET /teacher/questions/export - 真实业务断言：课程归属校验通过并导出 xlsx 文件流")
    void exportQuestions_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(questionService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(ownerQuestion()));
        mockMvc.perform(get("/teacher/questions/export?courseId=1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment; filename*=UTF-8''")))
                .andExpect(mvcResult -> {
                    byte[] body = mvcResult.getResponse().getContentAsByteArray();
                    assertTrue(body.length > 4, "导出内容不应为空");
                    // xlsx 本质是 ZIP 容器，魔数为 PK\x03\x04
                    assertTrue(body[0] == 'P' && body[1] == 'K' && body[2] == 3 && body[3] == 4,
                            "响应体应为 xlsx(ZIP) 字节流");
                });
        verify(questionService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("POST /teacher/questions/import-excel - 真实业务断言：解析 xlsx 逐题入库并统计")
    void importQuestionsExcel_authorized_passes_security() throws Exception {
        when(questionService.save(any(Question.class))).thenReturn(true);
        MockMultipartFile file = excelQuestionFile("单选题题干示例一", "单选题题干示例二");
        mockMvc.perform(multipart("/teacher/questions/import-excel").file(file).with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.importedCount").value(2))
                .andExpect(jsonPath("$.data.total").value(2));
        verify(questionService, times(2)).save(any(Question.class));
    }

    @Test
    @DisplayName("POST /teacher/questions/import-excel - 非 xlsx 文件返回业务错误400")
    void importQuestionsExcel_badFormat_returns_400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "bad.csv", "text/csv", "a,b,c".getBytes());
        mockMvc.perform(multipart("/teacher/questions/import-excel").file(file).with(withUser(1L, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(questionService, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("GET /teacher/questions/export - 未认证返回401")
    void exportQuestions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/questions/export?courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/questions/export - STUDENT角色返回403")
    void exportQuestions_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/questions/export?courseId=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    // ==================== 学情分析 ====================

    @Test
    @DisplayName("GET /teacher/analytics/class/1 - 真实业务断言：课程归属校验通过并委托 getCourseAnalysis")
    void getClassAnalytics_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(analyticsService.getCourseAnalysis(eq(1L))).thenReturn(Map.of("courseId", 1));
        mockMvc.perform(get("/teacher/analytics/class/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.courseId").value(1));
        verify(analyticsService).getCourseAnalysis(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/analytics/class/1 - 未认证返回401")
    void getClassAnalytics_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/analytics/class/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/analytics/class/1 - STUDENT角色返回403")
    void getClassAnalytics_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/analytics/class/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/analytics/students/1 - 真实业务断言：委托 getUserDashboard")
    void getStudentAnalytics_authorized_passes_security() throws Exception {
        when(enrollmentService.hasApprovedEnrollmentWithTeacher(eq(1L), eq(1L))).thenReturn(true);
        when(analyticsService.getUserDashboard(eq(1L))).thenReturn(Map.of("studentId", 1));
        mockMvc.perform(get("/teacher/analytics/students/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.studentId").value(1));
        verify(analyticsService).getUserDashboard(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/analytics/students/1 - 未认证返回401")
    void getStudentAnalytics_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/analytics/students/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/analytics/students/1 - STUDENT角色返回403")
    void getStudentAnalytics_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/analytics/students/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/analytics/assignments/1 - 真实业务断言：作业归属校验通过并委托 getTaskCompletionStats")
    void getAssignmentAnalytics_authorized_passes_security() throws Exception {
        when(taskService.getById(eq(1L))).thenReturn(ownerTask());
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(taskService.getTaskCompletionStats(eq(1L))).thenReturn(new com.aicourse.vo.TaskCompletionStatsVO(
                1L, "task", 0L, 0L, 0L, 0L, 0L, 0.0, 0.0, null, "PUBLISHED"));
        mockMvc.perform(get("/teacher/analytics/assignments/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                // 原桩键为 assignmentId，真实契约为 taskId；VO 化后按 taskId 断言
                .andExpect(jsonPath("$.data.taskId").value(1));
        verify(taskService).getTaskCompletionStats(eq(1L));
        verify(taskService).getById(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/analytics/assignments/1 - 未认证返回401")
    void getAssignmentAnalytics_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/analytics/assignments/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/analytics/assignments/1 - STUDENT角色返回403")
    void getAssignmentAnalytics_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/analytics/assignments/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/analytics/knowledge-points - 真实业务断言：课程归属校验通过并委托 getGradeDistribution")
    void getKnowledgePointAnalytics_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(analyticsService.getGradeDistribution(eq(1L))).thenReturn(Map.of("courseId", 1));
        mockMvc.perform(get("/teacher/analytics/knowledge-points?courseId=1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.courseId").value(1));
        verify(analyticsService).getGradeDistribution(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/analytics/knowledge-points - 未认证返回401")
    void getKnowledgePointAnalytics_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/analytics/knowledge-points?courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/analytics/knowledge-points - STUDENT角色返回403")
    void getKnowledgePointAnalytics_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/analytics/knowledge-points?courseId=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/analytics/report/export - 真实业务断言：课程归属校验通过并委托 exportReport")
    void exportReport_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(analyticsService.exportReport(eq(1L))).thenReturn(Map.of("courseId", 1));
        mockMvc.perform(get("/teacher/analytics/report/export?courseId=1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.courseId").value(1));
        verify(analyticsService).exportReport(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/analytics/report/export - 未认证返回401")
    void exportReport_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/analytics/report/export?courseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/analytics/report/export - STUDENT角色返回403")
    void exportReport_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/analytics/report/export?courseId=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    // ==================== 课程建设 / 学情驾驶舱 ====================

    @Test
    @DisplayName("GET /teacher/courses/1/dashboard - 真实业务断言：课程归属校验通过并委托 getCourseTeachingDashboard")
    void courseDashboard_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(teachingService.getCourseTeachingDashboard(eq(1L), eq(1L))).thenReturn(Map.of("courseId", 1));
        mockMvc.perform(get("/teacher/courses/1/dashboard").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.courseId").value(1));
        verify(teachingService).getCourseTeachingDashboard(eq(1L), eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/courses/1/dashboard - 未认证返回401")
    void courseDashboard_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/courses/1/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/courses/1/dashboard - STUDENT角色返回403")
    void courseDashboard_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/courses/1/dashboard").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /teacher/courses/ai/generate-outline - 真实业务断言：委托 generateCourseOutlineByAI 并透传 topic")
    void generateOutline_authorized_passes_security() throws Exception {
        when(teachingService.generateCourseOutlineByAI(anyString(), anyString(), anyString(), anyInt(), eq(1L)))
                .thenReturn(Map.of("topic", "test"));
        mockMvc.perform(post("/teacher/courses/ai/generate-outline?topic=test&category=test&level=test&chapterCount=1")
                        .with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.topic").value("test"));
        verify(teachingService).generateCourseOutlineByAI(eq("test"), eq("test"), eq("test"), eq(1), eq(1L));
    }

    @Test
    @DisplayName("POST /teacher/courses/ai/generate-outline - 未认证返回401")
    void generateOutline_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/teacher/courses/ai/generate-outline?topic=test&category=test&level=test&chapterCount=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /teacher/courses/ai/generate-outline - STUDENT角色返回403")
    void generateOutline_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/teacher/courses/ai/generate-outline?topic=test&category=test&level=test&chapterCount=1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /teacher/courses/1/clone - 真实业务断言：课程归属校验通过并委托 cloneCourse")
    void cloneCourse_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(teachingService.cloneCourse(eq(1L), eq("test"), eq(1L))).thenReturn(Map.of("sourceCourseId", 1));
        mockMvc.perform(post("/teacher/courses/1/clone?newName=test").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.sourceCourseId").value(1));
        verify(teachingService).cloneCourse(eq(1L), eq("test"), eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("POST /teacher/courses/1/clone - 未认证返回401")
    void cloneCourse_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/teacher/courses/1/clone?newName=test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /teacher/courses/1/clone - STUDENT角色返回403")
    void cloneCourse_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/teacher/courses/1/clone?newName=test").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/courses/summaries - 真实业务断言：委托 getMyCourseSummaries 并返回数组")
    void myCourseSummaries_authorized_passes_security() throws Exception {
        when(teachingService.getMyCourseSummaries(eq(1L))).thenReturn(List.of(Map.of("courseId", 1)));
        mockMvc.perform(get("/teacher/courses/summaries").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].courseId").value(1));
        verify(teachingService).getMyCourseSummaries(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/courses/summaries - 未认证返回401")
    void myCourseSummaries_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/courses/summaries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/courses/summaries - STUDENT角色返回403")
    void myCourseSummaries_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/courses/summaries").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/dashboard/overview - 真实业务断言：委托 getTeacherDashboard 并透传 teacherId")
    void teacherDashboard_authorized_passes_security() throws Exception {
        when(teachingService.getTeacherDashboard(eq(1L))).thenReturn(Map.of("teacherId", 1));
        mockMvc.perform(get("/teacher/dashboard/overview").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.teacherId").value(1));
        verify(teachingService).getTeacherDashboard(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/dashboard/ai-overview - 独立端点委托 getTeacherAIOverview")
    void teacherDashboardAiOverview_authorized() throws Exception {
        when(teachingService.getTeacherAIOverview(eq(1L))).thenReturn(Map.of("summary", "OK"));
        mockMvc.perform(get("/teacher/dashboard/ai-overview").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.summary").value("OK"));
        verify(teachingService).getTeacherAIOverview(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/dashboard/overview - 未认证返回401")
    void teacherDashboard_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/dashboard/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/dashboard/overview - STUDENT角色返回403")
    void teacherDashboard_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/dashboard/overview").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/dashboard/ai-overview - 委托独立入口并透传 teacherId（B-13 ⑯）")
    void teacherAiOverview_authorized_passes_security() throws Exception {
        when(teachingService.getTeacherAIOverview(eq(1L))).thenReturn(Map.of("summary", "AI 总览"));
        mockMvc.perform(get("/teacher/dashboard/ai-overview").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.summary").value("AI 总览"));
        verify(teachingService).getTeacherAIOverview(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/dashboard/ai-overview - 未认证返回401")
    void teacherAiOverview_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/dashboard/ai-overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/dashboard/ai-overview - STUDENT角色返回403")
    void teacherAiOverview_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/dashboard/ai-overview").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /teacher/courses/1/chapters/tree - 真实业务断言：课程归属校验通过并委托 getFullChapterTree")
    void chapterTree_authorized_passes_security() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(teachingService.getFullChapterTree(eq(1L))).thenReturn(List.of(Map.of("courseId", 1)));
        mockMvc.perform(get("/teacher/courses/1/chapters/tree").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].courseId").value(1));
        verify(teachingService).getFullChapterTree(eq(1L));
        verify(courseService).getById(eq(1L));
    }

    @Test
    @DisplayName("GET /teacher/courses/1/chapters/tree - 未认证返回401")
    void chapterTree_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/teacher/courses/1/chapters/tree"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /teacher/courses/1/chapters/tree - STUDENT角色返回403")
    void chapterTree_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/teacher/courses/1/chapters/tree").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    // ==================== D1 回归：章节归属校验（chapterId 必须属于 courseId） ====================

    @Test
    @DisplayName("PUT 章节 - 章节属于他人课程时拒绝且不落库（D1 越权 IDOR 回归）")
    void updateChapter_chapterBelongsToOtherCourse_forbidden() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        CourseChapter foreign = new CourseChapter();
        foreign.setId(2L);
        foreign.setCourseId(99L); // 章节实际挂在他人课程 99 下
        when(chapterService.getById(eq(2L))).thenReturn(foreign);
        mockMvc.perform(put("/teacher/courses/1/chapters/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权改写\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(chapterService, never()).updateById(any(CourseChapter.class));
    }

    @Test
    @DisplayName("DELETE 章节 - 章节属于他人课程时拒绝且不删除（D1 越权 IDOR 回归）")
    void deleteChapter_chapterBelongsToOtherCourse_forbidden() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        CourseChapter foreign = new CourseChapter();
        foreign.setId(2L);
        foreign.setCourseId(99L);
        when(chapterService.getById(eq(2L))).thenReturn(foreign);
        mockMvc.perform(delete("/teacher/courses/1/chapters/2").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(chapterService, never()).removeById(any(Long.class));
    }

    @Test
    @DisplayName("PUT 章节 - 章节不存在返回404且不落库（D1 边界）")
    void updateChapter_chapterNotFound_notFound() throws Exception {
        when(courseService.getById(eq(1L))).thenReturn(ownerCourse());
        when(chapterService.getById(eq(2L))).thenReturn(null);
        mockMvc.perform(put("/teacher/courses/1/chapters/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        verify(chapterService, never()).updateById(any(CourseChapter.class));
    }

    // ==================== D2 回归：学生学情归属校验 ====================

    @Test
    @DisplayName("GET 学生学情 - 教师与该生无选课归属时拒绝（D2 越权 IDOR 回归）")
    void getStudentAnalytics_noEnrollment_forbidden() throws Exception {
        when(enrollmentService.hasApprovedEnrollmentWithTeacher(eq(5L), eq(1L))).thenReturn(false);
        mockMvc.perform(get("/teacher/analytics/students/5").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(analyticsService, never()).getUserDashboard(any());
    }

    @Test
    @DisplayName("GET 学生学情 - 管理员放行（不受归属限制）")
    void getStudentAnalytics_adminBypass_passes() throws Exception {
        when(analyticsService.getUserDashboard(eq(5L))).thenReturn(Map.of("studentId", 5));
        mockMvc.perform(get("/teacher/analytics/students/5").with(withUser(9L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.studentId").value(5));
        verify(enrollmentService, never()).hasApprovedEnrollmentWithTeacher(any(), any());
        verify(analyticsService).getUserDashboard(eq(5L));
    }
}
