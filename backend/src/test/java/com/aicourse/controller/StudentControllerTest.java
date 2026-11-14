package com.aicourse.controller;
import com.aicourse.controller.StudentController;
import com.aicourse.common.ApiCode;
import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.vo.DailyLearningStatVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * StudentController 接口测试。
 * 升级自生成器产出的弱断言（status != 401 && status != 403），改为真实业务断言：
 * 对各 service 方法打桩并用 jsonPath 校验响应体关键字段透传，verify 确认路由委托与参数透传。
 * 类级 @PreAuthorize("hasRole('STUDENT')")，故每个接口新增 TEACHER 越权 403 测试。
 */
@WebMvcTest(StudentController.class)
class StudentControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.CourseService courseService;
    @MockitoBean
    private com.aicourse.service.CourseEnrollmentService enrollmentService;
    @MockitoBean
    private com.aicourse.service.TaskService taskService;
    @MockitoBean
    private com.aicourse.service.SubmissionService submissionService;
    @MockitoBean
    private com.aicourse.service.WrongQuestionService wrongQuestionService;
    @MockitoBean
    private com.aicourse.service.LearningRecordService learningRecordService;
    @MockitoBean
    private CourseAccessControl courseAccessControl;

    // ==================== 学习中心 ====================

    @Test
    @DisplayName("GET /student/courses - STUDENT 获取我的课程返回 code=200 且字段透传")
    void getMyCourses_authorized_passes_security() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(5L);
        enrollment.setStudentId(1L);
        enrollment.setIsActive(true);
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(enrollment));

        Course course = new Course();
        course.setId(5L);
        course.setName("高等数学");
        Page<Course> pageResult = new Page<>(1, 10);
        pageResult.setRecords(List.of(course));
        pageResult.setTotal(1);
        when(courseService.page(any(), any())).thenReturn(pageResult);

        mockMvc.perform(get("/student/courses?page=1&size=1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].id").value(5))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(enrollmentService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(courseService).page(any(), any());
    }


    @Test
    @DisplayName("GET /student/courses - 未认证返回401")
    void getMyCourses_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/courses?page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /student/courses - TEACHER角色返回403")
    void getMyCourses_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/courses?page=1&size=1").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /student/courses/1 - STUDENT 获取课程详情返回 code=200 且字段透传")
    void getCourseDetail_authorized_passes_security() throws Exception {
        Course course = new Course();
        course.setId(1L);
        course.setName("高等数学");
        when(courseService.getById(any())).thenReturn(course);
        when(enrollmentService.getOne(any())).thenReturn(new CourseEnrollment());

        mockMvc.perform(get("/student/courses/1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.course.id").value(1));

        verify(courseService).getById(eq(1L));
    }


    @Test
    @DisplayName("GET /student/courses/1 - 未认证返回401")
    void getCourseDetail_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/courses/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /student/courses/1 - TEACHER角色返回403")
    void getCourseDetail_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/courses/1").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /student/courses/1/enroll - STUDENT 加入课程返回 code=200")
    void enrollCourse_authorized_passes_security() throws Exception {
        mockMvc.perform(post("/student/courses/1/enroll").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(enrollmentService).enrollStudent(eq(1L), eq(1L), isNull());
    }


    @Test
    @DisplayName("POST /student/courses/1/enroll - 未认证返回401")
    void enrollCourse_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/student/courses/1/enroll"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /student/courses/1/enroll - TEACHER角色返回403")
    void enrollCourse_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/student/courses/1/enroll").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("DELETE /student/courses/1/enroll - STUDENT 退出课程走软删（P1-3 收口，不物理删行）")
    void unenrollCourse_authorized_passes_security() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setId(9L);
        when(enrollmentService.getOne(any())).thenReturn(enrollment);

        mockMvc.perform(delete("/student/courses/1/enroll").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 2026-11-14 P1-3：学生端与管理端统一走服务层软删（is_active=false），
        // 不再 removeById 物理删行（容量统计与历史留痕依赖软删行）
        verify(enrollmentService).unenroll(eq(9L), eq(1L));
        verify(enrollmentService, never()).removeById(anyLong());
    }


    @Test
    @DisplayName("DELETE /student/courses/1/enroll - 未认证返回401")
    void unenrollCourse_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/student/courses/1/enroll"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("DELETE /student/courses/1/enroll - TEACHER角色返回403")
    void unenrollCourse_teacher_forbidden_403() throws Exception {
        mockMvc.perform(delete("/student/courses/1/enroll").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    // ==================== 作业管理 ====================

    @Test
    @DisplayName("GET /student/assignments/pending - STUDENT 获取待完成作业返回 code=200 且字段透传")
    void getPendingAssignments_authorized_passes_security() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(3L);
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(enrollment));

        Task task = new Task();
        task.setId(3L);
        when(taskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(task));
        when(submissionService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/student/assignments/pending").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(3));

        verify(enrollmentService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(taskService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }


    @Test
    @DisplayName("GET /student/assignments/pending - 未认证返回401")
    void getPendingAssignments_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/assignments/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /student/assignments/pending - 最新提交为 RETURNED 的作业仍在待办（P1-4 收口）")
    void getPendingAssignments_returnedLatest_staysInPending() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(3L);
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(enrollment));

        Task task = new Task();
        task.setId(3L);
        when(taskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(task));

        // 两次提交：attempt 1 = GRADED，attempt 2 = RETURNED（最新一次被退回）
        Submission older = new Submission();
        older.setTaskId(3L);
        older.setAttemptNumber(1);
        older.setStatus("GRADED");
        Submission latest = new Submission();
        latest.setTaskId(3L);
        latest.setAttemptNumber(2);
        latest.setStatus("RETURNED");
        when(submissionService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(older, latest));

        mockMvc.perform(get("/student/assignments/pending").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(3));
    }

    @Test
    @DisplayName("GET /student/assignments/pending - 最新提交为 GRADED 的作业不在待办")
    void getPendingAssignments_gradedLatest_excluded() throws Exception {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setCourseId(3L);
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(enrollment));

        Task task = new Task();
        task.setId(3L);
        when(taskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(task));

        Submission graded = new Submission();
        graded.setTaskId(3L);
        graded.setAttemptNumber(2);
        graded.setStatus("GRADED");
        Submission returnedOlder = new Submission();
        returnedOlder.setTaskId(3L);
        returnedOlder.setAttemptNumber(1);
        returnedOlder.setStatus("RETURNED");
        when(submissionService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(graded, returnedOlder));

        mockMvc.perform(get("/student/assignments/pending").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }


    @Test
    @DisplayName("GET /student/assignments/pending - TEACHER角色返回403")
    void getPendingAssignments_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/assignments/pending").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /student/assignments/completed - STUDENT 获取已完成作业返回 code=200 且字段透传")
    void getCompletedAssignments_authorized_passes_security() throws Exception {
        Submission submission = new Submission();
        submission.setId(7L);
        when(submissionService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(submission));

        mockMvc.perform(get("/student/assignments/completed").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(7));

        verify(submissionService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }


    @Test
    @DisplayName("GET /student/assignments/completed - 未认证返回401")
    void getCompletedAssignments_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/assignments/completed"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /student/assignments/completed - TEACHER角色返回403")
    void getCompletedAssignments_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/assignments/completed").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /student/assignments/1 - STUDENT 获取作业详情返回 code=200 且字段透传")
    void getAssignmentDetail_authorized_passes_security() throws Exception {
        Task task = new Task();
        task.setId(1L);
        when(taskService.getById(any())).thenReturn(task);
        when(submissionService.getByTaskAndStudent(1L, 1L)).thenReturn(new Submission());

        mockMvc.perform(get("/student/assignments/1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.assignment.id").value(1));

        verify(taskService).getById(eq(1L));
        verify(submissionService).getByTaskAndStudent(1L, 1L);
        verify(submissionService, never()).getOne(any());
    }

    @Test
    @DisplayName("GET /student/assignments/1 - 同一学生多次尝试时取最新一条，不再走 getOne")
    void getAssignmentDetail_multipleAttempts_usesLatest() throws Exception {
        Task task = new Task();
        task.setId(1L);
        Submission latest = new Submission();
        latest.setId(12L);
        latest.setTaskId(1L);
        latest.setStudentId(1L);
        latest.setAttemptNumber(2);
        latest.setStatus("GRADED");
        when(taskService.getById(any())).thenReturn(task);
        when(submissionService.getByTaskAndStudent(1L, 1L)).thenReturn(latest);

        mockMvc.perform(get("/student/assignments/1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submission.id").value(12))
                .andExpect(jsonPath("$.data.submission.attemptNumber").value(2));

        verify(submissionService).getByTaskAndStudent(1L, 1L);
        verify(submissionService, never()).getOne(any());
    }


    @Test
    @DisplayName("GET /student/assignments/1 - 未认证返回401")
    void getAssignmentDetail_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/assignments/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /student/assignments/1 - 未选课学生读取返回403")
    void getAssignmentDetail_unenrolledStudent_forbidden_403() throws Exception {
        Task task = new Task();
        task.setId(1L);
        task.setCourseId(1L);
        when(taskService.getById(any())).thenReturn(task);
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "尚未加入该课程，无权查看课程内容"))
                .when(courseAccessControl).assertCanReadTask(any(Task.class), any());

        mockMvc.perform(get("/student/assignments/1").with(withUser(9L, "STUDENT")))
                .andExpect(status().isForbidden());
        verify(submissionService, never()).getByTaskAndStudent(any(), any());
    }

    @Test
    @DisplayName("GET /student/assignments/1 - TEACHER角色返回403")
    void getAssignmentDetail_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/assignments/1").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /student/assignments/1/submit - STUDENT 提交作业返回 code=200 且字段透传")
    void submitAssignment_authorized_passes_security() throws Exception {
        Submission submission = new Submission();
        submission.setId(8L);
        when(submissionService.submitAssignment(anyLong(), anyLong(), any(), any())).thenReturn(submission);

        mockMvc.perform(post("/student/assignments/1/submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(8));

        verify(submissionService).submitAssignment(eq(1L), eq(1L), any(), any());
    }


    @Test
    @DisplayName("POST /student/assignments/1/submit - 未认证返回401")
    void submitAssignment_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/student/assignments/1/submit").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /student/assignments/1/submit - TEACHER角色返回403")
    void submitAssignment_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/student/assignments/1/submit").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /student/assignments/1/result - STUDENT 获取批改结果返回 code=200 且字段透传")
    void getAssignmentResult_authorized_passes_security() throws Exception {
        Submission submission = new Submission();
        submission.setId(4L);
        when(submissionService.getByTaskAndStudent(1L, 1L)).thenReturn(submission);

        mockMvc.perform(get("/student/assignments/1/result").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(4));

        verify(submissionService).getByTaskAndStudent(1L, 1L);
        verify(submissionService, never()).getOne(any());
    }

    @Test
    @DisplayName("GET /student/assignments/1/result - 同一学生多次尝试时取最新一条，不再走 getOne")
    void getAssignmentResult_multipleAttempts_usesLatest() throws Exception {
        Submission latest = new Submission();
        latest.setId(13L);
        latest.setTaskId(1L);
        latest.setStudentId(1L);
        latest.setAttemptNumber(3);
        latest.setStatus("GRADED");
        when(submissionService.getByTaskAndStudent(1L, 1L)).thenReturn(latest);

        mockMvc.perform(get("/student/assignments/1/result").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(13))
                .andExpect(jsonPath("$.data.attemptNumber").value(3));

        verify(submissionService).getByTaskAndStudent(1L, 1L);
        verify(submissionService, never()).getOne(any());
    }


    @Test
    @DisplayName("GET /student/assignments/1/result - 未认证返回401")
    void getAssignmentResult_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/assignments/1/result"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /student/assignments/1/result - TEACHER角色返回403")
    void getAssignmentResult_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/assignments/1/result").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    // ==================== 错题本 ====================

    @Test
    @DisplayName("GET /student/wrong-questions - STUDENT 获取错题本返回 code=200 且字段透传")
    void getWrongQuestions_authorized_passes_security() throws Exception {
        WrongQuestion wq = new WrongQuestion();
        wq.setId(2L);
        Page<WrongQuestion> pageResult = new Page<>(1, 1);
        pageResult.setRecords(List.of(wq));
        pageResult.setTotal(1);
        when(wrongQuestionService.getWrongQuestionBook(anyLong(), anyLong(), anyInt(), anyInt())).thenReturn(pageResult);

        mockMvc.perform(get("/student/wrong-questions?courseId=1&page=1&size=1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].id").value(2))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(wrongQuestionService).getWrongQuestionBook(eq(1L), eq(1L), eq(1), eq(1));
    }


    @Test
    @DisplayName("GET /student/wrong-questions - 未认证返回401")
    void getWrongQuestions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/wrong-questions?courseId=1&page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /student/wrong-questions - TEACHER角色返回403")
    void getWrongQuestions_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/wrong-questions?courseId=1&page=1&size=1").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /student/wrong-questions - STUDENT 添加错题返回 code=200 且字段透传")
    void addWrongQuestion_authorized_passes_security() throws Exception {
        WrongQuestion wq = new WrongQuestion();
        wq.setId(10L);
        when(wrongQuestionService.recordWrongAnswer(anyLong(), anyLong(), anyLong(), any(), any()))
                .thenReturn(wq);

        mockMvc.perform(post("/student/wrong-questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"10\",\"courseId\":\"5\",\"wrongAnswer\":\"a\",\"sourceType\":\"PRACTICE\"}")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(10));

        verify(wrongQuestionService).recordWrongAnswer(eq(1L), eq(10L), eq(5L), eq("a"), eq("PRACTICE"));
    }


    @Test
    @DisplayName("POST /student/wrong-questions - 未认证返回401")
    void addWrongQuestion_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/student/wrong-questions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /student/wrong-questions - TEACHER角色返回403")
    void addWrongQuestion_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/student/wrong-questions").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("PUT /student/wrong-questions/1/mastered - STUDENT 标记掌握返回 code=200（带归属校验）")
    void markMastered_authorized_passes_security() throws Exception {
        doNothing().when(wrongQuestionService).markMasteredForStudent(anyLong(), anyLong());

        mockMvc.perform(put("/student/wrong-questions/1/mastered").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(wrongQuestionService).markMasteredForStudent(eq(1L), eq(1L));
    }


    @Test
    @DisplayName("PUT /student/wrong-questions/1/mastered - 未认证返回401")
    void markMastered_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/student/wrong-questions/1/mastered"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PUT /student/wrong-questions/1/mastered - TEACHER角色返回403")
    void markMastered_teacher_forbidden_403() throws Exception {
        mockMvc.perform(put("/student/wrong-questions/1/mastered").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("DELETE /student/wrong-questions/1 - STUDENT 移除错题返回 code=200（带归属校验）")
    void removeWrongQuestion_authorized_passes_security() throws Exception {
        doNothing().when(wrongQuestionService).deleteForStudent(anyLong(), anyLong());

        mockMvc.perform(delete("/student/wrong-questions/1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(wrongQuestionService).deleteForStudent(eq(1L), eq(1L));
    }


    @Test
    @DisplayName("DELETE /student/wrong-questions/1 - 未认证返回401")
    void removeWrongQuestion_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/student/wrong-questions/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("DELETE /student/wrong-questions/1 - TEACHER角色返回403")
    void removeWrongQuestion_teacher_forbidden_403() throws Exception {
        mockMvc.perform(delete("/student/wrong-questions/1").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /student/wrong-questions/practice - STUDENT 生成练习返回 code=200 且字段透传")
    void generatePractice_authorized_passes_security() throws Exception {
        Question question = new Question();
        question.setId(20L);
        when(wrongQuestionService.generatePracticePaper(anyLong(), any(), anyInt())).thenReturn(List.of(question));

        mockMvc.perform(post("/student/wrong-questions/practice").contentType(MediaType.APPLICATION_JSON)
                        .content("{}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(20));

        verify(wrongQuestionService).generatePracticePaper(eq(1L), any(), eq(10));
    }


    @Test
    @DisplayName("POST /student/wrong-questions/practice - 未认证返回401")
    void generatePractice_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/student/wrong-questions/practice").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /student/wrong-questions/practice - TEACHER角色返回403")
    void generatePractice_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/student/wrong-questions/practice").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    // ==================== 学习进度 ====================

    @Test
    @DisplayName("GET /student/progress/overview - STUDENT 获取学习概览返回 code=200 且字段透传")
    void getProgressOverview_authorized_passes_security() throws Exception {
        when(learningRecordService.getLearningStats(anyLong())).thenReturn(Map.of("totalMinutes", 100));

        mockMvc.perform(get("/student/progress/overview").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalMinutes").value(100));

        verify(learningRecordService).getLearningStats(eq(1L));
    }


    @Test
    @DisplayName("GET /student/progress/overview - 未认证返回401")
    void getProgressOverview_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/progress/overview"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /student/progress/overview - TEACHER角色返回403")
    void getProgressOverview_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/progress/overview").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /student/progress/calendar - STUDENT 获取学习日历返回 code=200 且为数组")
    void getProgressCalendar_authorized_passes_security() throws Exception {
        when(learningRecordService.getMonthlyLearningData(anyLong(), anyInt(), anyInt()))
                .thenReturn(List.of(new DailyLearningStatVO("2024-01-01", 30, 1)));

        mockMvc.perform(get("/student/progress/calendar?year=1&month=1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].date").value("2024-01-01"));

        verify(learningRecordService).getMonthlyLearningData(eq(1L), eq(1), eq(1));
    }


    @Test
    @DisplayName("GET /student/progress/calendar - 未认证返回401")
    void getProgressCalendar_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/progress/calendar?year=1&month=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /student/progress/calendar - TEACHER角色返回403")
    void getProgressCalendar_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/progress/calendar?year=1&month=1").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /student/progress/statistics - STUDENT 获取学习统计返回 code=200 且为数组")
    void getProgressStatistics_authorized_passes_security() throws Exception {
        when(learningRecordService.getLearningTrend(anyLong(), anyInt()))
                .thenReturn(List.of(new DailyLearningStatVO("2024-01-01", 0, 0)));

        mockMvc.perform(get("/student/progress/statistics?days=1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].date").value("2024-01-01"));

        verify(learningRecordService).getLearningTrend(eq(1L), eq(1));
    }


    @Test
    @DisplayName("GET /student/progress/statistics - 未认证返回401")
    void getProgressStatistics_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/progress/statistics?days=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /student/progress/statistics - TEACHER角色返回403")
    void getProgressStatistics_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/progress/statistics?days=1").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

}
