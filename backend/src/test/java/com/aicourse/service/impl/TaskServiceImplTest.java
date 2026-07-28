package com.aicourse.service.impl;

import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.TaskMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 任务服务单元测试
 * 规避原则：不对 mapper 做 verify（MyBatis-Plus 3.5.7 泛型重载冲突），
 * 改用返回值断言 + 对 {@link NotificationService} 等纯接口 mock 的 verify。
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock private TaskMapper taskMapper;
    @Mock private SubmissionService submissionService;
    @Mock private CourseEnrollmentService courseEnrollmentService;
    @Mock private NotificationService notificationService;
    @InjectMocks private TaskServiceImpl taskService;

    private Task sampleTask;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(taskService, "baseMapper", taskMapper);

        sampleTask = new Task();
        sampleTask.setId(1L);
        sampleTask.setCourseId(10L);
        sampleTask.setTitle("第一次作业");
        sampleTask.setTaskType("ASSIGNMENT");
        sampleTask.setIsPublished(false);
        sampleTask.setStatus("DRAFT");
        sampleTask.setMaxScore(BigDecimal.valueOf(100));
        sampleTask.setDueDate(LocalDateTime.now().plusDays(7));
        sampleTask.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("发布任务 - 成功发布并触发通知")
    void testPublishTask_success() {
        when(taskMapper.selectById(1L)).thenReturn(sampleTask);
        doThrow(new RuntimeException("通知不可用")).when(notificationService).onEvent(anyString(), anyMap());

        Task result = taskService.publishTask(1L);

        assertTrue(result.getIsPublished());
        assertEquals("PUBLISHED", result.getStatus());
        // 验证通知事件被触发（即使失败也不阻塞发布）
        verify(notificationService).onEvent(eq("TASK_CREATED"), anyMap());
    }

    @Test
    @DisplayName("发布任务 - 不存在抛异常")
    void testPublishTask_notFound() {
        when(taskMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> taskService.publishTask(99L));
    }

    @Test
    @DisplayName("发布任务 - 已发布抛异常")
    void testPublishTask_alreadyPublished() {
        sampleTask.setIsPublished(true);
        when(taskMapper.selectById(1L)).thenReturn(sampleTask);
        assertThrows(BusinessException.class, () -> taskService.publishTask(1L));
    }

    @Test
    @DisplayName("延长截止日期 - 成功")
    void testExtendDeadline_success() {
        sampleTask.setDueDate(LocalDateTime.now().plusDays(7));
        when(taskMapper.selectById(1L)).thenReturn(sampleTask);

        Task result = taskService.extendDeadline(1L, LocalDateTime.now().plusDays(14), "需更多时间");

        assertNotNull(result);
    }

    @Test
    @DisplayName("延长截止日期 - 提前抛异常")
    void testExtendDeadline_earlierDate() {
        sampleTask.setDueDate(LocalDateTime.now().plusDays(14));
        when(taskMapper.selectById(1L)).thenReturn(sampleTask);
        assertThrows(BusinessException.class,
                () -> taskService.extendDeadline(1L, LocalDateTime.now().plusDays(7), "提前"));
    }

    @Test
    @DisplayName("复制任务 - 成功创建草稿")
    void testCopyTask_success() {
        when(taskMapper.selectById(1L)).thenReturn(sampleTask);

        Task copy = taskService.copyTask(1L, "作业副本");

        assertFalse(copy.getIsPublished());
        assertEquals("DRAFT", copy.getStatus());
    }

    @Test
    @DisplayName("完成率统计")
    void testGetTaskCompletionStats() {
        when(taskMapper.selectById(1L)).thenReturn(sampleTask);
        // count() 5次 + list() 2次 + enrollment.count() 1次 = 8次
        when(submissionService.count(any())).thenReturn(10L, 0L, 0L, 0L, 0L);
        when(submissionService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
        when(courseEnrollmentService.count(any())).thenReturn(10L);

        Map<String, Object> s = taskService.getTaskCompletionStats(1L);
        assertEquals(10L, s.get("totalSubmissions"));
    }

    @Test
    @DisplayName("待批阅列表")
    void testGetPendingGradingTasks() {
        sampleTask.setCreatorId(5L);
        sampleTask.setIsPublished(true);
        when(taskMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(sampleTask));
        when(submissionService.count(any())).thenReturn(2L);

        assertEquals(1, taskService.getPendingGradingTasks(5L).size());
    }
}
