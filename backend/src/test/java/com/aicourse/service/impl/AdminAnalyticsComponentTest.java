package com.aicourse.service.impl;
import com.aicourse.service.component.analytics.AdminAnalyticsComponent;

import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdminAnalyticsComponent 单元测试（平台/管理员视角统计；getDashboard 采用 SQL 聚合下推实现）。
 * <p>无 Redis 场景（redisProvider 传 null -> 降级直算）；Redis 缓存分支见
 * {@code AdminAnalyticsComponentFullTest}。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminAnalyticsComponentTest {

    @Mock private UserService userService;
    @Mock private CourseService courseService;
    @Mock private ExamPaperService examPaperService;
    @Mock private SubmissionService submissionService;
    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private LearningRecordService learningRecordService;
    @Mock private TaskService taskService;

    private AdminAnalyticsComponent adminAnalytics;

    @BeforeEach
    void setUp() {
        adminAnalytics = new AdminAnalyticsComponent(
                userService, courseService, examPaperService, submissionService,
                enrollmentService, learningRecordService, taskService,
                null, new ObjectMapper());
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
    @DisplayName("仪表盘数据 - 包含所有必要字段（SQL 聚合下推）")
    void testGetDashboard_AllFields() {
        when(userService.count()).thenReturn(100L);
        when(userService.count(any())).thenReturn(80L, 10L, 70L, 1L, 1L);
        when(courseService.count()).thenReturn(20L);
        when(courseService.count(any())).thenReturn(15L);
        when(examPaperService.count()).thenReturn(50L);
        when(submissionService.count()).thenReturn(500L);
        when(enrollmentService.count()).thenReturn(200L);
        when(taskService.count()).thenReturn(30L);
        when(taskService.count(any())).thenReturn(25L);
        when(learningRecordService.count(any())).thenReturn(50L);
        // SQL 聚合：选课 200 条，进度>=100 共 40 条；分数段 1/1/1/1/1
        when(enrollmentService.listMaps(any(QueryWrapper.class))).thenReturn(Collections.singletonList(
                row("total", 200L, "completed", 40L, "excellent", 1L, "good", 1L,
                        "medium", 1L, "pass", 1L, "fail", 1L)));
        when(courseService.listMaps(any(QueryWrapper.class))).thenReturn(Arrays.asList(
                row("category", "计算机科学", "cnt", 1L)));

        Map<String, Object> dashboard = adminAnalytics.getDashboard();

        assertNotNull(dashboard);
        assertNotNull(dashboard.get("totalUsers"));
        assertNotNull(dashboard.get("activeUsers"));
        assertNotNull(dashboard.get("totalCourses"));
        assertNotNull(dashboard.get("activeCourses"));
        assertNotNull(dashboard.get("totalExams"));
        assertNotNull(dashboard.get("submissionCount"));
        assertEquals(1L, ((Map<?, ?>) dashboard.get("categoryDistribution")).get("计算机科学"));
        assertNotNull(dashboard.get("dailyActiveUsers"));
        assertEquals(20.0, (Double) dashboard.get("completionRate"), 0.001); // 40/200
        assertNotNull(dashboard.get("growthRate"));
        assertEquals(Arrays.asList(1L, 1L, 1L, 1L, 1L), dashboard.get("gradeDistribution"));
        assertNotNull(dashboard.get("roleActivity"));
    }
}
