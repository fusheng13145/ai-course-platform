package com.aicourse.service.impl;
import com.aicourse.service.component.analytics.AdminAnalyticsComponent;
import com.aicourse.service.component.analytics.CourseAnalyticsComponent;
import com.aicourse.service.component.analytics.LearnerAnalyticsComponent;
import com.aicourse.service.component.analytics.TeachingAnalyticsComponent;

import com.aicourse.entity.*;
import com.aicourse.mapper.TaskCompletionMapper;
import com.aicourse.service.*;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AnalyticsServiceImpl（门面）单元测试。
 * <p>统计聚合逻辑已拆分至四个协作者组件（Admin/Course/Learner/Teaching，各有独立测试覆盖），
 * 本测试锁定门面的「委派透传」契约 + 保留在门面的课程级学情方法逻辑。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsServiceImplTest {

    @Mock private AdminAnalyticsComponent adminAnalyticsComponent;
    @Mock private CourseAnalyticsComponent courseAnalyticsComponent;
    @Mock private LearnerAnalyticsComponent learnerAnalyticsComponent;
    @Mock private TeachingAnalyticsComponent teachingAnalyticsComponent;

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
                userService, enrollmentService, taskService, taskCompletionMapper,
                submissionService, examSessionService, examPaperService, courseChapterService);
    }

    private CourseEnrollment enrollment(Long courseId, Long studentId, BigDecimal progress, BigDecimal finalScore) {
        CourseEnrollment e = new CourseEnrollment();
        e.setCourseId(courseId);
        e.setStudentId(studentId);
        e.setProgress(progress);
        e.setFinalScore(finalScore);
        e.setIsActive(true);
        return e;
    }

    // ===================== 委派透传契约 =====================

    @Test
    @DisplayName("getDashboard 委托 AdminAnalyticsComponent")
    void delegate_getDashboard() {
        Map<String, Object> expected = new HashMap<>();
        expected.put("totalUsers", 1L);
        when(adminAnalyticsComponent.getDashboard()).thenReturn(expected);
        assertSame(expected, analyticsService.getDashboard());
        verify(adminAnalyticsComponent).getDashboard();
    }

    @Test
    @DisplayName("getPlatformOverview / getPlatformHeatmap / getUserGrowthTrend 委托 Admin 组件")
    void delegate_adminMethods() {
        when(adminAnalyticsComponent.getPlatformOverview()).thenReturn(Map.of("totalUsers", 1L));
        when(adminAnalyticsComponent.getPlatformHeatmap(30)).thenReturn(Collections.emptyList());
        when(adminAnalyticsComponent.getUserGrowthTrend(7)).thenReturn(Collections.emptyList());

        assertEquals(1L, analyticsService.getPlatformOverview().get("totalUsers"));
        assertTrue(analyticsService.getPlatformHeatmap(30).isEmpty());
        assertTrue(analyticsService.getUserGrowthTrend(7).isEmpty());
        verify(adminAnalyticsComponent).getPlatformOverview();
        verify(adminAnalyticsComponent).getPlatformHeatmap(30);
        verify(adminAnalyticsComponent).getUserGrowthTrend(7);
    }

    @Test
    @DisplayName("课程级统计 5 方法委托 CourseAnalyticsComponent")
    void delegate_courseMethods() {
        when(courseAnalyticsComponent.getGradeDistribution(1L)).thenReturn(Map.of("total", 1));
        when(courseAnalyticsComponent.getGradeDistributionHistogram(1L)).thenReturn(Map.of("total", 1));
        when(courseAnalyticsComponent.getCourseAnalysis(1L)).thenReturn(Map.of("enrollmentCount", 1));
        when(courseAnalyticsComponent.getCourseRanking()).thenReturn(Collections.emptyList());
        when(courseAnalyticsComponent.getExamStatistics(1L)).thenReturn(Map.of("totalParticipants", 1));

        assertNotNull(analyticsService.getGradeDistribution(1L));
        assertNotNull(analyticsService.getGradeDistributionHistogram(1L));
        assertNotNull(analyticsService.getCourseAnalysis(1L));
        assertTrue(analyticsService.getCourseRanking().isEmpty());
        assertEquals(1, analyticsService.getExamStatistics(1L).get("totalParticipants"));
        verify(courseAnalyticsComponent).getGradeDistribution(1L);
        verify(courseAnalyticsComponent).getGradeDistributionHistogram(1L);
        verify(courseAnalyticsComponent).getCourseAnalysis(1L);
        verify(courseAnalyticsComponent).getCourseRanking();
        verify(courseAnalyticsComponent).getExamStatistics(1L);
    }

    @Test
    @DisplayName("学习者 3 方法委托 LearnerAnalyticsComponent")
    void delegate_learnerMethods() {
        when(learnerAnalyticsComponent.getUserDashboard(7L)).thenReturn(Map.of("streakDays", 2));
        when(learnerAnalyticsComponent.getLearningTrends(7L)).thenReturn(Collections.emptyList());
        when(learnerAnalyticsComponent.getLearningHeatmap(7L, null, null)).thenReturn(Collections.emptyList());

        assertEquals(2, analyticsService.getUserDashboard(7L).get("streakDays"));
        assertTrue(analyticsService.getLearningTrends(7L).isEmpty());
        assertTrue(analyticsService.getLearningHeatmap(7L, null, null).isEmpty());
        verify(learnerAnalyticsComponent).getUserDashboard(7L);
        verify(learnerAnalyticsComponent).getLearningTrends(7L);
        verify(learnerAnalyticsComponent).getLearningHeatmap(7L, null, null);
    }

    @Test
    @DisplayName("教师端 3 方法委托 TeachingAnalyticsComponent")
    void delegate_teachingMethods() {
        when(teachingAnalyticsComponent.getTeacherDashboard(1L)).thenReturn(Map.of("courseCount", 1));
        when(teachingAnalyticsComponent.getAtRiskStudents("school", null, null, null, null))
                .thenReturn(Map.of("atRiskCount", 1));
        when(teachingAnalyticsComponent.exportReport(1L)).thenReturn(Map.of("reportType", "学情分析报告"));

        assertEquals(1, analyticsService.getTeacherDashboard(1L).get("courseCount"));
        assertEquals(1, analyticsService.getAtRiskStudents("school", null, null, null, null).get("atRiskCount"));
        assertEquals("学情分析报告", analyticsService.exportReport(1L).get("reportType"));
        verify(teachingAnalyticsComponent).getTeacherDashboard(1L);
        verify(teachingAnalyticsComponent).getAtRiskStudents("school", null, null, null, null);
        verify(teachingAnalyticsComponent).exportReport(1L);
    }

    // ===================== 保留在门面的课程级学生学情 =====================

    @Test
    @DisplayName("getStudentCourseProgressList - 无选课返回空列表")
    void progressList_noEnrollment() {
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(analyticsService.getStudentCourseProgressList(1L).isEmpty());
    }

    @Test
    @DisplayName("getStudentCourseProgressList - 正常聚合进度并排序")
    void progressList_aggregatesAndSorts() {
        List<CourseEnrollment> enrollments = Arrays.asList(
                enrollment(1L, 10L, new BigDecimal("100"), new BigDecimal("90")),
                enrollment(1L, 11L, new BigDecimal("40"), new BigDecimal("50")));
        when(enrollmentService.list(any(Wrapper.class))).thenReturn(enrollments);
        when(userService.listByIds(anyCollection())).thenReturn(Arrays.asList(user(10L, "甲"), user(11L, "乙")));
        when(taskService.list(any(Wrapper.class))).thenReturn(Collections.singletonList(task(1L)));
        when(taskCompletionMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(submissionService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(examSessionService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = analyticsService.getStudentCourseProgressList(1L);
        assertEquals(2, result.size());
        // 进度降序：100 的在前
        assertEquals(10L, result.get(0).get("studentId"));
        assertEquals(11L, result.get(1).get("studentId"));
        assertEquals(1L, result.get(0).get("totalTasks"));
    }

    @Test
    @DisplayName("getStudentCourseDetail - 学生为 null 时姓名/学号降级为 null 且不抛异常")
    void studentDetail_studentNull() {
        when(userService.getById(99L)).thenReturn(null);
        when(enrollmentService.getOne(any(Wrapper.class))).thenReturn(null);
        when(courseChapterService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(taskService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(examSessionService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> result = analyticsService.getStudentCourseDetail(1L, 99L);
        assertNull(result.get("studentName"));
        assertNotNull(result.get("chapters"));
    }

    private User user(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        return u;
    }

    private Task task(Long id) {
        Task t = new Task();
        t.setId(id);
        return t;
    }
}
