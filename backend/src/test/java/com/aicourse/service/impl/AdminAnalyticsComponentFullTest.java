package com.aicourse.service.impl;
import com.aicourse.service.component.*;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.LearningRecord;
import com.aicourse.entity.User;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdminAnalyticsComponent 覆盖率补充测试。
 * 重点补足原 AdminAnalyticsComponentTest 未覆盖的分支：
 *  - getDashboard 成绩分布空分支（enrollsWithScore 为空）
 *  - getDashboard 异常分支（协作者抛错后填充默认值）
 *  - getPlatformOverview 正常路径
 *  - getPlatformHeatmap 的 days<=0 回退、全部时段(slot 0..3)、null createdAt 过滤
 *  - getUserGrowthTrend 的 days<=0 回退、null createdAt 过滤、累计用户数
 */
@ExtendWith(MockitoExtension.class)
class AdminAnalyticsComponentFullTest {

    @Mock private UserService userService;
    @Mock private CourseService courseService;
    @Mock private ExamPaperService examPaperService;
    @Mock private SubmissionService submissionService;
    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private LearningRecordService learningRecordService;
    @Mock private TaskService taskService;

    // 缓存分支测试用（默认无 Redis 降级直算；缓存用例单独构造带 mock Redis 的组件）
    @Mock private ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ObjectMapper objectMapper;

    private AdminAnalyticsComponent adminAnalytics;

    private Course courseWithCategory;
    private Course courseWithoutCategory;

    @BeforeEach
    void setUp() {
        // 默认无 Redis（redisProvider 传 null -> 降级直算）；缓存分支用例另行构造
        adminAnalytics = new AdminAnalyticsComponent(
                userService, courseService, examPaperService, submissionService,
                enrollmentService, learningRecordService, taskService,
                null, new ObjectMapper());

        courseWithCategory = new Course();
        courseWithCategory.setId(1L);
        courseWithCategory.setName("Java");
        courseWithCategory.setCategory("计算机");
        courseWithCategory.setIsActive(true);

        courseWithoutCategory = new Course();
        courseWithoutCategory.setId(2L);
        courseWithoutCategory.setName("无分类课");
        courseWithoutCategory.setCategory(null);
        courseWithoutCategory.setIsActive(true);
    }

    // ===== getDashboard：成功路径（含成绩非空分布）=====
    @Test
    @DisplayName("getDashboard - 成功聚合且成绩分布非空（优秀/良好/中等/及格/不及格各一）")
    void testGetDashboard_SuccessWithGradeDistribution() {
        when(userService.count()).thenReturn(100L);
        when(userService.count(any(LambdaQueryWrapper.class))).thenReturn(80L, 10L, 60L, 30L, 10L);
        when(courseService.count()).thenReturn(20L);
        when(courseService.count(any(LambdaQueryWrapper.class))).thenReturn(15L);
        when(courseService.listMaps(any(QueryWrapper.class))).thenReturn(List.of(row("category", "计算机", "cnt", 1L)));
        when(examPaperService.count()).thenReturn(50L);
        when(submissionService.count()).thenReturn(500L);
        when(enrollmentService.count()).thenReturn(200L);
        // SQL 聚合行：completed=0（无进度>=100）；分数段 95/85/75/65/55 各 1
        when(enrollmentService.listMaps(any(QueryWrapper.class))).thenReturn(Collections.singletonList(
                row("total", 200L, "completed", 0L, "excellent", 1L, "good", 1L,
                        "medium", 1L, "pass", 1L, "fail", 1L)));

        when(taskService.count()).thenReturn(30L);
        when(taskService.count(any(LambdaQueryWrapper.class))).thenReturn(25L);
        when(learningRecordService.count(any(LambdaQueryWrapper.class))).thenReturn(50L);

        Map<String, Object> dashboard = adminAnalytics.getDashboard();

        assertNotNull(dashboard);
        assertFalse(dashboard.containsKey("error"));
        assertEquals(100L, dashboard.get("totalUsers"));
        assertEquals(20L, dashboard.get("totalCourses"));
        assertEquals(50L, dashboard.get("totalExams"));
        assertEquals(500L, dashboard.get("submissionCount"));
        assertEquals(200L, dashboard.get("totalEnrollments"));
        assertEquals(30L, dashboard.get("totalTasks"));
        assertEquals(25L, dashboard.get("publishedTasks"));
        assertEquals(50L, dashboard.get("dailyActiveUsers"));

        // 完成率：0 个进度>=100 / 200 = 0
        assertEquals(0.0, (Double) dashboard.get("completionRate"), 0.001);
        // 增长率：10 / 100 = 10
        assertEquals(10.0, (Double) dashboard.get("growthRate"), 0.001);
        assertEquals(10.0, (Double) dashboard.get("userGrowth"), 0.001);

        @SuppressWarnings("unchecked")
        Map<String, Long> categoryDist = (Map<String, Long>) dashboard.get("categoryDistribution");
        assertEquals(1L, categoryDist.get("计算机"));

        assertEquals(Arrays.asList(1L, 1L, 1L, 1L, 1L), dashboard.get("gradeDistribution"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roleActivity = (List<Map<String, Object>>) dashboard.get("roleActivity");
        assertEquals(3, roleActivity.size());
        assertEquals(30L, roleActivity.get(0).get("loginCount"));   // ADMIN=10 -> 30/50
        assertEquals(150L, roleActivity.get(1).get("loginCount"));  // TEACHER=30 -> 150/300
        assertEquals(600L, roleActivity.get(2).get("loginCount"));  // STUDENT=60 -> 600/1200
    }

    // ===== getDashboard：成绩分布为空分支（所有 finalScore 为 null）=====
    @Test
    @DisplayName("getDashboard - 选课记录无成绩时 gradeDistribution 返回全零")
    void testGetDashboard_GradeDistributionEmpty() {
        when(userService.count()).thenReturn(100L);
        when(userService.count(any(LambdaQueryWrapper.class))).thenReturn(80L, 10L, 60L, 30L, 10L);
        when(courseService.count()).thenReturn(20L);
        when(courseService.count(any(LambdaQueryWrapper.class))).thenReturn(15L);
        when(courseService.listMaps(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(examPaperService.count()).thenReturn(50L);
        when(submissionService.count()).thenReturn(500L);
        when(enrollmentService.count()).thenReturn(200L);
        // SQL 聚合行：completed=1（唯一选课进度 100）；所有分数段为 0
        when(enrollmentService.listMaps(any(QueryWrapper.class))).thenReturn(Collections.singletonList(
                row("total", 200L, "completed", 1L, "excellent", 0L, "good", 0L,
                        "medium", 0L, "pass", 0L, "fail", 0L)));

        when(taskService.count()).thenReturn(30L);
        when(taskService.count(any(LambdaQueryWrapper.class))).thenReturn(25L);
        when(learningRecordService.count(any(LambdaQueryWrapper.class))).thenReturn(50L);

        Map<String, Object> dashboard = adminAnalytics.getDashboard();

        // 完成率：1 个进度>=100 / 200 = 0.5
        assertEquals(0.5, (Double) dashboard.get("completionRate"), 0.001);
        assertEquals(Arrays.asList(0L, 0L, 0L, 0L, 0L), dashboard.get("gradeDistribution"));
    }

    // ===== getDashboard：异常分支 =====
    @Test
    @DisplayName("getDashboard - 协作者抛错时返回 error 并填充默认值")
    void testGetDashboard_ExceptionFillsDefaults() {
        when(userService.count()).thenThrow(new RuntimeException("db down"));

        Map<String, Object> dashboard = adminAnalytics.getDashboard();

        assertTrue(dashboard.containsKey("error"));
        assertEquals(0L, dashboard.get("userCount"));
        assertEquals(0L, dashboard.get("courseCount"));
        assertEquals(0L, dashboard.get("examCount"));
        assertEquals(0L, dashboard.get("submissionCount"));
        @SuppressWarnings("unchecked")
        Map<String, Long> categoryDist = (Map<String, Long>) dashboard.get("categoryDistribution");
        assertTrue(categoryDist.isEmpty());
        assertEquals(Arrays.asList(0L, 0L, 0L, 0L, 0L), dashboard.get("gradeDistribution"));
        @SuppressWarnings("unchecked")
        List<Object> roleActivity = (List<Object>) dashboard.get("roleActivity");
        assertTrue(roleActivity.isEmpty());
        // error 结果不入缓存
        verify(redisTemplate, never()).opsForValue();
    }

    // ===== getDashboard：Redis 缓存分支（60s 短缓存，error 不入缓存）=====

    @Test
    @DisplayName("getDashboard - 缓存命中直接返回，不重算任何聚合")
    void testGetDashboard_CacheHit() throws Exception {
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        AdminAnalyticsComponent cached = new AdminAnalyticsComponent(
                userService, courseService, examPaperService, submissionService,
                enrollmentService, learningRecordService, taskService,
                redisProvider, objectMapper);
        Map<String, Object> cachedData = new HashMap<>();
        cachedData.put("totalUsers", 999L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn("{\"totalUsers\":999}");
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(cachedData);

        Map<String, Object> dashboard = cached.getDashboard();

        assertSame(cachedData, dashboard);
        assertEquals(999L, dashboard.get("totalUsers"));
        // 未触发任何重算
        verify(userService, never()).count();
        verify(enrollmentService, never()).listMaps(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("getDashboard - 缓存未命中则重算并回写缓存（60s）")
    void testGetDashboard_CacheMissThenWrite() throws Exception {
        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        AdminAnalyticsComponent cached = new AdminAnalyticsComponent(
                userService, courseService, examPaperService, submissionService,
                enrollmentService, learningRecordService, taskService,
                redisProvider, objectMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null); // miss

        when(userService.count()).thenReturn(100L);
        when(userService.count(any(LambdaQueryWrapper.class))).thenReturn(80L, 10L, 60L, 30L, 10L);
        when(courseService.count()).thenReturn(20L);
        when(courseService.count(any(LambdaQueryWrapper.class))).thenReturn(15L);
        when(courseService.listMaps(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        when(examPaperService.count()).thenReturn(50L);
        when(submissionService.count()).thenReturn(500L);
        when(enrollmentService.count()).thenReturn(200L);
        when(enrollmentService.listMaps(any(QueryWrapper.class))).thenReturn(Collections.singletonList(
                row("total", 200L, "completed", 0L, "excellent", 0L, "good", 0L,
                        "medium", 0L, "pass", 0L, "fail", 0L)));
        when(taskService.count()).thenReturn(30L);
        when(taskService.count(any(LambdaQueryWrapper.class))).thenReturn(25L);
        when(learningRecordService.count(any(LambdaQueryWrapper.class))).thenReturn(50L);
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{}");

        Map<String, Object> dashboard = cached.getDashboard();

        assertFalse(dashboard.containsKey("error"));
        assertEquals(100L, dashboard.get("totalUsers"));
        // 回写缓存（60s TTL）
        verify(valueOps).set(eq("analytics:dashboard:v1"), eq("{}"), eq(java.time.Duration.ofSeconds(60)));
    }

    // ===== getPlatformOverview：成功路径 =====
    @Test
    @DisplayName("getPlatformOverview - 聚合总览指标")
    void testGetPlatformOverview_Success() {
        when(userService.count()).thenReturn(100L);
        when(userService.count(any(LambdaQueryWrapper.class))).thenReturn(80L);
        when(courseService.count()).thenReturn(20L);
        when(courseService.count(any(LambdaQueryWrapper.class))).thenReturn(15L);
        when(enrollmentService.count()).thenReturn(50L);
        when(enrollmentService.list()).thenReturn(Arrays.asList(
                enrollment(100, null),
                enrollment(50, null)
        ));
        when(learningRecordService.count(any(LambdaQueryWrapper.class))).thenReturn(40L);
        when(taskService.count()).thenReturn(30L);
        when(taskService.count(any(LambdaQueryWrapper.class))).thenReturn(25L);
        when(examPaperService.count()).thenReturn(5L);

        Map<String, Object> overview = adminAnalytics.getPlatformOverview();

        assertEquals(100L, overview.get("totalUsers"));
        assertEquals(80L, overview.get("activeUsers"));
        assertEquals(20L, overview.get("totalCourses"));
        assertEquals(15L, overview.get("activeCourses"));
        assertEquals(50L, overview.get("totalEnrollments"));
        assertEquals(40L, overview.get("activeLearningUsers"));
        assertEquals(40.0, (Double) overview.get("activityRate"), 0.001);
        assertEquals(1L, overview.get("completedEnrollments")); // 进度100 一个
        assertEquals(2.0, (Double) overview.get("completionRate"), 0.001);
        assertEquals(30L, overview.get("totalTasks"));
        assertEquals(25L, overview.get("publishedTasks"));
        assertEquals(5L, overview.get("totalExams"));
    }

    // ===== getPlatformHeatmap：days<=0 回退，覆盖所有时间段与 null 过滤 =====
    @Test
    @DisplayName("getPlatformHeatmap - days<=0 回退30天，统计全部时段(slot 0..3)，过滤 null createdAt")
    void testGetPlatformHeatmap_DefaultDaysAllSlots() {
        LocalDateTime now = LocalDateTime.now();
        LearningRecord r0 = record(1L, now.withHour(3));   // slot 0 (0-6)
        LearningRecord r1 = record(2L, now.withHour(9));   // slot 1 (6-12)
        LearningRecord r2 = record(3L, now.withHour(15));  // slot 2 (12-18)
        LearningRecord r3 = record(4L, now.withHour(21));  // slot 3 (18-24)
        LearningRecord rNull = new LearningRecord();       // createdAt null -> 被过滤
        when(learningRecordService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(r0, r1, r2, r3, rNull));

        List<Map<String, Object>> grid = adminAnalytics.getPlatformHeatmap(0);

        assertEquals(28, grid.size()); // 7x4
        int dayOfWeek = now.getDayOfWeek().getValue() % 7;
        long slot0 = grid.stream().filter(c -> c.get("dayOfWeek").equals(dayOfWeek) && c.get("hour").equals(0))
                .mapToLong(c -> (Long) c.get("value")).sum();
        long slot1 = grid.stream().filter(c -> c.get("dayOfWeek").equals(dayOfWeek) && c.get("hour").equals(1))
                .mapToLong(c -> (Long) c.get("value")).sum();
        long slot2 = grid.stream().filter(c -> c.get("dayOfWeek").equals(dayOfWeek) && c.get("hour").equals(2))
                .mapToLong(c -> (Long) c.get("value")).sum();
        long slot3 = grid.stream().filter(c -> c.get("dayOfWeek").equals(dayOfWeek) && c.get("hour").equals(3))
                .mapToLong(c -> (Long) c.get("value")).sum();
        assertEquals(1L, slot0);
        assertEquals(1L, slot1);
        assertEquals(1L, slot2);
        assertEquals(1L, slot3);
    }

    @Test
    @DisplayName("getPlatformHeatmap - 指定 days>0 正常统计")
    void testGetPlatformHeatmap_ExplicitDays() {
        LocalDateTime now = LocalDateTime.now();
        when(learningRecordService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(record(1L, now.withHour(10))));
        List<Map<String, Object>> grid = adminAnalytics.getPlatformHeatmap(7);
        assertEquals(28, grid.size());
    }

    // ===== getUserGrowthTrend：days<=0 回退30天，null createdAt 过滤，累计用户 =====
    @Test
    @DisplayName("getUserGrowthTrend - 正常天数填充全部日期并累计用户，过滤 null createdAt")
    void testGetUserGrowthTrend_FillsDates() {
        LocalDate today = LocalDate.now();
        User u1 = new User(); u1.setCreatedAt(today.minusDays(2).atStartOfDay());
        User u2 = new User(); u2.setCreatedAt(today.minusDays(1).atStartOfDay());
        User u3 = new User(); u3.setCreatedAt(today.atStartOfDay());
        User uNull = new User(); uNull.setCreatedAt(null); // 被过滤
        when(userService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(u1, u2, u3, uNull));

        List<Map<String, Object>> trend = adminAnalytics.getUserGrowthTrend(3);

        assertEquals(3, trend.size());
        assertEquals(today.minusDays(2).toString(), trend.get(0).get("date"));
        assertEquals(1L, trend.get(0).get("newUsers"));
        assertEquals(1L, trend.get(0).get("cumulativeUsers"));
        assertEquals(today.toString(), trend.get(2).get("date"));
        assertEquals(1L, trend.get(2).get("newUsers"));
        assertEquals(3L, trend.get(2).get("cumulativeUsers"));
    }

    @Test
    @DisplayName("getUserGrowthTrend - days<=0 回退30天全零填充")
    void testGetUserGrowthTrend_InvalidDaysDefaults() {
        when(userService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        List<Map<String, Object>> trend = adminAnalytics.getUserGrowthTrend(-5);
        assertEquals(30, trend.size());
        for (Map<String, Object> item : trend) {
            assertEquals(0L, item.get("newUsers"));
            assertEquals(0L, item.get("cumulativeUsers"));
        }
    }

    /** 构造 SQL 聚合结果行（listMaps 返回的 Map 行） */
    private Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private CourseEnrollment enrollment(int progress, BigDecimal finalScore) {
        CourseEnrollment e = new CourseEnrollment();
        e.setProgress(BigDecimal.valueOf(progress));
        e.setFinalScore(finalScore);
        return e;
    }

    private LearningRecord record(long userId, LocalDateTime createdAt) {
        LearningRecord r = new LearningRecord();
        r.setUserId(userId);
        r.setDuration(10);
        r.setCreatedAt(createdAt);
        return r;
    }
}
