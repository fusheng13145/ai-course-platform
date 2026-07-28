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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdminAnalyticsComponent 单元测试（由 AnalyticsServiceImpl 迁移而来）
 * 验证平台级（管理员）统计逻辑与原实现保持一致。
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

    @InjectMocks private AdminAnalyticsComponent adminAnalytics;

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

        Map<String, Object> dashboard = adminAnalytics.getDashboard();

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
}
