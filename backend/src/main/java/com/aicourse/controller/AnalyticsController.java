package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/user/{userId}")
    public ApiResponse<Map<String, Object>> userDashboard(@PathVariable Long userId) {
        return ApiResponse.success(analyticsService.getUserDashboard(userId));
    }

    @GetMapping("/learning-trends/{userId}")
    public ApiResponse<Object> learningTrends(@PathVariable Long userId) {
        return ApiResponse.success(analyticsService.getLearningTrends(userId));
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<Map<String, Object>> courseAnalysis(@PathVariable Long courseId) {
        return ApiResponse.success(analyticsService.getCourseAnalysis(courseId));
    }
}
