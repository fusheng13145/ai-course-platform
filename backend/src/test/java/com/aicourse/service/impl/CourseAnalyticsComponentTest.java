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
 * CourseAnalyticsComponent 单元测试（由 AnalyticsServiceImpl 迁移而来）
 * 验证课程/考试维度统计逻辑与原实现保持一致。
 */
@ExtendWith(MockitoExtension.class)
class CourseAnalyticsComponentTest {

    @Mock private CourseEnrollmentService enrollmentService;
    @Mock private CourseService courseService;
    @Mock private TaskService taskService;
    @Mock private ExamPaperService examPaperService;
    @Mock private ExamSessionService examSessionService;

    @InjectMocks private CourseAnalyticsComponent courseAnalytics;

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
    @DisplayName("课程分析 - 课程级别分析")
    void testGetCourseAnalysis_Success() {
        when(courseService.getById(1L)).thenReturn(testCourse);
        when(enrollmentService.count(any(LambdaQueryWrapper.class))).thenReturn(30L);
        when(taskService.count(any(LambdaQueryWrapper.class))).thenReturn(10L);
        when(enrollmentService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(testEnrollment));

        Map<String, Object> analysis = courseAnalytics.getCourseAnalysis(1L);

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

        Map<String, Object> analysis = courseAnalytics.getCourseAnalysis(999L);

        assertNotNull(analysis);
        assertEquals("课程不存在", analysis.get("error"));
    }

    @Test
    @DisplayName("成绩分布直方图 - 多分数段统计")
    void testGetGradeDistributionHistogram_MultipleCategories() {
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

        Map<String, Object> distribution = courseAnalytics.getGradeDistributionHistogram(1L);

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

        Map<String, Object> result = courseAnalytics.getGradeDistribution(1L);

        assertNotNull(result);
        assertEquals(1, result.get("total"));
        assertEquals(85.0, result.get("average"));
        assertEquals(1L, result.get("passed"));
    }
}
