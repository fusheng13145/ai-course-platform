package com.aicourse.service.component.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.LearningRecord;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.User;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AdminAnalyticsComponent 的 Mockito 直调单测（2026-11-06 覆盖率批补件，I-04 热点）。
 *
 * <p>此前行覆盖 52%，且平台级统计是管理端看板的唯一数据源。本测试覆盖：
 * 校级 dashboard（含 Redis 缓存命中/写入两条旁路）、异常兜底（error 键 + 默认值）、
 * 综合总览、热力图 7x4 网格聚合、用户增长趋势补零、院系口径全链与空院系短路。</p>
 */
class AdminAnalyticsComponentTest {

    private com.aicourse.service.UserService userService;
    private com.aicourse.service.CourseService courseService;
    private com.aicourse.service.ExamPaperService examPaperService;
    private com.aicourse.service.SubmissionService submissionService;
    private com.aicourse.service.CourseEnrollmentService enrollmentService;
    private com.aicourse.service.LearningRecordService learningRecordService;
    private com.aicourse.service.TaskService taskService;
    private ObjectMapper objectMapper;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 的 SFunctional 解析依赖 MyBatis-Plus 的表信息缓存（测试无 Spring 上下文）
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, Course.class);
        TableInfoHelper.initTableInfo(assistant, CourseEnrollment.class);
        TableInfoHelper.initTableInfo(assistant, Task.class);
        TableInfoHelper.initTableInfo(assistant, ExamPaper.class);
        TableInfoHelper.initTableInfo(assistant, Submission.class);
        TableInfoHelper.initTableInfo(assistant, LearningRecord.class);
    }

    @BeforeEach
    void setUp() {
        userService = mock(com.aicourse.service.UserService.class);
        courseService = mock(com.aicourse.service.CourseService.class);
        examPaperService = mock(com.aicourse.service.ExamPaperService.class);
        submissionService = mock(com.aicourse.service.SubmissionService.class);
        enrollmentService = mock(com.aicourse.service.CourseEnrollmentService.class);
        learningRecordService = mock(com.aicourse.service.LearningRecordService.class);
        taskService = mock(com.aicourse.service.TaskService.class);
        objectMapper = new ObjectMapper();
    }

    private AdminAnalyticsComponent componentWithoutRedis() {
        return new AdminAnalyticsComponent(userService, courseService, examPaperService,
                submissionService, enrollmentService, learningRecordService, taskService,
                null, objectMapper);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<StringRedisTemplate> providerReturning(StringRedisTemplate template) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        return provider;
    }

    private User user(long id, LocalDateTime createdAt) {
        User user = new User();
        user.setId(id);
        user.setCreatedAt(createdAt);
        return user;
    }

    private LearningRecord record(LocalDateTime createdAt) {
        LearningRecord record = new LearningRecord();
        record.setCreatedAt(createdAt);
        return record;
    }

    @Test
    @DisplayName("校级 dashboard：聚合各服务计数并写入 60s 缓存")
    void dashboardAggregatesAndCaches() {
        when(userService.count()).thenReturn(10L);
        when(userService.count(any())).thenReturn(3L);
        when(courseService.count()).thenReturn(5L);
        when(courseService.count(any())).thenReturn(2L);
        when(courseService.listMaps(ArgumentMatchers.<Wrapper<Course>>any())).thenReturn(List.of(Map.of("category", "数学", "cnt", 3L)));
        when(examPaperService.count()).thenReturn(7L);
        when(submissionService.count()).thenReturn(20L);
        when(enrollmentService.count()).thenReturn(30L);
        when(enrollmentService.listMaps(ArgumentMatchers.<Wrapper<CourseEnrollment>>any())).thenReturn(List.of(Map.of(
                "completed", 12L, "excellent", 6L, "good", 3L, "medium", 2L, "pass", 1L, "fail", 0L)));
        when(taskService.count()).thenReturn(12L);
        when(taskService.count(any())).thenReturn(8L);
        when(learningRecordService.count(any())).thenReturn(6L);

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        AdminAnalyticsComponent component = new AdminAnalyticsComponent(userService, courseService,
                examPaperService, submissionService, enrollmentService, learningRecordService,
                taskService, providerReturning(redis), objectMapper);

        Map<String, Object> dashboard = component.getDashboard();

        assertThat(dashboard.get("totalUsers")).isEqualTo(10L);
        assertThat(dashboard.get("totalCourses")).isEqualTo(5L);
        assertThat(dashboard.get("totalExams")).isEqualTo(7L);
        assertThat(dashboard.get("submissionCount")).isEqualTo(20L);
        assertThat(dashboard.get("totalEnrollments")).isEqualTo(30L);
        assertThat(dashboard.get("totalTasks")).isEqualTo(12L);
        assertThat(dashboard.get("publishedTasks")).isEqualTo(8L);
        assertThat(dashboard.get("dailyActiveUsers")).isEqualTo(6L);
        assertThat(dashboard.get("completionRate")).isEqualTo(40.0);
        assertThat(dashboard.get("gradeDistribution")).isEqualTo(List.of(6L, 3L, 2L, 1L, 0L));
        assertThat(dashboard.get("categoryDistribution")).isEqualTo(Map.of("数学", 3L));
        assertThat(dashboard).doesNotContainKey("error");
        // 成功结果必须落缓存（60s TTL）
        verify(valueOps).set(eq("analytics:dashboard:v1"), anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("dashboard 缓存命中：直接返回缓存内容，不再触库")
    void dashboardCacheHitSkipsDatabase() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("analytics:dashboard:v1"))
                .thenReturn(objectMapper.writeValueAsString(Map.of("totalUsers", 99)));

        AdminAnalyticsComponent component = new AdminAnalyticsComponent(userService, courseService,
                examPaperService, submissionService, enrollmentService, learningRecordService,
                taskService, providerReturning(redis), objectMapper);

        Map<String, Object> dashboard = component.getDashboard();

        assertThat(dashboard.get("totalUsers")).isEqualTo(99);
        verify(userService, never()).count();
    }

    @Test
    @DisplayName("dashboard 聚合异常：落 error 键并填充默认值（错误结果不写缓存）")
    void dashboardErrorFallback() {
        when(courseService.count()).thenThrow(new RuntimeException("db down"));

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        AdminAnalyticsComponent component = new AdminAnalyticsComponent(userService, courseService,
                examPaperService, submissionService, enrollmentService, learningRecordService,
                taskService, providerReturning(redis), objectMapper);

        Map<String, Object> dashboard = component.getDashboard();

        assertThat(dashboard.get("error")).asString().contains("db down");
        assertThat(dashboard.get("userCount")).isEqualTo(0L);
        assertThat(dashboard.get("courseCount")).isEqualTo(0L);
        assertThat(dashboard.get("gradeDistribution")).isEqualTo(List.of(0L, 0L, 0L, 0L, 0L));
        // 含 error 的结果不得进缓存，避免错误被持续命中 60s
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("校级综合总览：完成率按 progress>=100 的选课占比")
    void platformOverview() {
        when(userService.count()).thenReturn(4L);
        when(userService.count(any())).thenReturn(2L);
        when(courseService.count()).thenReturn(3L);
        when(courseService.count(any())).thenReturn(1L);
        when(enrollmentService.count()).thenReturn(2L);
        when(learningRecordService.count(any())).thenReturn(1L);
        when(taskService.count()).thenReturn(5L);
        when(taskService.count(any())).thenReturn(4L);
        when(examPaperService.count()).thenReturn(6L);

        CourseEnrollment done = new CourseEnrollment();
        done.setProgress(BigDecimal.valueOf(100));
        CourseEnrollment halfway = new CourseEnrollment();
        halfway.setProgress(BigDecimal.valueOf(50));
        when(enrollmentService.list()).thenReturn(List.of(done, halfway));

        Map<String, Object> overview = componentWithoutRedis().getPlatformOverview();

        assertThat(overview.get("totalUsers")).isEqualTo(4L);
        assertThat(overview.get("activeLearningUsers")).isEqualTo(1L);
        assertThat(overview.get("completedEnrollments")).isEqualTo(1L);
        assertThat(overview.get("completionRate")).isEqualTo(50.0);
        assertThat(overview.get("totalExams")).isEqualTo(6L);
    }

    @Test
    @DisplayName("校级热力图：7x4 网格、按星期与时段聚合、null 记录被过滤")
    void platformHeatmapBuildsFullGrid() {
        LocalDateTime monday3am = LocalDate.of(2026, 11, 2).atTime(3, 0);  // MON → dayOfWeek 1, slot 0
        LocalDateTime monday15pm = LocalDate.of(2026, 11, 2).atTime(15, 0); // MON → 1, slot 2
        LocalDateTime sunday23pm = LocalDate.of(2026, 11, 1).atTime(23, 0); // SUN → 7%7=0, slot 3
        when(learningRecordService.list(ArgumentMatchers.<Wrapper<LearningRecord>>any())).thenReturn(List.of(
                record(monday3am), record(monday3am), record(monday15pm), record(sunday23pm),
                record(null)));

        List<Map<String, Object>> grid = componentWithoutRedis().getPlatformHeatmap(7);

        assertThat(grid).hasSize(28);
        assertThat(countOf(grid, 1, 0)).isEqualTo(2L);
        assertThat(countOf(grid, 1, 2)).isEqualTo(1L);
        assertThat(countOf(grid, 0, 3)).isEqualTo(1L); // 周日归位到 0 号行
        assertThat(countOf(grid, 4, 1)).isEqualTo(0L);
    }

    @Test
    @DisplayName("热力图 days<=0 时回落 30 天口径")
    void platformHeatmapDefaultsDays() {
        when(learningRecordService.list(ArgumentMatchers.<Wrapper<LearningRecord>>any())).thenReturn(List.of());
        componentWithoutRedis().getPlatformHeatmap(0);
        verify(learningRecordService).list(ArgumentMatchers.<Wrapper<LearningRecord>>any());
    }

    @Test
    @DisplayName("院系热力图：按本院用户过滤；院系为空时直接空网格")
    void collegeHeatmapScoped() {
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of(user(1L, null), user(2L, null)));
        when(learningRecordService.list(ArgumentMatchers.<Wrapper<LearningRecord>>any())).thenReturn(
                List.of(record(LocalDate.of(2026, 11, 2).atTime(9, 0))));

        List<Map<String, Object>> grid = componentWithoutRedis().getPlatformHeatmap(7, 3L);
        assertThat(grid).hasSize(28);
        assertThat(countOf(grid, 1, 1)).isEqualTo(1L);

        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of());
        List<Map<String, Object>> empty = componentWithoutRedis().getPlatformHeatmap(7, 3L);
        assertThat(empty).hasSize(28);
        assertThat(empty.stream().filter(cell -> ((Number) cell.get("value")).longValue() != 0)).isEmpty();
    }

    @Test
    @DisplayName("用户增长趋势：逐日补零并给累计值")
    void userGrowthTrendFillsZeroDays() {
        // 相对当天构造，避免硬编码日期跨日后断言漂移
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of(
                user(1L, LocalDate.now().minusDays(1).atTime(10, 0)),
                user(2L, LocalDate.now().minusDays(1).atTime(18, 0)),
                user(3L, LocalDate.now().atTime(9, 0)),
                user(4L, null)));

        List<Map<String, Object>> trend = componentWithoutRedis().getUserGrowthTrend(3);

        assertThat(trend).hasSize(3);
        assertThat(trend.get(2).get("date")).isEqualTo(LocalDate.now().toString());
        assertThat(trend.get(2).get("newUsers")).isEqualTo(1L);
        assertThat(trend.get(2).get("cumulativeUsers")).isEqualTo(3L); // null createdAt 的用户不计
        assertThat(trend.get(1).get("newUsers")).isEqualTo(2L);
        assertThat(trend.get(1).get("cumulativeUsers")).isEqualTo(2L);
    }

    @Test
    @DisplayName("增长趋势 days<=0 回落 30 天；院系口径按学院过滤")
    void userGrowthTrendDefaultsAndCollegeScope() {
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of());
        assertThat(componentWithoutRedis().getUserGrowthTrend(0)).hasSize(30);

        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of(user(9L, LocalDate.now().atStartOfDay())));
        List<Map<String, Object>> collegeTrend = componentWithoutRedis().getUserGrowthTrend(5, 7L);
        assertThat(collegeTrend).hasSize(5);
        assertThat(collegeTrend.get(4).get("cumulativeUsers")).isEqualTo(1L);
    }

    @Test
    @DisplayName("院系 dashboard：用户/课程取自本院清单，聚合按院系短路")
    void collegeDashboardScoped() {
        User u1 = user(1L, LocalDateTime.now().minusDays(1));
        User u2 = user(2L, LocalDateTime.now().minusDays(10));
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of(u1, u2));
        Course course = new Course();
        course.setId(101L);
        course.setCategory("编程");
        when(courseService.list(ArgumentMatchers.<Wrapper<Course>>any())).thenReturn(List.of(course));
        when(userService.count(any())).thenReturn(2L);
        when(courseService.count(any())).thenReturn(1L);
        when(examPaperService.count(any())).thenReturn(2L);
        when(submissionService.count(any())).thenReturn(5L);
        when(enrollmentService.count(any())).thenReturn(10L);
        when(enrollmentService.listMaps(ArgumentMatchers.<Wrapper<CourseEnrollment>>any())).thenReturn(List.of(Map.of(
                "completed", 4L, "excellent", 2L, "good", 1L, "medium", 1L, "pass", 0L, "fail", 0L)));
        when(taskService.count(any())).thenReturn(3L);
        when(learningRecordService.count(any())).thenReturn(4L);

        Map<String, Object> dashboard = componentWithoutRedis().getDashboard(3L);

        assertThat(dashboard.get("totalUsers")).isEqualTo(2L);
        assertThat(dashboard.get("totalCourses")).isEqualTo(1L);
        assertThat(dashboard.get("totalExams")).isEqualTo(2L);
        assertThat(dashboard.get("submissionCount")).isEqualTo(5L);
        assertThat(dashboard.get("totalEnrollments")).isEqualTo(10L);
        assertThat(dashboard.get("completionRate")).isEqualTo(40.0);
        assertThat(dashboard.get("categoryDistribution")).isEqualTo(Map.of("编程", 1L));
        assertThat(dashboard.get("dailyActiveUsers")).isEqualTo(4L);
    }

    @Test
    @DisplayName("院系 dashboard：本院无课程/用户时全部计数走空短路")
    void collegeDashboardEmptyScope() {
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of());
        when(courseService.list(ArgumentMatchers.<Wrapper<Course>>any())).thenReturn(List.of());

        Map<String, Object> dashboard = componentWithoutRedis().getDashboard(3L);

        assertThat(dashboard.get("totalUsers")).isEqualTo(0L);
        assertThat(dashboard.get("activeCourses")).isEqualTo(0L);
        assertThat(dashboard.get("totalExams")).isEqualTo(0L);
        assertThat(dashboard.get("submissionCount")).isEqualTo(0L);
        assertThat(dashboard.get("totalEnrollments")).isEqualTo(0L);
        assertThat(dashboard.get("totalTasks")).isEqualTo(0L);
        assertThat(dashboard.get("dailyActiveUsers")).isEqualTo(0L);
        assertThat(dashboard.get("growthRate")).isEqualTo(0.0);
        // 空院系不触任何 count 查询（列表结果已决定答案）
        verify(courseService, never()).count(any());
        verify(enrollmentService, never()).count(any());
    }

    @Test
    @DisplayName("院系综合总览：完成率按本院选课口径")
    void collegePlatformOverviewScoped() {
        when(userService.list(ArgumentMatchers.<Wrapper<User>>any())).thenReturn(List.of(user(1L, null), user(2L, null)));
        Course course = new Course();
        course.setId(101L);
        when(courseService.list(ArgumentMatchers.<Wrapper<Course>>any())).thenReturn(List.of(course));
        when(userService.count(any())).thenReturn(2L);
        // 两次 count（总选课数 → 已完成数）连续取值 4 → 2
        when(enrollmentService.count(any())).thenReturn(4L, 2L);
        when(learningRecordService.count(any())).thenReturn(2L);
        when(taskService.count(any())).thenReturn(1L);
        when(examPaperService.count(any())).thenReturn(3L);

        Map<String, Object> overview = componentWithoutRedis().getPlatformOverview(3L);

        assertThat(overview.get("totalUsers")).isEqualTo(2L);
        assertThat(overview.get("totalEnrollments")).isEqualTo(4L);
        assertThat(overview.get("completedEnrollments")).isEqualTo(2L);
        assertThat(overview.get("completionRate")).isEqualTo(50.0);
        assertThat(overview.get("totalExams")).isEqualTo(3L);
    }

    private static long countOf(List<Map<String, Object>> grid, int dayOfWeek, int slot) {
        return grid.stream()
                .filter(cell -> cell.get("dayOfWeek").equals(dayOfWeek) && cell.get("hour").equals(slot))
                .findFirst()
                .map(cell -> (Number) cell.get("value"))
                .orElseThrow()
                .longValue();
    }
}
