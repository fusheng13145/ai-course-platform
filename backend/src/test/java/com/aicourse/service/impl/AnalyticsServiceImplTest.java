package com.aicourse.service.impl;

import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 数据统计分析服务单元测试
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

    @InjectMocks private AnalyticsServiceImpl analyticsService;

    private Course testCourse;
    private CourseEnrollment testEnrollment;
    private User testUser;

    @BeforeEach
    void setUp() {
        testCourse = new Course();
        testCourse.setId(1L);
        testCourse.setName("Java程序设计");
        testCourse.setCode("CS101");
        testCourse.setCategory("计算机科学");
        testCourse.setDifficulty("MEDIUM");
        testCourse.setIsActive(true);
        testCourse.setTeacherId(10L);

        testEnrollment = new CourseEnrollment();
        testEnrollment.setId(1L);
        testEnrollment.setStudentId(100L);
        testEnrollment.setCourseId(1L);
        testEnrollment.setFinalScore(new BigDecimal("85"));
        testEnrollment.setProgress(new BigDecimal("60.00"));

        testUser = new User();
        testUser.setId(100L);
        testUser.setRole("STUDENT");
        testUser.setIsActive(true);
    }

    // ==================== getDashboard ====================

    @Test
    @DisplayName("仪表盘数据 - 包含所有必要字段")
    void testGetDashboard_AllFields() {
        when(userService.count()).thenReturn(100L);
        when(userService.count(any(LambdaQueryWrapper.class))).thenReturn(80L, 10L, 70L, 1L, 1L);
        when(courseService.count()).thenReturn(20L);
        when(courseService.count(any(LambdaQueryWrapper.class))).thenReturn(15L);
        when(courseService.list()).thenReturn(List.of(testCourse));
        when(examPaperService.count()).thenReturn(50L);
        when(submissionService.count()).thenReturn(500L);
        when(enrollmentService.count()).thenReturn(200L);
        when(enrollmentService.list()).thenReturn(List.of(testEnrollment));
        when(taskService.count()).thenReturn(30L);
        when(taskService.count(any(LambdaQueryWrapper.class))).thenReturn(25L);
        when(learningRecordService.count(any(LambdaQueryWrapper.class))).thenReturn(50L);

        Map<String, Object> dashboard = analyticsService.getDashboard();

        assertNotNull(dashboard);
        assertNotNull(dashboard.get("totalUsers"));
        assertNotNull(dashboard.get("activeUsers"));
        assertNotNull(dashboard.get("totalCourses"));
        assertNotNull(dashboard.get("activeCourses"));
        assertNotNull(dashboard.get("totalExams"));
        assertNotNull(dashboard.get("submissionCount"));
        assertNotNull(dashboard.get("categoryDistribution"));
        assertNotNull(dashboard.get("dailyActiveUsers"));
        assertNotNull(dashboard.get("completionRate"));
        assertNotNull(dashboard.get("growthRate"));
        assertNotNull(dashboard.get("gradeDistribution"));
        assertNotNull(dashboard.get("roleActivity"));
    }

    // ==================== getUserDashboard ====================

    @Test
    @DisplayName("用户仪表盘 - 用户特定统计")
    void testGetUserDashboard_UserSpecificStats() {
        when(enrollmentService.count(any(LambdaQueryWrapper.class))).thenReturn(3L);
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(testEnrollment));

        LearningRecord record = new LearningRecord();
        record.setUserId(100L);
        record.setDuration(120);
        record.setCreatedAt(LocalDateTime.now());

        when(learningRecordService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(record));
        when(taskService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(submissionService.count(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Map<String, Object> userDashboard = analyticsService.getUserDashboard(100L);

        assertNotNull(userDashboard);
        assertNotNull(userDashboard.get("enrolledCourses"));
        assertNotNull(userDashboard.get("averageProgress"));
        assertNotNull(userDashboard.get("totalStudyTime"));
        assertNotNull(userDashboard.get("averageScore"));
        assertNotNull(userDashboard.get("streakDays"));
        assertNotNull(userDashboard.get("pendingTasks"));
    }

    // ==================== getCourseAnalysis ====================

    @Test
    @DisplayName("课程分析 - 课程级别分析")
    void testGetCourseAnalysis_Success() {
        when(courseService.getById(1L)).thenReturn(testCourse);
        when(enrollmentService.count(any(LambdaQueryWrapper.class))).thenReturn(30L);
        when(taskService.count(any(LambdaQueryWrapper.class))).thenReturn(10L);
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(testEnrollment));

        Map<String, Object> analysis = analyticsService.getCourseAnalysis(1L);

        assertNotNull(analysis);
        assertEquals(testCourse, analysis.get("courseInfo"));
        assertEquals(30L, analysis.get("enrollmentCount"));
        assertEquals(10L, analysis.get("taskCount"));
        assertEquals(60.0, analysis.get("averageProgress"));
        assertEquals(85.0, analysis.get("averageScore"));
    }

    @Test
    @DisplayName("课程分析 - 课程不存在")
    void testGetCourseAnalysis_NotFound() {
        when(courseService.getById(999L)).thenReturn(null);

        Map<String, Object> analysis = analyticsService.getCourseAnalysis(999L);

        assertNotNull(analysis);
        assertEquals("课程不存在", analysis.get("error"));
    }

    // ==================== getGradeDistribution ====================

    @Test
    @DisplayName("成绩分布 - 多分数段统计")
    void testGetGradeDistribution_MultipleCategories() {
        CourseEnrollment e1 = new CourseEnrollment();
        e1.setFinalScore(new BigDecimal("95"));
        CourseEnrollment e2 = new CourseEnrollment();
        e2.setFinalScore(new BigDecimal("85"));
        CourseEnrollment e3 = new CourseEnrollment();
        e3.setFinalScore(new BigDecimal("75"));
        CourseEnrollment e4 = new CourseEnrollment();
        e4.setFinalScore(new BigDecimal("65"));
        CourseEnrollment e5 = new CourseEnrollment();
        e5.setFinalScore(new BigDecimal("55"));

        when(enrollmentService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(e1, e2, e3, e4, e5));

        Map<String, Object> distribution = analyticsService.getGradeDistributionHistogram(1L);

        assertNotNull(distribution);
        assertEquals(5, distribution.get("total"));
        assertNotNull(distribution.get("average"));
        assertNotNull(distribution.get("distribution"));
        assertNotNull(distribution.get("passRate"));
        assertNotNull(distribution.get("excellentRate"));
    }

    @Test
    @DisplayName("成绩分布 - 基本统计")
    void testGetGradeDistribution_Basic() {
        when(enrollmentService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(testEnrollment));

        Map<String, Object> result = analyticsService.getGradeDistribution(1L);

        assertNotNull(result);
        assertEquals(1, result.get("total"));
        assertEquals(85.0, result.get("average"));
        assertEquals(1L, result.get("passed"));
    }

    // ==================== exportReport ====================

    @Test
    @DisplayName("导出报告 - 包含所有分析部分")
    void testExportReport_AllSections() {
        when(courseService.getById(1L)).thenReturn(testCourse);
        when(enrollmentService.count(any(LambdaQueryWrapper.class))).thenReturn(30L);
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(testEnrollment));
        when(taskService.count(any(LambdaQueryWrapper.class))).thenReturn(10L);
        when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> report = analyticsService.exportReport(1L);

        assertNotNull(report);
        assertNotNull(report.get("scoreDistribution"));
        assertNotNull(report.get("submissionRate"));
        assertNotNull(report.get("averageProgress"));
        assertNotNull(report.get("studentRanking"));
        assertNotNull(report.get("exportTime"));
        assertEquals("学情分析报告", report.get("reportType"));
    }
}