package com.aicourse.controller;
import com.aicourse.controller.StudentLearningController;
import com.aicourse.service.StudentLearningService;
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
 * StudentLearningController 接口测试（升级版）。
 * 将「伪覆盖弱断言」改造为真实业务断言：对每个 endpoint 依赖的 service 方法打桩返回合理业务对象，
 * 断言 HTTP 200 + ApiResponse.code=200 + 关键字段透传（jsonPath），并 verify 路由正确委托与参数透传。
 *
 * 注意：本控制器类级 @PreAuthorize("hasRole('STUDENT')")，故所有端点仅 STUDENT 可访问，
 * 额外为每个端点补充 TEACHER 越权 403 测试。
 */
@WebMvcTest(StudentLearningController.class)
class StudentLearningControllerTest extends ControllerTestBase {

    @MockitoBean
    private StudentLearningService studentLearningService;

    @Test
    @DisplayName("GET /student/dashboard - STUDENT返回200且data透传驾驶舱")
    void dashboard_authorized_returns_success() throws Exception {
        when(studentLearningService.getStudentDashboard(any())).thenReturn(Map.of("courses", List.of()));
        mockMvc.perform(get("/student/dashboard").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
        verify(studentLearningService).getStudentDashboard(eq(1L));
    }

    @Test
    @DisplayName("GET /student/dashboard - 未认证返回401")
    void dashboard_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /student/dashboard - TEACHER角色返回403")
    void dashboard_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/dashboard").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /student/courses/1/learning-center - STUDENT返回200且data透传学习中心")
    void learningCenter_authorized_returns_success() throws Exception {
        when(studentLearningService.getCourseLearningCenter(any(), any())).thenReturn(Map.of("courseId", 1L));
        mockMvc.perform(get("/student/courses/1/learning-center").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.courseId").value(1));
        verify(studentLearningService).getCourseLearningCenter(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /student/courses/1/learning-center - 未认证返回401")
    void learningCenter_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/courses/1/learning-center"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /student/courses/1/learning-center - TEACHER角色返回403")
    void learningCenter_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/courses/1/learning-center").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /student/courses/1/progress-summary - STUDENT返回200且data透传")
    void progressSummary_authorized_returns_success() throws Exception {
        when(studentLearningService.getCourseProgressSummary(any(), any())).thenReturn(Map.of("completed", 1, "total", 2));
        mockMvc.perform(get("/student/courses/1/progress-summary").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2));
        verify(studentLearningService).getCourseProgressSummary(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /student/courses/1/progress-summary - 未认证返回401")
    void progressSummary_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/courses/1/progress-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /student/courses/1/progress-summary - TEACHER角色返回403")
    void progressSummary_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/courses/1/progress-summary").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /student/courses/1/tasks/1/complete - STUDENT返回200且data透传完成态")
    void completeTask_authorized_returns_success() throws Exception {
        when(studentLearningService.markTaskComplete(any(), any(), any(), any())).thenReturn(Map.of("completed", true));
        mockMvc.perform(post("/student/courses/1/tasks/1/complete").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.completed").value(true));
        verify(studentLearningService).markTaskComplete(eq(1L), eq(1L), eq(1L), any());
    }

    @Test
    @DisplayName("POST /student/courses/1/tasks/1/complete - 未认证返回401")
    void completeTask_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/student/courses/1/tasks/1/complete").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /student/courses/1/tasks/1/complete - TEACHER角色返回403")
    void completeTask_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/student/courses/1/tasks/1/complete").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /student/courses/1/tasks/1/video-state - STUDENT返回200且data透传")
    void videoState_authorized_returns_success() throws Exception {
        when(studentLearningService.getVideoState(any(), any(), any())).thenReturn(Map.of("positionSeconds", 10));
        mockMvc.perform(get("/student/courses/1/tasks/1/video-state").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.positionSeconds").value(10));
        verify(studentLearningService).getVideoState(eq(1L), eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /student/courses/1/tasks/1/video-state - 未认证返回401")
    void videoState_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/courses/1/tasks/1/video-state"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /student/courses/1/tasks/1/video-state - TEACHER角色返回403")
    void videoState_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/courses/1/tasks/1/video-state").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /student/courses/1/tasks/1/video-heartbeat - STUDENT返回200且data透传")
    void videoHeartbeat_authorized_returns_success() throws Exception {
        when(studentLearningService.reportVideoHeartbeat(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(Map.of("updated", true));
        mockMvc.perform(post("/student/courses/1/tasks/1/video-heartbeat").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.updated").value(true));
        verify(studentLearningService).reportVideoHeartbeat(eq(1L), eq(1L), eq(1L), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /student/courses/1/tasks/1/video-heartbeat - 未认证返回401")
    void videoHeartbeat_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/student/courses/1/tasks/1/video-heartbeat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /student/courses/1/tasks/1/video-heartbeat - TEACHER角色返回403")
    void videoHeartbeat_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/student/courses/1/tasks/1/video-heartbeat").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /student/courses/1/tasks/1/quiz-submit - STUDENT返回200且data透传判分")
    void quizSubmit_authorized_returns_success() throws Exception {
        when(studentLearningService.submitQuiz(any(), any(), any(), any())).thenReturn(Map.of("total", 0));
        mockMvc.perform(post("/student/courses/1/tasks/1/quiz-submit").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
        verify(studentLearningService).submitQuiz(eq(1L), eq(1L), eq(1L), any());
    }

    @Test
    @DisplayName("POST /student/courses/1/tasks/1/quiz-submit - 未认证返回401")
    void quizSubmit_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/student/courses/1/tasks/1/quiz-submit").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /student/courses/1/tasks/1/quiz-submit - TEACHER角色返回403")
    void quizSubmit_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/student/courses/1/tasks/1/quiz-submit").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /student/grades - STUDENT返回200且data透传成绩")
    void grades_authorized_returns_success() throws Exception {
        when(studentLearningService.getGrades(any())).thenReturn(Map.of("gpa", 3.5));
        mockMvc.perform(get("/student/grades").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
        verify(studentLearningService).getGrades(eq(1L));
    }

    @Test
    @DisplayName("GET /student/grades - 未认证返回401")
    void grades_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/student/grades"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /student/grades - TEACHER角色返回403")
    void grades_teacher_forbidden_403() throws Exception {
        mockMvc.perform(get("/student/grades").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }
}
