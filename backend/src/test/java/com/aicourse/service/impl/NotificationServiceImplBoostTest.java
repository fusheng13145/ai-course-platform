package com.aicourse.service.impl;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Notification;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.NotificationMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.websocket.WebSocketSessionManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
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

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NotificationServiceImpl 指令覆盖率强化测试。
 * 覆盖发送/批量/已读/查询/删除/事件驱动(onEvent)全部分支，以及私有
 * buildNotification / pushRealtime / 各 handleXxx 事件处理。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplBoostTest {

    @Mock
    private CourseEnrollmentService courseEnrollmentService;
    @Mock
    private WebSocketSessionManager webSocketSessionManager;
    @Mock
    private NotificationMapper notificationMapper;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new NotificationServiceImpl(courseEnrollmentService, webSocketSessionManager));
        ReflectionTestUtils.setField(service, "baseMapper", notificationMapper);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), Notification.class);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), CourseEnrollment.class);
        // saveBatch 在 MyBatis-Plus 3.5.x 中会走 SqlSessionFactory（需要真实 SqlSession），
        // 纯 Mockito 单测无法提供；用 spy 拦截后直接委派 baseMapper.insert，保持单测无外部依赖。
        doAnswer(inv -> {
            Collection<Notification> list = inv.getArgument(0);
            if (list != null) {
                for (Notification n : list) notificationMapper.insert(n);
            }
            return true;
        }).when(service).saveBatch(anyCollection());
    }

    // ==================== 私有 buildNotification ====================

    @Test
    @DisplayName("buildNotification: 构建未读通知实体")
    void buildNotification() throws Exception {
        Method m = NotificationServiceImpl.class.getDeclaredMethod("buildNotification",
                Long.class, String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, Long.class, String.class);
        m.setAccessible(true);
        Notification n = (Notification) m.invoke(service, 1L, "title", "content", "CAT", "TYPE",
                "HIGH", "/url", "data", 9L, "REL");
        assertEquals(1L, n.getUserId());
        assertEquals("title", n.getTitle());
        assertEquals("content", n.getContent());
        assertEquals("CAT", n.getCategory());
        assertFalse(n.getIsRead());
        assertNotNull(n.getCreatedAt());
    }

    // ==================== 私有 pushRealtime ====================

    @Test
    @DisplayName("pushRealtime: null/无userId 早期返回 + 正常推送")
    void pushRealtime_branches() throws Exception {
        Method m = NotificationServiceImpl.class.getDeclaredMethod("pushRealtime", Notification.class);
        m.setAccessible(true);

        // null 通知 -> 早期返回
        m.invoke(service, (Notification) null);
        // 无 userId -> 早期返回
        m.invoke(service, new Notification());
        // 正常推送
        Notification n = new Notification();
        n.setId(5L);
        n.setUserId(7L);
        n.setTitle("<b>hi</b>");
        n.setContent("<script>");
        n.setCategory("CAT");
        n.setType("TYPE");
        n.setPriority("HIGH");
        n.setActionUrl("/a");
        n.setRelatedId(9L);
        n.setRelatedType("REL");
        n.setCreatedAt(java.time.LocalDateTime.now());
        m.invoke(service, n);
        verify(webSocketSessionManager).sendToUser(eq(7L), anyMap());
    }

    @Test
    @DisplayName("pushRealtime: 推送异常被捕获不抛出")
    void pushRealtime_exceptionCaught() throws Exception {
        Method m = NotificationServiceImpl.class.getDeclaredMethod("pushRealtime", Notification.class);
        m.setAccessible(true);
        doThrow(new RuntimeException("ws down")).when(webSocketSessionManager).sendToUser(anyLong(), anyMap());
        Notification n = new Notification();
        n.setId(5L);
        n.setUserId(7L);
        n.setTitle("t");
        n.setContent("c");
        assertDoesNotThrow(() -> m.invoke(service, n));
    }

    // ==================== sendNotification ====================

    @Test
    @DisplayName("sendNotification: 保存并实时推送")
    void sendNotification_success() {
        Notification r = service.sendNotification(7L, "标题", "内容", "CAT", "TYPE",
                "HIGH", "/url", "data", 9L, "REL");
        assertNotNull(r);
        assertFalse(r.getIsRead());
        verify(notificationMapper).insert(any(Notification.class));
        verify(webSocketSessionManager).sendToUser(eq(7L), anyMap());
    }

    // ==================== batchSendNotifications ====================

    @Test
    @DisplayName("batchSendNotifications: 空列表直接返回")
    void batchSendNotifications_empty() {
        service.batchSendNotifications(Collections.emptyList(), "t", "c", "CAT", "TYPE", "LOW", null, null);
        verify(notificationMapper, never()).insert(any(Notification.class));
        verify(webSocketSessionManager, never()).sendToUser(anyLong(), anyMap());
    }

    @Test
    @DisplayName("batchSendNotifications: 非空列表批量保存并推送")
    void batchSendNotifications_success() {
        service.batchSendNotifications(Arrays.asList(1L, 2L), "t", "c", "CAT", "TYPE", "LOW", null, null);
        verify(notificationMapper, times(2)).insert(any(Notification.class));
        verify(webSocketSessionManager, times(2)).sendToUser(anyLong(), anyMap());
    }

    // ==================== markAsRead ====================

    @Test
    @DisplayName("markAsRead: 通知不存在抛异常")
    void markAsRead_notFound() {
        when(notificationMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.markAsRead(1L, 7L));
        assertTrue(ex.getMessage().contains("通知不存在"));
    }

    @Test
    @DisplayName("markAsRead: 无权操作抛异常")
    void markAsRead_noPermission() {
        Notification n = new Notification();
        n.setId(1L);
        n.setUserId(8L);
        when(notificationMapper.selectById(1L)).thenReturn(n);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.markAsRead(1L, 7L));
        assertTrue(ex.getMessage().contains("无权操作"));
    }

    @Test
    @DisplayName("markAsRead: 成功标记已读")
    void markAsRead_success() {
        Notification n = new Notification();
        n.setId(1L);
        n.setUserId(7L);
        when(notificationMapper.selectById(1L)).thenReturn(n);
        service.markAsRead(1L, 7L);
        assertTrue(n.getIsRead());
        assertNotNull(n.getReadAt());
        verify(notificationMapper).updateById(any(Notification.class));
    }

    // ==================== markAllAsRead ====================

    @Test
    @DisplayName("markAllAsRead: 批量更新未读状态")
    void markAllAsRead_success() {
        assertDoesNotThrow(() -> service.markAllAsRead(7L));
    }

    // ==================== getUnreadCount ====================

    @Test
    @DisplayName("getUnreadCount: 返回未读数")
    void getUnreadCount_success() {
        when(notificationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        assertEquals(5L, service.getUnreadCount(7L));
    }

    // ==================== getUserNotifications ====================

    @Test
    @DisplayName("getUserNotifications: 无分类/已读筛选 + XSS 转义分支")
    void getUserNotifications_noFilter() {
        when(notificationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(inv -> {
                    Page<Notification> p = inv.getArgument(0);
                    Notification n1 = new Notification();
                    n1.setTitle("<b>title</b>");
                    n1.setContent("<script>");
                    Notification n2 = new Notification();
                    n2.setTitle(null);
                    n2.setContent(null);
                    p.setRecords(Arrays.asList(n1, n2));
                    p.setTotal(2);
                    return p;
                });
        Page<Notification> page = service.getUserNotifications(7L, null, null, 1, 10);
        assertEquals(2, page.getRecords().size());
        // XSS 转义后尖括号被替换
        assertTrue(page.getRecords().get(0).getTitle().contains("&lt;"));
        assertNull(page.getRecords().get(1).getTitle());
    }

    @Test
    @DisplayName("getUserNotifications: 带分类与已读筛选")
    void getUserNotifications_withFilter() {
        when(notificationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(inv -> {
                    Page<Notification> p = inv.getArgument(0);
                    p.setRecords(Collections.singletonList(new Notification()));
                    p.setTotal(1);
                    return p;
                });
        Page<Notification> page = service.getUserNotifications(7L, "CAT", true, 0, 5);
        assertEquals(1, page.getRecords().size());
    }

    // ==================== deleteNotification ====================

    @Test
    @DisplayName("deleteNotification: 通知不存在抛异常")
    void deleteNotification_notFound() {
        when(notificationMapper.selectById(1L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteNotification(1L, 7L));
        assertTrue(ex.getMessage().contains("通知不存在"));
    }

    @Test
    @DisplayName("deleteNotification: 无权删除抛异常")
    void deleteNotification_noPermission() {
        Notification n = new Notification();
        n.setId(1L);
        n.setUserId(8L);
        when(notificationMapper.selectById(1L)).thenReturn(n);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteNotification(1L, 7L));
        assertTrue(ex.getMessage().contains("无权删除"));
    }

    @Test
    @DisplayName("deleteNotification: 成功删除")
    void deleteNotification_success() {
        Notification n = new Notification();
        n.setId(1L);
        n.setUserId(7L);
        when(notificationMapper.selectById(1L)).thenReturn(n);
        service.deleteNotification(1L, 7L);
        verify(notificationMapper).deleteById(1L);
    }

    // ==================== getUnreadGroupedByCategory ====================

    @Test
    @DisplayName("getUnreadGroupedByCategory: 按分类分组统计（含 OTHER 兜底）")
    void getUnreadGroupedByCategory_mixed() {
        Notification a = new Notification();
        a.setCategory("ASSIGNMENT");
        Notification b = new Notification();
        b.setCategory(null); // -> OTHER
        when(notificationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(a, b));
        Map<String, Long> grouped = service.getUnreadGroupedByCategory(7L);
        assertEquals(1L, grouped.get("ASSIGNMENT"));
        assertEquals(1L, grouped.get("OTHER"));
    }

    @Test
    @DisplayName("getUnreadGroupedByCategory: 空列表返回空 Map")
    void getUnreadGroupedByCategory_empty() {
        when(notificationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(service.getUnreadGroupedByCategory(7L).isEmpty());
    }

    // ==================== onEvent 事件驱动 ====================

    @Test
    @DisplayName("onEvent: TASK_CREATED 正常创建通知")
    void onEvent_taskCreated() {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(7L);
        when(courseEnrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(e));
        Map<String, Object> data = new HashMap<>();
        data.put("courseId", 100L);
        data.put("taskTitle", "作业1");
        data.put("taskId", 200L);
        service.onEvent("TASK_CREATED", data);
        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    @DisplayName("onEvent: TASK_CREATED courseId 为空直接返回")
    void onEvent_taskCreated_noCourse() {
        Map<String, Object> data = new HashMap<>();
        data.put("courseId", null);
        service.onEvent("TASK_CREATED", data);
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    @DisplayName("onEvent: TASK_CREATED 无选课学生直接返回")
    void onEvent_taskCreated_noEnrollments() {
        when(courseEnrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        Map<String, Object> data = new HashMap<>();
        data.put("courseId", 100L);
        service.onEvent("TASK_CREATED", data);
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    @DisplayName("onEvent: EXAM_PUBLISHED 正常创建通知")
    void onEvent_examPublished() {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(7L);
        when(courseEnrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(e));
        Map<String, Object> data = new HashMap<>();
        data.put("courseId", 100L);
        data.put("examTitle", "期中");
        data.put("examId", 300L);
        service.onEvent("EXAM_PUBLISHED", data);
        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    @DisplayName("onEvent: EXAM_PUBLISHED courseId 为空返回")
    void onEvent_examPublished_noCourse() {
        Map<String, Object> data = new HashMap<>();
        data.put("courseId", null);
        service.onEvent("EXAM_PUBLISHED", data);
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    @DisplayName("onEvent: SUBMISSION_GRADED 给学生发送成绩通知")
    void onEvent_submissionGraded() {
        Map<String, Object> data = new HashMap<>();
        data.put("studentId", 7L);
        data.put("taskTitle", "作业1");
        data.put("submissionId", 200L);
        service.onEvent("SUBMISSION_GRADED", data);
        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    @DisplayName("onEvent: SUBMISSION_GRADED studentId 为空返回")
    void onEvent_submissionGraded_noStudent() {
        Map<String, Object> data = new HashMap<>();
        data.put("studentId", null);
        service.onEvent("SUBMISSION_GRADED", data);
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    @DisplayName("onEvent: DISCUSSION_REPLIED 给被回复者发通知")
    void onEvent_discussionReplied() {
        Map<String, Object> data = new HashMap<>();
        data.put("recipientId", 7L);
        data.put("topicId", 1L);
        data.put("topicTitle", "话题");
        data.put("postId", 2L);
        service.onEvent("DISCUSSION_REPLIED", data);
        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    @DisplayName("onEvent: DISCUSSION_REPLIED recipientId 为空返回")
    void onEvent_discussionReplied_noRecipient() {
        Map<String, Object> data = new HashMap<>();
        data.put("recipientId", null);
        service.onEvent("DISCUSSION_REPLIED", data);
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    @DisplayName("onEvent: DISCUSSION_MENTIONED 给被@者发通知")
    void onEvent_discussionMentioned() {
        Map<String, Object> data = new HashMap<>();
        data.put("recipientId", 7L);
        data.put("topicId", 1L);
        data.put("topicTitle", "话题");
        data.put("postId", 2L);
        service.onEvent("DISCUSSION_MENTIONED", data);
        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    @DisplayName("onEvent: DISCUSSION_MENTIONED recipientId 为空返回")
    void onEvent_discussionMentioned_noRecipient() {
        Map<String, Object> data = new HashMap<>();
        data.put("recipientId", null);
        service.onEvent("DISCUSSION_MENTIONED", data);
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    @DisplayName("onEvent: 未知事件类型不处理")
    void onEvent_unknown() {
        service.onEvent("WHAT_EVER", new HashMap<>());
        verify(notificationMapper, never()).insert(any(Notification.class));
    }
}
