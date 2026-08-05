package com.aicourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.LearningRecord;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.TaskCompletion;
import com.aicourse.entity.User;
import com.aicourse.mapper.TaskCompletionMapper;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AnalyticsServiceImpl 单元测试。
 * <p>实现为直算模式：直接聚合 UserService / CourseService / CourseEnrollmentService /
 * TaskService / SubmissionService / LearningRecordService / ExamSessionService / ExamPaperService
 * 的 MyBatis-Plus 查询结果。本测试 mock 上述真实依赖，验证各统计方法的聚合计算行为
 * （正常路径 + 关键分支），与同目录其他 ServiceImpl 测试风格一致。</p>
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock private UserService userService;
    @Mock private CourseService courseService;
    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private TaskService taskService;
    @Mock private SubmissionService submissionService;
    @Mock private LearningRecordService learningRecordService;
    @Mock private ExamSessionService examSessionService;
    @Mock private ExamPaperService examPaperService;
    @Mock private TaskCompletionMapper taskCompletionMapper;
    @Mock private CourseChapterService courseChapterService;

    private AnalyticsServiceImpl analyticsService;

    @BeforeEach
    void setUp() {
        // getAtRiskStudents 已委托给 TeachingAnalyticsComponent（真实实例 + 现有 mock 依赖），
        // 因此这里手动装配，保证委托链路在单测中可执行。
        TeachingAnalyticsComponent component = new TeachingAnalyticsComponent(
                courseService, enrollmentService, taskService, submissionService, userService, null);
        analyticsService = new AnalyticsServiceImpl(
                userService, courseService, enrollmentService, taskService,
                submissionService, learningRecordService, examSessionService, examPaperService,
                component, taskCompletionMapper, courseChapterService);
    }

    private Course course(Long id, String name, String code, String category, String difficulty,
                          Long teacherId, boolean active, LocalDateTime createdAt) {
        Course c = new Course();
        c.setId(id);
        c.setName(name);
        c.setCode(code);
        c.setCategory(category);
        c.setDifficulty(difficulty);
        c.setTeacherId(teacherId);
        c.setIsActive(active);
        c.setCreatedAt(createdAt);
        return c;
    }

    private CourseEnrollment enrollment(Long courseId, Long studentId, BigDecimal progress, BigDecimal finalScore) {
        CourseEnrollment e = new CourseEnrollment();
        e.setCourseId(courseId);
        e.setStudentId(studentId);
        e.setProgress(progress);
        e.setFinalScore(finalScore);
        return e;
    }

    private LearningRecord record(Long userId, Integer duration, LocalDateTime createdAt) {
        LearningRecord r = new LearningRecord();
        r.setUserId(userId);
        r.setDuration(duration);
        r.setCreatedAt(createdAt);
        return r;
    }

    private User userCreatedAt(LocalDateTime createdAt) {
        User u = new User();
        u.setCreatedAt(createdAt);
        return u;
    }

    /** 构造 SQL 聚合结果行（listMaps 返回的 Map 行） */
    private Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("getDashboard - 聚合各服务计数与分布数据")
    void testGetDashboard_AggregatesCoreStats() {
        when(userService.count()).thenReturn(100L);
        when(userService.count(any())).thenReturn(80L, 10L, 60L, 30L, 10L); // active, new30d, STUDENT, TEACHER, ADMIN
        when(courseService.count()).thenReturn(10L);
        when(courseService.count(any())).thenReturn(7L);
        when(examPaperService.count()).thenReturn(5L);
        when(submissionService.count()).thenReturn(200L);
        when(enrollmentService.count()).thenReturn(50L);
        // SQL 聚合行：进度>=100 共 3 条；分数段 95/85/75/65/55 各 1 条
        when(enrollmentService.listMaps(any(QueryWrapper.class))).thenReturn(Collections.singletonList(
                row("total", 5L, "completed", 3L, "excellent", 1L, "good", 1L,
                        "medium", 1L, "pass", 1L, "fail", 1L)
        ));
        when(taskService.count()).thenReturn(30L);
        when(taskService.count(any())).thenReturn(20L);
        when(learningRecordService.count(any())).thenReturn(40L);
        when(courseService.listMaps(any(QueryWrapper.class))).thenReturn(Arrays.asList(
                row("category", "技术", "cnt", 2L),
                row("category", "数学", "cnt", 1L)
        ));

        Map<String, Object> dashboard = analyticsService.getDashboard();

        assertEquals(100L, dashboard.get("totalUsers"));
        assertEquals(80L, dashboard.get("activeUsers"));
        assertEquals(10L, dashboard.get("totalCourses"));
        assertEquals(7L, dashboard.get("activeCourses"));
        assertEquals(5L, dashboard.get("totalExams"));
        assertEquals(200L, dashboard.get("submissionCount"));
        assertEquals(50L, dashboard.get("totalEnrollments"));
        assertEquals(30L, dashboard.get("totalTasks"));
        assertEquals(20L, dashboard.get("publishedTasks"));
        assertEquals(40L, dashboard.get("dailyActiveUsers"));

        @SuppressWarnings("unchecked")
        Map<String, Long> categoryDist = (Map<String, Long>) dashboard.get("categoryDistribution");
        assertEquals(2L, categoryDist.get("技术"));
        assertEquals(1L, categoryDist.get("数学"));

        // 3 条进度>=100 / 50 条选课 = 6.0%
        assertEquals(6.0, (Double) dashboard.get("completionRate"), 0.001);
        // 10 个新用户 / 100 总用户 = 10.0%
        assertEquals(10.0, (Double) dashboard.get("growthRate"), 0.001);
        assertEquals(10.0, (Double) dashboard.get("userGrowth"), 0.001);

        // 分数段：95/85/75/65/55 -> 优秀1 良好1 中等1 及格1 不及格1
        assertEquals(Arrays.asList(1L, 1L, 1L, 1L, 1L), dashboard.get("gradeDistribution"));

        // 角色活跃度：ADMIN=10 -> 30/50, TEACHER=30 -> 150/300, STUDENT=60 -> 600/1200
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roleActivity = (List<Map<String, Object>>) dashboard.get("roleActivity");
        assertEquals(3, roleActivity.size());
        assertEquals(30L, roleActivity.get(0).get("loginCount"));
        assertEquals(50L, roleActivity.get(0).get("actionCount"));
        assertEquals(150L, roleActivity.get(1).get("loginCount"));
        assertEquals(600L, roleActivity.get(2).get("loginCount"));
        assertEquals(1200L, roleActivity.get(2).get("actionCount"));
    }

    @Test
    @DisplayName("getDashboard - 空数据时返回全零默认值")
    void testGetDashboard_EmptyData() {
        when(userService.count()).thenReturn(0L);
        when(userService.count(any())).thenReturn(0L, 0L, 0L, 0L, 0L);
        when(courseService.count()).thenReturn(0L);
        when(courseService.count(any())).thenReturn(0L);
        when(examPaperService.count()).thenReturn(0L);
        when(submissionService.count()).thenReturn(0L);
        when(enrollmentService.count()).thenReturn(0L);
        when(enrollmentService.listMaps(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(taskService.count()).thenReturn(0L);
        when(taskService.count(any())).thenReturn(0L);
        when(learningRecordService.count(any())).thenReturn(0L);
        when(courseService.listMaps(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> dashboard = analyticsService.getDashboard();

        assertEquals(0L, dashboard.get("userCount"));
        assertEquals(0L, dashboard.get("courseCount"));
        assertEquals(0L, dashboard.get("examCount"));
        assertEquals(0L, dashboard.get("submissionCount"));
        assertEquals(0.0, (Double) dashboard.get("completionRate"), 0.001);
        assertEquals(0.0, (Double) dashboard.get("growthRate"), 0.001);
        assertEquals(Arrays.asList(0L, 0L, 0L, 0L, 0L), dashboard.get("gradeDistribution"));
        assertFalse(dashboard.containsKey("error"));
    }

    @Test
    @DisplayName("getPlatformOverview - 聚合总览指标")
    void testGetPlatformOverview_AggregatesOverviewStats() {
        when(userService.count()).thenReturn(100L);
        when(userService.count(any())).thenReturn(80L);
        when(courseService.count()).thenReturn(10L);
        when(courseService.count(any())).thenReturn(7L);
        when(enrollmentService.count()).thenReturn(50L);
        when(enrollmentService.list()).thenReturn(Arrays.asList(
                enrollment(1L, 1L, new BigDecimal("100"), null),
                enrollment(2L, 1L, new BigDecimal("50"), null),
                enrollment(3L, 1L, new BigDecimal("100"), null)
        ));
        when(learningRecordService.count(any())).thenReturn(40L);
        when(taskService.count()).thenReturn(30L);
        when(taskService.count(any())).thenReturn(20L);
        when(examPaperService.count()).thenReturn(5L);

        Map<String, Object> overview = analyticsService.getPlatformOverview();

        assertEquals(100L, overview.get("totalUsers"));
        assertEquals(80L, overview.get("activeUsers"));
        assertEquals(10L, overview.get("totalCourses"));
        assertEquals(7L, overview.get("activeCourses"));
        assertEquals(50L, overview.get("totalEnrollments"));
        assertEquals(40L, overview.get("activeLearningUsers"));
        assertEquals(40.0, (Double) overview.get("activityRate"), 0.001);
        assertEquals(2L, overview.get("completedEnrollments"));
        assertEquals(4.0, (Double) overview.get("completionRate"), 0.001);
        assertEquals(30L, overview.get("totalTasks"));
        assertEquals(20L, overview.get("publishedTasks"));
        assertEquals(5L, overview.get("totalExams"));
    }

    @Test
    @DisplayName("getUserDashboard - 计算学习进度/时长/成绩/连续天数/待办")
    void testGetUserDashboard_ComputesStudentStats() {
        when(enrollmentService.count(any())).thenReturn(3L);
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                enrollment(1L, 7L, new BigDecimal("50"), new BigDecimal("80")),
                enrollment(2L, 7L, new BigDecimal("100"), new BigDecimal("90"))
        ));
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                record(7L, 30, LocalDateTime.now()),
                record(7L, 60, LocalDateTime.now().minusDays(1))
        ));
        when(taskService.count(any())).thenReturn(2L);
        when(submissionService.count(any())).thenReturn(1L);

        Map<String, Object> data = analyticsService.getUserDashboard(7L);

        assertEquals(3L, data.get("enrolledCourses"));
        assertEquals(75.0, (Double) data.get("averageProgress"), 0.001);
        assertEquals(90, data.get("totalStudyTime"));
        assertEquals(85.0, (Double) data.get("averageScore"), 0.001);
        assertEquals(85L, data.get("avgScore"));
        // 今天+昨天均有记录 -> 连续2天
        assertEquals(2, data.get("streakDays"));
        assertEquals(2, data.get("consecutiveDays"));
        // 2 个待办任务 - 1 个已提交 = 1
        assertEquals(1L, data.get("pendingTasks"));
        assertEquals(1L, data.get("todoCount"));
        assertFalse(data.containsKey("error"));
    }

    @Test
    @DisplayName("getUserDashboard - 无选课/学习记录时返回默认值")
    void testGetUserDashboard_EmptyData() {
        when(enrollmentService.count(any())).thenReturn(0L);
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(submissionService.count(any())).thenReturn(0L);

        Map<String, Object> data = analyticsService.getUserDashboard(7L);

        assertEquals(0L, data.get("enrolledCourses"));
        assertEquals(0.0, (Double) data.get("averageProgress"), 0.001);
        assertEquals(0, data.get("totalStudyTime"));
        assertEquals(0.0, (Double) data.get("averageScore"), 0.001);
        assertEquals(0, data.get("streakDays"));
        assertEquals(0L, data.get("pendingTasks"));
    }

    @Test
    @DisplayName("getLearningTrends - 按日期分组累加学习时长")
    void testGetLearningTrends_GroupsByDate() {
        LocalDateTime today = LocalDateTime.now();
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                record(7L, 30, today.withHour(10)),
                record(7L, 60, today.withHour(14)),
                record(7L, 15, today.minusDays(1).withHour(9))
        ));

        List<Map<String, Object>> trends = analyticsService.getLearningTrends(7L);

        assertEquals(2, trends.size());
        Map<String, Object> todayItem = trends.get(0);
        assertEquals(today.toLocalDate().toString(), todayItem.get("date"));
        assertEquals(90, todayItem.get("duration"));
        assertEquals(today.minusDays(1).toLocalDate().toString(), trends.get(1).get("date"));
        assertEquals(15, trends.get(1).get("duration"));
    }

    @Test
    @DisplayName("getLearningHeatmap - 按日期统计并计算热力等级")
    void testGetLearningHeatmap_ComputesHeatLevels() {
        LocalDate start = LocalDate.now().minusDays(2);
        LocalDate end = LocalDate.now();
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                record(7L, 25, start.atStartOfDay()),       // <30 -> level 1
                record(7L, 65, start.plusDays(1).atStartOfDay()), // 60-120 -> level 3
                record(7L, 200, end.atStartOfDay())         // >180 -> level 5
        ));

        List<Map<String, Object>> heatmap = analyticsService.getLearningHeatmap(7L, start, end);

        assertEquals(3, heatmap.size());
        for (Map<String, Object> item : heatmap) {
            int duration = (Integer) item.get("duration");
            int level = (Integer) item.get("level");
            if (duration == 25) assertEquals(1, level);
            if (duration == 65) assertEquals(3, level);
            if (duration == 200) assertEquals(5, level);
        }
        // 日期范围参数确实传给了查询
        verify(learningRecordService).list(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("getPlatformHeatmap - 返回完整 7x4 网格")
    void testGetPlatformHeatmap_ReturnsFullGrid() {
        LocalDateTime now = LocalDateTime.now();
        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                record(1L, 10, now.withHour(10)),  // 当天：slot 1
                record(2L, 10, now.withHour(20))   // 当天：slot 3
        ));

        List<Map<String, Object>> grid = analyticsService.getPlatformHeatmap(7);

        assertEquals(28, grid.size());
        // 完整网格中所有格子都有值，未命中的为 0
        for (Map<String, Object> cell : grid) {
            assertNotNull(cell.get("dayOfWeek"));
            assertNotNull(cell.get("hour"));
            assertNotNull(cell.get("value"));
        }
        // 当天的两个时段有记录
        int dayOfWeek = now.getDayOfWeek().getValue() % 7;
        long morningCount = grid.stream()
                .filter(c -> c.get("dayOfWeek").equals(dayOfWeek) && c.get("hour").equals(1))
                .mapToLong(c -> (Long) c.get("count"))
                .sum();
        long eveningCount = grid.stream()
                .filter(c -> c.get("dayOfWeek").equals(dayOfWeek) && c.get("hour").equals(3))
                .mapToLong(c -> (Long) c.get("count"))
                .sum();
        assertEquals(1L, morningCount);
        assertEquals(1L, eveningCount);
    }

    @Test
    @DisplayName("getGradeDistribution - 计算平均分与及格人数")
    void testGetGradeDistribution_ComputesAverageAndPassed() {
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                enrollment(1L, 1L, null, new BigDecimal("95")),
                enrollment(1L, 2L, null, new BigDecimal("55")),
                enrollment(1L, 3L, null, null),
                enrollment(1L, 4L, null, new BigDecimal("70"))
        ));

        Map<String, Object> grade = analyticsService.getGradeDistribution(1L);

        assertEquals(4, grade.get("total"));
        assertEquals(73.33, (Double) grade.get("average"), 0.001);
        assertEquals(2L, grade.get("passed"));
    }

    @Test
    @DisplayName("getGradeDistributionHistogram - 按分数段统计分布")
    void testGetGradeDistributionHistogram_BucketsScores() {
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                enrollment(1L, 1L, null, new BigDecimal("95")),
                enrollment(1L, 2L, null, new BigDecimal("55")),
                enrollment(1L, 3L, null, new BigDecimal("70")),
                enrollment(1L, 4L, null, new BigDecimal("85"))
        ));

        Map<String, Object> histogram = analyticsService.getGradeDistributionHistogram(1L);

        assertEquals(4, histogram.get("total"));
        assertEquals(76.25, (Double) histogram.get("average"), 0.001);
        assertEquals(95.0, (Double) histogram.get("maxScore"), 0.001);
        assertEquals(55.0, (Double) histogram.get("minScore"), 0.001);

        @SuppressWarnings("unchecked")
        Map<String, Long> distribution = (Map<String, Long>) histogram.get("distribution");
        assertEquals(1L, distribution.get("90-100（优秀）"));
        assertEquals(1L, distribution.get("80-89（良好）"));
        assertEquals(1L, distribution.get("70-79（中等）"));
        assertEquals(1L, distribution.get("0-59（不及格）"));
        assertEquals(75.0, (Double) histogram.get("passRate"), 0.001);
        assertEquals(25.0, (Double) histogram.get("excellentRate"), 0.001);
    }

    @Test
    @DisplayName("getCourseAnalysis - 课程存在时返回课程与统计信息")
    void testGetCourseAnalysis_WithCourse() {
        Course course = course(3L, "Java", "J101", "技术", "中等", 10L, true, LocalDateTime.now());
        when(courseService.getById(3L)).thenReturn(course);
        when(enrollmentService.count(any())).thenReturn(5L);
        when(taskService.count(any())).thenReturn(3L);
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                enrollment(3L, 1L, new BigDecimal("80"), new BigDecimal("90")),
                enrollment(3L, 2L, new BigDecimal("100"), null),
                enrollment(3L, 3L, null, new BigDecimal("50"))
        ));

        Map<String, Object> analysis = analyticsService.getCourseAnalysis(3L);

        assertSame(course, analysis.get("courseInfo"));
        assertEquals(5L, analysis.get("enrollmentCount"));
        assertEquals(3L, analysis.get("taskCount"));
        // (80 + 100 + 0) / 3 = 60
        assertEquals(60.0, (Double) analysis.get("averageProgress"), 0.001);
        // (90 + 50) / 2 = 70
        assertEquals(70.0, (Double) analysis.get("averageScore"), 0.001);
    }

    @Test
    @DisplayName("getCourseAnalysis - 课程不存在返回错误提示")
    void testGetCourseAnalysis_CourseNotFound() {
        when(courseService.getById(999L)).thenReturn(null);

        Map<String, Object> analysis = analyticsService.getCourseAnalysis(999L);

        assertEquals("课程不存在", analysis.get("error"));
    }

    @Test
    @DisplayName("getCourseRanking - 按选课人数降序返回前20门课程")
    void testGetCourseRanking_SortedByEnrollment() {
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                course(1L, "Java", "J101", "技术", "中等", 10L, true, LocalDateTime.now()),
                course(2L, "Python", "P101", "技术", "简单", 10L, true, LocalDateTime.now()),
                course(3L, "数学", "M101", "数学", "困难", 11L, true, LocalDateTime.now())
        ));
        when(enrollmentService.count(any())).thenReturn(5L, 10L, 2L);

        List<Map<String, Object>> ranking = analyticsService.getCourseRanking();

        assertEquals(3, ranking.size());
        assertEquals(2L, ranking.get(0).get("courseId"));
        assertEquals(10L, ranking.get(0).get("enrollmentCount"));
        assertEquals("Python", ranking.get(0).get("courseName"));
        assertEquals(1L, ranking.get(1).get("courseId"));
        assertEquals(3L, ranking.get(2).get("courseId"));
        assertEquals("数学", ranking.get(2).get("courseName"));
    }

    @Test
    @DisplayName("getUserGrowthTrend - 填充全部日期并累计用户数")
    void testGetUserGrowthTrend_FillsAllDates() {
        LocalDate today = LocalDate.now();
        when(userService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                userCreatedAt(today.minusDays(2).atStartOfDay()),
                userCreatedAt(today.minusDays(1).atStartOfDay()),
                userCreatedAt(today.atStartOfDay())
        ));

        List<Map<String, Object>> trend = analyticsService.getUserGrowthTrend(3);

        assertEquals(3, trend.size());
        assertEquals(today.minusDays(2).toString(), trend.get(0).get("date"));
        assertEquals(1L, trend.get(0).get("newUsers"));
        assertEquals(1L, trend.get(0).get("cumulativeUsers"));
        assertEquals(today.toString(), trend.get(2).get("date"));
        assertEquals(1L, trend.get(2).get("newUsers"));
        assertEquals(3L, trend.get(2).get("cumulativeUsers"));
    }

    @Test
    @DisplayName("getUserGrowthTrend - 非法天数回退为30天并全零填充")
    void testGetUserGrowthTrend_InvalidDaysDefaultsTo30() {
        when(userService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> trend = analyticsService.getUserGrowthTrend(0);

        assertEquals(30, trend.size());
        for (Map<String, Object> item : trend) {
            assertEquals(0L, item.get("newUsers"));
            assertEquals(0L, item.get("cumulativeUsers"));
        }
    }

    @Test
    @DisplayName("getExamStatistics - 试卷存在时聚合会话成绩统计")
    void testGetExamStatistics_WithSessions() {
        ExamPaper paper = new ExamPaper();
        paper.setId(1L);
        paper.setTitle("期末测试");
        paper.setTotalScore(new BigDecimal("100"));
        paper.setQuestionCount(10);
        paper.setDuration(60);
        when(examPaperService.getById(1L)).thenReturn(paper);

        ExamSession s1 = new ExamSession();
        s1.setTotalScore(new BigDecimal("90"));
        s1.setPercentage(new BigDecimal("90.00"));
        s1.setTimeSpent(3000);
        ExamSession s2 = new ExamSession();
        s2.setTotalScore(new BigDecimal("60"));
        s2.setPercentage(new BigDecimal("60.00"));
        s2.setTimeSpent(1200);
        ExamSession s3 = new ExamSession();
        s3.setTotalScore(null);
        s3.setPercentage(null);
        s3.setTimeSpent(null);
        when(examSessionService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(s1, s2, s3));

        Map<String, Object> statistics = analyticsService.getExamStatistics(1L);

        assertEquals(1L, statistics.get("examPaperId"));
        assertEquals("期末测试", statistics.get("examTitle"));
        assertEquals(3, statistics.get("totalParticipants"));
        assertEquals(75.0, (Double) statistics.get("averageScore"), 0.001);
        assertEquals(90.0, (Double) statistics.get("maxScore"), 0.001);
        assertEquals(60.0, (Double) statistics.get("minScore"), 0.001);
        // 及格线 60：90/60 两人及格 -> 2/3 = 66.67
        assertEquals(66.67, (Double) statistics.get("passRate"), 0.001);
        // 优秀线 85：90 一人 -> 1/3 = 33.33
        assertEquals(33.33, (Double) statistics.get("excellentRate"), 0.001);

        @SuppressWarnings("unchecked")
        Map<String, Long> scoreDistribution = (Map<String, Long>) statistics.get("scoreDistribution");
        assertEquals(1L, scoreDistribution.get("90-100（优秀）"));
        assertEquals(1L, scoreDistribution.get("60-69（及格）"));
        // 平均用时：(3000+1200)/2 = 2100
        assertEquals(2100L, statistics.get("avgTimeSpentMinutes"));
    }

    @Test
    @DisplayName("getExamStatistics - 试卷不存在返回错误提示")
    void testGetExamStatistics_ExamPaperNotFound() {
        when(examPaperService.getById(999L)).thenReturn(null);

        Map<String, Object> statistics = analyticsService.getExamStatistics(999L);

        assertEquals("试卷不存在", statistics.get("error"));
    }

    @Test
    @DisplayName("getTeacherDashboard - 聚合教师课程/学生/待批改/平均分")
    void testGetTeacherDashboard_AggregatesTeacherStats() {
        Course c1 = course(1L, "Java", "J101", "技术", "中等", 9L, true, LocalDateTime.now());
        Course c2 = course(2L, "Python", "P101", "技术", "简单", 9L, true, LocalDateTime.now());
        when(courseService.count(any())).thenReturn(2L);
        when(courseService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(c1, c2));

        Task t1 = new Task();
        t1.setId(11L);
        t1.setCourseId(1L);
        t1.setIsPublished(true);
        Task t2 = new Task();
        t2.setId(12L);
        t2.setCourseId(2L);
        t2.setIsPublished(true);
        Task t3 = new Task();
        t3.setId(13L);
        t3.setCourseId(1L);
        t3.setIsPublished(false);
        when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(t1, t2, t3));

        // studentCount + 每门课分布 = 5, 3, 2
        when(enrollmentService.count(any())).thenReturn(5L, 3L, 2L);
        // pendingGradeCount + 7 天趋势
        when(submissionService.count(any())).thenReturn(4L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

        Submission sub1 = new Submission();
        sub1.setScore(new BigDecimal("80"));
        Submission sub2 = new Submission();
        sub2.setScore(new BigDecimal("60"));
        when(submissionService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(sub1, sub2));

        Map<String, Object> dashboard = analyticsService.getTeacherDashboard(9L);

        assertEquals(2L, dashboard.get("courseCount"));
        assertEquals(2L, dashboard.get("totalCourses"));
        assertEquals(5L, dashboard.get("studentCount"));
        assertEquals(5L, dashboard.get("totalStudents"));
        assertEquals(4L, dashboard.get("pendingGradeCount"));
        assertEquals(4L, dashboard.get("pendingGrading"));
        assertEquals(70.0, (Double) dashboard.get("avgScore"), 0.001);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courseDistribution = (List<Map<String, Object>>) dashboard.get("courseDistribution");
        assertEquals(2, courseDistribution.size());
        assertEquals("Java", courseDistribution.get(0).get("name"));
        assertEquals(3L, courseDistribution.get(0).get("studentCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trendData = (List<Map<String, Object>>) dashboard.get("trendData");
        assertEquals(7, trendData.size());
        assertEquals(0L, trendData.get(0).get("count"));
        assertFalse(dashboard.containsKey("error"));
    }

    @Test
    @DisplayName("getAtRiskStudents - 按课程分组聚合（委托 TeachingAnalyticsComponent 全量实现）")
    void testGetAtRiskStudents_GroupsByCourse() {
        when(enrollmentService.list()).thenReturn(Arrays.asList(
                enrollment(1L, 1L, null, null),
                enrollment(1L, 2L, null, null),
                enrollment(2L, 3L, null, null)
        ));

        Map<String, Object> result = analyticsService.getAtRiskStudents("course", null, null, null, null);

        // 进度/成绩均为 null → 无人命中掉队判定，但分组聚合仍按课程维度返回
        assertEquals(3, result.get("totalStudents"));
        assertEquals(0, result.get("atRiskCount"));
        assertEquals(0.0, (Double) result.get("atRiskRate"), 0.001);
        assertEquals("course", result.get("groupBy"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        assertEquals(2, groups.size());
        Map<String, Object> group1 = groups.stream()
                .filter(i -> i.get("key").equals("课程#1")).findFirst().orElseThrow();
        assertEquals(2L, group1.get("total"));
        assertEquals(0L, group1.get("atRisk"));
        Map<String, Object> group2 = groups.stream()
                .filter(i -> i.get("key").equals("课程#2")).findFirst().orElseThrow();
        assertEquals(1L, group2.get("total"));
        assertEquals(0L, group2.get("atRisk"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
        assertTrue(details.isEmpty());
    }

    @Test
    @DisplayName("getAtRiskStudents - 进度<40 或 成绩<60 判定为掉队并输出明细")
    void testGetAtRiskStudents_DetectsRiskAndDetails() {
        when(enrollmentService.list()).thenReturn(Arrays.asList(
                enrollment(1L, 1L, BigDecimal.valueOf(30), null),   // 进度过低 → 掉队
                enrollment(1L, 2L, BigDecimal.valueOf(80), BigDecimal.valueOf(55)), // 成绩<60 → 掉队
                enrollment(2L, 3L, BigDecimal.valueOf(90), BigDecimal.valueOf(90))  // 正常
        ));

        Map<String, Object> result = analyticsService.getAtRiskStudents("course", null, null, null, null);

        assertEquals(3, result.get("totalStudents"));
        assertEquals(2, result.get("atRiskCount"));
        assertEquals(66.67, (Double) result.get("atRiskRate"), 0.01);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups = (List<Map<String, Object>>) result.get("groups");
        Map<String, Object> group1 = groups.stream()
                .filter(i -> i.get("key").equals("课程#1")).findFirst().orElseThrow();
        assertEquals(2L, group1.get("total"));
        assertEquals(2L, group1.get("atRisk"));
        assertEquals(100.0, (Double) group1.get("atRiskRate"), 0.001);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
        assertEquals(2, details.size());
        assertEquals(1L, details.get(0).get("studentId"));
        assertEquals(30.0, (Double) details.get(0).get("progress"), 0.001);
        assertEquals("MEDIUM", details.get(0).get("level"));
    }

    @Test
    @DisplayName("exportReport - 返回课程概览、选课人数、作业数与生成时间")
    void testExportReport_ContainsCourseStats() {
        Course course = new Course();
        course.setId(3L);
        course.setName("测试课程");
        when(courseService.getById(3L)).thenReturn(course);
        when(enrollmentService.count(any())).thenReturn(5L);
        when(taskService.count(any())).thenReturn(7L);
        when(enrollmentService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of());

        Map<String, Object> report = analyticsService.exportReport(3L);

        assertEquals(3L, report.get("courseId"));
        assertEquals(5L, report.get("enrollmentCount"));
        assertEquals(7L, report.get("taskCount"));
        assertNotNull(report.get("generatedAt"));
        assertNull(report.get("error"));
        assertNotNull(report.get("gradeHistogram"));

        // 课程不存在时返回错误标记
        when(courseService.getById(99L)).thenReturn(null);
        assertEquals("课程不存在", analyticsService.exportReport(99L).get("error"));
    }

    @Test
    @DisplayName("getStudentCourseProgressList - 按课程聚合每位学生学习进度与成绩")
    void testGetStudentCourseProgressList_AggregatesPerStudent() {
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                enrollment(1L, 101L, new BigDecimal("100"), null),
                enrollment(1L, 102L, new BigDecimal("50"), null)
        ));
        User u1 = new User();
        u1.setId(101L);
        u1.setFullName("张三");
        u1.setStudentId("20260001");
        User u2 = new User();
        u2.setId(102L);
        u2.setFullName("李四");
        u2.setStudentId("20260002");
        when(userService.listByIds(any())).thenReturn(Arrays.asList(u1, u2));

        Task t1 = new Task();
        t1.setId(11L);
        t1.setCourseId(1L);
        t1.setTaskType("VIDEO");
        Task t2 = new Task();
        t2.setId(12L);
        t2.setCourseId(1L);
        t2.setTaskType("DOC");
        Task t3 = new Task();
        t3.setId(13L);
        t3.setCourseId(1L);
        t3.setTaskType("ASSIGNMENT");
        when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(t1, t2, t3));

        TaskCompletion c1 = new TaskCompletion();
        c1.setStudentId(101L);
        c1.setTaskId(11L);
        c1.setCompleted(true);
        when(taskCompletionMapper.selectList(any())).thenReturn(Collections.singletonList(c1));

        Submission s1 = new Submission(); // 张三 作业已提交有分 → 计入完成与均分
        s1.setTaskId(13L);
        s1.setStudentId(101L);
        s1.setStatus("SUBMITTED");
        s1.setScore(new BigDecimal("80"));
        Submission s2 = new Submission(); // 李四 作业已批改 → 计入完成与均分
        s2.setTaskId(13L);
        s2.setStudentId(102L);
        s2.setStatus("GRADED");
        s2.setScore(new BigDecimal("90"));
        Submission s3 = new Submission(); // 张三 草稿不计入完成/均分
        s3.setTaskId(12L);
        s3.setStudentId(101L);
        s3.setStatus("DRAFT");
        s3.setScore(new BigDecimal("10"));
        when(submissionService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(s1, s2, s3));

        ExamSession es1 = new ExamSession();
        es1.setStudentId(101L);
        es1.setPercentage(new BigDecimal("85"));
        es1.setCreatedAt(LocalDateTime.of(2026, 7, 30, 9, 30));
        when(examSessionService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(es1));

        List<Map<String, Object>> list = analyticsService.getStudentCourseProgressList(1L);

        assertEquals(2, list.size());
        // 进度 100 的张三排第一
        assertEquals(101L, list.get(0).get("studentId"));
        assertEquals("张三", list.get(0).get("studentName"));
        assertEquals("20260001", list.get(0).get("studentNo"));
        assertEquals(3L, list.get(0).get("totalTasks"));
        // 完成 = 视频完成 + 作业提交 = 2（草稿不计）
        assertEquals(2, list.get(0).get("completedTasks"));
        assertEquals(80.0, (Double) list.get(0).get("avgAssignmentScore"), 0.001);
        assertEquals(85.0, (Double) list.get(0).get("avgExamScore"), 0.001);
        // 李四：仅作业完成
        assertEquals(102L, list.get(1).get("studentId"));
        assertEquals(1, list.get(1).get("completedTasks"));
        assertEquals(90.0, (Double) list.get(1).get("avgAssignmentScore"), 0.001);
        assertNull(list.get(1).get("avgExamScore"));
        assertNotNull(list.get(0).get("lastActiveAt"));
    }

    @Test
    @DisplayName("getStudentCourseProgressList - 无选课学生返回空列表")
    void testGetStudentCourseProgressList_NoEnrollment() {
        when(enrollmentService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        assertTrue(analyticsService.getStudentCourseProgressList(1L).isEmpty());
    }

    @Test
    @DisplayName("getStudentCourseDetail - 章节任务点/作业成绩/考试成绩明细")
    void testGetStudentCourseDetail_BuildsDetail() {
        User u = new User();
        u.setId(101L);
        u.setFullName("张三");
        u.setStudentId("20260001");
        when(userService.getById(101L)).thenReturn(u);
        when(enrollmentService.getOne(any(LambdaQueryWrapper.class)))
                .thenReturn(enrollment(1L, 101L, new BigDecimal("100"), new BigDecimal("88")));

        CourseChapter ch1 = new CourseChapter();
        ch1.setId(1L);
        ch1.setCourseId(1L);
        ch1.setTitle("第一章 计算机网络概述");
        ch1.setOrderIndex(1);
        CourseChapter ch2 = new CourseChapter();
        ch2.setId(2L);
        ch2.setCourseId(1L);
        ch2.setTitle("第二章 物理层");
        ch2.setOrderIndex(2);
        when(courseChapterService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(ch1, ch2));

        Task t1 = new Task();
        t1.setId(11L);
        t1.setCourseId(1L);
        t1.setChapterId(1L);
        t1.setTaskType("VIDEO");
        t1.setTitle("第一节 视频");
        Task t2 = new Task();
        t2.setId(12L);
        t2.setCourseId(1L);
        t2.setChapterId(1L);
        t2.setTaskType("QUIZ");
        t2.setTitle("小测验");
        Task t3 = new Task();
        t3.setId(13L);
        t3.setCourseId(1L);
        t3.setChapterId(2L);
        t3.setTaskType("ASSIGNMENT");
        t3.setTitle("作业一");
        when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(t1, t2, t3));

        TaskCompletion c1 = new TaskCompletion();
        c1.setStudentId(101L);
        c1.setTaskId(11L);
        c1.setCompleted(true);
        when(taskCompletionMapper.selectList(any())).thenReturn(Collections.singletonList(c1));

        Submission s1 = new Submission();
        s1.setTaskId(13L);
        s1.setStudentId(101L);
        s1.setStatus("GRADED");
        s1.setScore(new BigDecimal("80"));
        s1.setMaxScore(new BigDecimal("100"));
        when(submissionService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(s1));

        ExamPaper paper = new ExamPaper();
        paper.setId(21L);
        paper.setCourseId(1L);
        paper.setTitle("期末考试");
        paper.setTotalScore(new BigDecimal("100"));
        ExamSession es = new ExamSession();
        es.setId(31L);
        es.setStudentId(101L);
        es.setExamPaperId(21L);
        es.setStatus("GRADED");
        es.setPercentage(new BigDecimal("92"));
        when(examSessionService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(es));
        when(examPaperService.listByIds(any())).thenReturn(Collections.singletonList(paper));

        Map<String, Object> detail = analyticsService.getStudentCourseDetail(1L, 101L);

        assertEquals("张三", detail.get("studentName"));
        assertEquals(new BigDecimal("100"), detail.get("progress"));
        assertEquals(new BigDecimal("88"), detail.get("finalScore"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) detail.get("chapters");
        assertEquals(2, chapters.size());
        assertEquals("第一章 计算机网络概述", chapters.get(0).get("title"));
        // 第一章：视频完成 + 测验未完成 = 1/2；作业提交也算完成 → 第二章 1/1
        assertEquals(2, chapters.get(0).get("totalTasks"));
        assertEquals(1, chapters.get(0).get("completedTasks"));
        assertEquals(1, chapters.get(1).get("completedTasks"));
        assertEquals(3, detail.get("totalTasks"));
        assertEquals(2, detail.get("completedTasks"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) detail.get("assignments");
        assertEquals(1, assignments.size());
        assertEquals("作业一", assignments.get(0).get("title"));
        assertEquals(new BigDecimal("80"), assignments.get(0).get("score"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exams = (List<Map<String, Object>>) detail.get("exams");
        assertEquals(1, exams.size());
        assertEquals("期末考试", exams.get(0).get("title"));
        assertEquals(new BigDecimal("92"), exams.get(0).get("percentage"));
        assertEquals(21L, exams.get(0).get("examPaperId"));
    }
}
