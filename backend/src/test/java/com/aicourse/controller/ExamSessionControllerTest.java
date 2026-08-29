package com.aicourse.controller;
import com.aicourse.controller.ExamSessionController;
import com.aicourse.common.ApiCode;
import com.aicourse.entity.ExamSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.ExamSessionService;
import com.aicourse.security.ExamSessionAccessControl;
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
 * ExamSessionController 接口测试（升级版：真实业务断言）。
 * 针对每个被测 endpoint 打桩对应的 service 方法并返回合理业务对象，
 * 断言 HTTP 200 / code=200 以及业务字段透传到响应体，并用 verify 确认路由正确委托。
 */
@WebMvcTest(ExamSessionController.class)
class ExamSessionControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.ExamSessionService examSessionService;

    @MockitoBean
    private com.aicourse.security.ExamSessionAccessControl examSessionAccessControl;

    @Test
    @DisplayName("POST /exam-sessions/start - 学生开始考试返回200，会话ID透传")
    void start_authorized_passes_security() throws Exception {
        ExamSession session = new ExamSession();
        session.setId(1L);
        when(examSessionService.startExam(eq(1L), eq(1L), anyString(), anyString())).thenReturn(session);
        mockMvc.perform(post("/exam-sessions/start").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"examPaperId\":1}").header("User-Agent", "JUnit")
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examSessionService).startExam(eq(1L), eq(1L), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /exam-sessions/start - 未认证返回401")
    void start_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-sessions/start").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /exam-sessions/1/questions - 返回题目列表，数组与字段透传")
    void questions_authorized_passes_security() throws Exception {
        when(examSessionService.getSessionQuestions(eq(1L)))
                .thenReturn(List.of(Map.of("id", 1, "content", "q1")));
        mockMvc.perform(get("/exam-sessions/1/questions").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(examSessionService).getSessionQuestions(eq(1L));
    }

    @Test
    @DisplayName("GET /exam-sessions/1/questions - 未认证返回401")
    void questions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-sessions/1/questions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/auto-save - 自动保存返回200")
    void autoSave_authorized_passes_security() throws Exception {
        mockMvc.perform(post("/exam-sessions/1/auto-save").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"q1\":\"a\"}}").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(examSessionService).autoSaveAnswers(eq(1L), any());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/auto-save - 未认证返回401")
    void autoSave_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-sessions/1/auto-save").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/submit - 提交试卷返回200，会话ID透传")
    void submit_authorized_passes_security() throws Exception {
        ExamSession session = new ExamSession();
        session.setId(1L);
        when(examSessionService.submitExam(eq(1L), any())).thenReturn(session);
        mockMvc.perform(post("/exam-sessions/1/submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"q1\":\"a\"}}").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examSessionService).submitExam(eq(1L), any());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/submit - 未认证返回401")
    void submit_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-sessions/1/submit").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/force-submit - 教师/管理员强制交卷返回200，会话ID透传")
    void forceSubmit_authorized_passes_security() throws Exception {
        ExamSession session = new ExamSession();
        session.setId(1L);
        when(examSessionService.forceSubmit(eq(1L))).thenReturn(session);
        mockMvc.perform(post("/exam-sessions/1/force-submit").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examSessionService).forceSubmit(eq(1L));
        verify(examSessionAccessControl).assertManageable(eq(1L), any());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/force-submit - 教师越权收其他教师课程的考试返回403")
    void forceSubmit_otherTeachersCourse_forbidden_403() throws Exception {
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权管理其他教师的考试"))
                .when(examSessionAccessControl).assertManageable(eq(1L), any());
        mockMvc.perform(post("/exam-sessions/1/force-submit").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(examSessionService, never()).forceSubmit(anyLong());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/force-submit - 未认证返回401")
    void forceSubmit_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-sessions/1/force-submit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/force-submit - 学生越权返回403")
    void forceSubmit_forbidden_for_student() throws Exception {
        mockMvc.perform(post("/exam-sessions/1/force-submit").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /exam-sessions/1/time-remaining - 返回剩余秒数透传")
    void timeRemaining_authorized_passes_security() throws Exception {
        when(examSessionService.getRemainingTime(eq(1L))).thenReturn(3600L);
        mockMvc.perform(get("/exam-sessions/1/time-remaining").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(3600));
        verify(examSessionService).getRemainingTime(eq(1L));
    }

    @Test
    @DisplayName("GET /exam-sessions/1/time-remaining - 未认证返回401")
    void timeRemaining_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-sessions/1/time-remaining"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/auto-grade - 教师/管理员自动阅卷返回200，结果透传")
    void autoGrade_authorized_passes_security() throws Exception {
        when(examSessionService.autoGrade(eq(1L))).thenReturn(Map.of("score", 90));
        mockMvc.perform(post("/exam-sessions/1/auto-grade").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.score").value(90));
        verify(examSessionService).autoGrade(eq(1L));
        verify(examSessionAccessControl).assertManageable(eq(1L), any());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/auto-grade - 教师越权阅其他教师课程的考试返回403")
    void autoGrade_otherTeachersCourse_forbidden_403() throws Exception {
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权管理其他教师的考试"))
                .when(examSessionAccessControl).assertManageable(eq(1L), any());
        mockMvc.perform(post("/exam-sessions/1/auto-grade").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(examSessionService, never()).autoGrade(anyLong());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/auto-grade - 未认证返回401")
    void autoGrade_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-sessions/1/auto-grade"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/auto-grade - 学生越权返回403")
    void autoGrade_forbidden_for_student() throws Exception {
        mockMvc.perform(post("/exam-sessions/1/auto-grade").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/manual-grade - 教师/管理员人工评分返回200")
    void manualGrade_authorized_passes_security() throws Exception {
        mockMvc.perform(post("/exam-sessions/1/manual-grade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":99,\"score\":10,\"comment\":\"good\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(examSessionService).manualGrade(eq(1L), eq(99L), any(), any());
        verify(examSessionAccessControl).assertManageable(eq(1L), any());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/manual-grade - 教师越权评其他教师课程的考试返回403")
    void manualGrade_otherTeachersCourse_forbidden_403() throws Exception {
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权管理其他教师的考试"))
                .when(examSessionAccessControl).assertManageable(eq(1L), any());
        mockMvc.perform(post("/exam-sessions/1/manual-grade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":99,\"score\":10,\"comment\":\"good\"}")
                        .with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(examSessionService, never()).manualGrade(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/manual-grade - 未认证返回401")
    void manualGrade_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-sessions/1/manual-grade").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/manual-grade - 学生越权返回403")
    void manualGrade_forbidden_for_student() throws Exception {
        mockMvc.perform(post("/exam-sessions/1/manual-grade").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":99,\"score\":10,\"comment\":\"good\"}")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /exam-sessions/1/cheating-detect - 教师越权检测其他教师课程的考试返回403")
    void detectCheating_otherTeachersCourse_forbidden_403() throws Exception {
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权管理其他教师的考试"))
                .when(examSessionAccessControl).assertManageable(eq(1L), any());
        mockMvc.perform(get("/exam-sessions/1/cheating-detect").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(examSessionService, never()).detectCheating(anyLong());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/check-cheating - 教师越权检测其他教师课程的考试返回403")
    void checkCheating_otherTeachersCourse_forbidden_403() throws Exception {
        doThrow(new BusinessException(ApiCode.FORBIDDEN, "无权管理其他教师的考试"))
                .when(examSessionAccessControl).assertManageable(eq(1L), any());
        mockMvc.perform(post("/exam-sessions/1/check-cheating").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(examSessionService, never()).checkCheating(anyLong());
    }

    @Test
    @DisplayName("GET /exam-sessions/1/report - 返回成绩报告，字段透传")
    void report_authorized_passes_security() throws Exception {
        when(examSessionService.generateScoreReport(eq(1L))).thenReturn(Map.of("rank", 1));
        mockMvc.perform(get("/exam-sessions/1/report").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.rank").value(1));
        verify(examSessionService).generateScoreReport(eq(1L));
    }

    @Test
    @DisplayName("GET /exam-sessions/1/report - 未认证返回401")
    void report_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-sessions/1/report"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /exam-sessions/1/result - 返回会话详情，ID透传")
    void result_authorized_passes_security() throws Exception {
        ExamSession session = new ExamSession();
        session.setId(1L);
        when(examSessionAccessControl.assertReadable(eq(1L), any())).thenReturn(session);
        mockMvc.perform(get("/exam-sessions/1/result").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examSessionAccessControl).assertReadable(eq(1L), any());
    }

    @Test
    @DisplayName("GET /exam-sessions/1/result - 未认证返回401")
    void result_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-sessions/1/result"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /exam-sessions/1/cheating-detect - 教师/管理员检测作弊返回200，列表透传")
    void detectCheating_authorized_passes_security() throws Exception {
        when(examSessionService.detectCheating(eq(1L))).thenReturn(List.of("suspect1"));
        mockMvc.perform(get("/exam-sessions/1/cheating-detect").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("suspect1"));
        verify(examSessionService).detectCheating(eq(1L));
    }

    @Test
    @DisplayName("GET /exam-sessions/1/cheating-detect - 未认证返回401")
    void detectCheating_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-sessions/1/cheating-detect"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /exam-sessions/1/cheating-detect - 学生越权返回403")
    void detectCheating_forbidden_for_student() throws Exception {
        mockMvc.perform(get("/exam-sessions/1/cheating-detect").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/check-cheating - 教师/管理员作弊检测返回200，标记透传")
    void checkCheating_authorized_passes_security() throws Exception {
        when(examSessionService.checkCheating(eq(1L))).thenReturn(Map.of("flag", true));
        mockMvc.perform(post("/exam-sessions/1/check-cheating").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.flag").value(true));
        verify(examSessionService).checkCheating(eq(1L));
    }

    @Test
    @DisplayName("POST /exam-sessions/1/check-cheating - 未认证返回401")
    void checkCheating_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/exam-sessions/1/check-cheating"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /exam-sessions/1/check-cheating - 学生越权返回403")
    void checkCheating_forbidden_for_student() throws Exception {
        mockMvc.perform(post("/exam-sessions/1/check-cheating").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /exam-sessions/active - 教师/管理员返回进行中考试列表，数组透传")
    void activeExams_authorized_passes_security() throws Exception {
        ExamSession session = new ExamSession();
        session.setId(1L);
        when(examSessionService.getActiveExams()).thenReturn(List.of(session));
        mockMvc.perform(get("/exam-sessions/active").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(examSessionService).getActiveExams();
    }

    @Test
    @DisplayName("GET /exam-sessions/active - 未认证返回401")
    void activeExams_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-sessions/active"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /exam-sessions/active - 学生越权返回403")
    void activeExams_forbidden_for_student() throws Exception {
        mockMvc.perform(get("/exam-sessions/active").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /exam-sessions/paper/1 - 返回最新会话，ID透传")
    void mySession_authorized_passes_security() throws Exception {
        ExamSession session = new ExamSession();
        session.setId(1L);
        when(examSessionService.getLatestSession(eq(1L), eq(1L))).thenReturn(session);
        mockMvc.perform(get("/exam-sessions/paper/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(examSessionService).getLatestSession(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /exam-sessions/paper/1 - 未认证返回401")
    void mySession_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-sessions/paper/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /exam-sessions/my - 返回我的考试记录列表（含课程维度排名字段），数组透传")
    void mySessions_authorized_passes_security() throws Exception {
        // 2026-08-27 契约更新：/my 委托 getMySessionsWithCourseRank（附 courseRank/courseTotalStudents）
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", 1);
        row.put("courseRank", 2);
        row.put("courseTotalStudents", 30);
        when(examSessionService.getMySessionsWithCourseRank(1L)).thenReturn(List.of(row));
        mockMvc.perform(get("/exam-sessions/my").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].courseRank").value(2))
                .andExpect(jsonPath("$.data[0].courseTotalStudents").value(30));
        verify(examSessionService).getMySessionsWithCourseRank(1L);
    }

    @Test
    @DisplayName("GET /exam-sessions/my - 未认证返回401")
    void mySessions_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/exam-sessions/my"))
                .andExpect(status().isUnauthorized());
    }
}
