package com.aicourse.service.impl;
import com.aicourse.service.component.*;

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
 * TeachingAnalyticsComponent 单元测试（由 AnalyticsServiceImpl 迁移而来）
 * 验证教师视角与教学监管统计逻辑与原实现保持一致。
 * exportReport 复用了 CourseAnalyticsComponent.getCourseAnalysis，此处将其作为协作者桩化。
 */
@ExtendWith(MockitoExtension.class)
class TeachingAnalyticsComponentTest {

    @Mock private CourseService courseService;
    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private TaskService taskService;
    @Mock private UserService userService;
    @Mock private CourseAnalyticsComponent courseAnalytics;

    @InjectMocks private TeachingAnalyticsComponent teachingAnalytics;

    private Course testCourse;
    private CourseEnrollment testEnrollment;

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
    }

    @Test
    @DisplayName("导出报告 - 包含所有分析部分")
    void testExportReport_AllSections() {
        when(courseAnalytics.getCourseAnalysis(1L)).thenReturn(new HashMap<>());
        when(courseService.getById(1L)).thenReturn(testCourse);
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(testEnrollment));
        when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> report = teachingAnalytics.exportReport(1L);

        assertNotNull(report);
        assertNotNull(report.get("scoreDistribution"));
        assertNotNull(report.get("submissionRate"));
        assertNotNull(report.get("averageProgress"));
        assertNotNull(report.get("studentRanking"));
        assertNotNull(report.get("exportTime"));
        assertEquals("学情分析报告", report.get("reportType"));
    }
}
