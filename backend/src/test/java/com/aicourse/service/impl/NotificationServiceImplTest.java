package com.aicourse.service.impl;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Notification;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.NotificationMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.websocket.WebSocketSessionManager;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 通知服务单元测试
 * 规避原则：MyBatis-Plus saveBatch/updateById 需要真实 MyBatis 基础设施，
 * 因此这批测试只验证：返回值逻辑、异常抛出、以及纯接口 mock（WebSocket/EnrollmentService）的交互。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private CourseEnrollmentService courseEnrollmentService;
    @Mock private WebSocketSessionManager webSocketSessionManager;
    @InjectMocks private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "baseMapper", notificationMapper);
        // WebSocket 在构造期间被调用，这里用 lenient 避免严格的未使用校验
        lenient().doNothing().when(webSocketSessionManager).sendToUser(anyLong(), anyMap());
    }

    @Test
    @DisplayName("发送单条通知 - 调用 sendToUser")
    void testSendNotification() {
        clearInvocations(webSocketSessionManager); // 清除构造期调用

        notificationService.sendNotification(100L, "标题", "内容", "A", "T", "LOW", "/u", null, 1L, "T");

        verify(webSocketSessionManager).sendToUser(eq(100L), anyMap());
    }

    @Test
    @DisplayName("批量发送 - 空列表安全")
    void testBatchSend_empty() {
        notificationService.batchSendNotifications(List.of(), "t", "c", "C", "T", "L", "/u", null);
        // 无异常即通过
    }

    @Test
    @DisplayName("批量发送多用户 - 逐个 WebSocket 推送")
    void testBatchSend_multiUser() {
        notificationService.batchSendNotifications(List.of(1L, 2L), "t", "c", "C", "T", "L", "/u", null);
        verify(webSocketSessionManager, times(2)).sendToUser(anyLong(), anyMap());
    }

    @Test
    @DisplayName("标记已读 - 无权限抛异常")
    void testMarkAsRead_notOwner() {
        Notification n = new Notification(); n.setId(1L); n.setUserId(100L);
        when(notificationMapper.selectById(1L)).thenReturn(n);
        assertThrows(BusinessException.class, () -> notificationService.markAsRead(1L, 999L));
    }

    @Test
    @DisplayName("标记已读 - 通知不存在抛异常")
    void testMarkAsRead_notFound() {
        when(notificationMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> notificationService.markAsRead(99L, 100L));
    }

    @Test
    @DisplayName("全标已读 - 安全调用")
    void testMarkAllAsRead() {
        notificationService.markAllAsRead(100L);
    }

    @Test
    @DisplayName("获取未读数 - 调用 count")
    void testGetUnreadCount() {
        when(notificationMapper.selectCount(any(Wrapper.class))).thenReturn(5L);
        assertEquals(5L, notificationService.getUnreadCount(100L));
    }

    @Test
    @DisplayName("分页通知 - XSS 转义")
    void testGetUserNotifications() {
        Notification n1 = new Notification();
        n1.setUserId(100L); n1.setIsRead(false);
        n1.setTitle("<script>alert</script>");
        n1.setContent("<b>bold</b>");

        Page<Notification> p = new Page<>(1, 20);
        p.setRecords(List.of(n1));
        p.setTotal(1);
        when(notificationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(p);

        Page<Notification> r = notificationService.getUserNotifications(100L, null, null, 1, 20);
        assertFalse(r.getRecords().get(0).getTitle().contains("<script>"), "XSS转义应生效");
    }

    @Test
    @DisplayName("删除通知 - 非归属者抛异常")
    void testDeleteNotification_notOwner() {
        Notification n = new Notification(); n.setId(1L); n.setUserId(100L);
        when(notificationMapper.selectById(1L)).thenReturn(n);
        assertThrows(BusinessException.class, () -> notificationService.deleteNotification(1L, 999L));
    }

    @Test
    @DisplayName("事件驱动 - 任务创建查找选课学生")
    void testOnEvent_taskCreated() {
        CourseEnrollment e = new CourseEnrollment(); e.setStudentId(101L);
        when(courseEnrollmentService.list(any(Wrapper.class))).thenReturn(List.of(e));

        notificationService.onEvent("TASK_CREATED", Map.of("courseId", 10L, "taskTitle", "作业1", "taskId", 1L));

        verify(courseEnrollmentService).list(any(Wrapper.class));
    }

    @Test
    @DisplayName("事件驱动 - 考试发布查找选课学生")
    void testOnEvent_examPublished() {
        CourseEnrollment e = new CourseEnrollment(); e.setStudentId(101L);
        when(courseEnrollmentService.list(any(Wrapper.class))).thenReturn(List.of(e));

        notificationService.onEvent("EXAM_PUBLISHED", Map.of("courseId", 10L, "examTitle", "期中", "examId", 5L));

        verify(courseEnrollmentService).list(any(Wrapper.class));
    }

    @Test
    @DisplayName("事件驱动 - 作业批阅完成触发 WebSocket")
    void testOnEvent_submissionGraded() {
        clearInvocations(webSocketSessionManager);
        notificationService.onEvent("SUBMISSION_GRADED",
                Map.of("studentId", 100L, "taskTitle", "作业", "submissionId", 99L));
        verify(webSocketSessionManager).sendToUser(anyLong(), anyMap());
    }

    @Test
    @DisplayName("事件驱动 - 讨论回复通知")
    void testOnEvent_discussionReplied() {
        clearInvocations(webSocketSessionManager);
        notificationService.onEvent("DISCUSSION_REPLIED",
                Map.of("recipientId", 100L, "topicId", 1L, "topicTitle", "话题", "postId", 99L));
        verify(webSocketSessionManager).sendToUser(anyLong(), anyMap());
    }

    @Test
    @DisplayName("事件驱动 - 未知事件不查选课表")
    void testOnEvent_unknown() {
        notificationService.onEvent("UNKNOWN", Map.of());
        verify(courseEnrollmentService, never()).list(any(Wrapper.class));
    }
}
