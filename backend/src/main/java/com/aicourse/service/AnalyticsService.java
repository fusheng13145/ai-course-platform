package com.aicourse.service;

import java.util.List;
import java.util.Map;

/**
 * 数据统计分析业务接口
 */
public interface AnalyticsService {

    /**
     * 获取平台总览数据
     * @return 仪表盘统计数据
     */
    Map<String, Object> getDashboard();

    /**
     * 获取个人学习数据概览
     * @param userId 用户ID
     * @return 个人统计数据
     */
    Map<String, Object> getUserDashboard(Long userId);

    /**
     * 获取用户学习趋势数据
     * @param userId 用户ID
     * @return 趋势数据列表
     */
    List<Map<String, Object>> getLearningTrends(Long userId);

    /**
     * 获取课程成绩分布
     * @param courseId 课程ID
     * @return 成绩分布数据
     */
    Map<String, Object> getGradeDistribution(Long courseId);

    /**
     * 获取课程综合分析数据
     * @param courseId 课程ID
     * @return 课程分析结果
     */
    Map<String, Object> getCourseAnalysis(Long courseId);
}