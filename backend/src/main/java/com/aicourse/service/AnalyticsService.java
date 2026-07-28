package com.aicourse.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据统计分析业务接口
 * 提供平台总览、用户学习数据、课程分析、考试统计等全方位数据分析功能
 */
public interface AnalyticsService {

    /**
     * 获取平台总览数据（基础仪表盘）
     * @return 仪表盘统计数据
     */
    Map<String, Object> getDashboard();

    /**
     * 获取平台综合总览（增强版：含活跃度、完成率等）
     * @return 平台总览数据
     */
    Map<String, Object> getPlatformOverview();

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
     * 获取学习热力图数据（按日期统计学习时长）
     * @param userId 用户ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 热力图数据列表
     */
    List<Map<String, Object>> getLearningHeatmap(Long userId, LocalDate startDate, LocalDate endDate);

    /**
     * 获取平台级热力图数据（管理员专用，聚合所有用户按星期+时段的活跃度）
     * @param days 统计天数
     * @return 热力图数据列表 [dayOfWeek, hour, count]
     */
    List<Map<String, Object>> getPlatformHeatmap(int days);

    /**
     * 获取课程成绩分布（基础版）
     * @param courseId 课程ID
     * @return 成绩分布数据
     */
    Map<String, Object> getGradeDistribution(Long courseId);

    /**
     * 获取课程成绩分布直方图（增强版：按分数段统计）
     * @param courseId 课程ID
     * @return 成绩分布直方图数据
     */
    Map<String, Object> getGradeDistributionHistogram(Long courseId);

    /**
     * 获取课程综合分析数据
     * @param courseId 课程ID
     * @return 课程分析结果
     */
    Map<String, Object> getCourseAnalysis(Long courseId);

    /**
     * 获取热门课程排行（按选课人数排序）
     * @return 课程排行榜列表
     */
    List<Map<String, Object>> getCourseRanking();

    /**
     * 获取用户增长趋势（按天统计新增用户）
     * @param days 统计天数
     * @return 用户增长趋势数据
     */
    List<Map<String, Object>> getUserGrowthTrend(int days);

    /**
     * 获取考试统计详情
     * @param examPaperId 试卷ID
     * @return 考试统计数据
     */
    Map<String, Object> getExamStatistics(Long examPaperId);

    /**
     * 获取教师端仪表盘数据
     * @param teacherId 教师ID
     * @return 教师统计数据（课程数、学生数、待批改数、平均分等）
     */
    Map<String, Object> getTeacherDashboard(Long teacherId);

    /**
     * 获取挂科风险学生列表（管理员/教师）
     * @param groupBy 分组维度（class/department/major）
     * @param courseId 课程ID（可选）
     * @param department 院系（可选）
     * @param major 专业（可选）
     * @param classId 班级ID（可选）
     * @return 挂科风险数据
     */
    Map<String, Object> getAtRiskStudents(String groupBy, Long courseId, String department, String major, Long classId);

    /**
     * 导出教学报告
     * @param courseId 课程ID
     * @return 报告数据
     */
    Map<String, Object> exportReport(Long courseId);
}