package com.aicourse.controller;
import com.aicourse.controller.NotificationController;
import com.aicourse.service.NotificationService;
import com.aicourse.entity.Notification;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
 * NotificationController 接口测试（升级版）。
 * 原生成器弱断言（status != 401 && status != 403）已全部替换为真实业务断言：
 * 对依赖的 NotificationService 方法打桩返回合理业务对象，断言 200/code=200，并校验关键字段透传、verify 路由委托。
 * 仅 /notifications/send 带 @PreAuthorize("hasRole('ADMIN')")，新增 STUDENT 越权 403 测试。
 */
@WebMvcTest(NotificationController.class)
class NotificationControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.NotificationService notificationService;

    private Notification sampleNotification(Long id) {
        Notification n = new Notification();
        n.setId(id);
        n.setTitle("系统通知");
        return n;
    }

    @Test
    @DisplayName("GET /notifications - 返回200且用户通知分页透传")
    void getUserNotifications_authorized_returns_200() throws Exception {
        Page<Notification> p = new Page<>(1, 1);
        p.setRecords(List.of(sampleNotification(1L)));
        p.setTotal(1);
        when(notificationService.getUserNotifications(1L, "test", Boolean.TRUE, 1, 1)).thenReturn(p);
        mockMvc.perform(get("/notifications?category=test&isRead=true&page=1&size=1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(1));
        verify(notificationService).getUserNotifications(eq(1L), eq("test"), eq(Boolean.TRUE), eq(1), eq(1));
    }


    @Test
    @DisplayName("GET /notifications?category=test&isRead=true&page=1&size=1 - 未认证返回401")
    void getUserNotifications_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/notifications?category=test&isRead=true&page=1&size=1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("GET /notifications/unread-count - 返回200且未读数量透传")
    void getUnreadCount_authorized_returns_200() throws Exception {
        when(notificationService.getUnreadCount(1L)).thenReturn(5L);
        mockMvc.perform(get("/notifications/unread-count").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(5));
        verify(notificationService).getUnreadCount(eq(1L));
    }


    @Test
    @DisplayName("GET /notifications/unread-count - 未认证返回401")
    void getUnreadCount_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PATCH /notifications/1/read - 返回200且标记已读委托被调用")
    void markAsRead_authorized_returns_200() throws Exception {
        mockMvc.perform(patch("/notifications/1/read").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(notificationService).markAsRead(eq(1L), eq(1L));
    }


    @Test
    @DisplayName("PATCH /notifications/1/read - 未认证返回401")
    void markAsRead_unauthorized_returns_401() throws Exception {
        mockMvc.perform(patch("/notifications/1/read"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("PATCH /notifications/read-all - 返回200且全部已读委托被调用")
    void markAllAsRead_authorized_returns_200() throws Exception {
        mockMvc.perform(patch("/notifications/read-all").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(notificationService).markAllAsRead(eq(1L));
    }


    @Test
    @DisplayName("PATCH /notifications/read-all - 未认证返回401")
    void markAllAsRead_unauthorized_returns_401() throws Exception {
        mockMvc.perform(patch("/notifications/read-all"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("DELETE /notifications/1 - 返回200且删除委托被调用")
    void deleteNotification_authorized_returns_200() throws Exception {
        mockMvc.perform(delete("/notifications/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(notificationService).deleteNotification(eq(1L), eq(1L));
    }


    @Test
    @DisplayName("DELETE /notifications/1 - 未认证返回401")
    void deleteNotification_unauthorized_returns_401() throws Exception {
        mockMvc.perform(delete("/notifications/1"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @DisplayName("POST /notifications/send - 返回200且批量发送委托被调用")
    void sendNotification_authorized_returns_200() throws Exception {
        mockMvc.perform(post("/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserIds\":[1,2],\"title\":\"hi\",\"content\":\"hello\"}")
                        .with(withUser(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(notificationService).batchSendNotifications(any(), eq("hi"), eq("hello"), any(), any(), any(), any(), any());
    }


    @Test
    @DisplayName("POST /notifications/send - STUDENT 越权返回403")
    void sendNotification_student_forbidden_403() throws Exception {
        mockMvc.perform(post("/notifications/send").contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[1],\"title\":\"hi\",\"content\":\"hello\"}").with(withUser(2L, "STUDENT")))
                .andExpect(status().isForbidden());
    }


    @Test
    @DisplayName("POST /notifications/send - 未认证返回401")
    void sendNotification_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/notifications/send").contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[1],\"title\":\"hi\",\"content\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }

}
