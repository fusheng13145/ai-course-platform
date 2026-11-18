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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NotificationServiceImpl 覆盖率补测（I-04 第七批）。
 *
 * <p>覆盖批量发送 null、实时推送可空字段与推送异常、已读/删除成功路径、
 * 列表 XSS 转义空值、事件驱动各事件缺字段与 @提及、分类未读分组。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplCoverageTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private CourseEnrollmentService courseEnrollmentService;
    @Mock private WebSocketSessionManager webSocketSessionManager;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new NotificationServiceImpl(courseEnrollmentService, webSocketSessionManager));
        ReflectionTestUtils.setField(service, "baseMapper", notificationMapper);
        lenient().doReturn(true).when(service).saveBatch(any());
        lenient().doNothing().when(webSocketSessionManager).sendToUser(anyLong(), anyMap());
    }

    private Notification notification(Long id, Long userId, String title, String content, String category) {
        Notification n = new Notification();
        n.setId(id);
        n.setUserId(userId);
        n.setTitle(title);
        n.setContent(content);
        n.setCategory(category);
        n.setIsRead(false);
        return n;
    }

    private CourseEnrollment enrollment(Long studentId) {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(studentId);
        return e;
    }

    // ===================== 发送与实时推送 =====================

    @Test
    @DisplayName("批量发送：userIds 为 null 时直接返回")
    void batchSend_nullList() {
        service.batchSendNotifications(null, "t", "c", "C", "T", "L", "/u", null);

        verify(service, never()).saveBatch(any());
        verify(webSocketSessionManager, never()).sendToUser(anyLong(), anyMap());
    }

    @Test
    @DisplayName("单条发送：userId 为 null 时不推送但仍落库")
    void sendNotification_nullUserIdNoPush() {
        service.sendNotification(null, "t", "c", "C", "T", "L", "/u", null, null, null);

        verify(webSocketSessionManager, never()).sendToUser(anyLong(), anyMap());
    }

    @Test
    @DisplayName("单条发送：标题/正文/跳转/关联均为 null 且推送异常被吞掉")
    void sendNotification_nullFieldsAndPushFailure() {
        doThrow(new RuntimeException("ws down")).when(webSocketSessionManager)
                .sendToUser(anyLong(), anyMap());

        assertDoesNotThrow(() -> service.sendNotification(
                100L, null, null, null, null, null, null, null, null, null));

        verify(webSocketSessionManager).sendToUser(eq(100L), anyMap());
    }

    // ===================== 已读与删除 =====================

    @Test
    @DisplayName("标记已读：归属者本人更新已读与读取时间")
    void markAsRead_success() {
        Notification n = notification(1L, 100L, "t", "c", "A");
        when(notificationMapper.selectById(1L)).thenReturn(n);

        service.markAsRead(1L, 100L);

        assertTrue(n.getIsRead());
        assertNotNull(n.getReadAt());
    }

    @Test
    @DisplayName("删除通知：归属者本人删除；不存在时抛异常")
    void deleteNotification_successAndNotFound() {
        Notification n = notification(2L, 100L, "t", "c", "A");
        when(notificationMapper.selectById(2L)).thenReturn(n);
        when(notificationMapper.selectById(99L)).thenReturn(null);

        service.deleteNotification(2L, 100L);
        assertThrows(BusinessException.class, () -> service.deleteNotification(99L, 100L));

        verify(notificationMapper).deleteById(2L);
    }

    // ===================== 列表与转义 =====================

    @Test
    @DisplayName("用户通知列表：分类非空+已读筛选，标题/正文为 null 不转义")
    void getUserNotifications_filteredAndNullText() {
        Notification n = notification(3L, 100L, null, null, "A");
        Page<Notification> p = new Page<>(1, 10);
        p.setRecords(List.of(n));
        when(notificationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(p);

        Page<Notification> result = service.getUserNotifications(100L, "A", true, 1, 10);

        assertEquals(1, result.getRecords().size());
        assertNull(result.getRecords().get(0).getTitle());
        assertNull(result.getRecords().get(0).getContent());
    }

    @Test
    @DisplayName("用户通知列表：分类空串不加条件，records 为 null 时安全返回")
    void getUserNotifications_emptyCategoryAndNullRecords() {
        Page<Notification> p = new Page<>(1, 10);
        p.setRecords(null);
        when(notificationMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(p);

        Page<Notification> result = service.getUserNotifications(100L, "", false, 1, 10);

        assertNull(result.getRecords());
    }

    @Test
    @DisplayName("课程通知：records 为 null 时安全返回")
    void getCourseNotifications_nullRecords() {
        Page<Notification> p = new Page<>(1, 10);
        p.setRecords(null);
        when(notificationMapper.selectCourseNotifications(any(Page.class), eq(100L), eq(1L))).thenReturn(p);

        Page<Notification> result = service.getCourseNotifications(100L, 1L, 1, 10);

        assertNull(result.getRecords());
    }

    // ===================== 事件驱动：任务/考试 =====================

    @Test
    @DisplayName("任务创建：courseId 缺失直接返回")
    void taskCreated_missingCourseId() {
        service.onEvent("TASK_CREATED", Map.of());

        verify(courseEnrollmentService, never()).list(any(Wrapper.class));
    }

    @Test
    @DisplayName("任务创建：标题/任务ID 缺失取默认，选课为 null/空均返回")
    void taskCreated_defaultFieldsAndEmptyEnrollments() {
        when(courseEnrollmentService.list(any(Wrapper.class))).thenReturn(null, List.of());

        service.onEvent("TASK_CREATED", Map.of("courseId", 10L));
        service.onEvent("TASK_CREATED", Map.of("courseId", 10L));

        verify(service, never()).saveBatch(any());
    }

    @Test
    @DisplayName("考试发布：courseId 缺失直接返回；标题/考试ID 缺失取默认")
    void examPublished_missingAndDefaultFields() {
        service.onEvent("EXAM_PUBLISHED", Map.of());

        when(courseEnrollmentService.list(any(Wrapper.class))).thenReturn(List.of());

        service.onEvent("EXAM_PUBLISHED", Map.of("courseId", 10L));

        verify(service, never()).saveBatch(any());
    }

    // ===================== 事件驱动：批阅/讨论 =====================

    @Test
    @DisplayName("作业批阅：studentId 缺失直接返回；标题/提交ID 缺失取默认")
    void submissionGraded_missingAndDefaultFields() {
        service.onEvent("SUBMISSION_GRADED", Map.of());
        service.onEvent("SUBMISSION_GRADED", Map.of("studentId", 100L));

        verify(webSocketSessionManager, times(1)).sendToUser(anyLong(), anyMap());
    }

    @Test
    @DisplayName("讨论回复：recipientId 缺失直接返回；标题/主题/帖子ID 缺失取默认")
    void discussionReplied_missingAndDefaultFields() {
        service.onEvent("DISCUSSION_REPLIED", Map.of());
        service.onEvent("DISCUSSION_REPLIED", Map.of("recipientId", 100L));

        verify(webSocketSessionManager, times(1)).sendToUser(anyLong(), anyMap());
    }

    @Test
    @DisplayName("讨论@提及：完整事件推送；recipientId 缺失直接返回")
    void discussionMentioned_fullAndMissing() {
        service.onEvent("DISCUSSION_MENTIONED", Map.of());
        service.onEvent("DISCUSSION_MENTIONED", Map.of(
                "recipientId", 100L, "topicId", 1L, "topicTitle", "话题", "postId", 9L));

        verify(webSocketSessionManager, times(1)).sendToUser(eq(100L), anyMap());
    }

    // ===================== 分类未读分组 =====================

    @Test
    @DisplayName("未读分类分组：category 为 null 归入 OTHER")
    void unreadGroupedByCategory() {
        when(notificationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                notification(1L, 100L, "t", "c", null),
                notification(2L, 100L, "t", "c", "ASSIGNMENT"),
                notification(3L, 100L, "t", "c", "ASSIGNMENT")));

        Map<String, Long> grouped = service.getUnreadGroupedByCategory(100L);

        assertEquals(1L, grouped.get("OTHER"));
        assertEquals(2L, grouped.get("ASSIGNMENT"));
    }
}
