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
import com.aicourse.vo.TaskCompletionStatsVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 任务服务单元测试
 * 对 mapper 的 verify 一律使用带类型的 ArgumentCaptor（{@code any()} 会与 MP 3.5.7 的
 * 集合重载产生歧义），捕获后断言查询条件与落库实体的字段映射。
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
        // LambdaQueryWrapper 的条件在 getSqlSegment() 时才落列名，需先注册实体元数据，
        // 否则无法断言「统计口径真的按 taskId/状态过滤」。
        for (Class<?> e : new Class<?>[]{Task.class, Submission.class, CourseEnrollment.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), e);
        }

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

    /** 触发 wrapper 构建 SQL 片段，返回其条件参数值（用于断言过滤口径而非放任 any()）。 */
    private static Collection<Object> wrapperParams(Wrapper<?> wrapper) {
        assertNotNull(wrapper, "查询条件 wrapper 不得为 null");
        AbstractWrapper<?, ?, ?> aw = (AbstractWrapper<?, ?, ?>) wrapper;
        aw.getSqlSegment();
        return aw.getParamNameValuePairs().values();
    }

    // ===================== 发布任务 =====================

    @Test
    @DisplayName("发布任务 - 成功发布并触发通知，事件载荷带齐课程/任务标识")
    void testPublishTask_success() {
        when(taskMapper.selectById(1L)).thenReturn(sampleTask);
        doThrow(new RuntimeException("通知不可用")).when(notificationService).onEvent(anyString(), anyMap());

        Task result = taskService.publishTask(1L);

        assertTrue(result.getIsPublished());
        assertEquals("PUBLISHED", result.getStatus());
        // 验证通知事件被触发（即使失败也不阻塞发布）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> eventCap = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).onEvent(eq("TASK_CREATED"), eventCap.capture());
        // 事件名之外的载荷是通知能否落到正确课程/任务的唯一依据：
        // courseId 丢失 -> 铃铛不知道该通知哪些选课学生；taskId/taskTitle 丢失 -> 点击无法跳转、文案为空
        Map<String, Object> payload = eventCap.getValue();
        assertEquals(10L, payload.get("courseId"), "事件载荷必须带课程ID，实际=" + payload);
        assertEquals(1L, payload.get("taskId"), "事件载荷必须带任务ID，实际=" + payload);
        assertEquals("第一次作业", payload.get("taskTitle"), "事件载荷必须带任务标题，实际=" + payload);
    }

    @Test
    @DisplayName("发布任务 - 不存在抛异常")
    void testPublishTask_notFound() {
        when(taskMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> taskService.publishTask(99L));
        verify(notificationService, never()).onEvent(anyString(), anyMap());
    }

    @Test
    @DisplayName("发布任务 - 已发布抛异常")
    void testPublishTask_alreadyPublished() {
        sampleTask.setIsPublished(true);
        when(taskMapper.selectById(1L)).thenReturn(sampleTask);
        assertThrows(BusinessException.class, () -> taskService.publishTask(1L));
        verify(notificationService, never()).onEvent(anyString(), anyMap());
    }

    // ===================== 延长截止日期 =====================

    @Test
    @DisplayName("延长截止日期 - 成功")
    void testExtendDeadline_success() {
        sampleTask.setDueDate(LocalDateTime.now().plusDays(7));
        sampleTask.setRequirements("原要求");
        when(taskMapper.selectById(1L)).thenReturn(sampleTask);

        LocalDateTime newDue = LocalDateTime.now().plusDays(14);
        Task result = taskService.extendDeadline(1L, newDue, "需更多时间");

        assertNotNull(result);
        // 真实业务断言：截止日期被改写为传入的新值（而非任意时间）
        assertEquals(newDue, result.getDueDate(), "延长后 dueDate 必须等于新截止日期");
        // 延长原因被追加记录进 requirements：保留原文 + 追加带原因与新日期的说明
        assertTrue(result.getRequirements().startsWith("原要求"),
                "原要求不应丢失，实际=" + result.getRequirements());
        assertTrue(result.getRequirements().contains("需更多时间"),
                "应记录延长原因，实际=" + result.getRequirements());
        assertTrue(result.getRequirements().contains("延长截止日期"),
                "应含延长标记，实际=" + result.getRequirements());
        // 新日期也要写进说明文案，否则审计时无法核对到底延到了哪天
        assertTrue(result.getRequirements().contains(newDue.toString()),
                "说明文案应含新截止日期，实际=" + result.getRequirements());
        // 变更必须真正落库（返回对象被改写不等于持久化成功）
        ArgumentCaptor<Task> persistCap = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(persistCap.capture());
        assertEquals(newDue, persistCap.getValue().getDueDate(), "落库实体必须带新截止日期");
    }

    @Test
    @DisplayName("延长截止日期 - 提前抛异常")
    void testExtendDeadline_earlierDate() {
        sampleTask.setDueDate(LocalDateTime.now().plusDays(14));
        when(taskMapper.selectById(1L)).thenReturn(sampleTask);
        assertThrows(BusinessException.class,
                () -> taskService.extendDeadline(1L, LocalDateTime.now().plusDays(7), "提前"));
        verify(taskMapper, never()).updateById(any(Task.class));
    }

    // ===================== 复制任务 =====================

    @Test
    @DisplayName("复制任务 - 成功创建草稿，归属字段随源任务透传且不带源主键/截止日期")
    void testCopyTask_success() {
        sampleTask.setChapterId(3L);
        sampleTask.setCreatorId(5L);
        sampleTask.setDescription("作业说明");
        sampleTask.setRequirements("提交 PDF");
        sampleTask.setSubmissionFormat("FILE");
        sampleTask.setAttachmentUrl("oss://template.pdf");
        when(taskMapper.selectById(1L)).thenReturn(sampleTask);

        Task copy = taskService.copyTask(1L, "作业副本");

        assertFalse(copy.getIsPublished());
        assertEquals("DRAFT", copy.getStatus());

        ArgumentCaptor<Task> cap = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(cap.capture());
        Task saved = cap.getValue();
        assertSame(copy, saved, "返回的必须是被持久化的那条副本");
        // 副本必须是新记录：沿用源主键会让 insert 变成 update，直接改写源任务
        assertNull(saved.getId(), "副本不得携带源任务主键");
        assertEquals("作业副本", saved.getTitle());
        // 归属类字段必须从源任务透传：courseId/chapterId 丢失会让副本在课程与章节视图里同时消失
        assertEquals(10L, saved.getCourseId());
        assertEquals(3L, saved.getChapterId(), "P0-7 回归：复制任务必须保留章节归属");
        assertEquals(5L, saved.getCreatorId());
        assertEquals("ASSIGNMENT", saved.getTaskType());
        assertEquals(0, new BigDecimal("100").compareTo(saved.getMaxScore()));
        // 内容类字段同样要复制，否则教师得重新写一遍
        assertEquals("作业说明", saved.getDescription());
        assertEquals("提交 PDF", saved.getRequirements());
        assertEquals("FILE", saved.getSubmissionFormat());
        assertEquals("oss://template.pdf", saved.getAttachmentUrl());
        // 截止日期必须留空待教师手动设置：照抄源日期会让副本一出生就截止
        assertNull(saved.getDueDate(), "副本不应继承源任务的截止日期");
    }

    // ===================== 统计与列表 =====================

    @Test
    @DisplayName("完成率统计 - 五个口径各自按 taskId/状态过滤，出参字段按位映射不错位")
    void testGetTaskCompletionStats() {
        when(taskMapper.selectById(1L)).thenReturn(sampleTask);
        // count() 5 次，按源码顺序：总数 / 待批阅 / 已批改 / 已退回 / 迟交
        when(submissionService.count(any())).thenReturn(10L, 2L, 3L, 1L, 4L);
        Submission graded1 = gradedSubmission(11L, "100");
        Submission graded2 = gradedSubmission(12L, "80");
        Submission submitted1 = statusSubmission(11L, SubmissionStatus.SUBMITTED);
        Submission submitted2 = statusSubmission(11L, SubmissionStatus.GRADED);
        Submission submitted3 = statusSubmission(12L, SubmissionStatus.RETURNED);
        // list() 2 次：先「已批改且有分数」用于算均分，再「已进入任一状态」用于去重统计提交人数
        when(submissionService.list(any(Wrapper.class)))
                .thenReturn(List.of(graded1, graded2), List.of(submitted1, submitted2, submitted3));
        when(courseEnrollmentService.count(any())).thenReturn(8L);

        TaskCompletionStatsVO s = taskService.getTaskCompletionStats(1L);

        // 出参逐字段断言：构造器 11 个同类型参数，顺序写错（如 gradedCount 与 returnedCount 互换）
        // 只断言 totalSubmissions 是发现不了的
        assertEquals(1L, s.getTaskId());
        assertEquals("第一次作业", s.getTaskTitle());
        assertEquals(10L, s.getTotalSubmissions());
        assertEquals(2L, s.getPendingReview());
        assertEquals(3L, s.getGradedCount());
        assertEquals(1L, s.getReturnedCount());
        assertEquals(4L, s.getLateCount());
        assertEquals(90.0, s.getAverageScore(), "已批改均分应为 (100+80)/2");
        assertEquals(25.0, s.getCompletionRate(), "完成率应为 去重提交人数2 / 选课人数8");
        assertEquals(sampleTask.getDueDate(), s.getDueDate());
        assertEquals("DRAFT", s.getStatus());

        // 五个 count 口径必须各自限定在当前任务上（漏掉 taskId 就是把全系统的提交算进本课作业）
        ArgumentCaptor<Wrapper> countCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(submissionService, times(5)).count(countCap.capture());
        List<Wrapper> counts = countCap.getAllValues();
        for (int i = 0; i < counts.size(); i++) {
            assertTrue(wrapperParams(counts.get(i)).contains(1L),
                    "第 " + (i + 1) + " 个统计口径必须带 taskId，实际参数=" + wrapperParams(counts.get(i)));
        }
        // 口径区分靠状态集合：待批阅含 SUBMITTED/RESUBMITTED，已批改只含 GRADED，已退回只含 RETURNED
        Collection<Object> pendingParams = wrapperParams(counts.get(1));
        assertTrue(pendingParams.contains(SubmissionStatus.SUBMITTED) && pendingParams.contains(SubmissionStatus.RESUBMITTED),
                "待批阅口径必须同时覆盖 SUBMITTED 与 RESUBMITTED，实际=" + pendingParams);
        assertTrue(wrapperParams(counts.get(2)).contains(SubmissionStatus.GRADED), "已批改口径应为 GRADED");
        assertFalse(wrapperParams(counts.get(2)).contains(SubmissionStatus.RETURNED), "已批改口径不应混入 RETURNED");
        assertTrue(wrapperParams(counts.get(3)).contains(SubmissionStatus.RETURNED), "已退回口径应为 RETURNED");
        // 迟交口径按 isLate=true 过滤
        assertTrue(wrapperParams(counts.get(4)).contains(Boolean.TRUE), "迟交口径必须按 isLate=true 过滤，实际="
                + wrapperParams(counts.get(4)));

        // 均分明细查询必须带「分数非空」约束，否则未批改记录会以 0 分拉低均分
        ArgumentCaptor<Wrapper> listCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(submissionService, times(2)).list(listCap.capture());
        Wrapper gradedDetail = listCap.getAllValues().get(0);
        Collection<Object> gradedDetailParams = wrapperParams(gradedDetail);
        assertTrue(gradedDetailParams.contains(SubmissionStatus.GRADED), "均分只统计已批改，实际=" + gradedDetailParams);
        assertTrue(gradedDetail.getSqlSegment().toUpperCase().contains("IS NOT NULL"),
                "均分查询必须排除未打分记录，实际 SQL=" + gradedDetail.getSqlSegment());

        // 完成率分母 = 该课程的「有效」选课人数：courseId 与 isActive 任一缺失都会算出虚高的完成率
        ArgumentCaptor<Wrapper> enrollCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(courseEnrollmentService).count(enrollCap.capture());
        Collection<Object> enrollParams = wrapperParams(enrollCap.getValue());
        assertTrue(enrollParams.contains(10L), "选课人数须限定课程，实际=" + enrollParams);
        assertTrue(enrollParams.contains(Boolean.TRUE), "选课人数须只算有效选课，实际=" + enrollParams);
    }

    private static Submission gradedSubmission(Long studentId, String score) {
        Submission sub = new Submission();
        sub.setStudentId(studentId);
        sub.setTaskId(1L);
        sub.setStatus(SubmissionStatus.GRADED);
        sub.setScore(new BigDecimal(score));
        return sub;
    }

    private static Submission statusSubmission(Long studentId, String status) {
        Submission sub = new Submission();
        sub.setStudentId(studentId);
        sub.setTaskId(1L);
        sub.setStatus(status);
        return sub;
    }

    @Test
    @DisplayName("待批阅列表 - 任务按创建人+已发布过滤，计数按 taskId+待批阅状态过滤")
    void testGetPendingGradingTasks() {
        sampleTask.setCreatorId(5L);
        sampleTask.setIsPublished(true);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sampleTask));
        when(submissionService.count(any())).thenReturn(2L);

        List<Task> pending = taskService.getPendingGradingTasks(5L);

        assertEquals(1, pending.size());
        // 教师维度必须落在 creatorId 上：漏掉就是把全校的任务列给这位老师
        ArgumentCaptor<Wrapper> taskCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).selectList(taskCap.capture());
        Collection<Object> taskParams = wrapperParams(taskCap.getValue());
        assertTrue(taskParams.contains(5L), "任务查询必须限定创建人，实际参数=" + taskParams);
        assertTrue(taskParams.contains(Boolean.TRUE), "任务查询必须限定已发布，实际参数=" + taskParams);

        // 「待批阅」的状态集合是这份清单的语义本身，写成 GRADED 会让清单永远为空或全是已批改
        ArgumentCaptor<Wrapper> subCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(submissionService).count(subCap.capture());
        Collection<Object> subParams = wrapperParams(subCap.getValue());
        assertTrue(subParams.contains(1L), "提交计数必须限定任务，实际参数=" + subParams);
        assertTrue(subParams.contains(SubmissionStatus.SUBMITTED) && subParams.contains(SubmissionStatus.RESUBMITTED),
                "待批阅须覆盖 SUBMITTED/RESUBMITTED，实际=" + subParams);
        assertFalse(subParams.contains(SubmissionStatus.GRADED), "已批改不应计入待批阅，实际=" + subParams);
    }

    @Test
    @DisplayName("待批阅列表 - 无待批阅提交的任务被剔除")
    void testGetPendingGradingTasks_nonePending() {
        sampleTask.setCreatorId(5L);
        sampleTask.setIsPublished(true);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sampleTask));
        when(submissionService.count(any())).thenReturn(0L);

        assertTrue(taskService.getPendingGradingTasks(5L).isEmpty());
    }
}
