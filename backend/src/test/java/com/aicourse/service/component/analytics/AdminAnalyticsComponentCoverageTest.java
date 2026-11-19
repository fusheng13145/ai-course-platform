package com.aicourse.service.component.analytics;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.LearningRecord;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.User;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 平台分析剩余分支：空聚合、成长率零分母、院系空集/过滤边界。
 */
class AdminAnalyticsComponentCoverageTest {

    private UserService userService;
    private CourseService courseService;
    private ExamPaperService examPaperService;
    private SubmissionService submissionService;
    private CourseEnrollmentService enrollmentService;
    private LearningRecordService learningRecordService;
    private TaskService taskService;
    private AdminAnalyticsComponent component;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, Course.class);
        TableInfoHelper.initTableInfo(assistant, CourseEnrollment.class);
        TableInfoHelper.initTableInfo(assistant, ExamPaper.class);
        TableInfoHelper.initTableInfo(assistant, Submission.class);
        TableInfoHelper.initTableInfo(assistant, Task.class);
        TableInfoHelper.initTableInfo(assistant, LearningRecord.class);
    }

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        courseService = mock(CourseService.class);
        examPaperService = mock(ExamPaperService.class);
        submissionService = mock(SubmissionService.class);
        enrollmentService = mock(CourseEnrollmentService.class);
        learningRecordService = mock(LearningRecordService.class);
        taskService = mock(TaskService.class);
        component = new AdminAnalyticsComponent(userService, courseService, examPaperService,
                submissionService, enrollmentService, learningRecordService, taskService,
                null, new ObjectMapper());
    }

    private User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @Test
    @DisplayName("校级 dashboard：类目脏数据过滤、选课聚合空表与全空字段")
    void dashboardDirtyCategoryAndEmptyAggregate() {
        when(userService.count()).thenReturn(0L);
        when(userService.count(any())).thenReturn(0L);
        when(courseService.count()).thenReturn(0L);
        when(courseService.count(any())).thenReturn(0L);
        when(examPaperService.count()).thenReturn(0L);
        when(submissionService.count()).thenReturn(0L);
        when(enrollmentService.count()).thenReturn(0L);
        when(taskService.count()).thenReturn(0L);
        when(taskService.count(any())).thenReturn(0L);
        when(learningRecordService.count(any())).thenReturn(0L);

        Map<String, Object> nullCat = new HashMap<>();
        nullCat.put("category", null); nullCat.put("cnt", 3L);
        Map<String, Object> emptyCat = new HashMap<>();
        emptyCat.put("category", ""); emptyCat.put("cnt", 2L);
        Map<String, Object> nullCnt = new HashMap<>();
        nullCnt.put("category", "编程"); nullCnt.put("cnt", null);
        Map<String, Object> valid = new HashMap<>();
        valid.put("category", "数学"); valid.put("cnt", 4L);
        when(courseService.listMaps(any(Wrapper.class))).thenReturn(List.of(nullCat, emptyCat, nullCnt, valid));

        Map<String, Object> emptyAgg = new HashMap<>();
        emptyAgg.put("completed", null); emptyAgg.put("excellent", null); emptyAgg.put("good", null);
        emptyAgg.put("medium", null); emptyAgg.put("pass", null); emptyAgg.put("fail", null);
        when(enrollmentService.listMaps(any(Wrapper.class))).thenReturn(List.of(emptyAgg));

        Map<String, Object> dashboard = component.getDashboard();

        assertEquals(Map.of("", 2L, "数学", 4L), dashboard.get("categoryDistribution"));
        assertEquals(0.0, dashboard.get("completionRate"));
        assertEquals(0.0, dashboard.get("growthRate"));
        assertEquals(List.of(0L, 0L, 0L, 0L, 0L), dashboard.get("gradeDistribution"));
    }

    @Test
    @DisplayName("校级 dashboard：选课聚合完全空表")
    void dashboardEmptyEnrollmentAggregate() {
        when(userService.count()).thenReturn(1L);
        when(enrollmentService.count()).thenReturn(1L);
        when(enrollmentService.listMaps(any(Wrapper.class))).thenReturn(List.of());
        when(courseService.listMaps(any(Wrapper.class))).thenReturn(List.of());

        Map<String, Object> dashboard = component.getDashboard();

        assertEquals(0.0, dashboard.get("completionRate"));
        assertEquals(0L, ((List<?>) dashboard.get("gradeDistribution")).get(0));
    }

    @Test
    @DisplayName("校级总览：零分母与 null 进度")
    void platformOverviewZeroAndNullProgress() {
        when(userService.count()).thenReturn(0L);
        when(userService.count(any())).thenReturn(0L);
        when(courseService.count()).thenReturn(0L);
        when(courseService.count(any())).thenReturn(0L);
        when(enrollmentService.count()).thenReturn(0L);
        when(learningRecordService.count(any())).thenReturn(0L);
        when(taskService.count()).thenReturn(0L);
        when(taskService.count(any())).thenReturn(0L);
        when(examPaperService.count()).thenReturn(0L);

        CourseEnrollment nullProgress = new CourseEnrollment();
        nullProgress.setProgress(null);
        CourseEnrollment done = new CourseEnrollment();
        done.setProgress(BigDecimal.valueOf(100));
        when(enrollmentService.list()).thenReturn(List.of(nullProgress, done));

        Map<String, Object> overview = component.getPlatformOverview();

        assertEquals(0.0, overview.get("activityRate"));
        assertEquals(0.0, overview.get("completionRate"));
        assertEquals(1L, overview.get("completedEnrollments"));
    }

    @Test
    @DisplayName("院系总览：空院系全短路；null 学院递归校级")
    void scopedOverviewEmptyAndNullCollege() {
        when(userService.list(any(Wrapper.class))).thenReturn(List.of());
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of());

        Map<String, Object> empty = component.getPlatformOverview(3L);
        assertEquals(0L, empty.get("totalUsers"));
        assertEquals(0L, empty.get("activeCourses"));
        assertEquals(0L, empty.get("totalEnrollments"));
        assertEquals(0L, empty.get("activeLearningUsers"));
        assertEquals(0L, empty.get("completedEnrollments"));
        assertEquals(0L, empty.get("totalTasks"));
        assertEquals(0L, empty.get("publishedTasks"));
        assertEquals(0L, empty.get("totalExams"));

        Map<String, Object> school = component.getPlatformOverview(null);
        assertFalse(school.isEmpty());
    }

    @Test
    @DisplayName("院系 dashboard：类目 null/空串过滤，聚合空表")
    void scopedDashboardCategoryAndEmptyAggregate() {
        when(userService.list(any(Wrapper.class))).thenReturn(List.of(user(1L), user(2L)));
        Course nullCategory = new Course(); nullCategory.setId(101L); nullCategory.setCategory(null);
        Course emptyCategory = new Course(); emptyCategory.setId(102L); emptyCategory.setCategory("");
        Course valid = new Course(); valid.setId(103L); valid.setCategory("编程");
        when(courseService.list(any(Wrapper.class))).thenReturn(List.of(nullCategory, emptyCategory, valid));
        when(userService.count(any())).thenReturn(2L);
        when(courseService.count(any())).thenReturn(1L);
        when(examPaperService.count(any())).thenReturn(1L);
        when(submissionService.count(any())).thenReturn(2L);
        when(enrollmentService.count(any())).thenReturn(3L);
        when(taskService.count(any())).thenReturn(2L);
        when(learningRecordService.count(any())).thenReturn(1L);
        when(enrollmentService.listMaps(any(Wrapper.class))).thenReturn(List.of());

        Map<String, Object> dashboard = component.getDashboard(3L);

        assertEquals(Map.of("编程", 1L), dashboard.get("categoryDistribution"));
        assertEquals(0.0, dashboard.get("completionRate"));
    }

    @Test
    @DisplayName("院系热力图/趋势：days<=0 回落、null 学院递归、空用户短路")
    void scopedHeatmapAndTrendDefaults() {
        when(userService.list(any(Wrapper.class))).thenReturn(List.of());

        assertDoesNotThrow(() -> component.getPlatformHeatmap(0, null));
        assertDoesNotThrow(() -> component.getPlatformHeatmap(0, 3L));
        assertEquals(28, component.getPlatformHeatmap(0, 3L).size());

        assertDoesNotThrow(() -> component.getUserGrowthTrend(0, null));
        assertEquals(30, component.getUserGrowthTrend(0, 3L).size());
    }
}
