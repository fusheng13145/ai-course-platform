package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.AnalyticsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.success(analyticsService.getDashboard());
    }

    @GetMapping("/students/{studentId}")
    public ApiResponse<Map<String, Object>> studentDashboard(@PathVariable Long studentId) {
        return ApiResponse.success(analyticsService.getUserDashboard(studentId));
    }

    @GetMapping("/trends")
    public ApiResponse<Object> learningTrends(@RequestParam(required = false) Long userId) {
        return ApiResponse.success(analyticsService.getLearningTrends(userId));
    }

    @GetMapping("/courses/{courseId}")
    public ApiResponse<Map<String, Object>> courseAnalysis(@PathVariable Long courseId) {
        return ApiResponse.success(analyticsService.getCourseAnalysis(courseId));
    }
}
