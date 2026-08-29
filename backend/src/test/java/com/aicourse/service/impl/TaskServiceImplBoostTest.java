package com.aicourse.service.impl;

import com.aicourse.constant.SubmissionStatus;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.TaskMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.SubmissionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 纯 Mockito 单元测试：补充覆盖 TaskServiceImpl 的发布/更新/延期/复制/待批阅/完成率统计分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceImplBoostTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private SubmissionService submissionService;
    @Mock
    private CourseEnrollmentService courseEnrollmentService;
    @Mock
    private NotificationService notificationService;

    private TaskServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Task.class);
        service = new TaskServiceImpl(submissionService, courseEnrollmentService, notificationService);
        ReflectionTestUtils.setField(service, "baseMapper", taskMapper);
    }

    // ===== publishTask =====

    @Test
    @DisplayName("publishTask 任务不存在抛异常")
    void publish_null() {
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.publishTask(1L));
    }

    @Test
    @DisplayName("publishTask 已发布抛异常")
    void publish_already() {
        Task t = new Task();
        t.setId(1L);
        t.setIsPublished(true);
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(t);
        assertThrows(BusinessException.class, () -> service.publishTask(1L));
    }

    @Test
    @DisplayName("publishTask 成功并推送通知")
    void publish_ok() {
        Task t = new Task();
        t.setId(1L);
        t.setCourseId(5L);
        t.setIsPublished(false);
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(t);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        Task r = service.publishTask(1L);
        assertTrue(r.getIsPublished());
        verify(notificationService).onEvent(eq("TASK_CREATED"), any());
    }

    @Test
    @DisplayName("publishTask 通知失败被吞掉")
    void publish_notifyFails() {
        Task t = new Task();
        t.setId(1L);
        t.setCourseId(5L);
        t.setIsPublished(false);
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(t);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        doThrow(new RuntimeException("boom")).when(notificationService).onEvent(anyString(), any());
        Task r = service.publishTask(1L);
        assertTrue(r.getIsPublished());
    }

    // ===== updateTaskById =====

    @Test
    @DisplayName("updateTaskById 更新并返回")
    void updateById_ok() {
        Task updated = new Task();
        updated.setId(1L);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(updated);
        Task r = service.updateTaskById(1L, new Task());
        assertEquals(1L, r.getId());
    }

    // ===== extendDeadline =====

    @Test
    @DisplayName("extendDeadline 任务不存在抛异常")
    void extend_null() {
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.extendDeadline(1L, LocalDateTime.now(), "r"));
    }

    @Test
    @DisplayName("extendDeadline 新截止日期为 null 抛异常")
    void extend_dateNull() {
        Task t = new Task();
        t.setId(1L);
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(t);
        assertThrows(BusinessException.class,
                () -> service.extendDeadline(1L, null, "r"));
    }

    @Test
    @DisplayName("extendDeadline 未晚于当前截止日期抛异常")
    void extend_notLater() {
        Task t = new Task();
        t.setId(1L);
        t.setDueDate(LocalDateTime.now().plusDays(1)); // 截止日期在未来，now 不晚于它 -> 应抛异常
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(t);
        assertThrows(BusinessException.class,
                () -> service.extendDeadline(1L, LocalDateTime.now(), "r"));
    }

    @Test
    @DisplayName("extendDeadline 成功并记录原因")
    void extend_ok() {
        Task t = new Task();
        t.setId(1L);
        t.setDueDate(LocalDateTime.now().minusDays(2));
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(t);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        Task r = service.extendDeadline(1L, LocalDateTime.now(), "需要更多时间");
        assertNotNull(r.getDueDate());
        assertTrue(r.getRequirements().contains("需要更多时间"));
    }

    @Test
    @DisplayName("extendDeadline 原因为 null 用未说明")
    void extend_reasonNull() {
        Task t = new Task();
        t.setId(1L);
        t.setDueDate(LocalDateTime.now().minusDays(2));
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(t);
        when(taskMapper.updateById(any(Task.class))).thenReturn(1);
        Task r = service.extendDeadline(1L, LocalDateTime.now(), null);
        assertTrue(r.getRequirements().contains("未说明"));
    }

    // ===== copyTask =====

    @Test
    @DisplayName("copyTask 源任务不存在抛异常")
    void copy_null() {
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.copyTask(1L, "new"));
    }

    @Test
    @DisplayName("copyTask 新标题为 null 使用副本名")
    void copy_titleNull() {
        Task src = new Task();
        src.setId(1L);
        src.setTitle("原任务");
        src.setCourseId(5L);
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(src);
        when(taskMapper.insert(any(Task.class))).thenReturn(1);
        Task r = service.copyTask(1L, null);
        assertEquals("原任务(副本)", r.getTitle());
    }

    @Test
    @DisplayName("copyTask 成功复制")
    void copy_ok() {
        Task src = new Task();
        src.setId(1L);
        src.setTitle("原任务");
        src.setCourseId(5L);
        src.setChapterId(3L);
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(src);
        when(taskMapper.insert(any(Task.class))).thenReturn(1);
        Task r = service.copyTask(1L, "副本");
        assertEquals("副本", r.getTitle());
        assertEquals(3L, r.getChapterId());
    }

    // ===== getPendingGradingTasks =====

    @Test
    @DisplayName("getPendingGradingTasks 有待批阅")
    void pending_ok() {
        Task t = new Task();
        t.setId(1L);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(t));
        when(submissionService.count(any())).thenReturn(1L);
        assertEquals(1, service.getPendingGradingTasks(9L).size());
    }

    @Test
    @DisplayName("getPendingGradingTasks 无待批阅")
    void pending_none() {
        Task t = new Task();
        t.setId(1L);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(t));
        when(submissionService.count(any())).thenReturn(0L);
        assertTrue(service.getPendingGradingTasks(9L).isEmpty());
    }

    // ===== getTaskCompletionStats =====

    @Test
    @DisplayName("getTaskCompletionStats 任务不存在抛异常")
    void stats_null() {
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getTaskCompletionStats(1L));
    }

    @Test
    @DisplayName("getTaskCompletionStats 无已批改且课程为空")
    void stats_gradedZero() {
        Task t = new Task();
        t.setId(1L);
        t.setCourseId(null);
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(t);
        when(submissionService.count(any())).thenReturn(0L);
        Map<String, Object> r = service.getTaskCompletionStats(1L);
        assertEquals(0.0, r.get("averageScore"));
        assertEquals(0.0, r.get("completionRate"));
    }

    @Test
    @DisplayName("getTaskCompletionStats 含已批改与完成率")
    void stats_full() {
        Task t = new Task();
        t.setId(1L);
        t.setCourseId(5L);
        when(taskMapper.selectById(any(Serializable.class))).thenReturn(t);
        when(submissionService.count(any())).thenReturn(2L);
        Submission s1 = new Submission();
        s1.setStudentId(1L);
        s1.setScore(new java.math.BigDecimal("80"));
        Submission s2 = new Submission();
        s2.setStudentId(2L);
        s2.setScore(new java.math.BigDecimal("90"));
        when(submissionService.list(any(Wrapper.class))).thenReturn(List.of(s1, s2));
        when(courseEnrollmentService.count(any())).thenReturn(4L);
        Map<String, Object> r = service.getTaskCompletionStats(1L);
        assertEquals(85.0, r.get("averageScore"));
        assertEquals(50.0, r.get("completionRate"));
    }
}
