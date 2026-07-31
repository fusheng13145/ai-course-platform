package com.aicourse.service.impl;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Notification;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.NotificationMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.websocket.WebSocketSessionManager;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 通知服务单元测试。
 *
 * <p>使用 @Spy 替代 @InjectMocks，以便对 saveBatch / lambdaUpdate 等
 * 需要真实 MyBatis 基础设施的方法进行 stub，绕过 Mockito 对 Mapper 代理的限制。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private CourseEnrollmentService courseEnrollmentService;
    @Mock private WebSocketSessionManager webSocketSessionManager;
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        // NotificationServiceImpl 无无参构造器，@Spy 无法直接初始化，
        // 改为在 setUp 中用 mocked 依赖手动创建 spy。
        notificationService = spy(new NotificationServiceImpl(courseEnrollmentService, webSocketSessionManager));

        // 通过反射将 mock mapper 注入到 ServiceImpl 的 baseMapper 字段，
        // 使 selectById / selectList / selectPage 等简单委托方法可用。
        org.springframework.test.util.ReflectionTestUtils.setField(notificationService, "baseMapper", notificationMapper);

        // 手动注入构造器依赖（Spy 不自动处理 InjectMocks）
        org.springframework.test.util.ReflectionTestUtils.setField(notificationService, "courseEnrollmentService", courseEnrollmentService);
        org.springframework.test.util.ReflectionTestUtils.setField(notificationService, "webSocketSessionManager", webSocketSessionManager);

        // WebSocket push 默认无操作
        lenient().doNothing().when(webSocketSessionManager).sendToUser(anyLong(), anyMap());

        // saveBatch 需要真实 MyBatis 代理；Spy 拦截返回默认值（不影响测试断言）
        lenient().doReturn(true).when(notificationService).saveBatch(any());
    }

    @Test
    @DisplayName("发送单条通知 - 调用 sendToUser")
    void testSendNotification() {
        clearInvocations(webSocketSessionManager);
        notificationService.sendNotification(100L, "标题", "内容", "A", "T", "LOW", "/u", null, 1L, "T");
        verify(webSocketSessionManager).sendToUser(eq(100L), anyMap());
    }

    @Test
    @DisplayName("批量发送 - 空列表安全")
    void testBatchSend_empty() {
        notificationService.batchSendNotifications(List.of(), "t", "c", "C", "T", "L", "/u", null);
        // 空列表应直接返回：不触发批量落库，也不做任何 WebSocket 推送
        verify(notificationService, never()).saveBatch(any());
        verify(webSocketSessionManager, never()).sendToUser(anyLong(), anyMap());
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
    @DisplayName("全标已读 - 构建更新链并执行 update")
    void testMarkAllAsRead() {
        // lambdaUpdate 链需要 MyBatis-Plus 实体缓存，无法在纯 Mockito 环境真实执行；
        // 用 mock 的 LambdaUpdateChainWrapper 验证条件与更新动作被正确组装并执行。
        @SuppressWarnings("unchecked")
        LambdaUpdateChainWrapper<Notification> chain = mock(LambdaUpdateChainWrapper.class);
        doReturn(chain).when(notificationService).lambdaUpdate();
        when(chain.eq(any(), any())).thenReturn(chain);
        when(chain.set(any(), any())).thenReturn(chain);
        when(chain.update()).thenReturn(true);

        notificationService.markAllAsRead(100L);

        verify(chain).eq(any(), eq(100L));
        verify(chain).eq(any(), eq(false));
        verify(chain).set(any(), eq(true));
        verify(chain).update();
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
