package com.aicourse.service;

import java.util.List;
import java.util.Map;

public interface AnalyticsService {
    Map<String, Object> getDashboard();
    Map<String, Object> getUserDashboard(Long userId);
    List<Map<String, Object>> getLearningTrends(Long userId);
    Map<String, Object> getGradeDistribution(Long courseId);
    Map<String, Object> getCourseAnalysis(Long courseId);
}
