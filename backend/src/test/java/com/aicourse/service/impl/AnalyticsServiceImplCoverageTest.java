package com.aicourse.service.impl;

import com.aicourse.constant.SubmissionStatus;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.TaskCompletion;
import com.aicourse.entity.User;
import com.aicourse.mapper.TaskCompletionMapper;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.LearningRiskService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
import com.aicourse.service.component.analytics.AdminAnalyticsComponent;
import com.aicourse.service.component.analytics.CourseAnalyticsComponent;
import com.aicourse.service.component.analytics.LearnerAnalyticsComponent;
import com.aicourse.service.component.analytics.TeachingAnalyticsComponent;
import com.aicourse.vo.CourseRiskPredictionVO;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AnalyticsServiceImpl} 补充分支覆盖测试（CoverageTest 命名惯例，自包含）。
 *
 * <p>门面本身只有两个方法承载真实逻辑（{@code getStudentCourseProgressList} /
 * {@code getStudentCourseDetail}），其余统计职责委派给四个组件；既有
 * {@code AnalyticsServiceImplTest} 只走了「有选课 + 空数据」的主干，
 * 两个方法的兜底/降级分支（无有效学生、无任务点、成绩/时间为 null、
 * 非本学生记录、任务/试卷归属不匹配、异常吞掉）几乎全部未触达。</p>
 *
 * <p>本类按源码分支逐条构造数据命中：</p>
 * <ol>
 *   <li>选课全为 {@code studentId=null} 且课程无任务点 —— 空集合兜底（进度列表不查库）；</li>
 *   <li>有任务点但无有效学生 —— 提交/考试批量查询的短路分支；</li>
 *   <li>三名学生全量聚合 —— 完成记录/提交/考试会话三条时间线的所有 null 与比较分支、
 *       四种提交状态、进度并列时的次级排序、缺用户的字段降级；</li>
 *   <li>课程级学情详情的章节排序、任务点归属、作业/考试过滤与时间格式化分支；</li>
 *   <li>两处 try/catch 兜底：底层查询异常时返回空集合/半成品 Map（不抛给调用方）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceImplCoverageTest {

    private static final LocalDateTime T_0101 = LocalDateTime.of(2024, 1, 1, 0, 0);
    private static final LocalDateTime T_0105 = LocalDateTime.of(2024, 1, 5, 0, 0);
    private static final LocalDateTime T_0106 = LocalDateTime.of(2024, 1, 6, 0, 0);
    private static final LocalDateTime T_0108 = LocalDateTime.of(2024, 1, 8, 0, 0);
    private static final LocalDateTime T_0110 = LocalDateTime.of(2024, 1, 10, 0, 0);
    private static final LocalDateTime T_0120 = LocalDateTime.of(2024, 1, 20, 0, 0);
    private static final LocalDateTime T_0130 = LocalDateTime.of(2024, 1, 30, 0, 0);
    private static final LocalDateTime T_0150 = LocalDateTime.of(2024, 1, 31, 23, 59);

    @Mock private AdminAnalyticsComponent adminAnalyticsComponent;
    @Mock private CourseAnalyticsComponent courseAnalyticsComponent;
    @Mock private LearnerAnalyticsComponent learnerAnalyticsComponent;
    @Mock private TeachingAnalyticsComponent teachingAnalyticsComponent;
    @Mock private LearningRiskService learningRiskService;

    @Mock private UserService userService;
    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private TaskService taskService;
    @Mock private TaskCompletionMapper taskCompletionMapper;
    @Mock private SubmissionService submissionService;
    @Mock private ExamSessionService examSessionService;
    @Mock private ExamPaperService examPaperService;
    @Mock private CourseChapterService courseChapterService;

    private AnalyticsServiceImpl analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsServiceImpl(
                adminAnalyticsComponent, courseAnalyticsComponent, learnerAnalyticsComponent, teachingAnalyticsComponent,
                learningRiskService,
                userService, enrollmentService, taskService, taskCompletionMapper,
                submissionService, examSessionService, examPaperService, courseChapterService);
    }

    // ===================== 门面委派（predictCourseRisk 未被既有测试触达） =====================

    @Test
    @DisplayName("predictCourseRisk 委派 LearningRiskService 并原样返回")
    void predictCourseRisk_delegates() {
        CourseRiskPredictionVO vo = new CourseRiskPredictionVO();
        vo.setCourseId(8L);
        vo.setHighCount(2);
        when(learningRiskService.predictCourseRisk(8L)).thenReturn(vo);

        CourseRiskPredictionVO result = analyticsService.predictCourseRisk(8L);
        assertSame(vo, result);
        assertEquals(2, result.getHighCount());
        verify(learningRiskService).predictCourseRisk(8L);
    }

    // ===================== getStudentCourseProgressList：兜底分支 =====================

    @Test
    @DisplayName("进度列表 - 选课记录全为 studentId=null 且课程无任务点：不查用户/完成/提交/考试")
    void progressList_onlyNullStudentIdsAndNoTasks() {
        when(enrollmentService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Collections.singletonList(enrollment(null, null))));
        when(taskService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());

        List<Map<String, Object>> result = analyticsService.getStudentCourseProgressList(1L);

        assertTrue(result.isEmpty());
        // studentIds 为空 -> userMap 走 emptyMap 分支；taskIds 为空 -> completions 走 emptyList 分支
        verify(userService, never()).listByIds(anyCollection());
        verify(taskCompletionMapper, never()).selectList(any(Wrapper.class));
        verify(submissionService, never()).list(any(Wrapper.class));
        verify(examSessionService, never()).list(any(Wrapper.class));
    }

    @Test
    @DisplayName("进度列表 - 有任务点但无有效学生：完成记录查库、提交与考试短路跳过")
    void progressList_hasTasksButNoValidStudent() {
        when(enrollmentService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Collections.singletonList(enrollment(null, null))));
        when(taskService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Collections.singletonList(task(1L, null, null))));
        when(taskCompletionMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());

        List<Map<String, Object>> result = analyticsService.getStudentCourseProgressList(1L);

        assertTrue(result.isEmpty());
        verify(taskCompletionMapper).selectList(any(Wrapper.class));
        // taskIds 非空但 studentIds 为空 -> submissions 走 emptyList；sessions 同样短路
        verify(submissionService, never()).list(any(Wrapper.class));
        verify(examSessionService, never()).list(any(Wrapper.class));
    }

    @Test
    @DisplayName("进度列表 - 底层查询异常被吞掉并返回空列表（不阻断接口）")
    void progressList_swallowsException() {
        when(enrollmentService.list(any(Wrapper.class)))
                .thenThrow(new IllegalStateException("db down"));

        List<Map<String, Object>> result = analyticsService.getStudentCourseProgressList(1L);

        assertTrue(result.isEmpty());
        verify(taskService, never()).list(any(Wrapper.class));
    }

    // ===================== getStudentCourseProgressList：全量聚合 =====================

    @Test
    @DisplayName("进度列表 - 三学生全量聚合：完成/提交/考试三条时间线、四种提交状态、并列次级排序")
    @SuppressWarnings("unchecked")
    void progressList_aggregatesEveryTimeline() {
        // 学生 10（进度 90，有用户）、学生 11（进度 null，无用户）、学生 12（进度 90，有用户）
        // 另有一条 studentId=null 的脏选课，命中 enrollMap 过滤分支
        List<CourseEnrollment> enrollments = new ArrayList<>(Arrays.asList(
                enrollment(null, null),
                enrollment(10L, new BigDecimal("90")),
                enrollment(11L, null),
                enrollment(12L, new BigDecimal("90"))));
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(enrollments);
        when(userService.listByIds(anyCollection()))
                .thenReturn(new ArrayList<>(Arrays.asList(user(10L, "张三", "S010", "a10"), user(12L, "李四", "S012", null))));
        when(taskService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Arrays.asList(task(1L, null, null), task(2L, null, null))));

        // 完成记录：本学生（含 updatedAt/createdAt/全 null/更晚/更早）+ 他班学生（不等分支）
        List<TaskCompletion> completions = new ArrayList<>(Arrays.asList(
                completion(10L, 1L, T_0105, null),
                completion(10L, 2L, null, T_0101),
                completion(10L, 2L, T_0110, null),
                completion(10L, 1L, null, null),
                completion(11L, 1L, null, null),
                completion(99L, 1L, T_0120, null)));
        when(taskCompletionMapper.selectList(any(Wrapper.class))).thenReturn(completions);

        // 提交：SUBMITTED/GRADED(score=null)/RETURNED/DRAFT + 他班学生
        List<Submission> submissions = new ArrayList<>(Arrays.asList(
                submission(10L, 1L, SubmissionStatus.SUBMITTED, new BigDecimal("80"), T_0106, null),
                submission(10L, 2L, SubmissionStatus.GRADED, null, null, T_0120),
                submission(10L, 1L, SubmissionStatus.RETURNED, new BigDecimal("70"), null, null),
                submission(10L, 2L, "DRAFT", new BigDecimal("60"), T_0130, null),
                submission(11L, 1L, SubmissionStatus.SUBMITTED, new BigDecimal("60"), null, T_0130)));
        when(submissionService.list(any(Wrapper.class))).thenReturn(submissions);

        // 考试会话：有百分比/无百分比/updatedAt 缺失回落 createdAt/时间全空 + 他班学生
        List<ExamSession> sessions = new ArrayList<>(Arrays.asList(
                examSession(10L, new BigDecimal("88"), T_0108, null),
                examSession(10L, new BigDecimal("95"), null, T_0150),
                examSession(10L, null, T_0150, null),
                examSession(10L, new BigDecimal("70"), null, null),
                examSession(11L, new BigDecimal("50"), T_0110, null),
                examSession(12L, new BigDecimal("77"), T_0101, null),
                examSession(12L, new BigDecimal("80"), null, T_0105)));
        when(examSessionService.list(any(Wrapper.class))).thenReturn(sessions);

        List<Map<String, Object>> result = analyticsService.getStudentCourseProgressList(1L);

        assertEquals(3, result.size());
        // 进度同为 90 时按完成任务数降序：10（2 个任务点均完成）在 12（0 个）之前，进度 null 的 11 殿后
        assertEquals(10L, result.get(0).get("studentId"));
        assertEquals(12L, result.get(1).get("studentId"));
        assertEquals(11L, result.get(2).get("studentId"));

        Map<String, Object> first = result.get(0);
        assertEquals("张三", first.get("studentName"));
        assertEquals("S010", first.get("studentNo"));
        assertEquals("a10", first.get("avatarUrl"));
        assertEquals(2L, first.get("totalTasks"));
        assertEquals(2, first.get("completedTasks"));
        // 80 / 70（GRADED 但 score 为 null 的不计入）
        assertEquals(75.0, first.get("avgAssignmentScore"));
        // (88 + 95 + 70) / 3 = 84.333... -> 84.33
        assertEquals(84.33, first.get("avgExamScore"));
        assertEquals(T_0150.toString(), first.get("lastActiveAt"));

        Map<String, Object> second = result.get(1);
        assertEquals("李四", second.get("studentName"));
        // avatarUrl 为 null -> 字段降级为 null
        assertNull(second.get("avatarUrl"));
        assertEquals(0, second.get("completedTasks"));
        // 学生 12 无提交 -> 作业均分 null；考试 (77+80)/2 = 78.5
        assertNull(second.get("avgAssignmentScore"));
        assertEquals(78.5, second.get("avgExamScore"));
        assertEquals(T_0105.toString(), second.get("lastActiveAt"));

        Map<String, Object> third = result.get(2);
        // 选课在、用户表查不到 -> 姓名/学号/头像降级
        assertNull(third.get("studentName"));
        assertNull(third.get("studentNo"));
        assertNull(third.get("avatarUrl"));
        assertNull(third.get("progress"));
        assertEquals(1, third.get("completedTasks"));
        assertEquals(60.0, third.get("avgAssignmentScore"));
        assertEquals(50.0, third.get("avgExamScore"));
        // 学生 11 完成记录无时间、提交时间与考试时间并存，取最大者
        assertEquals(T_0130.toString(), third.get("lastActiveAt"));

        verify(userService).listByIds(anyCollection());
        verify(taskCompletionMapper).selectList(any(Wrapper.class));
    }

    // ===================== getStudentCourseDetail =====================

    @Test
    @DisplayName("学情详情 - 章节/任务点/作业/考试全量分支聚合")
    @SuppressWarnings("unchecked")
    void studentDetail_aggregatesAllSections() {
        when(userService.getById(10L)).thenReturn(user(10L, "张三", "S010", "a10"));
        CourseEnrollment en = enrollment(10L, new BigDecimal("80.00"));
        en.setFinalScore(new BigDecimal("92.50"));
        when(enrollmentService.getOne(any(Wrapper.class))).thenReturn(en);

        // orderIndex 一个为 null（兜底 0）、一个为 2 -> 排序后 chapter2 在前
        CourseChapter ch3 = chapter(3L, 2, "第二章");
        CourseChapter ch2 = chapter(2L, null, "第一章");
        when(courseChapterService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Arrays.asList(ch3, ch2)));

        Task t100 = task(100L, 3L, "ASSIGNMENT");
        t100.setTitle("作业1");
        t100.setDueDate(T_0110);
        Task t101 = task(101L, 3L, "VIDEO");
        t101.setTitle("视频1");
        t101.setDueDate(null);
        Task t102 = task(102L, null, "QUIZ");
        when(taskService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Arrays.asList(t100, t101, t102)));

        when(taskCompletionMapper.selectList(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Collections.singletonList(completion(10L, 100L, T_0105, null))));

        Submission s1 = submission(10L, 100L, SubmissionStatus.GRADED, new BigDecimal("90"), T_0106, null);
        s1.setId(1001L);
        s1.setMaxScore(new BigDecimal("100"));
        s1.setFeedback("很好");
        s1.setIsLate(Boolean.FALSE);
        s1.setSubmittedAt(T_0105);
        Submission s2 = submission(10L, 999L, SubmissionStatus.GRADED, new BigDecimal("10"), null, null);
        Submission s3 = submission(10L, 101L, "DRAFT", new BigDecimal("20"), null, null);
        Submission s4 = submission(10L, 100L, SubmissionStatus.SUBMITTED, new BigDecimal("30"), null, null);
        s4.setId(1004L);
        when(submissionService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Arrays.asList(s1, s2, s3, s4)));

        ExamSession es1 = examSession(10L, new BigDecimal("90"), T_0108, null);
        es1.setId(2001L);
        es1.setExamPaperId(500L);
        es1.setStatus("GRADED");
        es1.setSubmitTime(T_0106);
        ExamSession es2 = examSession(10L, new BigDecimal("60"), T_0108, null);
        es2.setId(2002L);
        es2.setExamPaperId(501L);
        ExamSession es3 = examSession(10L, new BigDecimal("70"), T_0108, null);
        es3.setId(2003L);
        es3.setExamPaperId(502L);
        es3.setSubmitTime(null);
        ExamSession es4 = examSession(10L, new BigDecimal("85"), T_0108, null);
        es4.setId(2004L);
        es4.setExamPaperId(500L);
        es4.setSubmitTime(null);
        when(examSessionService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Arrays.asList(es1, es2, es3, es4)));

        when(examPaperService.listByIds(anyCollection()))
                .thenReturn(new ArrayList<>(Arrays.asList(paper(500L, 1L, "期中试卷", new BigDecimal("100")),
                        paper(501L, 2L, "别的课的试卷", new BigDecimal("100")))));

        Map<String, Object> result = analyticsService.getStudentCourseDetail(1L, 10L);

        assertEquals(10L, result.get("studentId"));
        assertEquals("张三", result.get("studentName"));
        assertEquals("S010", result.get("studentNo"));
        assertEquals(new BigDecimal("80.00"), result.get("progress"));
        assertEquals(new BigDecimal("92.50"), result.get("finalScore"));
        assertEquals(3, result.get("totalTasks"));
        // done = 完成记录 {100} ∪ 已提交/已批改/已退回的提交 taskId {100, 999}
        assertEquals(2, result.get("completedTasks"));

        List<Map<String, Object>> chapters = (List<Map<String, Object>>) result.get("chapters");
        assertEquals(2, chapters.size());
        // orderIndex null 兜底为 0 -> 排在最前，且该章无任务点
        assertEquals(2L, chapters.get(0).get("chapterId"));
        assertEquals(0, chapters.get(0).get("totalTasks"));
        assertEquals(0, chapters.get(0).get("completedTasks"));
        assertEquals(3L, chapters.get(1).get("chapterId"));
        assertEquals(2, chapters.get(1).get("totalTasks"));
        assertEquals(1, chapters.get(1).get("completedTasks"));
        List<Map<String, Object>> chTasks = (List<Map<String, Object>>) chapters.get(1).get("tasks");
        assertEquals(2, chTasks.size());
        assertEquals(100L, chTasks.get(0).get("taskId"));
        assertEquals("作业1", chTasks.get(0).get("title"));
        assertEquals("ASSIGNMENT", chTasks.get(0).get("taskType"));
        assertEquals(Boolean.TRUE, chTasks.get(0).get("completed"));
        assertEquals(T_0110.toString(), chTasks.get(0).get("dueDate"));
        assertEquals(101L, chTasks.get(1).get("taskId"));
        assertEquals(Boolean.FALSE, chTasks.get(1).get("completed"));
        assertNull(chTasks.get(1).get("dueDate"));

        List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("assignments");
        // 仅 taskType=ASSIGNMENT 的提交进入；taskId 不在任务表 / VIDEO 类型均跳过
        assertEquals(2, assignments.size());
        assertEquals(1001L, assignments.get(0).get("submissionId"));
        assertEquals("作业1", assignments.get(0).get("title"));
        assertEquals(new BigDecimal("90"), assignments.get(0).get("score"));
        assertEquals(new BigDecimal("100"), assignments.get(0).get("maxScore"));
        assertEquals(SubmissionStatus.GRADED, assignments.get(0).get("status"));
        assertEquals("很好", assignments.get(0).get("feedback"));
        assertEquals(Boolean.FALSE, assignments.get(0).get("isLate"));
        assertEquals(T_0105.toString(), assignments.get(0).get("submittedAt"));
        assertEquals(1004L, assignments.get(1).get("submissionId"));
        assertNull(assignments.get(1).get("submittedAt"));

        List<Map<String, Object>> exams = (List<Map<String, Object>>) result.get("exams");
        // 试卷不存在（502）与不属于本课程（501）均跳过；本课程 500 的两条保留
        assertEquals(2, exams.size());
        assertEquals(2001L, exams.get(0).get("sessionId"));
        assertEquals(500L, exams.get(0).get("examPaperId"));
        assertEquals("期中试卷", exams.get(0).get("title"));
        assertEquals(new BigDecimal("90"), exams.get(0).get("percentage"));
        assertEquals(new BigDecimal("100"), exams.get(0).get("totalScore"));
        assertEquals("GRADED", exams.get(0).get("status"));
        assertEquals(T_0106.toString(), exams.get(0).get("submitTime"));
        assertEquals(2004L, exams.get(1).get("sessionId"));
        assertNull(exams.get(1).get("submitTime"));
    }

    @Test
    @DisplayName("学情详情 - 学生与选课均缺失、课程无任务点时字段全部降级为 null")
    @SuppressWarnings("unchecked")
    void studentDetail_allEmpty() {
        when(userService.getById(99L)).thenReturn(null);
        when(enrollmentService.getOne(any(Wrapper.class))).thenReturn(null);
        when(courseChapterService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(taskService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(examSessionService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Collections.singletonList(nullSession())));

        Map<String, Object> result = analyticsService.getStudentCourseDetail(1L, 99L);

        assertNull(result.get("studentName"));
        assertNull(result.get("studentNo"));
        assertNull(result.get("progress"));
        assertNull(result.get("finalScore"));
        assertEquals(0, result.get("totalTasks"));
        assertEquals(0, result.get("completedTasks"));
        assertTrue(((List<Map<String, Object>>) result.get("chapters")).isEmpty());
        assertTrue(((List<Map<String, Object>>) result.get("assignments")).isEmpty());
        assertTrue(((List<Map<String, Object>>) result.get("exams")).isEmpty());
        // examPaperId 为 null 的记录被 filter 掉，paperIds 为空 -> 不查试卷表
        verify(examPaperService, never()).listByIds(anyCollection());
        verify(taskCompletionMapper, never()).selectList(any(Wrapper.class));
        verify(submissionService, never()).list(any(Wrapper.class));
    }

    @Test
    @DisplayName("学情详情 - 试卷批量查询异常被吞掉：已完成章节/作业，缺 exams 键")
    @SuppressWarnings("unchecked")
    void studentDetail_swallowsException() {
        when(userService.getById(10L)).thenReturn(user(10L, "张三", "S010", null));
        when(enrollmentService.getOne(any(Wrapper.class))).thenReturn(null);
        when(courseChapterService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(taskService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Collections.singletonList(task(7L, 3L, "QUIZ"))));
        when(taskCompletionMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>());
        when(submissionService.list(any(Wrapper.class))).thenReturn(new ArrayList<>());
        ExamSession withPaper = examSession(10L, new BigDecimal("70"), T_0108, null);
        withPaper.setExamPaperId(500L);
        when(examSessionService.list(any(Wrapper.class)))
                .thenReturn(new ArrayList<>(Collections.singletonList(withPaper)));
        when(examPaperService.listByIds(anyCollection()))
                .thenThrow(new IllegalStateException("paper db down"));

        Map<String, Object> result = analyticsService.getStudentCourseDetail(1L, 10L);

        assertEquals("张三", result.get("studentName"));
        assertEquals(1, result.get("totalTasks"));
        assertFalse(result.containsKey("exams"));
        // 章节/作业段落已在异常前写入
        assertTrue(((List<Map<String, Object>>) result.get("chapters")).isEmpty());
        assertTrue(((List<Map<String, Object>>) result.get("assignments")).isEmpty());
    }

    // ===================== 测试数据工厂 =====================

    private CourseEnrollment enrollment(Long studentId, BigDecimal progress) {
        CourseEnrollment e = new CourseEnrollment();
        e.setCourseId(1L);
        e.setStudentId(studentId);
        e.setProgress(progress);
        e.setIsActive(true);
        return e;
    }

    private User user(Long id, String fullName, String studentNo, String avatarUrl) {
        User u = new User();
        u.setId(id);
        u.setFullName(fullName);
        u.setStudentId(studentNo);
        u.setAvatarUrl(avatarUrl);
        return u;
    }

    private Task task(Long id, Long chapterId, String taskType) {
        Task t = new Task();
        t.setId(id);
        t.setCourseId(1L);
        t.setChapterId(chapterId);
        t.setTaskType(taskType);
        return t;
    }

    private TaskCompletion completion(Long studentId, Long taskId, LocalDateTime updatedAt, LocalDateTime createdAt) {
        TaskCompletion c = new TaskCompletion();
        c.setStudentId(studentId);
        c.setTaskId(taskId);
        c.setCompleted(true);
        c.setUpdatedAt(updatedAt);
        c.setCreatedAt(createdAt);
        return c;
    }

    private Submission submission(Long studentId, Long taskId, String status, BigDecimal score,
                                  LocalDateTime updatedAt, LocalDateTime submittedAt) {
        Submission s = new Submission();
        s.setStudentId(studentId);
        s.setTaskId(taskId);
        s.setStatus(status);
        s.setScore(score);
        s.setUpdatedAt(updatedAt);
        s.setSubmittedAt(submittedAt);
        return s;
    }

    private ExamSession examSession(Long studentId, BigDecimal percentage,
                                    LocalDateTime updatedAt, LocalDateTime createdAt) {
        ExamSession s = new ExamSession();
        s.setStudentId(studentId);
        s.setPercentage(percentage);
        s.setUpdatedAt(updatedAt);
        s.setCreatedAt(createdAt);
        return s;
    }

    private ExamSession nullSession() {
        ExamSession s = new ExamSession();
        s.setStudentId(99L);
        s.setExamPaperId(null);
        s.setPercentage(null);
        return s;
    }

    private CourseChapter chapter(Long id, Integer orderIndex, String title) {
        CourseChapter c = new CourseChapter();
        c.setId(id);
        c.setCourseId(1L);
        c.setOrderIndex(orderIndex);
        c.setTitle(title);
        return c;
    }

    private ExamPaper paper(Long id, Long courseId, String title, BigDecimal totalScore) {
        ExamPaper p = new ExamPaper();
        p.setId(id);
        p.setCourseId(courseId);
        p.setTitle(title);
        p.setTotalScore(totalScore);
        return p;
    }
}
