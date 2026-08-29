package com.aicourse.controller;

import com.aicourse.entity.Reminder;
import com.aicourse.exception.GlobalExceptionHandler;
import com.aicourse.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖率测试：ReminderController（独立 MockMvc，不加载 Spring 上下文）。
 * 覆盖创建（特权/非特权/校验失败分支）、待办、todos(管理员/普通)、取消/改期/更新(归属校验各分支)、
 * 定时发送等端点的成功与异常分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReminderController 覆盖率测试")
class ReminderControllerCoverageTest {

    @Mock
    private ReminderService reminderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        ReminderController controller = new ReminderController(reminderService);
        // 独立 MockMvc 需手动注册全局异常处理器，使归属/不存在等 BusinessException
        // 被统一转换为 4xx，而非穿透为 500。
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        Reminder owner = new Reminder();
        owner.setId(1L);
        owner.setUserId(1L);
        lenient().when(reminderService.getById(any())).thenReturn(owner);
        lenient().when(reminderService.createReminder(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(owner);
        lenient().doNothing().when(reminderService).cancelReminder(any());
        lenient().doNothing().when(reminderService).rescheduleReminder(any(), any());
        lenient().when(reminderService.updateReminder(any(), any())).thenReturn(owner);
        lenient().when(reminderService.getPendingReminders(any())).thenReturn(List.of(owner));
        lenient().when(reminderService.sendDueReminders(any())).thenReturn(List.of(owner));

        // getTodos 管理员分支使用 lambdaQuery 链式查询
        LambdaQueryChainWrapper<Reminder> chain =
                mock(LambdaQueryChainWrapper.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        lenient().when(chain.list()).thenReturn(List.of());
        lenient().when(reminderService.lambdaQuery()).thenReturn(chain);
    }

    private static RequestPostProcessor withAuth(Long userId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        Authentication auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        return request -> {
            request.setUserPrincipal(auth);
            if (userId != null) request.setRemoteUser(String.valueOf(userId));
            return request;
        };
    }

    @Test
    @DisplayName("POST /reminders 特权(ADMIN)指定接收人")
    void createPrivileged() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/reminders").with(withAuth(1L, "ADMIN"))
                        .contentType("application/json")
                        .content("{\"userId\":5,\"reminderType\":\"TASK\",\"title\":\"t\","
                                + "\"dueDate\":\"2025-01-01T10:00:00\",\"triggerDay\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /reminders 非特权(STUDENT)忽略 userId，使用当前用户")
    void createNonPrivileged() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/reminders").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json")
                        .content("{\"userId\":5,\"reminderType\":\"TASK\",\"title\":\"t\","
                                + "\"dueDate\":\"2025-01-01T10:00:00\",\"triggerDay\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /reminders 特权但未传 userId -> 回退当前用户")
    void createPrivilegedNoUserId() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/reminders").with(withAuth(1L, "ADMIN"))
                        .contentType("application/json")
                        .content("{\"reminderType\":\"TASK\",\"title\":\"t\","
                                + "\"dueDate\":\"2025-01-01T10:00:00\",\"triggerDay\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /reminders 校验失败：reminderType 为空 / title 为空 / dueDate 为空 / triggerDay 为空")
    void createValidation() throws Exception {
        // 缺 reminderType -> 控制器校验 -> 400
        mockMvc.perform(MockMvcRequestBuilders.post("/reminders").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json")
                        .content("{\"title\":\"t\",\"dueDate\":\"2025-01-01T10:00:00\",\"triggerDay\":1}"))
                .andExpect(status().isBadRequest());
        // 缺 title -> 控制器校验 -> 400
        mockMvc.perform(MockMvcRequestBuilders.post("/reminders").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json")
                        .content("{\"reminderType\":\"TASK\",\"dueDate\":\"2025-01-01T10:00:00\",\"triggerDay\":1}"))
                .andExpect(status().isBadRequest());
        // 缺 dueDate -> 控制器校验 -> 400
        mockMvc.perform(MockMvcRequestBuilders.post("/reminders").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json")
                        .content("{\"reminderType\":\"TASK\",\"title\":\"t\",\"triggerDay\":1}"))
                .andExpect(status().isBadRequest());
        // 缺 triggerDay -> 控制器校验 -> 400
        mockMvc.perform(MockMvcRequestBuilders.post("/reminders").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json")
                        .content("{\"reminderType\":\"TASK\",\"title\":\"t\",\"dueDate\":\"2025-01-01T10:00:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /reminders/pending")
    void pending() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/reminders/pending").with(withAuth(1L, "STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /reminders/todos 管理员分支 / 普通用户分支")
    void todos() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/reminders/todos").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/reminders/todos").with(withAuth(1L, "STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /reminders/{id}/cancel 管理员 / 本人(STUDENT) / 他人(403) / 不存在")
    void cancel() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.patch("/reminders/1/cancel").with(withAuth(1L, "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.patch("/reminders/1/cancel").with(withAuth(1L, "STUDENT")))
                .andExpect(status().isOk());
        Reminder other = new Reminder();
        other.setId(2L);
        other.setUserId(2L);
        when(reminderService.getById(1L)).thenReturn(other);
        mockMvc.perform(MockMvcRequestBuilders.patch("/reminders/1/cancel").with(withAuth(1L, "STUDENT")))
                .andExpect(status().isForbidden());
        when(reminderService.getById(1L)).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.patch("/reminders/1/cancel").with(withAuth(1L, "STUDENT")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("PATCH /reminders/{id}/reschedule 成功 / newScheduledAt 为空(异常->400)")
    void reschedule() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.patch("/reminders/1/reschedule").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json")
                        .content("{\"title\":\"提醒\",\"newScheduledAt\":\"2025-01-02T10:00:00\"}"))
                .andExpect(status().isOk());
        // 缺 newScheduledAt -> BusinessException 新的计划发送时间不能为空 -> 400
        mockMvc.perform(MockMvcRequestBuilders.patch("/reminders/1/reschedule").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content("{\"title\":\"提醒\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /reminders/send-due")
    void sendDue() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/reminders/send-due")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /reminders/{id} 更新成功 / 不存在(异常)")
    void update() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/reminders/1").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json")
                        .content("{\"title\":\"新标题\",\"content\":\"新内容\",\"remindAt\":\"2025-01-02T10:00:00\",\"targetUserId\":2}"))
                .andExpect(status().isOk());
        when(reminderService.getById(1L)).thenReturn(null);
        mockMvc.perform(MockMvcRequestBuilders.put("/reminders/1").with(withAuth(1L, "STUDENT"))
                        .contentType("application/json").content("{\"title\":\"新标题\"}"))
                .andExpect(status().is4xxClientError());
    }
}
