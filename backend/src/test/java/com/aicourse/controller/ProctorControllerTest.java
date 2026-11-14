package com.aicourse.controller;
import com.aicourse.controller.ProctorController;
import com.aicourse.entity.ProctorEvent;
import com.aicourse.entity.ProctorSession;
import com.aicourse.service.ProctorService;
import com.aicourse.vo.ProctorRiskSummaryVO;
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
 * ProctorController 接口测试。
 * 升级自生成器产出的弱断言（status != 401 && status != 403），改为真实业务断言：
 * 对 ProctorService 的相关方法打桩并用 jsonPath 校验响应体关键字段透传，verify 确认路由委托与参数透传。
 * 针对 @PreAuthorize 限定角色的接口新增 403 越权测试（studentSession 对所有已认证角色开放，不臆造 403）。
 */
@WebMvcTest(ProctorController.class)
class ProctorControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.ProctorService proctorService;

    /**
     * 2026-11-13 对象级收口：监考全链经 proctor session→exam_session→course 溯源做课程归属校验。
     * 本切片只验路由与信封透传，替身默认不拦截；归属判定语义由 403 回归用例覆盖。
     */
    @MockitoBean
    private com.aicourse.security.ExamSessionAccessControl examSessionAccessControl;

    @Test
    @DisplayName("POST /proctor/session/start - STUDENT 开始监考返回 code=200 且字段透传")
    void start_authorized_passes_security() throws Exception {
        ProctorSession session = new ProctorSession();
        session.setId(100L);
        session.setExamSessionId(1L);
        session.setStudentId(1L);
        session.setStatus("IN_PROGRESS");
        when(proctorService.startSession(anyLong(), anyLong())).thenReturn(session);

        mockMvc.perform(post("/proctor/session/start?examSessionId=1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.studentId").value(1));

        verify(proctorService).startSession(eq(1L), eq(1L));
    }


    @Test
    @DisplayName("POST /proctor/session/start - 未认证返回401")
    void start_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/proctor/session/start?examSessionId=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /proctor/session/start - TEACHER角色（无STUDENT）返回403")
    void start_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/proctor/session/start?examSessionId=1").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /proctor/session/1/event - STUDENT 上报事件返回 code=200 且字段透传")
    void logEvent_authorized_passes_security() throws Exception {
        ProctorSession session = new ProctorSession();
        session.setId(1L);
        session.setStudentId(1L);
        when(proctorService.getById(1L)).thenReturn(session);

        ProctorEvent event = new ProctorEvent();
        event.setId(50L);
        event.setType("TAB_SWITCH");
        when(proctorService.logEvent(anyLong(), any(), any(), any(), any())).thenReturn(event);

        mockMvc.perform(post("/proctor/session/1/event").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TAB_SWITCH\"}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(50));

        verify(proctorService).getById(eq(1L));
        verify(proctorService).logEvent(eq(1L), any(), any(), any(), any());
    }


    @Test
    @DisplayName("POST /proctor/session/1/event - 未认证返回401")
    void logEvent_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/proctor/session/1/event").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /proctor/session/1/event - TEACHER角色（无STUDENT）返回403")
    void logEvent_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/proctor/session/1/event").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /proctor/session/1/end - STUDENT 结束监考返回 code=200 且字段透传")
    void end_authorized_passes_security() throws Exception {
        // 2026-11-13 收口：结束前校验会话归属（与 logEvent 同口径）
        ProctorSession owned = new ProctorSession();
        owned.setId(1L);
        owned.setStudentId(1L);
        when(proctorService.getById(1L)).thenReturn(owned);
        ProctorSession session = new ProctorSession();
        session.setId(1L);
        session.setStatus("ENDED");
        when(proctorService.endSession(anyLong())).thenReturn(session);

        mockMvc.perform(post("/proctor/session/1/end").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(proctorService).getById(eq(1L));
        verify(proctorService).endSession(eq(1L));
    }

    @Test
    @DisplayName("POST /proctor/session/1/end - 学生结束他人监考会话返回403且不落服务")
    void end_otherStudentsSession_forbidden_403() throws Exception {
        ProctorSession others = new ProctorSession();
        others.setId(1L);
        others.setStudentId(999L);
        when(proctorService.getById(1L)).thenReturn(others);

        mockMvc.perform(post("/proctor/session/1/end").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(proctorService, never()).endSession(anyLong());
    }


    @Test
    @DisplayName("POST /proctor/session/1/end - 未认证返回401")
    void end_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/proctor/session/1/end"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /proctor/session/1/end - TEACHER角色（无STUDENT）返回403")
    void end_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/proctor/session/1/end").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /proctor/session/1/events - TEACHER 查询事件列表返回 code=200 且为数组")
    void events_authorized_passes_security() throws Exception {
        // 2026-11-13 收口：先取监考会话做 exam_session→course 溯源，再返回事件列表
        ProctorSession session = new ProctorSession();
        session.setId(1L);
        session.setExamSessionId(1L);
        when(proctorService.getById(1L)).thenReturn(session);
        ProctorEvent event = new ProctorEvent();
        event.setId(5L);
        when(proctorService.listEvents(anyLong())).thenReturn(List.of(event));

        mockMvc.perform(get("/proctor/session/1/events").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(5));

        verify(proctorService).getById(eq(1L));
        verify(examSessionAccessControl).assertManageable(eq(1L), any());
        verify(proctorService).listEvents(eq(1L));
    }

    @Test
    @DisplayName("GET /proctor/session/1/events - 他课教师（无课程管理权）返回403且不查事件")
    void events_crossCourseTeacher_forbidden_403() throws Exception {
        ProctorSession session = new ProctorSession();
        session.setId(1L);
        session.setExamSessionId(1L);
        when(proctorService.getById(1L)).thenReturn(session);
        doThrow(new com.aicourse.exception.BusinessException(com.aicourse.common.ApiCode.FORBIDDEN, "无权管理其他教师的考试"))
                .when(examSessionAccessControl).assertManageable(eq(1L), any());

        mockMvc.perform(get("/proctor/session/1/events").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(proctorService, never()).listEvents(anyLong());
    }


    @Test
    @DisplayName("GET /proctor/session/1/events - 未认证返回401")
    void events_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/proctor/session/1/events"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /proctor/session/1/events - STUDENT角色返回403")
    void events_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/proctor/session/1/events").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /proctor/exam/1/student/1/session - TEACHER 查询考生会话返回 code=200 且字段透传")
    void studentSession_authorized_passes_security() throws Exception {
        ProctorSession session = new ProctorSession();
        session.setId(9L);
        session.setExamSessionId(1L);
        session.setStudentId(1L);
        when(proctorService.getStudentSession(anyLong(), anyLong())).thenReturn(session);

        mockMvc.perform(get("/proctor/exam/1/student/1/session").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(9));

        verify(proctorService).getStudentSession(eq(1L), eq(1L));
    }


    @Test
    @DisplayName("GET /proctor/exam/1/student/1/session - 未认证返回401")
    void studentSession_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/proctor/exam/1/student/1/session"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /proctor/exam/1/risk - TEACHER 查询风险汇总返回 code=200 且为数组")
    void risk_authorized_passes_security() throws Exception {
        when(proctorService.riskSummary(anyLong())).thenReturn(
                List.of(new ProctorRiskSummaryVO(1L, 1L, "ACTIVE", 10, "LOW", 0, Map.of())));

        mockMvc.perform(get("/proctor/exam/1/risk").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].studentId").value(1));

        verify(examSessionAccessControl).assertManageable(eq(1L), any());
        verify(proctorService).riskSummary(eq(1L));
    }

    @Test
    @DisplayName("GET /proctor/exam/1/risk - 他课教师（无课程管理权）返回403且不查汇总")
    void risk_crossCourseTeacher_forbidden_403() throws Exception {
        doThrow(new com.aicourse.exception.BusinessException(com.aicourse.common.ApiCode.FORBIDDEN, "无权管理其他教师的考试"))
                .when(examSessionAccessControl).assertManageable(eq(1L), any());

        mockMvc.perform(get("/proctor/exam/1/risk").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(proctorService, never()).riskSummary(anyLong());
    }


    @Test
    @DisplayName("GET /proctor/exam/1/risk - 未认证返回401")
    void risk_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/proctor/exam/1/risk"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /proctor/exam/1/risk - STUDENT角色返回403")
    void risk_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/proctor/exam/1/risk").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /proctor/thresholds - TEACHER 读取阈值返回 code=200 且字段透传")
    void getThresholds_authorized_passes_security() throws Exception {
        when(proctorService.getThresholds()).thenReturn(new com.aicourse.vo.ProctorThresholdVO(6, 3, 1, 2, 3));

        mockMvc.perform(get("/proctor/thresholds").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.high").value(6));

        verify(proctorService).getThresholds();
    }


    @Test
    @DisplayName("GET /proctor/thresholds - 未认证返回401")
    void getThresholds_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/proctor/thresholds"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /proctor/thresholds - STUDENT角色返回403")
    void getThresholds_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/proctor/thresholds").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("PUT /proctor/thresholds - ADMIN 更新阈值返回 code=200")
    void updateThresholds_authorized_passes_security() throws Exception {
        doNothing().when(proctorService).updateThresholds(any());

        mockMvc.perform(put("/proctor/thresholds").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"faceThreshold\":80}").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(proctorService).updateThresholds(any());
    }


    @Test
    @DisplayName("PUT /proctor/thresholds - 未认证返回401")
    void updateThresholds_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/proctor/thresholds").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PUT /proctor/thresholds - TEACHER角色（无ADMIN）返回403")
    void updateThresholds_teacher_forbidden_403() throws Exception {
        mockMvc.perform(put("/proctor/thresholds").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("GET /proctor/events/1 - TEACHER 查询事件返回 code=200 且字段透传")
    void getEvent_authorized_passes_security() throws Exception {
        // 2026-11-13 收口：event → proctor session → exam_session → course 溯源后再返回
        ProctorEvent event = new ProctorEvent();
        event.setId(1L);
        event.setType("TAB_SWITCH");
        event.setProctorSessionId(1L);
        when(proctorService.getEvent(anyLong())).thenReturn(event);
        ProctorSession session = new ProctorSession();
        session.setId(1L);
        session.setExamSessionId(1L);
        when(proctorService.getById(1L)).thenReturn(session);

        mockMvc.perform(get("/proctor/events/1").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(examSessionAccessControl).assertManageable(eq(1L), any());
        verify(proctorService).getEvent(eq(1L));
    }

    @Test
    @DisplayName("GET /proctor/events/1 - 他课教师（无课程管理权）返回403")
    void getEvent_crossCourseTeacher_forbidden_403() throws Exception {
        ProctorEvent event = new ProctorEvent();
        event.setId(1L);
        event.setProctorSessionId(1L);
        when(proctorService.getEvent(anyLong())).thenReturn(event);
        ProctorSession session = new ProctorSession();
        session.setId(1L);
        session.setExamSessionId(1L);
        when(proctorService.getById(1L)).thenReturn(session);
        doThrow(new com.aicourse.exception.BusinessException(com.aicourse.common.ApiCode.FORBIDDEN, "无权管理其他教师的考试"))
                .when(examSessionAccessControl).assertManageable(eq(1L), any());

        mockMvc.perform(get("/proctor/events/1").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }


    @Test
    @DisplayName("GET /proctor/events/1 - 未认证返回401")
    void getEvent_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/proctor/events/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /proctor/events/1 - STUDENT角色返回403")
    void getEvent_student_forbidden_403() throws Exception {
        mockMvc.perform(get("/proctor/events/1").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /proctor/events/1/review - TEACHER 复核事件返回 code=200 且字段透传")
    void reviewEvent_authorized_passes_security() throws Exception {
        // 2026-11-13 收口：仅加课程管理权前置，复核/豁免触发重算回撤分的服务层逻辑不动
        ProctorEvent event = new ProctorEvent();
        event.setId(1L);
        event.setProctorSessionId(1L);
        when(proctorService.getEvent(anyLong())).thenReturn(event);
        ProctorSession session = new ProctorSession();
        session.setId(1L);
        session.setExamSessionId(1L);
        when(proctorService.getById(1L)).thenReturn(session);
        ProctorEvent reviewed = new ProctorEvent();
        reviewed.setId(1L);
        reviewed.setStatus("REVIEWED");
        when(proctorService.reviewEvent(anyLong(), any(), any(), anyLong())).thenReturn(reviewed);

        mockMvc.perform(post("/proctor/events/1/review").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWED\",\"reviewNote\":\"ok\"}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(examSessionAccessControl).assertManageable(eq(1L), any());
        verify(proctorService).reviewEvent(eq(1L), any(), any(), eq(1L));
    }

    @Test
    @DisplayName("POST /proctor/events/1/review - 他课教师（无课程管理权）返回403且不触发复核重算")
    void reviewEvent_crossCourseTeacher_forbidden_403() throws Exception {
        ProctorEvent event = new ProctorEvent();
        event.setId(1L);
        event.setProctorSessionId(1L);
        when(proctorService.getEvent(anyLong())).thenReturn(event);
        ProctorSession session = new ProctorSession();
        session.setId(1L);
        session.setExamSessionId(1L);
        when(proctorService.getById(1L)).thenReturn(session);
        doThrow(new com.aicourse.exception.BusinessException(com.aicourse.common.ApiCode.FORBIDDEN, "无权管理其他教师的考试"))
                .when(examSessionAccessControl).assertManageable(eq(1L), any());

        mockMvc.perform(post("/proctor/events/1/review").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EXEMPTED\",\"reviewNote\":\"豁免\"}").with(withUser(2L, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(proctorService, never()).reviewEvent(anyLong(), any(), any(), anyLong());
    }


    @Test
    @DisplayName("POST /proctor/events/1/review - 未认证返回401")
    void reviewEvent_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/proctor/events/1/review").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /proctor/events/1/review - STUDENT角色返回403")
    void reviewEvent_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/proctor/events/1/review").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

}
