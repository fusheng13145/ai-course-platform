package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据统计分析接口控制器
 */
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * 首页大盘统计数据
     * @return 汇总统计信息
     */
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> data = analyticsService.getDashboard();
        return ApiResponse.success(data);
    }

    /**
     * 学生个人学习数据看板
     * @param studentId 学生ID
     * @return 学生统计数据
     */
    @GetMapping("/students/{studentId}")
    public ApiResponse<Map<String, Object>> studentDashboard(@PathVariable Long studentId) {
        Map<String, Object> data = analyticsService.getUserDashboard(studentId);
        return ApiResponse.success(data);
    }

    /**
     * 学习趋势数据
     * @param userId 用户ID（可选）
     * @return 趋势统计数据
     */
    @GetMapping("/trends")
    public ApiResponse<Object> learningTrends(@RequestParam(required = false) Long userId) {
        Object data = analyticsService.getLearningTrends(userId);
        return ApiResponse.success(data);
    }

    /**
     * 课程整体数据分析
     * @param courseId 课程ID
     * @return 课程统计数据
     */
    @GetMapping("/courses/{courseId}")
    public ApiResponse<Map<String, Object>> courseAnalysis(@PathVariable Long courseId) {
        Map<String, Object> data = analyticsService.getCourseAnalysis(courseId);
        return ApiResponse.success(data);
    }
}