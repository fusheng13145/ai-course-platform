package com.aicourse.controller;

import jakarta.validation.Valid;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Course;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import com.aicourse.common.RoleConstants;

/**
 * 系统管理接口控制器（仅管理员可访问）
 * 提供用户管理、系统配置、数据统计、日志、备份等管理员专属RESTful API
 * 严格按照需求文档定义：17个接口
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "系统管理", description = "用户、配置、统计、备份、健康检查等管理功能")
public class SystemAdminController {

    private final SystemService systemService;
    private final UserService userService;
    private final CourseService courseService;
    private final AnalyticsService analyticsService;
    private final CourseEnrollmentService enrollmentService;
    private final PasswordEncoder passwordEncoder;

    public SystemAdminController(SystemService systemService,
                                  UserService userService,
                                  CourseService courseService,
                                  AnalyticsService analyticsService,
                                  CourseEnrollmentService enrollmentService,
                                  PasswordEncoder passwordEncoder) {
        this.systemService = systemService;
        this.userService = userService;
        this.courseService = courseService;
        this.analyticsService = analyticsService;
        this.enrollmentService = enrollmentService;
        this.passwordEncoder = passwordEncoder;
    }


    // ==================== 用户管理（7个接口） ====================

    /**
     * 获取用户列表（支持角色/状态/关键词筛选）
     */
    @GetMapping("/admin/users")
    public ApiResponse<PagedResponse<User>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<User>()
                .eq(role != null && !role.isEmpty(), User::getRole, role)
                .and(keyword != null && !keyword.isEmpty(), w -> w
                        .like(User::getUsername, keyword)
                        .or()
                        .like(User::getFullName, keyword)
                        .or()
                        .like(User::getEmail, keyword));
        Page<User> userPage = userService.page(Page.of(page, size), qw);
        // 密码脱敏
        userPage.getRecords().forEach(u -> u.setPassword(null));
        return ApiResponse.success(PagedResponse.of(userPage));
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/admin/users/{userId}")
    public ApiResponse<User> getUserDetail(@PathVariable Long userId) {
        User user = userService.getUserProfile(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return ApiResponse.success(user);
    }

    /**
     * 创建用户
     */
    @PostMapping("/admin/users")
    public ApiResponse<User> createUser(@Valid @RequestBody User user) {
        // 密码加密
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setIsActive(true);
        userService.save(user);
        user.setPassword(null);
        return ApiResponse.success("创建成功", user);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/admin/users/{userId}")
    public ApiResponse<User> updateUser(@PathVariable Long userId, @Valid @RequestBody User user) {
        User existing = userService.getById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        user.setId(userId);
        // 不更新密码字段（通过专门接口重置密码）
        user.setPassword(null);
        userService.updateById(user);
        User updated = userService.getUserProfile(userId);
        return ApiResponse.success("更新成功", updated);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/admin/users/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        User existing = userService.getById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        userService.removeById(userId);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 重置用户密码
     */
    @PutMapping("/admin/users/{userId}/reset-password")
    public ApiResponse<Void> resetUserPassword(@PathVariable Long userId,
                                                @Valid @RequestBody Map<String, String> body) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.isEmpty()) {
            throw new BusinessException("新密码不能为空");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateById(user);
        return ApiResponse.success("密码重置成功", null);
    }

    /**
     * 批量导入用户
     */
    @PostMapping("/admin/users/import")
    public ApiResponse<Map<String, Object>> importUsers(@Valid @RequestBody List<User> users) {
        int successCount = 0;
        int failCount = 0;
        for (User user : users) {
            try {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
                user.setIsActive(true);
                userService.save(user);
                successCount++;
            } catch (Exception e) {
                failCount++;
            }
        }
        return ApiResponse.success("导入完成",
                Map.of("successCount", successCount, "failCount", failCount, "total", users.size()));
    }

    // ==================== 系统配置（4个接口） ====================

    /**
     * 获取系统配置
     */
    @GetMapping("/admin/configs")
    public ApiResponse<List<Map<String, Object>>> getConfigs(
            @RequestParam(required = false) String group) {
        List<Map<String, Object>> configList = systemService.getConfigs(group);
        return ApiResponse.success(configList);
    }

    /**
     * 更新系统配置
     */
    @PutMapping("/admin/configs/{configKey}")
    public ApiResponse<Void> updateConfig(@PathVariable String configKey,
                                           @Valid @RequestBody Map<String, String> body) {
        String value = body.get("value");
        String group = body.get("group");
        systemService.saveConfig(configKey, value, group);
        return ApiResponse.success("配置更新成功", null);
    }

    /**
     * 获取系统日志
     */
    @GetMapping("/admin/logs")
    public ApiResponse<List<Map<String, Object>>> getLogs() {
        List<Map<String, Object>> logFileList = systemService.getLogFiles();
        return ApiResponse.success(logFileList);
    }

    /**
     * 获取系统健康状态
     */
    @GetMapping("/admin/health")
    public ApiResponse<Map<String, Object>> getHealth() {
        Map<String, Object> healthInfo = systemService.getHealth();
        return ApiResponse.success(healthInfo);
    }

    // ==================== 数据统计（6个接口） ====================

    /**
     * 获取平台数据概览
     */
    @GetMapping("/admin/statistics/overview")
    public ApiResponse<Map<String, Object>> getStatisticsOverview() {
        long totalUsers = userService.count();
        long totalCourses = courseService.count();
        long totalEnrollments = enrollmentService.count();
        Map<String, Object> overview = new HashMap<>();
        overview.put("totalUsers", totalUsers);
        overview.put("totalCourses", totalCourses);
        overview.put("totalEnrollments", totalEnrollments);
        // 统计各角色用户数量
        Map<String, Object> roleDistribution = new HashMap<>();
        roleDistribution.put("student", userService.count(
                new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.STUDENT)));
        roleDistribution.put("teacher", userService.count(
                new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.TEACHER)));
        roleDistribution.put("admin", userService.count(
                new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.ADMIN)));
        overview.put("roleDistribution", roleDistribution);
        // 活跃课程统计
        long activeCourses = courseService.count(
                new LambdaQueryWrapper<Course>().eq(Course::getIsActive, true));
        overview.put("activeCourses", activeCourses);
        return ApiResponse.success(overview);
    }

    /**
     * 获取用户增长趋势
     */
    @GetMapping("/admin/statistics/user-growth")
    public ApiResponse<List<Map<String, Object>>> getUserGrowth(
            @RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> trend = analyticsService.getUserGrowthTrend(days);
        return ApiResponse.success(trend);
    }

    /**
     * 获取课程统计
     */
    @GetMapping("/admin/statistics/courses")
    public ApiResponse<Map<String, Object>> getCourseStatistics() {
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

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCourses", totalCourses);
        stats.put("activeCourses", activeCourses);
        stats.put("categoryDistribution", categoryDistribution);
        stats.put("topCourses", topCourses);
        return ApiResponse.success(stats);
    }

    /**
     * 获取AI功能使用统计
     */
    @GetMapping("/admin/statistics/ai-usage")
    public ApiResponse<Map<String, Object>> getAIUsageStatistics() {
        Map<String, Object> aiUsage = systemService.getAIUsageStats();
        return ApiResponse.success(aiUsage);
    }

    /**
     * 导出数据报表
     */
    @GetMapping("/admin/statistics/export")
    public ApiResponse<Map<String, Object>> exportStatisticsReport() {
        Map<String, Object> report = new HashMap<>();

        // 获取平台仪表盘数据
        Map<String, Object> dashboard = analyticsService.getDashboard();
        report.put("dashboard", dashboard);

        // 获取AI使用统计
        Map<String, Object> aiUsage = systemService.getAIUsageStats();
        report.put("aiUsage", aiUsage);

        // 获取课程统计
        List<Course> allCourses = courseService.list();
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
        long totalUsers = userService.count();
        long students = userService.count(
                new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.STUDENT));
        long teachers = userService.count(
                new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.TEACHER));
        Map<String, Object> userStats = new HashMap<>();
        userStats.put("totalUsers", totalUsers);
        userStats.put("students", students);
        userStats.put("teachers", teachers);
        report.put("userStatistics", userStats);

        // 报表元信息
        report.put("exportTime", java.time.LocalDateTime.now().toString());
        report.put("reportType", "comprehensive");
        report.put("status", "completed");

        return ApiResponse.success("报表导出成功", report);
    }

    // ==================== 数据备份 ====================

    /**
     * 数据备份
     */
    @PostMapping("/admin/backup")
    public ApiResponse<String> createBackup() {
        String backupFileName = systemService.createBackup();
        return ApiResponse.success("备份成功", backupFileName);
    }

    // ==================== 保留原有兼容接口 ====================

    /**
     * 获取系统概览统计数据（原接口，保留兼容）
     */
    @GetMapping("/admin/system/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> overviewData = systemService.getOverview();
        return ApiResponse.success(overviewData);
    }

    /**
     * 查询系统配置项（原接口，保留兼容）
     */
    @GetMapping("/admin/system/configs")
    public ApiResponse<List<Map<String, Object>>> systemConfigs(
            @RequestParam(required = false) String group) {
        return getConfigs(group);
    }

    /**
     * 保存/更新系统配置（原接口，保留兼容）
     */
    @PostMapping("/admin/system/configs")
    public ApiResponse<Void> saveConfig(@Valid @RequestBody Map<String, String> body) {
        String key = body.get("key");
        String value = body.get("value");
        String group = body.get("group");
        systemService.saveConfig(key, value, group);
        return ApiResponse.success("保存成功", null);
    }

    /**
     * 检测系统运行状态（原接口，保留兼容）
     */
    @GetMapping("/admin/system/health")
    public ApiResponse<Map<String, Object>> systemHealth() {
        return getHealth();
    }

    /**
     * 查询所有备份文件列表（原接口，保留兼容）
     */
    @GetMapping("/admin/backup")
    public ApiResponse<List<Map<String, Object>>> getBackups() {
        List<Map<String, Object>> backupList = systemService.getBackups();
        return ApiResponse.success(backupList);
    }

    /**
     * 从指定备份文件恢复数据（原接口，保留兼容）
     */
    @PostMapping("/admin/system/backup/{fileName}/restore")
    public ApiResponse<Void> restoreBackup(@PathVariable String fileName) {
        systemService.restoreBackup(fileName);
        return ApiResponse.success("恢复成功", null);
    }

    /**
     * 查询日志文件列表（原接口，保留兼容）
     */
    @GetMapping("/admin/system/logs")
    public ApiResponse<List<Map<String, Object>>> getLogFiles() {
        return getLogs();
    }

    /**
     * 读取指定日志内容（原接口，保留兼容）
     */
    @GetMapping("/admin/system/logs/{fileName}")
    public ApiResponse<String> readLog(
            @PathVariable String fileName,
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(required = false) String keyword) {
        String logContent = systemService.readLogFile(fileName, lines, keyword);
        return ApiResponse.success(logContent);
    }

    // ==================== AI 智能监控 ====================

    /**
     * AI 异常监控：汇总最近登录/操作日志，调用 LLM 检测异常（暴力破解、成绩异动等）
     * GET /admin/ai-monitor
     */
    @GetMapping("/admin/ai-monitor")
    public Callable<ApiResponse<Map<String, Object>>> aiMonitor() {
        return () -> ApiResponse.success(systemService.aiMonitor());
    }
}