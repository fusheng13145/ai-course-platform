package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Question;
import com.aicourse.entity.SignIn;
import com.aicourse.entity.SignInRecord;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.TaskCompletion;
import com.aicourse.entity.VideoLearningRecord;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.TaskCompletionMapper;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 覆盖率补齐测试：针对 StudentLearningServiceImpl 中现有测试未覆盖的分支
 * （异常兜底 catch、任务完成态、quiz 部分得分/题目缺失/客观题类型、章节排序比较器等）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentLearningServiceImplBoostTest {

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

    @InjectMocks private StudentLearningServiceImpl service;

    private CourseEnrollment enr;
    private Course course;
    private Task task;

    @BeforeEach
    void setUp() {
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

        lenient().when(enrollmentService.getOne(any(Wrapper.class))).thenReturn(enr);
        lenient().when(enrollmentService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        lenient().when(enrollmentService.update(any(Wrapper.class))).thenReturn(true);
        lenient().when(courseService.getById(anyLong())).thenReturn(course);
        lenient().when(courseService.listByIds(any())).thenReturn(new ArrayList<>());
        lenient().when(taskService.getById(anyLong())).thenReturn(task);
        lenient().when(taskService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        lenient().when(taskService.listByIds(any())).thenReturn(new ArrayList<>());
        lenient().when(taskService.count(any(Wrapper.class))).thenReturn(0L);
        lenient().when(submissionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        lenient().when(submissionService.count(any(Wrapper.class))).thenReturn(0L);
        lenient().when(examPaperService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        lenient().when(examPaperService.listByIds(any())).thenReturn(new ArrayList<>());
        lenient().when(examSessionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        lenient().when(signInService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        lenient().when(signInService.listMyRecords(anyLong())).thenReturn(new ArrayList<>());
        lenient().when(courseChapterService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        lenient().when(questionService.listByIds(any())).thenReturn(new ArrayList<>());
        lenient().when(notificationService.getUnreadCount(anyLong())).thenReturn(0L);
        lenient().when(notificationService.getUnreadGroupedByCategory(anyLong())).thenReturn(Collections.emptyMap());
        lenient().when(learningRecordService.getLearningStats(anyLong())).thenReturn(Collections.emptyMap());
        lenient().when(analyticsService.getUserDashboard(anyLong())).thenReturn(Collections.emptyMap());
        VideoLearningRecord rec = new VideoLearningRecord();
        rec.setWatchDuration(100); rec.setLastPosition(50); rec.setTotalDuration(100); rec.setCompleteCount(0);
        lenient().when(videoLearningRecordService.updateProgress(anyLong(), anyLong(), any(), any(), any())).thenReturn(rec);
        lenient().when(taskCompletionMapper.selectList(any())).thenReturn(new ArrayList<>());
        lenient().when(taskCompletionMapper.selectOne(any())).thenReturn(null);
        lenient().when(taskCompletionMapper.selectCount(any())).thenReturn(0L);
        lenient().when(taskCompletionMapper.insert(any(TaskCompletion.class))).thenReturn(1);
        lenient().when(taskCompletionMapper.updateById(any(TaskCompletion.class))).thenReturn(1);
    }

    // ===== getStudentDashboard 异常兜底分支 =====

    @Test
    @DisplayName("dashboard 课程聚合异常被捕获")
    void dashboard_courseAggFailure() {
        when(enrollmentService.list(any(Wrapper.class))).thenThrow(new RuntimeException("db err"));
        Map<String, Object> r = service.getStudentDashboard(11L);
        assertNotNull(r);
        assertEquals(0, ((List<?>) r.get("courses")).size());
    }

    @Test
    @DisplayName("dashboard 待办聚合异常被捕获")
    void dashboard_todoAggFailure() {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(11L); e.setCourseId(1L); e.setIsActive(Boolean.TRUE);
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(e));
        when(taskService.list(any(Wrapper.class))).thenThrow(new RuntimeException("task err"));
        Map<String, Object> r = service.getStudentDashboard(11L);
        assertNotNull(r);
        @SuppressWarnings("unchecked")
        Map<String, Object> todos = (Map<String, Object>) r.get("todos");
        assertEquals(0, ((List<?>) todos.get("assignments")).size());
    }

    @Test
    @DisplayName("dashboard 未读消息查询异常被捕获兜底")
    void dashboard_notificationError() {
        when(notificationService.getUnreadCount(anyLong())).thenThrow(new RuntimeException("redis down"));
        Map<String, Object> r = service.getStudentDashboard(11L);
        assertEquals(0L, r.get("unreadCount"));
        assertNotNull(r.get("unreadByCategory"));
    }

    @Test
    @DisplayName("dashboard 学习统计异常被捕获")
    void dashboard_learningStatsFailure() {
        when(learningRecordService.getLearningStats(anyLong())).thenThrow(new RuntimeException("stats err"));
        Map<String, Object> r = service.getStudentDashboard(11L);
        assertNotNull(r.get("learningStats"));
    }

    @Test
    @DisplayName("dashboard 学情分析异常被捕获")
    void dashboard_analyticsFailure() {
        when(analyticsService.getUserDashboard(anyLong())).thenThrow(new RuntimeException("analytics err"));
        Map<String, Object> r = service.getStudentDashboard(11L);
        assertNotNull(r.get("analytics"));
    }

    @Test
    @DisplayName("dashboard 已提交作业不计入待办")
    void dashboard_assignmentAlreadySubmitted() {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(11L); e.setCourseId(1L); e.setIsActive(Boolean.TRUE);
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(e));
        when(courseService.listByIds(any())).thenReturn(List.of(course));
        Task asg = new Task();
        asg.setId(2L); asg.setCourseId(1L); asg.setTaskType("ASSIGNMENT"); asg.setIsPublished(Boolean.TRUE);
        asg.setTitle("作业1"); asg.setDueDate(LocalDateTime.now().plusDays(1));
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of(asg));
        Submission sub = new Submission();
        sub.setTaskId(2L); sub.setStudentId(11L); sub.setStatus("SUBMITTED");
        when(submissionService.list(any(Wrapper.class))).thenReturn(List.of(sub));
        Map<String, Object> r = service.getStudentDashboard(11L);
        @SuppressWarnings("unchecked")
        Map<String, Object> todos = (Map<String, Object>) r.get("todos");
        assertEquals(0, ((List<?>) todos.get("assignments")).size());
    }

    @Test
    @DisplayName("dashboard 已考考试/已签签到不计入待办")
    void dashboard_examTakenAndSignInSigned() {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(11L); e.setCourseId(1L); e.setIsActive(Boolean.TRUE);
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(e));
        when(courseService.listByIds(any())).thenReturn(List.of(course));
        ExamPaper paper = new ExamPaper();
        paper.setId(3L); paper.setCourseId(1L); paper.setTitle("期中");
        paper.setStatus("PUBLISHED"); paper.setStartTime(LocalDateTime.now().minusHours(1));
        paper.setEndTime(LocalDateTime.now().plusHours(1)); paper.setDuration(60);
        when(examPaperService.list(any(Wrapper.class))).thenReturn(List.of(paper));
        ExamSession s = new ExamSession();
        s.setStudentId(11L); s.setExamPaperId(3L);
        when(examSessionService.list(any(Wrapper.class))).thenReturn(List.of(s));
        SignIn sign = new SignIn();
        sign.setId(4L); sign.setCourseId(1L); sign.setTitle("签到1"); sign.setType("NORMAL");
        sign.setStatus("ONGOING"); sign.setClosedAt(LocalDateTime.now().plusMinutes(10));
        when(signInService.list(any(Wrapper.class))).thenReturn(List.of(sign));
        SignInRecord rec = new SignInRecord();
        rec.setSignInId(4L);
        when(signInService.listMyRecords(anyLong())).thenReturn(List.of(rec));
        Map<String, Object> r = service.getStudentDashboard(11L);
        @SuppressWarnings("unchecked")
        Map<String, Object> todos = (Map<String, Object>) r.get("todos");
        assertEquals(0, ((List<?>) todos.get("exams")).size());
        assertEquals(0, ((List<?>) todos.get("signIns")).size());
    }

    // ===== getCourseLearningCenter 分支 =====

    @Test
    @DisplayName("学习中心 课程为 null 兜底")
    void center_courseNull() {
        when(courseService.getById(anyLong())).thenReturn(null);
        when(courseChapterService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(taskService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(submissionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(taskCompletionMapper.selectList(any())).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getCourseLearningCenter(11L, 1L);
        assertNull(r.get("courseName"));
    }

    @Test
    @DisplayName("学习中心 任务已完成 -> 章节解锁并统计完成数")
    void center_completedTasks() {
        CourseChapter c = new CourseChapter();
        c.setId(1L); c.setCourseId(1L); c.setTitle("第一章"); c.setOrderIndex(1); c.setIsPublished(Boolean.TRUE); c.setParentId(0L);
        when(courseChapterService.list(any(Wrapper.class))).thenReturn(List.of(c));
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of(task));
        Submission sub = new Submission();
        sub.setTaskId(1L); sub.setStudentId(11L); sub.setStatus("GRADED");
        when(submissionService.list(any(Wrapper.class))).thenReturn(List.of(sub));
        when(taskCompletionMapper.selectList(any())).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getCourseLearningCenter(11L, 1L);
        assertEquals(1, r.get("totalTasks"));
        assertEquals(1, r.get("completedTasks"));
        assertEquals(100, ((Number) r.get("completionPercent")).intValue());
    }

    @Test
    @DisplayName("学习中心 多顶层章节 + orderIndex 为 null 比较分支")
    void center_multipleTopAndNullOrder() {
        CourseChapter a = new CourseChapter();
        a.setId(2L); a.setCourseId(1L); a.setTitle("A"); a.setParentId(0L); a.setOrderIndex(null);
        CourseChapter b = new CourseChapter();
        b.setId(1L); b.setCourseId(1L); b.setTitle("B"); b.setParentId(0L); b.setOrderIndex(null);
        when(courseChapterService.list(any(Wrapper.class))).thenReturn(List.of(a, b));
        when(taskService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(submissionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(taskCompletionMapper.selectList(any())).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getCourseLearningCenter(11L, 1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tree = (List<Map<String, Object>>) r.get("chapters");
        assertEquals(2, tree.size());
    }

    // ===== getCourseProgressSummary =====

    @Test
    @DisplayName("进度汇总 含任务章节计算百分比")
    void progressSummary_withTasks() {
        CourseChapter parent = new CourseChapter();
        parent.setId(1L); parent.setCourseId(1L); parent.setTitle("父章"); parent.setOrderIndex(1); parent.setParentId(0L);
        CourseChapter child = new CourseChapter();
        child.setId(2L); child.setCourseId(1L); child.setTitle("子章"); child.setOrderIndex(1); child.setParentId(1L);
        when(courseChapterService.list(any(Wrapper.class))).thenReturn(List.of(parent, child));
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of(task));
        when(submissionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(taskCompletionMapper.selectList(any())).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getCourseProgressSummary(11L, 1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> summary = (List<Map<String, Object>>) r.get("chapters");
        assertEquals(2, summary.size());
        assertEquals(0, ((Number) summary.get(0).get("percent")).intValue());
    }

    // ===== markTaskComplete completionType 推导 =====

    @Test
    @DisplayName("markTaskComplete completionType 为空时取任务类型")
    void mark_completionTypeFromTask() {
        Task asg = new Task();
        asg.setId(5L); asg.setCourseId(1L); asg.setTaskType("ASSIGNMENT");
        when(taskService.getById(5L)).thenReturn(asg);
        Map<String, Object> r = service.markTaskComplete(11L, 1L, 5L, null);
        assertEquals("ASSIGNMENT", r.get("completionType"));
    }

    // ===== reportVideoHeartbeat 退化路径完成判定 =====

    @Test
    @DisplayName("reportVideoHeartbeat 退化路径累计达 90% 判定完成")
    void heartbeat_degraded_completed() {
        when(taskService.getById(anyLong())).thenReturn(task);
        TaskCompletion existing = new TaskCompletion();
        existing.setLastPosition(0); existing.setWatchedDuration(0); existing.setTotalDuration(100);
        existing.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        when(taskCompletionMapper.selectOne(any())).thenReturn(existing);
        Map<String, Object> r = service.reportVideoHeartbeat(11L, 1L, 1L, null, 100, 100, 100, 1.0);
        assertNotNull(r);
        assertEquals(true, r.get("completed"));
    }

    // ===== submitQuiz 分支 =====

    @Test
    @DisplayName("submitQuiz 部分正确得分按比例")
    void quiz_partialScore() {
        when(taskService.getById(anyLong())).thenReturn(task);
        Question q1 = new Question(); q1.setId(5L); q1.setCorrectAnswers("A"); q1.setType("SINGLE_CHOICE");
        Question q2 = new Question(); q2.setId(6L); q2.setCorrectAnswers("A"); q2.setType("SINGLE_CHOICE");
        when(questionService.listByIds(any())).thenReturn(List.of(q1, q2));
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("5", "A"); answers.put("6", "B");
        Map<String, Object> r = service.submitQuiz(11L, 1L, 1L, answers);
        assertEquals(2, r.get("total"));
        assertEquals(1, r.get("correct"));
        assertEquals(50, ((Number) r.get("score")).intValue());
    }

    @Test
    @DisplayName("submitQuiz 题目不存在分支")
    void quiz_questionNull() {
        when(taskService.getById(anyLong())).thenReturn(task);
        when(questionService.listByIds(any())).thenReturn(new ArrayList<>());
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("5", "A");
        Map<String, Object> r = service.submitQuiz(11L, 1L, 1L, answers);
        assertEquals(1, r.get("total"));
        assertEquals(0, r.get("correct"));
    }

    @Test
    @DisplayName("submitQuiz 多种客观题类型答错均收录错题本")
    void quiz_objectiveVariants() {
        when(taskService.getById(anyLong())).thenReturn(task);
        for (String type : new String[]{"SINGLE", "MULTIPLE", "JUDGE", "TRUE_FALSE", "MULTIPLE_CHOICE"}) {
            Question q = new Question(); q.setId(7L); q.setCorrectAnswers("A"); q.setType(type);
            when(questionService.listByIds(any())).thenReturn(List.of(q));
            Map<String, String> answers = new LinkedHashMap<>();
            answers.put("7", "B");
            service.submitQuiz(11L, 1L, 1L, answers);
        }
        verify(wrongQuestionService, times(5)).recordWrongAnswer(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("submitQuiz 错题收录失败被捕获")
    void quiz_recordWrongFails() {
        when(taskService.getById(anyLong())).thenReturn(task);
        Question q = new Question(); q.setId(5L); q.setCorrectAnswers("A"); q.setType("SINGLE_CHOICE");
        when(questionService.listByIds(any())).thenReturn(List.of(q));
        doThrow(new RuntimeException("wrong save err")).when(wrongQuestionService).recordWrongAnswer(anyLong(), anyLong(), anyLong(), any(), any());
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("5", "B");
        Map<String, Object> r = service.submitQuiz(11L, 1L, 1L, answers);
        assertEquals(0, r.get("correct"));
    }

    // ===== getGrades 分支 =====

    @Test
    @DisplayName("getGrades 课程为 null 跳过")
    void grades_courseNullSkipped() {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(11L); e.setCourseId(99L); e.setIsActive(Boolean.TRUE);
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(e));
        when(courseService.listByIds(any())).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getGrades(11L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) r.get("courses");
        assertTrue(courses.isEmpty());
    }

    @Test
    @DisplayName("getGrades 作业对应任务为 null 跳过")
    void grades_assignmentTaskNull() {
        CourseEnrollment e = new CourseEnrollment();
        e.setStudentId(11L); e.setCourseId(1L); e.setIsActive(Boolean.TRUE);
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(List.of(e));
        when(courseService.listByIds(any())).thenReturn(List.of(course));
        Submission sub = new Submission();
        sub.setId(7L); sub.setTaskId(123L); sub.setStudentId(11L); sub.setStatus("GRADED");
        when(submissionService.list(any(Wrapper.class))).thenReturn(List.of(sub));
        when(taskService.listByIds(any())).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getGrades(11L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) r.get("courses");
        assertEquals(1, courses.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) courses.get(0).get("assignments");
        assertTrue(assignments.isEmpty());
    }
}
