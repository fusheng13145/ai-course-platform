package com.aicourse.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AnalyticsServiceImpl 门面委托测试
 * <p>重构后门面不再持有底层 Service，而是把每个接口方法一行委派给对应协作者。
 * 本测试验证「门面正确委托给对应协作者并透传返回值/参数」，使用
 * {@code when(component.method(arg)).thenReturn(x)} + {@code assertSame} + {@code verify}。</p>
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock private AdminAnalyticsComponent adminAnalytics;
    @Mock private LearnerAnalyticsComponent learnerAnalytics;
    @Mock private CourseAnalyticsComponent courseAnalytics;
    @Mock private TeachingAnalyticsComponent teachingAnalytics;

    @InjectMocks private AnalyticsServiceImpl analyticsService;

    @Test
    @DisplayName("门面将所有接口方法正确委托给对应协作者并透传返回值与参数")
    void testFacadeDelegatesAllMethodsToCollaborators() {
        // 准备互不相同的桩返回值，便于 assertSame 校验透传
        Map<String, Object> dm1 = new HashMap<>();
        Map<String, Object> dm2 = new HashMap<>();
        Map<String, Object> dm3 = new HashMap<>();
        Map<String, Object> dm4 = new HashMap<>();
        Map<String, Object> dm5 = new HashMap<>();
        Map<String, Object> dm6 = new HashMap<>();
        Map<String, Object> dm7 = new HashMap<>();
        Map<String, Object> dm8 = new HashMap<>();
        Map<String, Object> dm9 = new HashMap<>();
        Map<String, Object> dm10 = new HashMap<>();
        List<Map<String, Object>> dl1 = new ArrayList<>();
        List<Map<String, Object>> dl2 = new ArrayList<>();
        List<Map<String, Object>> dl3 = new ArrayList<>();
        List<Map<String, Object>> dl4 = new ArrayList<>();
        List<Map<String, Object>> dl5 = new ArrayList<>();

        when(adminAnalytics.getDashboard()).thenReturn(dm1);
        when(adminAnalytics.getPlatformOverview()).thenReturn(dm2);
        when(adminAnalytics.getPlatformHeatmap(anyInt())).thenReturn(dl1);
        when(adminAnalytics.getUserGrowthTrend(anyInt())).thenReturn(dl2);
        when(learnerAnalytics.getUserDashboard(any())).thenReturn(dm3);
        when(learnerAnalytics.getLearningTrends(any())).thenReturn(dl3);
        when(learnerAnalytics.getLearningHeatmap(any(), any(), any())).thenReturn(dl4);
        when(courseAnalytics.getGradeDistribution(any())).thenReturn(dm4);
        when(courseAnalytics.getGradeDistributionHistogram(any())).thenReturn(dm5);
        when(courseAnalytics.getCourseAnalysis(any())).thenReturn(dm6);
        when(courseAnalytics.getCourseRanking()).thenReturn(dl5);
        when(courseAnalytics.getExamStatistics(any())).thenReturn(dm7);
        when(teachingAnalytics.getTeacherDashboard(any())).thenReturn(dm8);
        when(teachingAnalytics.exportReport(any())).thenReturn(dm9);
        when(teachingAnalytics.getAtRiskStudents(any(), any(), any(), any(), any())).thenReturn(dm10);

        // 调用参数固定引用，便于 verify(eq(...))
        Long uid = 7L;
        Long cid = 3L;
        Long classId = 9L;
        int days = 5;
        LocalDate start = LocalDate.now();
        LocalDate end = LocalDate.now();

        // 断言返回值透传
        assertSame(dm1, analyticsService.getDashboard());
        assertSame(dm2, analyticsService.getPlatformOverview());
        assertSame(dm3, analyticsService.getUserDashboard(uid));
        assertSame(dl3, analyticsService.getLearningTrends(uid));
        assertSame(dl4, analyticsService.getLearningHeatmap(uid, start, end));
        assertSame(dl1, analyticsService.getPlatformHeatmap(days));
        assertSame(dm4, analyticsService.getGradeDistribution(cid));
        assertSame(dm5, analyticsService.getGradeDistributionHistogram(cid));
        assertSame(dm6, analyticsService.getCourseAnalysis(cid));
        assertSame(dl5, analyticsService.getCourseRanking());
        assertSame(dl2, analyticsService.getUserGrowthTrend(days));
        assertSame(dm7, analyticsService.getExamStatistics(cid));
        assertSame(dm8, analyticsService.getTeacherDashboard(uid));
        assertSame(dm9, analyticsService.exportReport(cid));
        assertSame(dm10, analyticsService.getAtRiskStudents("school", cid, "dep", "maj", classId));

        // 校验确实委托到对应协作者且参数透传
        verify(adminAnalytics).getDashboard();
        verify(adminAnalytics).getPlatformOverview();
        verify(adminAnalytics).getPlatformHeatmap(eq(days));
        verify(adminAnalytics).getUserGrowthTrend(eq(days));
        verify(learnerAnalytics).getUserDashboard(eq(uid));
        verify(learnerAnalytics).getLearningTrends(eq(uid));
        verify(learnerAnalytics).getLearningHeatmap(eq(uid), eq(start), eq(end));
        verify(courseAnalytics).getGradeDistribution(eq(cid));
        verify(courseAnalytics).getGradeDistributionHistogram(eq(cid));
        verify(courseAnalytics).getCourseAnalysis(eq(cid));
        verify(courseAnalytics).getCourseRanking();
        verify(courseAnalytics).getExamStatistics(eq(cid));
        verify(teachingAnalytics).getTeacherDashboard(eq(uid));
        verify(teachingAnalytics).exportReport(eq(cid));
        verify(teachingAnalytics).getAtRiskStudents(eq("school"), eq(cid), eq("dep"), eq("maj"), eq(classId));
    }
}
