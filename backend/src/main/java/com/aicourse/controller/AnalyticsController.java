package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据统计分析接口控制器
 * 提供平台总览、学习趋势、成绩分布、课程排行、用户增长、考试统计等数据分析API
 */
@RestController
@RequestMapping("/analytics")
@Tag(name = "数据统计分析", description = "提供平台总览、学习趋势、成绩分布、课程分析等统计功能")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * 首页大盘统计数据（基础版）
     */
    @GetMapping("/dashboard")
    @Operation(summary = "获取基础仪表盘数据", description = "返回用户数、课程数、选课数、任务数等基础统计")
    public ApiResponse<Map<String, Object>> dashboard(
            @Parameter(description = "教师ID（可选，传入时返回教师端数据）") @RequestParam(required = false) Long teacherId) {
        Map<String, Object> data;
        if (teacherId != null) {
            data = analyticsService.getTeacherDashboard(teacherId);
        } else {
            data = analyticsService.getDashboard();
        }
        return ApiResponse.success(data);
    }

    /**
     * 平台综合总览（增强版）
     */
    @GetMapping("/overview")
    @Operation(summary = "获取平台综合总览", description = "返回用户数、活跃度、完成率等多维度平台运营数据")
    public ApiResponse<Map<String, Object>> platformOverview() {
        Map<String, Object> data = analyticsService.getPlatformOverview();
        return ApiResponse.success(data);
    }

    /**
     * 学生个人学习数据看板
     */
    @GetMapping("/students/{studentId}")
    @Operation(summary = "获取学生个人学习数据", description = "返回学生的选课数、平均进度、累计学习时长等个人统计")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER') or #studentId == authentication.principal")
    public ApiResponse<Map<String, Object>> studentDashboard(
            @Parameter(description = "学生ID") @PathVariable Long studentId) {
        Map<String, Object> data = analyticsService.getUserDashboard(studentId);
        return ApiResponse.success(data);
    }

    /**
     * 学习趋势数据
     */
    @GetMapping("/trends")
    @Operation(summary = "获取学习趋势", description = "返回用户按日期分组的学习时长趋势数据")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER') or #userId == authentication.principal")
    public ApiResponse<Object> learningTrends(
            @Parameter(description = "用户ID（可选）") @RequestParam(required = false) Long userId) {
        Object data = analyticsService.getLearningTrends(userId);
        return ApiResponse.success(data);
    }

    /**
     * 学习热力图数据
     */
    @GetMapping("/heatmap")
    @Operation(summary = "获取学习热力图数据", description = "返回指定用户在日期范围内的每日学习时长及强度等级")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER') or #userId == authentication.principal")
    public ApiResponse<List<Map<String, Object>>> learningHeatmap(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "开始日期（格式：yyyy-MM-dd）") @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @Parameter(description = "结束日期（格式：yyyy-MM-dd）") @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        List<Map<String, Object>> data = analyticsService.getLearningHeatmap(userId, startDate, endDate);
        return ApiResponse.success(data);
    }

    /**
     * 平台级学习热力图（管理员专用，聚合所有用户数据）
     */
    @GetMapping("/heatmap/platform")
    @Operation(summary = "获取平台级热力图", description = "返回平台所有用户的每周各时段学习活跃度分布，仅限管理员")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> platformHeatmap(
            @Parameter(description = "统计天数（默认30天）") @RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> data = analyticsService.getPlatformHeatmap(days);
        return ApiResponse.success(data);
    }

    /**
     * 课程整体数据分析
     */
    @GetMapping("/courses/{courseId}")
    @Operation(summary = "获取课程综合分析", description = "返回课程信息、选课人数、任务数、平均进度和平均成绩")
    public ApiResponse<Map<String, Object>> courseAnalysis(
            @Parameter(description = "课程ID") @PathVariable Long courseId) {
        Map<String, Object> data = analyticsService.getCourseAnalysis(courseId);
        return ApiResponse.success(data);
    }

    /**
     * 课程成绩分布（基础版）
     */
    @GetMapping("/courses/{courseId}/grades")
    @Operation(summary = "获取课程成绩分布", description = "返回课程的平均分、及格人数等基础成绩统计")
    public ApiResponse<Map<String, Object>> gradeDistribution(
            @Parameter(description = "课程ID") @PathVariable Long courseId) {
        Map<String, Object> data = analyticsService.getGradeDistribution(courseId);
        return ApiResponse.success(data);
    }

    /**
     * 课程成绩分布直方图（增强版）
     */
    @GetMapping("/courses/{courseId}/grades/histogram")
    @Operation(summary = "获取成绩分布直方图", description = "返回按分数段（0-59/60-69/70-79/80-89/90-100）统计的人数分布、最高分、最低分、及格率、优秀率")
    public ApiResponse<Map<String, Object>> gradeDistributionHistogram(
            @Parameter(description = "课程ID") @PathVariable Long courseId) {
        Map<String, Object> data = analyticsService.getGradeDistributionHistogram(courseId);
        return ApiResponse.success(data);
    }

    /**
     * 热门课程排行
     */
    @GetMapping("/courses/ranking")
    @Operation(summary = "获取热门课程排行", description = "按选课人数降序排列，返回前20门热门课程及其选课数")
    public ApiResponse<List<Map<String, Object>>> courseRanking() {
        List<Map<String, Object>> data = analyticsService.getCourseRanking();
        return ApiResponse.success(data);
    }

    /**
     * 用户增长趋势
     */
    @GetMapping("/users/growth")
    @Operation(summary = "获取用户增长趋势", description = "按天统计最近N天的新增注册用户数和累计用户总数")
    public ApiResponse<List<Map<String, Object>>> userGrowthTrend(
            @Parameter(description = "统计天数（默认30天）") @RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> data = analyticsService.getUserGrowthTrend(days);
        return ApiResponse.success(data);
    }

    /**
     * 考试统计详情
     */
    @GetMapping("/exams/{examPaperId}/statistics")
    @Operation(summary = "获取考试统计详情", description = "返回试卷的参考人数、平均分、最高/最低分、及格率、优秀率、分数段分布、平均用时等详细统计")
    public ApiResponse<Map<String, Object>> examStatistics(
            @Parameter(description = "试卷ID") @PathVariable Long examPaperId) {
        Map<String, Object> data = analyticsService.getExamStatistics(examPaperId);
        return ApiResponse.success(data);
    }

    /**
     * 全校/多维「学习掉队」预警聚合（教学运行监管核心）
     */
    @GetMapping("/at-risk/students")
    @Operation(summary = "获取掉队预警聚合", description = "按 school/course/department/major/class 维度聚合掉队学生（进度<40% 或 成绩<60），支持院系/专业/班级/课程过滤下钻")
    public ApiResponse<Map<String, Object>> atRiskStudents(
            @Parameter(description = "聚合维度：school(默认)/course/department/major/class") @RequestParam(required = false) String groupBy,
            @Parameter(description = "课程ID（可选过滤）") @RequestParam(required = false) Long courseId,
            @Parameter(description = "院系（可选过滤）") @RequestParam(required = false) String department,
            @Parameter(description = "专业（可选过滤）") @RequestParam(required = false) String major,
            @Parameter(description = "班级ID（可选过滤）") @RequestParam(required = false) Long classId) {
        Map<String, Object> data = analyticsService.getAtRiskStudents(groupBy, courseId, department, major, classId);
        return ApiResponse.success(data);
    }
}