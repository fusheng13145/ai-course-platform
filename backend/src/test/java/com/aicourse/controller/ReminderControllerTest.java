package com.aicourse.controller;
import com.aicourse.controller.ReminderController;
import com.aicourse.entity.Reminder;
import com.aicourse.service.ReminderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ReminderController 接口测试（升级版）。
 * 将「伪覆盖弱断言」改造为真实业务断言：对每个 endpoint 依赖的 service 方法打桩返回合理业务对象，
 * 断言 HTTP 200 + ApiResponse.code=200 + 关键字段透传（jsonPath），并 verify 路由正确委托与参数透传。
 *
 * 注：仅 POST /reminders/send-due 有 @PreAuthorize("hasRole('ADMIN')")，补充 STUDENT 越权 403 测试。
 */
@WebMvcTest(ReminderController.class)
class ReminderControllerTest extends ControllerTestBase {

    @MockitoBean
    private ReminderService reminderService;

    private Reminder sampleReminder() {
        Reminder r = new Reminder();
        r.setId(1L);
        return r;
    }

    @Test
    @DisplayName("POST /reminders - 返回200且data透传创建的提醒")
    void createReminder_authorized_returns_success() throws Exception {
        when(reminderService.createReminder(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(sampleReminder());
        mockMvc.perform(post("/reminders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"reminderType\":\"TASK\",\"title\":\"t\",\"dueDate\":\"2025-01-01T10:00:00\",\"triggerDay\":1}")
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(reminderService).createReminder(eq(1L), eq("TASK"), any(), any(), eq("t"), any(), any(), eq(1));
    }

    @Test
    @DisplayName("POST /reminders - 未认证返回401")
    void createReminder_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/reminders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /reminders/pending - 返回200且data为提醒列表")
    void getPendingReminders_authorized_returns_success() throws Exception {
        when(reminderService.getPendingReminders(any())).thenReturn(List.of(sampleReminder()));
        mockMvc.perform(get("/reminders/pending").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(reminderService).getPendingReminders(eq(1L));
    }

    @Test
    @DisplayName("GET /reminders/pending - 未认证返回401")
    void getPendingReminders_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/reminders/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /reminders/1/cancel - 返回200且正确委托cancelReminder")
    void cancelReminder_authorized_returns_success() throws Exception {
        doNothing().when(reminderService).cancelReminder(any());
        mockMvc.perform(patch("/reminders/1/cancel").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(reminderService).cancelReminder(eq(1L));
    }

    @Test
    @DisplayName("PATCH /reminders/1/cancel - 未认证返回401")
    void cancelReminder_unauthorized_returns_401() throws Exception {
        mockMvc.perform(patch("/reminders/1/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /reminders/1/reschedule - 返回200且正确委托rescheduleReminder")
    void rescheduleReminder_authorized_returns_success() throws Exception {
        doNothing().when(reminderService).rescheduleReminder(any(), any());
        mockMvc.perform(patch("/reminders/1/reschedule").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"提醒\",\"newScheduledAt\":\"2025-01-02T10:00:00\"}").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(reminderService).rescheduleReminder(eq(1L), any());
    }

    @Test
    @DisplayName("PATCH /reminders/1/reschedule - 未认证返回401")
    void rescheduleReminder_unauthorized_returns_401() throws Exception {
        mockMvc.perform(patch("/reminders/1/reschedule").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /reminders/send-due - ADMIN返回200且data为已发送列表")
    void sendDueReminders_authorized_returns_success() throws Exception {
        when(reminderService.sendDueReminders(any())).thenReturn(List.of(sampleReminder()));
        mockMvc.perform(post("/reminders/send-due").with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        verify(reminderService).sendDueReminders(any());
    }

    @Test
    @DisplayName("POST /reminders/send-due - 未认证返回401")
    void sendDueReminders_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/reminders/send-due"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /reminders/send-due - STUDENT角色返回403（仅ADMIN可发送）")
    void sendDueReminders_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/reminders/send-due").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /reminders/1 - 返回200且正确委托updateReminder（修复前端断链）")
    void updateReminder_authorized_returns_success() throws Exception {
        when(reminderService.updateReminder(any(), any())).thenReturn(sampleReminder());
        mockMvc.perform(put("/reminders/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新标题\",\"content\":\"新内容\",\"remindAt\":\"2025-01-02T10:00:00\",\"targetUserId\":2}")
                        .with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(reminderService).updateReminder(eq(1L), any());
    }

    @Test
    @DisplayName("PUT /reminders/1 - 未认证返回401")
    void updateReminder_unauthorized_returns_401() throws Exception {
        mockMvc.perform(put("/reminders/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /reminders/batch-trigger - 无对应 POST 端点（命中 PUT /{id} 模板返回405，杜绝死代码断链）")
    void batchTrigger_endpoint_not_found_405() throws Exception {
        mockMvc.perform(post("/reminders/batch-trigger").with(withUser(1L, "ADMIN")))
                .andExpect(status().isMethodNotAllowed());
    }
}
