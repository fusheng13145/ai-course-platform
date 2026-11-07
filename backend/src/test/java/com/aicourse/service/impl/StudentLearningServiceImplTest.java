package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Question;
import com.aicourse.entity.SignIn;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.TaskCompletion;
import com.aicourse.entity.VideoLearningRecord;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.TaskCompletionMapper;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.SignInService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.VideoLearningRecordService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.component.student.StudentDashboardComponent;
import com.aicourse.service.component.student.StudentLearningCenterComponent;
import com.aicourse.service.component.student.StudentTaskComponent;
import com.aicourse.service.component.student.StudentTaskCompletionComponent;
import com.aicourse.service.component.student.StudentVisualizationComponent;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 学生端学习核心聚合门面单元测试（B 主战场 P0）。
 * 依赖 14 个 Service 接口 + 1 个 TaskCompletionMapper(BaseMapper)；
 * 内部以 LambdaQueryWrapper 调服务/ mapper，故 @BeforeEach 注册内部所用实体的 TableInfo，
 * 且 *.list(any()) 须锚定 any(Wrapper.class)；taskCompletionMapper.insert/updateById 须 any(TaskCompletion.class)。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentLearningServiceImplTest {

    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private CourseService courseService;
    @Mock private CourseChapterService courseChapterService;
    @Mock private TaskService taskService;
    @Mock private SubmissionService submissionService;
    @Mock private TaskCompletionMapper taskCompletionMapper;
    @Mock private NotificationService notificationService;
    @Mock private SignInService signInService;
    @Mock private ExamPaperService examPaperService;
    @Mock private ExamSessionService examSessionService;
    @Mock private AnalyticsService analyticsService;
    @Mock private LearningRecordService learningRecordService;
    @Mock private QuestionService questionService;
    @Mock private WrongQuestionService wrongQuestionService;
    @Mock private VideoLearningRecordService videoLearningRecordService;

    private StudentLearningServiceImpl service;

    private CourseEnrollment enr;
    private Course course;
    private Task task;

    @BeforeEach
    void setUp() {
        // 注册内部所用实体的 lambda 元数据（LambdaQueryWrapper 急切解析列名所需）
        for (Class<?> e : new Class<?>[]{CourseEnrollment.class, Course.class, Task.class, Submission.class,
                ExamPaper.class, ExamSession.class, SignIn.class, CourseChapter.class, TaskCompletion.class, Question.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), e);
        }

        enr = new CourseEnrollment();
        enr.setStudentId(11L); enr.setCourseId(1L); enr.setIsActive(Boolean.TRUE);
        enr.setProgress(new BigDecimal("50")); enr.setFinalScore(new BigDecimal("60"));

        course = new Course();
        course.setId(1L); course.setName("Java 程序设计"); course.setCoverImage("cover.png");

        task = new Task();
        task.setId(1L); task.setCourseId(1L); task.setChapterId(1L); task.setTaskType("VIDEO");
        task.setTitle("视频任务1"); task.setIsPublished(Boolean.TRUE); task.setMaxScore(new BigDecimal("100"));

        // 原兼容构造器已移除：内联其组件组装逻辑，再走主构造器（5 个组件）
        StudentTaskCompletionComponent taskCompletion =
                new StudentTaskCompletionComponent(taskService, taskCompletionMapper, enrollmentService,
                        org.mockito.Mockito.mock(com.aicourse.service.component.org.TeachingClassSnapshotResolver.class));
        service = new StudentLearningServiceImpl(
                new StudentDashboardComponent(enrollmentService, courseService, taskService, submissionService,
                        examPaperService, examSessionService, signInService, notificationService,
                        learningRecordService, analyticsService),
                new StudentLearningCenterComponent(courseService, courseChapterService, taskService,
                        submissionService, taskCompletionMapper, taskCompletion),
                new StudentTaskComponent(taskService, videoLearningRecordService, questionService,
                        wrongQuestionService, taskCompletion),
                new StudentVisualizationComponent(learningRecordService, wrongQuestionService, courseService),
                taskCompletion);

        // ===== 默认桩（LENIENT：未触发的桩容忍）=====
        when(enrollmentService.getOne(any(Wrapper.class))).thenReturn(enr);
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(enrollmentService.update(any(Wrapper.class))).thenReturn(true);

        when(courseService.getById(anyLong())).thenReturn(course);
        when(courseService.listByIds(any())).thenReturn(new ArrayList<>());

        when(taskService.getById(anyLong())).thenReturn(task);
        when(taskService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(taskService.listByIds(any())).thenReturn(new ArrayList<>());
        when(taskService.count(any(Wrapper.class))).thenReturn(0L);

        when(submissionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(submissionService.count(any(Wrapper.class))).thenReturn(0L);

        when(examPaperService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(examPaperService.listByIds(any())).thenReturn(new ArrayList<>());
        when(examSessionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());

        when(signInService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(signInService.listMyRecords(anyLong())).thenReturn(new ArrayList<>());

        when(courseChapterService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());

        when(questionService.listByIds(any())).thenReturn(new ArrayList<>());

        when(notificationService.getUnreadCount(anyLong())).thenReturn(0L);
        when(notificationService.getUnreadGroupedByCategory(anyLong())).thenReturn(Collections.emptyMap());
        when(learningRecordService.getLearningStats(anyLong())).thenReturn(Collections.emptyMap());
        when(analyticsService.getUserDashboard(anyLong())).thenReturn(Collections.emptyMap());

        VideoLearningRecord rec = new VideoLearningRecord();
        rec.setWatchDuration(100); rec.setLastPosition(50); rec.setTotalDuration(100); rec.setCompleteCount(0);
        when(videoLearningRecordService.updateProgress(anyLong(), anyLong(), any(), any(), any())).thenReturn(rec);

        when(taskCompletionMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(taskCompletionMapper.selectOne(any())).thenReturn(null);
        when(taskCompletionMapper.selectCount(any())).thenReturn(0L);
        when(taskCompletionMapper.insert(any(TaskCompletion.class))).thenReturn(1);
        when(taskCompletionMapper.updateById(any(TaskCompletion.class))).thenReturn(1);
    }

    // ===================== wrapper 参数捕获辅助 =====================

    /**
     * 取出 wrapper 的实际查询条件值。
     * <p>MyBatis-Plus 把 lambda 条件参数延迟到 {@code getSqlSegment()} 时才落入
     * {@code paramNameValuePairs}，故先触发构建再读取。学生端所有聚合查询都必须被
     * studentId（以及 courseId）限定住——这是「学生只能看到自己数据」的最后一道防线，
     * 用 {@code any(Wrapper.class)} 打桩完全无法发现条件被写错或漏写。</p>
     */
    private static Collection<Object> wrapperParams(Wrapper<?> wrapper) {
        assertNotNull(wrapper, "查询条件 wrapper 不得为 null");
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> aw =
                (com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) wrapper;
        aw.getSqlSegment();
        return aw.getParamNameValuePairs().values();
    }

    /** 断言某个查询被限定在指定学生名下。 */
    private static void assertScopedToStudent(Wrapper<?> wrapper, long studentId) {
        Collection<Object> params = wrapperParams(wrapper);
        assertTrue(params.contains(studentId), "查询条件必须包含 studentId=" + studentId + "，实际参数=" + params);
    }

    // ===================== ensureTable =====================

    @Test
    @DisplayName("ensureTable 正常建表不抛异常")
    void ensureTable_ok() {
        doNothing().when(taskCompletionMapper).createTableIfNotExists();
        assertDoesNotThrow(() -> service.ensureTable());
        verify(taskCompletionMapper).createTableIfNotExists();
    }

    @Test
    @DisplayName("ensureTable 建表失败被捕获不抛异常")
    void ensureTable_fail() {
        doThrow(new RuntimeException("table err")).when(taskCompletionMapper).createTableIfNotExists();
        assertDoesNotThrow(() -> service.ensureTable());
    }

    // ===================== getStudentDashboard =====================

    @Test
    @DisplayName("getStudentDashboard 空数据返回结构完整")
    void dashboard_empty() {
        Map<String, Object> r = service.getStudentDashboard(11L);
        assertNotNull(r);
        assertEquals(0, ((List<?>) r.get("courses")).size());
        @SuppressWarnings("unchecked")
        Map<String, Object> todos = (Map<String, Object>) r.get("todos");
        assertEquals(0, ((List<?>) todos.get("assignments")).size());
        assertNotNull(r.get("summary"));
    }

    @Test
    @DisplayName("getStudentDashboard 聚合课程/作业/考试/签到/消息")
    void dashboard_rich() {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(11L); e.setCourseId(1L); e.setIsActive(Boolean.TRUE); e.setProgress(new BigDecimal("50"));
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(e));
        when(courseService.listByIds(any())).thenReturn(List.of(course));

        Task asg = new Task();
        asg.setId(2L); asg.setCourseId(1L); asg.setTaskType("ASSIGNMENT"); asg.setIsPublished(Boolean.TRUE);
        asg.setTitle("作业1"); asg.setDueDate(LocalDateTime.now().plusDays(1));
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of(asg));

        ExamPaper paper = new ExamPaper();
        paper.setId(3L); paper.setCourseId(1L); paper.setTitle("期中");
        paper.setStatus("PUBLISHED"); paper.setStartTime(LocalDateTime.now().minusHours(1));
        paper.setEndTime(LocalDateTime.now().plusHours(1)); paper.setDuration(60);
        when(examPaperService.list(any(Wrapper.class))).thenReturn(List.of(paper));
        when(examSessionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());

        SignIn sign = new SignIn();
        sign.setId(4L); sign.setCourseId(1L); sign.setTitle("签到1"); sign.setType("NORMAL");
        sign.setStatus("ONGOING"); sign.setClosedAt(LocalDateTime.now().plusMinutes(10));
        when(signInService.list(any(Wrapper.class))).thenReturn(List.of(sign));
        when(signInService.listMyRecords(anyLong())).thenReturn(new ArrayList<>());

        when(notificationService.getUnreadCount(anyLong())).thenReturn(3L);
        when(notificationService.getUnreadGroupedByCategory(anyLong())).thenReturn(Map.of("SYSTEM", 3L));

        Map<String, Object> r = service.getStudentDashboard(11L);
        @SuppressWarnings("unchecked")
        Map<String, Object> todos = (Map<String, Object>) r.get("todos");
        assertEquals(1, ((List<?>) todos.get("assignments")).size());
        assertEquals(1, ((List<?>) todos.get("exams")).size());
        assertEquals(1, ((List<?>) todos.get("signIns")).size());
        assertEquals(3L, r.get("unreadCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) r.get("summary");
        assertEquals(1, summary.get("courseCount"));
        assertEquals(1, summary.get("pendingAssignmentCount"));

        // 「我的课程」聚合必须按 studentId 过滤，否则驾驶舱会把别人的选课与进度渲染进来
        ArgumentCaptor<Wrapper> enrCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(enrollmentService).list(enrCap.capture());
        assertScopedToStudent(enrCap.getValue(), 11L);
        // 未读消息数同样要按当前学生取，写死或漏传 userId 会让所有人看到同一个红点
        verify(notificationService).getUnreadCount(11L);
    }

    // ===================== getCourseLearningCenter =====================

    @Test
    @DisplayName("getCourseLearningCenter 单层章节树构建")
    void center_singleLevel() {
        CourseChapter c = new CourseChapter();
        c.setId(1L); c.setCourseId(1L); c.setTitle("第一章"); c.setOrderIndex(1); c.setIsPublished(Boolean.TRUE);
        when(courseChapterService.list(any(Wrapper.class))).thenReturn(List.of(c));
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of(task));
        when(submissionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(taskCompletionMapper.selectList(any())).thenReturn(new ArrayList<>());

        Map<String, Object> r = service.getCourseLearningCenter(11L, 1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tree = (List<Map<String, Object>>) r.get("chapters");
        assertEquals(1, tree.size());
        assertEquals(1, r.get("totalTasks"));
        assertEquals(0, r.get("completedTasks"));
        assertEquals(0, ((Number) r.get("completionPercent")).intValue());

        // 选课校验的查询必须被 (studentId, courseId, isActive) 三元组限定住：
        // 丢掉 studentId 就退化成「库里存在任意一条选课记录即视为已选课」的越权判定
        ArgumentCaptor<Wrapper> enCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(enrollmentService).getOne(enCap.capture());
        Collection<Object> enParams = wrapperParams(enCap.getValue());
        assertTrue(enParams.contains(11L), "选课校验必须带 studentId，实际参数=" + enParams);
        assertTrue(enParams.contains(1L), "选课校验必须带 courseId，实际参数=" + enParams);
        assertTrue(enParams.contains(Boolean.TRUE), "选课校验必须只看有效选课，实际参数=" + enParams);

        // 完成态预取（提交记录 / task_completions）也必须按学生维度限定，
        // 否则会把他人的作业提交与完成情况渲染进当前学生的闯关树
        ArgumentCaptor<Wrapper> subCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(submissionService).list(subCap.capture());
        assertScopedToStudent(subCap.getValue(), 11L);
        ArgumentCaptor<Wrapper> tcCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskCompletionMapper).selectList(tcCap.capture());
        assertScopedToStudent(tcCap.getValue(), 11L);
    }

    @Test
    @DisplayName("getCourseLearningCenter 三层章节 + 祖链锁定（父章未完成则子章解锁继承 false）")
    void center_withChildren() {
        // 祖父 g(parentId=0)，父 p(parentId=g)，子 c(parentId=p)
        CourseChapter g = new CourseChapter();
        g.setId(1L); g.setCourseId(1L); g.setTitle("祖父章"); g.setOrderIndex(1); g.setIsPublished(Boolean.TRUE); g.setParentId(0L);
        CourseChapter p = new CourseChapter();
        p.setId(2L); p.setCourseId(1L); p.setTitle("父章"); p.setOrderIndex(1); p.setIsPublished(Boolean.TRUE); p.setParentId(1L);
        CourseChapter c = new CourseChapter();
        c.setId(3L); c.setCourseId(1L); c.setTitle("子章"); c.setOrderIndex(1); c.setIsPublished(Boolean.TRUE); c.setParentId(2L);
        when(courseChapterService.list(any(Wrapper.class))).thenReturn(List.of(g, p, c));
        // 仅祖父章挂一个未完成任务 -> 祖父 allCompleted=false -> 父章(子层) chapterUnlocked=false
        Task gTask = new Task();
        gTask.setId(9L); gTask.setCourseId(1L); gTask.setChapterId(1L); gTask.setIsPublished(Boolean.TRUE); gTask.setTitle("g任务");
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of(gTask));
        when(submissionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(taskCompletionMapper.selectList(any())).thenReturn(new ArrayList<>());

        Map<String, Object> r = service.getCourseLearningCenter(11L, 1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tree = (List<Map<String, Object>>) r.get("chapters");
        assertEquals(1, tree.size()); // 顶层仅祖父
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pNodes = (List<Map<String, Object>>) tree.get(0).get("children");
        assertEquals(1, pNodes.size()); // 祖父的子=父章
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cNodes = (List<Map<String, Object>>) pNodes.get(0).get("children");
        assertEquals(1, cNodes.size()); // 父章的子=子章
        // 祖父任务未完成 -> 父章 chapterUnlocked=false -> 子章继承锁定 unlocked=false
        assertEquals(false, cNodes.get(0).get("unlocked"));
    }

    @Test
    @DisplayName("reportVideoHeartbeat 退化路径基于 task_completions 累计")
    void heartbeat_degraded() {
        when(taskService.getById(anyLong())).thenReturn(task);
        // 预置已存在完成记录：upsert 走 updateById 分支，getVideoState 读到的即为该记录
        TaskCompletion after = new TaskCompletion();
        after.setId(9L);
        after.setStudentId(11L); after.setCourseId(1L); after.setTaskId(1L);
        after.setLastPosition(50); after.setWatchedDuration(40); after.setTotalDuration(100); after.setCompleted(false);
        when(taskCompletionMapper.selectOne(any())).thenReturn(after);
        Map<String, Object> r = service.reportVideoHeartbeat(11L, 1L, 1L, null, 50, 40, 100, 1.0);
        assertNotNull(r);
        assertEquals(50, r.get("positionSeconds"));
        assertEquals(40, r.get("watchedDuration"));

        ArgumentCaptor<TaskCompletion> cap = ArgumentCaptor.forClass(TaskCompletion.class);
        verify(taskCompletionMapper).updateById(cap.capture());
        TaskCompletion saved = cap.getValue();
        // 已有记录必须原地更新（不得新插一条，否则唯一索引冲突/进度重复计数）
        verify(taskCompletionMapper, never()).insert(any(TaskCompletion.class));
        // 必须带着原记录主键去 updateById：id 丢失会让更新退化成按全字段匹配甚至影响 0 行
        assertSame(after, saved, "应复用查出的原记录做更新");
        assertEquals(9L, saved.getId());
        // 归属三元组沿用库中原值，不被心跳上报覆盖（防跨学生串号）
        assertEquals(11L, saved.getStudentId());
        assertEquals(1L, saved.getCourseId());
        assertEquals(1L, saved.getTaskId());
        // 反拖拽累计：位置沿用客户端上报值，有效观看时长按真实经过时长裁剪（本例无经过时间 -> 增量 0，仍为 40）
        assertEquals(50, saved.getLastPosition());
        assertEquals(40, saved.getWatchedDuration());
        assertEquals(100, saved.getTotalDuration());
        assertEquals("VIDEO", saved.getCompletionType());
        // 40 < 100*0.9 -> 未达完成阈值，不得被标记为已完成
        assertNotEquals(Boolean.TRUE, saved.getCompleted());
    }

    @Test
    @DisplayName("getCourseLearningCenter 未加入课程抛异常")
    void center_notEnrolled() {
        when(enrollmentService.getOne(any(Wrapper.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getCourseLearningCenter(11L, 1L));
    }

    // ===================== getCourseProgressSummary =====================

    @Test
    @DisplayName("getCourseProgressSummary 收集章节汇总(含子章节递归)")
    void progressSummary_ok() {
        CourseChapter parent = new CourseChapter();
        parent.setId(1L); parent.setCourseId(1L); parent.setTitle("父章"); parent.setOrderIndex(1); parent.setParentId(0L);
        CourseChapter child = new CourseChapter();
        child.setId(2L); child.setCourseId(1L); child.setTitle("子章"); child.setOrderIndex(1); child.setParentId(1L);
        when(courseChapterService.list(any(Wrapper.class))).thenReturn(List.of(parent, child));
        when(taskService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(submissionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(taskCompletionMapper.selectList(any())).thenReturn(new ArrayList<>());

        Map<String, Object> r = service.getCourseProgressSummary(11L, 1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> summary = (List<Map<String, Object>>) r.get("chapters");
        // collectSummary 将父子章节递归拍平到同一列表，父+子共 2 条
        assertEquals(2, summary.size());
    }

    @Test
    @DisplayName("getCourseProgressSummary 未加入课程抛异常")
    void progressSummary_notEnrolled() {
        when(enrollmentService.getOne(any(Wrapper.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getCourseProgressSummary(11L, 1L));
    }

    // ===================== markTaskComplete =====================

    @Test
    @DisplayName("markTaskComplete 正常标记完成：落库实体的归属与类型由入参映射")
    void mark_ok() {
        when(taskService.getById(anyLong())).thenReturn(task);
        Map<String, Object> r = service.markTaskComplete(11L, 1L, 1L, null);
        assertEquals(true, r.get("completed"));
        assertEquals("VIDEO", r.get("completionType"));

        ArgumentCaptor<TaskCompletion> cap = ArgumentCaptor.forClass(TaskCompletion.class);
        verify(taskCompletionMapper).insert(cap.capture());
        TaskCompletion saved = cap.getValue();
        // 完成态记录必须由 (studentId, courseId, taskId) 定位：任一映射错位都会把完成态记到别人/别课上
        assertEquals(11L, saved.getStudentId());
        assertEquals(1L, saved.getCourseId());
        assertEquals(1L, saved.getTaskId());
        // 章节与任务类型从任务点反查补齐（客户端不可信，不能由上报值决定归属）
        assertEquals(1L, saved.getChapterId());
        assertEquals("VIDEO", saved.getTaskType());
        // completionType 传 null 时应回退为任务类型，而不是留空（留空会让完成态统计按类型聚合时丢桶）
        assertEquals("VIDEO", saved.getCompletionType());
        assertEquals(Boolean.TRUE, saved.getCompleted());
        assertNotNull(saved.getCompletedAt());
    }

    @Test
    @DisplayName("markTaskComplete 任务点不存在抛异常")
    void mark_taskNull() {
        when(taskService.getById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.markTaskComplete(11L, 1L, 1L, "MANUAL"));
    }

    @Test
    @DisplayName("markTaskComplete 未加入课程抛异常")
    void mark_notEnrolled() {
        when(enrollmentService.getOne(any(Wrapper.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.markTaskComplete(11L, 1L, 1L, "MANUAL"));
    }

    // ===================== getVideoState =====================

    @Test
    @DisplayName("getVideoState 无记录返回零值")
    void videoState_null() {
        when(taskCompletionMapper.selectOne(any())).thenReturn(null);
        Map<String, Object> r = service.getVideoState(11L, 1L, 1L);
        assertEquals(0, r.get("positionSeconds"));
        assertEquals(false, r.get("completed"));
    }

    @Test
    @DisplayName("getVideoState 有记录返回真实值")
    void videoState_withRecord() {
        TaskCompletion tc = new TaskCompletion();
        tc.setLastPosition(42); tc.setWatchedDuration(80); tc.setTotalDuration(100); tc.setCompleted(true);
        when(taskCompletionMapper.selectOne(any())).thenReturn(tc);
        Map<String, Object> r = service.getVideoState(11L, 1L, 1L);
        assertEquals(42, r.get("positionSeconds"));
        assertEquals(80, r.get("watchedDuration"));
        assertEquals(true, r.get("completed"));
    }

    // ===================== reportVideoHeartbeat =====================

    @Test
    @DisplayName("reportVideoHeartbeat 带 resourceId 委托视频服务：转发参数为用户/资源/位置/总时长/倍速")
    void heartbeat_withResource() {
        when(taskService.getById(anyLong())).thenReturn(task);
        VideoLearningRecord rec = new VideoLearningRecord();
        rec.setWatchDuration(90); rec.setLastPosition(95); rec.setTotalDuration(100); rec.setCompleteCount(1);
        when(videoLearningRecordService.updateProgress(anyLong(), anyLong(), any(), any(), any())).thenReturn(rec);
        Map<String, Object> r = service.reportVideoHeartbeat(11L, 1L, 1L, 9L, 95, 90, 100, 1.0);
        assertNotNull(r);
        assertEquals(1L, r.get("taskId"));
        // 精确校验转发参数：观看时长(watchedDuration=90) 被刻意丢弃——它是客户端自报值，
        // 视频服务只接受 (userId, resourceId, position, totalDuration, playbackSpeed)，
        // 一旦改成透传 watchedDuration，#65 反拖拽收口就失效了。
        verify(videoLearningRecordService).updateProgress(11L, 9L, 95, 100, 1.0);
        verify(videoLearningRecordService, never()).updateProgress(anyLong(), anyLong(), any(), eq(90), any());

        // 心跳落库的完成态：观看时长/位置/总时长取视频服务返回值（服务端真相源），而非上报值
        ArgumentCaptor<TaskCompletion> cap = ArgumentCaptor.forClass(TaskCompletion.class);
        verify(taskCompletionMapper).insert(cap.capture());
        TaskCompletion saved = cap.getValue();
        assertEquals(11L, saved.getStudentId());
        assertEquals(1L, saved.getCourseId());
        assertEquals(1L, saved.getTaskId());
        assertEquals(95, saved.getLastPosition());
        assertEquals(90, saved.getWatchedDuration());
        assertEquals(100, saved.getTotalDuration());
        assertEquals("VIDEO", saved.getCompletionType());
        assertEquals(Boolean.TRUE, saved.getCompleted(), "completeCount>0 应判定为已看完");
    }

    @Test
    @DisplayName("reportVideoHeartbeat 任务点不存在抛异常")
    void heartbeat_taskNull() {
        when(taskService.getById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.reportVideoHeartbeat(11L, 1L, 1L, 9L, 50, 40, 100, 1.0));
    }

    // ===================== submitQuiz =====================

    @Test
    @DisplayName("submitQuiz 客观题答对计 correct 且不计错题")
    void quiz_correct() {
        when(taskService.getById(anyLong())).thenReturn(task);
        Question q = new Question();
        q.setId(5L); q.setCorrectAnswers("A"); q.setType("SINGLE_CHOICE"); q.setExplanation("因为A");
        when(questionService.listByIds(any())).thenReturn(List.of(q));
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("5", "A");
        Map<String, Object> r = service.submitQuiz(11L, 1L, 1L, answers);
        assertEquals(1, r.get("total"));
        assertEquals(1, r.get("correct"));
        assertEquals(100, ((Number) r.get("score")).intValue());
        verify(wrongQuestionService, never()).recordWrongAnswerInNewTx(anyLong(), anyLong(), anyLong(), any(), any());

        // 题目 ID 集合必须以 Long 形式批量预取（字符串键直接进 listByIds 会查不到题）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> idsCap = ArgumentCaptor.forClass(Collection.class);
        verify(questionService).listByIds(idsCap.capture());
        assertEquals(List.of(5L), new ArrayList<>(idsCap.getValue()), "答案键应转成题目ID再批量预取");

        // 判分结果落库：得分与完成态由服务端算出的 score 写入，而不是回显客户端上报值
        ArgumentCaptor<TaskCompletion> cap = ArgumentCaptor.forClass(TaskCompletion.class);
        verify(taskCompletionMapper).insert(cap.capture());
        TaskCompletion saved = cap.getValue();
        assertEquals(11L, saved.getStudentId());
        assertEquals(1L, saved.getTaskId());
        assertEquals(1L, saved.getCourseId());
        assertEquals("QUIZ", saved.getCompletionType());
        assertEquals(0, saved.getScore().compareTo(new BigDecimal("100")));
        assertEquals(Boolean.TRUE, saved.getCompleted());
    }

    @Test
    @DisplayName("submitQuiz 客观题答错收录错题本（走 REQUIRES_NEW 独立事务方法，D3）")
    void quiz_wrong_objective() {
        when(taskService.getById(anyLong())).thenReturn(task);
        Question q = new Question();
        q.setId(5L); q.setCorrectAnswers("A"); q.setType("SINGLE_CHOICE");
        when(questionService.listByIds(any())).thenReturn(List.of(q));
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("5", "B");
        Map<String, Object> r = service.submitQuiz(11L, 1L, 1L, answers);
        assertEquals(0, r.get("correct"));
        // D3：错题收录必须走独立事务方法 recordWrongAnswerInNewTx，
        // 绝不能再用会加入外层共享事务的 recordWrongAnswer（否则收录失败拖垮整次提交）。
        // 参数按值锁定：学生=11、题目=5、课程=1、学生作答=B、来源=QUIZ。
        // 来源标记写成 EXAM 会把练习错题混进考试错题本，课程写错则错题本归属到别的课。
        verify(wrongQuestionService).recordWrongAnswerInNewTx(11L, 5L, 1L, "B", "QUIZ");
        verify(wrongQuestionService, never()).recordWrongAnswer(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("submitQuiz 错题收录抛异常时判分结果仍正常返回（D3 回归：收录失败不拖垮提交）")
    void quiz_wrongRecordFails_gradingStillReturns() {
        // 说明：真实的 UnexpectedRollbackException 需事务同步器参与，Mockito 单元层无法复现回滚语义。
        // 故此处断言「收录异常被吞、判分明细照常返回且不抛出」+「调用的是 REQUIRES_NEW 方法」，
        // 事务传播属性本身由 WrongQuestionServiceImplTest#recordWrongAnswerInNewTx_requiresNew 反射断言。
        when(taskService.getById(anyLong())).thenReturn(task);
        Question q = new Question();
        q.setId(5L); q.setCorrectAnswers("A"); q.setType("SINGLE_CHOICE");
        when(questionService.listByIds(any())).thenReturn(List.of(q));
        doThrow(new RuntimeException("模拟错题收录失败"))
                .when(wrongQuestionService).recordWrongAnswerInNewTx(anyLong(), anyLong(), anyLong(), any(), any());
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("5", "B");

        Map<String, Object> r = service.submitQuiz(11L, 1L, 1L, answers);

        // 收录失败不影响判分：total/correct/score 与明细均已计算并返回
        assertEquals(1, r.get("total"));
        assertEquals(0, r.get("correct"));
        assertEquals(0, ((Number) r.get("score")).intValue());
        assertNotNull(r.get("details"));
        verify(wrongQuestionService).recordWrongAnswerInNewTx(11L, 5L, 1L, "B", "QUIZ");
        // 判分明细要能定位到具体题目并给出正确答案，否则学生看到分数却无从复盘
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) r.get("details");
        assertEquals(1, details.size());
        assertEquals(5L, details.get(0).get("questionId"));
        assertEquals(false, details.get(0).get("correct"));
        assertEquals("A", details.get(0).get("correctAnswer"));
    }

    @Test
    @DisplayName("submitQuiz 主观题答错不收录错题本")
    void quiz_wrong_subjective() {
        when(taskService.getById(anyLong())).thenReturn(task);
        Question q = new Question();
        q.setId(5L); q.setCorrectAnswers("A"); q.setType("SHORT_ANSWER");
        when(questionService.listByIds(any())).thenReturn(List.of(q));
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("5", "B");
        service.submitQuiz(11L, 1L, 1L, answers);
        verify(wrongQuestionService, never()).recordWrongAnswerInNewTx(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("submitQuiz 非法答案键被跳过")
    void quiz_invalidKey() {
        when(taskService.getById(anyLong())).thenReturn(task);
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("notANumber", "A");
        Map<String, Object> r = service.submitQuiz(11L, 1L, 1L, answers);
        assertEquals(0, r.get("total"));
        assertEquals(0, ((Number) r.get("score")).intValue());
    }

    @Test
    @DisplayName("submitQuiz 空答案返回零分")
    void quiz_empty() {
        when(taskService.getById(anyLong())).thenReturn(task);
        Map<String, Object> r = service.submitQuiz(11L, 1L, 1L, null);
        assertEquals(0, r.get("total"));
        assertEquals(0, ((Number) r.get("score")).intValue());
    }

    @Test
    @DisplayName("submitQuiz 任务点不存在抛异常")
    void quiz_taskNull() {
        when(taskService.getById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.submitQuiz(11L, 1L, 1L, Map.of("5", "A")));
    }

    // ===================== getGrades =====================

    @Test
    @DisplayName("getGrades 聚合作业与考试成绩")
    void grades_ok() {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(11L); e.setCourseId(1L); e.setIsActive(Boolean.TRUE); e.setFinalScore(new BigDecimal("88")); e.setProgress(new BigDecimal("90"));
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(e));
        when(courseService.listByIds(any())).thenReturn(List.of(course));

        Submission sub = new Submission();
        sub.setId(7L); sub.setTaskId(1L); sub.setStudentId(11L); sub.setScore(new BigDecimal("95"));
        sub.setStatus("GRADED"); sub.setSubmittedAt(LocalDateTime.now());
        when(submissionService.list(any(Wrapper.class))).thenReturn(List.of(sub));
        when(taskService.listByIds(any())).thenReturn(List.of(task));

        ExamSession s = new ExamSession();
        s.setId(8L); s.setStudentId(11L); s.setExamPaperId(3L); s.setPercentage(new BigDecimal("80"));
        s.setStatus("GRADED"); s.setSubmitTime(LocalDateTime.now());
        when(examSessionService.list(any(Wrapper.class))).thenReturn(List.of(s));
        ExamPaper paper = new ExamPaper();
        paper.setId(3L); paper.setCourseId(1L); paper.setTitle("期末"); paper.setTotalScore(new BigDecimal("100"));
        when(examPaperService.listByIds(any())).thenReturn(List.of(paper));

        Map<String, Object> r = service.getGrades(11L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) r.get("courses");
        assertEquals(1, courses.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) courses.get(0).get("assignments");
        assertEquals(1, assignments.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exams = (List<Map<String, Object>>) courses.get(0).get("exams");
        assertEquals(1, exams.size());

        // 学情查询必须全部按 studentId 收口：任一 wrapper 丢了学生维度就是「看到全班成绩」的越权
        ArgumentCaptor<Wrapper> enrCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(enrollmentService).list(enrCap.capture());
        assertScopedToStudent(enrCap.getValue(), 11L);
        ArgumentCaptor<Wrapper> subCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(submissionService).list(subCap.capture());
        assertScopedToStudent(subCap.getValue(), 11L);
        ArgumentCaptor<Wrapper> examCap = ArgumentCaptor.forClass(Wrapper.class);
        verify(examSessionService).list(examCap.capture());
        assertScopedToStudent(examCap.getValue(), 11L);
    }

    @Test
    @DisplayName("getGrades 无选课返回空课程列表")
    void grades_empty() {
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getGrades(11L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) r.get("courses");
        assertTrue(courses.isEmpty());
    }
}
