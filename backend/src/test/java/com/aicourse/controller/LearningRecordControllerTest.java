package com.aicourse.controller;
import com.aicourse.controller.LearningRecordController;
import com.aicourse.entity.LearningRecord;
import com.aicourse.service.LearningRecordService;
import com.aicourse.vo.DailyLearningStatVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * LearningRecordController 接口测试（升级版）。
 * 将「伪覆盖弱断言」改造为真实业务断言：对每个 endpoint 依赖的 service 方法打桩返回合理业务对象，
 * 断言 HTTP 200 + ApiResponse.code=200 + 关键字段透传（jsonPath），并 verify 路由正确委托与参数透传。
 */
@WebMvcTest(LearningRecordController.class)
@Import({com.aicourse.application.student.StudentLearningApplicationService.class})
class LearningRecordControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.CourseService courseService;

    @MockitoBean
    private com.aicourse.service.CourseEnrollmentService courseEnrollmentService;

    @MockitoBean
    private com.aicourse.service.TaskService taskService;

    @MockitoBean
    private com.aicourse.service.SubmissionService submissionService;

    @MockitoBean
    private com.aicourse.service.TeachingClassService teachingClassService;

    @MockitoBean
    private com.aicourse.service.WrongQuestionService wrongQuestionService;

    @MockitoBean
    private LearningRecordService learningRecordService;

    private LearningRecord sampleRecord() {
        LearningRecord r = new LearningRecord();
        r.setId(1L);
        return r;
    }

    @Test
    @DisplayName("POST /learning-records - STUDENT角色返回200且正确委托save")
    void record_authorized_returns_success() throws Exception {
        when(learningRecordService.save(any())).thenReturn(true);
        mockMvc.perform(post("/learning-records").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
        verify(learningRecordService).save(any(LearningRecord.class));
    }

    @Test
    @DisplayName("POST /learning-records - 未认证返回401")
    void record_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/learning-records").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /learning-records - TEACHER角色返回403（仅STUDENT/ADMIN可记）")
    void record_teacher_forbidden_403() throws Exception {
        mockMvc.perform(post("/learning-records").contentType(MediaType.APPLICATION_JSON).content("{}").with(withUser(1L, "TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /learning-records - 返回200且data为记录列表")
    void myRecords_authorized_returns_success() throws Exception {
        when(learningRecordService.list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(List.of(sampleRecord()));
        mockMvc.perform(get("/learning-records").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(learningRecordService).list(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("GET /learning-records - 未认证返回401")
    void myRecords_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-records"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /learning-records/stats - 返回200且data透传统计")
    void getStats_authorized_returns_success() throws Exception {
        when(learningRecordService.getLearningStats(any())).thenReturn(Map.of("total", 1));
        mockMvc.perform(get("/learning-records/stats").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));
        verify(learningRecordService).getLearningStats(eq(1L));
    }

    @Test
    @DisplayName("GET /learning-records/stats - 未认证返回401")
    void getStats_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-records/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /learning-records/trend?days=1 - 返回200且data为数组")
    void getTrend_authorized_returns_success() throws Exception {
        when(learningRecordService.getLearningTrend(any(), anyInt())).thenReturn(List.of(new DailyLearningStatVO("2024-01-01", 0, 0)));
        mockMvc.perform(get("/learning-records/trend?days=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(learningRecordService).getLearningTrend(eq(1L), eq(1));
    }

    @Test
    @DisplayName("GET /learning-records/trend?days=1 - 未认证返回401")
    void getTrend_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-records/trend?days=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /learning-records/calendar?year=1&month=1 - 返回200且data为数组")
    void getCalendarData_authorized_returns_success() throws Exception {
        when(learningRecordService.getMonthlyLearningData(any(), anyInt(), anyInt())).thenReturn(List.of(new DailyLearningStatVO("2024-01-01", 0, 0)));
        mockMvc.perform(get("/learning-records/calendar?year=1&month=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(learningRecordService).getMonthlyLearningData(eq(1L), eq(1), eq(1));
    }

    @Test
    @DisplayName("GET /learning-records/calendar?year=1&month=1 - 未认证返回401")
    void getCalendarData_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/learning-records/calendar?year=1&month=1"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 入参收实体回归：请求体里的 userId/id/createdAt 属于服务端字段，必须被 DTO 挡掉，
     * 归属用户只认登录身份。否则学生可把学时记到别人账上（跨人写越权）。
     */
    @Test
    @DisplayName("POST /learning-records - 请求体注入 userId/id/createdAt 一律忽略，userId 取登录身份")
    void record_ignoresInjectedServerFields() throws Exception {
        when(learningRecordService.save(any())).thenReturn(true);
        String body = "{\"userId\":999,\"id\":777,\"createdAt\":\"2026-01-01T00:00:00\","
                + "\"courseId\":4007,\"actionType\":\"VIEW\",\"duration\":600,\"progress\":45.5}";
        mockMvc.perform(post("/learning-records").contentType(MediaType.APPLICATION_JSON).content(body).with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);
        verify(learningRecordService).save(captor.capture());
        LearningRecord saved = captor.getValue();
        assertEquals(1L, saved.getUserId().longValue(), "userId 必须取登录用户，不能被请求体覆盖");
        assertNull(saved.getId(), "主键不能被请求体指定");
        assertNull(saved.getCreatedAt(), "创建时间不能被请求体指定");
        assertEquals(4007L, saved.getCourseId().longValue());
        assertEquals("VIEW", saved.getActionType());
        assertEquals(600, saved.getDuration().intValue());
    }
}
