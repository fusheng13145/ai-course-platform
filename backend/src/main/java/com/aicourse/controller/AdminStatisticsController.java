package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.User;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.SystemService;
import com.aicourse.service.UserService;
import com.aicourse.util.ExcelUtil;
import com.aicourse.vo.CourseStatisticsVO;
import com.aicourse.vo.RoleDistributionVO;
import com.aicourse.vo.StatisticsOverviewVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员数据统计接口（S-01 从 {@code SystemAdminController} 拆出）。
 *
 * <p>URL、请求参数、响应体与类级 {@code @PreAuthorize} 全部保持原样；
 * 院系管理员的 {@link AdminScopeGuard#currentCollegeFilter()} 收敛逻辑不变。</p>
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "系统管理", description = "平台数据统计与报表导出")
public class AdminStatisticsController {

    private final SystemService systemService;
    private final UserService userService;
    private final CourseService courseService;
    private final AnalyticsService analyticsService;
    private final CourseEnrollmentService enrollmentService;
    private final AdminScopeGuard adminScopeGuard;

    public AdminStatisticsController(SystemService systemService,
                                     UserService userService,
                                     CourseService courseService,
                                     AnalyticsService analyticsService,
                                     CourseEnrollmentService enrollmentService,
                                     AdminScopeGuard adminScopeGuard) {
        this.systemService = systemService;
        this.userService = userService;
        this.courseService = courseService;
        this.analyticsService = analyticsService;
        this.enrollmentService = enrollmentService;
        this.adminScopeGuard = adminScopeGuard;
    }

    // ==================== 数据统计（6个接口） ====================

    /**
     * 获取平台数据概览
     */
    @GetMapping("/admin/statistics/overview")
    public ApiResponse<StatisticsOverviewVO> getStatisticsOverview() {
        Long collegeId = adminScopeGuard.currentCollegeFilter();
        if (collegeId != null) {
            List<Long> courseIds = scopedCourseIds(collegeId);
            long totalUsers = userService.count(new LambdaQueryWrapper<User>()
                    .eq(User::getCollegeId, collegeId));
            long totalCourses = courseIds.size();
            long totalEnrollments = courseIds.isEmpty() ? 0L
                    : enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>()
                            .in(CourseEnrollment::getCourseId, courseIds));
            RoleDistributionVO roleDistribution = new RoleDistributionVO(
                    userService.count(new LambdaQueryWrapper<User>()
                            .eq(User::getCollegeId, collegeId)
                            .eq(User::getRole, RoleConstants.STUDENT)),
                    userService.count(new LambdaQueryWrapper<User>()
                            .eq(User::getCollegeId, collegeId)
                            .eq(User::getRole, RoleConstants.TEACHER)),
                    userService.count(new LambdaQueryWrapper<User>()
                            .eq(User::getCollegeId, collegeId)
                            .eq(User::getRole, RoleConstants.ADMIN)));
            long activeCourses = courseIds.isEmpty() ? 0L
                    : courseService.count(new LambdaQueryWrapper<Course>()
                            .in(Course::getId, courseIds)
                            .eq(Course::getIsActive, true));
            return ApiResponse.success(new StatisticsOverviewVO(
                    totalUsers, totalCourses, totalEnrollments, roleDistribution, activeCourses));
        }
        long totalUsers = userService.count();
        long totalCourses = courseService.count();
        long totalEnrollments = enrollmentService.count();
        // 统计各角色用户数量
        RoleDistributionVO roleDistribution = new RoleDistributionVO(
                userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.STUDENT)),
                userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.TEACHER)),
                userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.ADMIN)));
        // 活跃课程统计
        long activeCourses = courseService.count(
                new LambdaQueryWrapper<Course>().eq(Course::getIsActive, true));
        return ApiResponse.success(new StatisticsOverviewVO(
                totalUsers, totalCourses, totalEnrollments, roleDistribution, activeCourses));
    }

    /**
     * 获取用户增长趋势
     */
    @GetMapping("/admin/statistics/user-growth")
    public ApiResponse<List<Map<String, Object>>> getUserGrowth(
            @RequestParam(defaultValue = "30") int days) {
        Long collegeId = adminScopeGuard.currentCollegeFilter();
        List<Map<String, Object>> trend = collegeId == null
                ? analyticsService.getUserGrowthTrend(days)
                : analyticsService.getUserGrowthTrend(days, collegeId);
        return ApiResponse.success(trend);
    }

    /**
     * 获取课程统计
     */
    @GetMapping("/admin/statistics/courses")
    public ApiResponse<CourseStatisticsVO> getCourseStatistics() {
        Long collegeId = adminScopeGuard.currentCollegeFilter();
        if (collegeId != null) {
            List<Long> courseIds = scopedCourseIds(collegeId);
            List<Course> allCourses = courseIds.isEmpty()
                    ? List.of()
                    : courseService.list(new LambdaQueryWrapper<Course>()
                            .in(Course::getId, courseIds));
            long activeCourses = allCourses.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                    .count();
            Map<String, Long> categoryDistribution = allCourses.stream()
                    .filter(c -> c.getCategory() != null)
                    .collect(Collectors.groupingBy(Course::getCategory, Collectors.counting()));
            List<Map<String, Object>> topCourses = analyticsService.getCourseRanking(collegeId)
                    .stream().limit(5).collect(Collectors.toList());
            return ApiResponse.success(new CourseStatisticsVO(
                    allCourses.size(), activeCourses, categoryDistribution, topCourses));
        }
        long totalCourses = courseService.count();
        long activeCourses = courseService.count(
                new LambdaQueryWrapper<Course>().eq(Course::getIsActive, true));
        // 按分类统计
        List<Course> allCourses = courseService.list();
        Map<String, Long> categoryDistribution = allCourses.stream()
                .filter(c -> c.getCategory() != null)
                .collect(Collectors.groupingBy(Course::getCategory, Collectors.counting()));
        // 热门课程 Top5
        List<Map<String, Object>> topCourses = analyticsService.getCourseRanking()
                .stream().limit(5).collect(Collectors.toList());

        return ApiResponse.success(new CourseStatisticsVO(
                totalCourses, activeCourses, categoryDistribution, topCourses));
    }

    /**
     * 获取AI功能使用统计
     */
    @GetMapping("/admin/statistics/ai-usage")
    public ApiResponse<Map<String, Object>> getAIUsageStatistics() {
        Long collegeId = adminScopeGuard.currentCollegeFilter();
        Map<String, Object> aiUsage = collegeId == null
                ? systemService.getAIUsageStats()
                : systemService.getAIUsageStats(collegeId);
        return ApiResponse.success(aiUsage);
    }

    /**
     * 获取**按课程聚合**的 AI 使用统计（跨课程 AI 统计）。
     *
     * <p>数据源是 {@code ai_call_logs}（2026-10-13 埋点上线）——与上方 {@code /ai-usage} 的
     * "无逐次留痕"口径是两回事，两者不可互换、也不可用一个去校正另一个。</p>
     *
     * <p>⚠️ 只统计自埋点上线后的调用，历史数据不可回填；响应内的 {@code collectionNote}
     * 会把这条边界显式带给前端，避免页面自己编一条"历史趋势"。</p>
     *
     * @param days 统计天数（默认 30，1–365 越界收敛）
     */
    @GetMapping("/admin/statistics/ai-usage/by-course")
    public ApiResponse<Map<String, Object>> getAIUsageByCourse(
            @RequestParam(defaultValue = "30") int days) {
        Long collegeId = adminScopeGuard.currentCollegeFilter();
        Map<String, Object> result = collegeId == null
                ? systemService.getAIUsageByCourse(days)
                : systemService.getAIUsageByCourse(days, collegeId);
        return ApiResponse.success(result);
    }

    /**
     * 按日聚合的 AI 用量（2026-11-07 P2 批次：补齐「按日维度」，B-13④）。
     * 无调用的日期不出行，由前端按区间补零渲染趋势；院系管理员自动收敛到本院口径。
     *
     * @param days 统计天数（默认 30，1–365 越界收敛）
     */
    @GetMapping("/admin/statistics/ai-usage/by-day")
    public ApiResponse<Map<String, Object>> getAIUsageByDay(
            @RequestParam(defaultValue = "30") int days) {
        Long collegeId = adminScopeGuard.currentCollegeFilter();
        Map<String, Object> result = collegeId == null
                ? systemService.getAIUsageByDay(days)
                : systemService.getAIUsageByDay(days, collegeId);
        return ApiResponse.success(result);
    }

    /**
     * 按能力（调用场景）聚合的 AI 用量（补齐「按能力维度」，B-13④）；
     * scene 取值即 ai_call_logs 埋点的 12 个业务场景，院系管理员自动收敛到本院口径。
     *
     * @param days 统计天数（默认 30，1–365 越界收敛）
     */
    @GetMapping("/admin/statistics/ai-usage/by-scene")
    public ApiResponse<Map<String, Object>> getAIUsageByScene(
            @RequestParam(defaultValue = "30") int days) {
        Long collegeId = adminScopeGuard.currentCollegeFilter();
        Map<String, Object> result = collegeId == null
                ? systemService.getAIUsageByScene(days)
                : systemService.getAIUsageByScene(days, collegeId);
        return ApiResponse.success(result);
    }

    /**
     * 导出数据报表
     */
    @Operation(summary = "导出数据报表", description = "导出平台综合数据报表为 Excel(.xlsx) 文件（多 Sheet：平台概览/AI使用统计/课程统计/用户统计等）")
    @GetMapping("/admin/statistics/export")
    public void exportStatisticsReport(HttpServletResponse response) throws IOException {
        ExcelUtil.setDownloadHeaders(response, "平台数据报表.xlsx");
        ExcelUtil.writeReport(response.getOutputStream(),
                buildStatisticsReport(adminScopeGuard.currentCollegeFilter()));
    }

    /**
     * 构建平台综合数据报表（供导出与接口复用）。
     * @param collegeId 学院ID；null 表示校级全校口径
     */
    private Map<String, Object> buildStatisticsReport(Long collegeId) {
        Map<String, Object> report = new HashMap<>();

        // 获取平台仪表盘数据
        Map<String, Object> dashboard = collegeId == null
                ? analyticsService.getDashboard()
                : analyticsService.getDashboard(collegeId);
        report.put("dashboard", dashboard);

        // 获取AI使用统计
        Map<String, Object> aiUsage = collegeId == null
                ? systemService.getAIUsageStats()
                : systemService.getAIUsageStats(collegeId);
        report.put("aiUsage", aiUsage);

        // 获取课程统计
        List<Course> allCourses;
        if (collegeId == null) {
            allCourses = courseService.list();
        } else {
            List<Long> courseIds = scopedCourseIds(collegeId);
            allCourses = courseIds.isEmpty()
                    ? List.of()
                    : courseService.list(new LambdaQueryWrapper<Course>()
                            .in(Course::getId, courseIds));
        }
        long activeCourses = allCourses.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .count();
        Map<String, Long> categoryDistribution = allCourses.stream()
                .filter(c -> c.getCategory() != null)
                .collect(Collectors.groupingBy(Course::getCategory, Collectors.counting()));
        Map<String, Object> courseStats = new HashMap<>();
        courseStats.put("totalCourses", allCourses.size());
        courseStats.put("activeCourses", activeCourses);
        courseStats.put("categoryDistribution", categoryDistribution);
        report.put("courseStatistics", courseStats);

        // 获取用户统计
        long totalUsers;
        long students;
        long teachers;
        if (collegeId == null) {
            totalUsers = userService.count();
            students = userService.count(
                    new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.STUDENT));
            teachers = userService.count(
                    new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.TEACHER));
        } else {
            totalUsers = userService.count(new LambdaQueryWrapper<User>()
                    .eq(User::getCollegeId, collegeId));
            students = userService.count(new LambdaQueryWrapper<User>()
                    .eq(User::getCollegeId, collegeId)
                    .eq(User::getRole, RoleConstants.STUDENT));
            teachers = userService.count(new LambdaQueryWrapper<User>()
                    .eq(User::getCollegeId, collegeId)
                    .eq(User::getRole, RoleConstants.TEACHER));
        }
        Map<String, Object> userStats = new HashMap<>();
        userStats.put("totalUsers", totalUsers);
        userStats.put("students", students);
        userStats.put("teachers", teachers);
        report.put("userStatistics", userStats);

        // 报表元信息
        report.put("exportTime", java.time.LocalDateTime.now().toString());
        report.put("reportType", "comprehensive");
        report.put("status", "completed");

        return report;
    }

    /**
     * 院系口径下的课程ID集合：按课程所属学院（V13 起由负责人学院回填）。
     * @param collegeId 学院ID
     * @return 本院课程ID列表（可能为空）
     */
    private List<Long> scopedCourseIds(Long collegeId) {
        return courseService.list(new LambdaQueryWrapper<Course>()
                        .eq(Course::getCollegeId, collegeId))
                .stream()
                .map(Course::getId)
                .toList();
    }
}
