package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.AdminCreateUserDTO;
import com.aicourse.dto.AdminUpdateUserDTO;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.*;
import com.aicourse.util.ExcelUtil;
import com.aicourse.util.PasswordPolicy;
import com.aicourse.vo.AdminUserImportResultVO;
import com.aicourse.vo.CourseStatisticsVO;
import com.aicourse.vo.RoleDistributionVO;
import com.aicourse.vo.StatisticsOverviewVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
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

    private static final Logger log = LoggerFactory.getLogger(SystemAdminController.class);

    /**
     * 允许通过导入创建的角色白名单。
     *
     * <p>Excel 导入路径不经过 {@code AdminCreateUserDTO} 的 {@code @Pattern} 校验，
     * 而数据库 {@code users.role} 仅为 {@code varchar(20)} 无枚举约束 —— 若不显式校验，
     * 任意角色值（如 "BOSS"）都能建号成功并进入权限体系。</p>
     */
    private static final Set<String> VALID_ROLES =
            Set.of(RoleConstants.STUDENT, RoleConstants.TEACHER, RoleConstants.ADMIN);

    /**
     * 手机号格式（与 {@code AdminCreateUserDTO} 的 {@code @Pattern("^1[3-9]\\d{9}$")} 一致）。
     *
     * <p>与角色白名单同源缺口：Excel 导入路径不经过 DTO 校验，此前手机号可写入任意文本。</p>
     */
    private static final java.util.regex.Pattern PHONE_PATTERN =
            java.util.regex.Pattern.compile("^1[3-9]\\d{9}$");

    private final SystemService systemService;
    private final UserService userService;
    private final CourseService courseService;
    private final AnalyticsService analyticsService;
    private final CourseEnrollmentService enrollmentService;
    private final PasswordEncoder passwordEncoder;
    private final AdminScopeGuard adminScopeGuard;

    public SystemAdminController(SystemService systemService,
                                  UserService userService,
                                  CourseService courseService,
                                  AnalyticsService analyticsService,
                                  CourseEnrollmentService enrollmentService,
                                  PasswordEncoder passwordEncoder,
                                  AdminScopeGuard adminScopeGuard) {
        this.systemService = systemService;
        this.userService = userService;
        this.courseService = courseService;
        this.analyticsService = analyticsService;
        this.enrollmentService = enrollmentService;
        this.passwordEncoder = passwordEncoder;
        this.adminScopeGuard = adminScopeGuard;
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
        Long collegeFilter = adminScopeGuard.currentCollegeFilter();
        if (collegeFilter != null) {
            qw.eq(User::getCollegeId, collegeFilter);
        }
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
        adminScopeGuard.assertUserAccess(user);
        return ApiResponse.success(user);
    }

    /**
     * 创建用户（修复：改用 AdminCreateUserDTO 白名单绑定，password 可正常反序列化；
     * 原 @Valid @RequestBody User 因 User.password @JsonIgnore + @NotBlank 矛盾，创建永远 400 不可用）
     */
    @PostMapping("/admin/users")
    public ApiResponse<User> createUser(@Valid @RequestBody AdminCreateUserDTO dto) {
        PasswordPolicy.validate(dto.getPassword());
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setFullName(dto.getFullName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole());
        user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        // 密码加密
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        if (adminScopeGuard.isCollegeAdmin()) {
            if (RoleConstants.ADMIN.equals(dto.getRole())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "院系管理员不能创建管理员账号");
            }
            Long ownCollegeId = adminScopeGuard.currentCollegeId();
            if (dto.getCollegeId() != null && !ownCollegeId.equals(dto.getCollegeId())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "只能创建本学院用户");
            }
            user.setCollegeId(ownCollegeId);
        } else if (dto.getCollegeId() != null) {
            user.setCollegeId(dto.getCollegeId());
        }
        userService.save(user);
        user.setPassword(null);
        return ApiResponse.success("创建成功", user);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/admin/users/{userId}")
    public ApiResponse<User> updateUser(@PathVariable Long userId, @Valid @RequestBody AdminUpdateUserDTO dto) {
        User existing = userService.getById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        adminScopeGuard.assertUserManageable(existing);
        if (adminScopeGuard.isCollegeAdmin()) {
            if (RoleConstants.ADMIN.equals(dto.getRole())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "院系管理员不能把用户改为管理员");
            }
            if (dto.getCollegeId() != null
                    && !adminScopeGuard.currentCollegeId().equals(dto.getCollegeId())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "不能把用户移出本学院");
            }
        }
        // 白名单字段复制：仅允许 DTO 暴露的业务字段更新（不含密码/主键/审计字段）
        User user = new User();
        user.setId(userId);
        user.setUsername(dto.getUsername());
        user.setFullName(dto.getFullName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole());
        user.setIsActive(dto.getIsActive());
        user.setAvatarUrl(dto.getAvatarUrl());
        user.setBio(dto.getBio());
        if (dto.getCollegeId() != null) {
            user.setCollegeId(dto.getCollegeId());
        }
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
        adminScopeGuard.assertUserManageable(existing);
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
        adminScopeGuard.assertUserManageable(user);
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.isEmpty()) {
            throw new BusinessException("新密码不能为空");
        }
        // 修复：重置密码同样必须满足统一复杂度策略，防止管理端设置弱口令
        PasswordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userService.updateById(user);
        return ApiResponse.success("密码重置成功", null);
    }

    /**
     * 批量导入用户
     */
    @Operation(summary = "批量导入用户", description = "JSON 批量导入（兼容保留）；Excel(.xlsx) 批量导入请使用 /admin/users/import-excel")
    @PostMapping("/admin/users/import")
    public ApiResponse<AdminUserImportResultVO> importUsers(@Valid @RequestBody List<AdminCreateUserDTO> userDTOs) {
        adminScopeGuard.assertSchoolOnly();
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        for (AdminCreateUserDTO dto : userDTOs) {
            String username = dto.getUsername();
            try {
                // 与 Excel 路径一致的显式角色校验（@Valid 不校验集合元素）
                if (!VALID_ROLES.contains(dto.getRole())) {
                    throw new BusinessException("角色不合法（应为 STUDENT/TEACHER/ADMIN）");
                }
                User user = new User();
                user.setUsername(username);
                user.setFullName(dto.getFullName());
                user.setPhone(dto.getPhone());
                user.setEmail(dto.getEmail());
                user.setRole(dto.getRole());
                user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
                user.setPassword(passwordEncoder.encode(dto.getPassword()));
                userService.save(user);
                successCount++;
            } catch (Exception e) {
                log.warn("批量导入用户失败，已跳过 [{}]: {}", username, e.getMessage());
                failCount++;
                errors.add("用户名「" + username + "」：" + friendlyImportReason(e));
            }
        }
        return ApiResponse.success("导入完成",
                new AdminUserImportResultVO(successCount, failCount, userDTOs.size(), null,
                        errors.isEmpty() ? null : errors));
    }

    /**
     * 将底层异常转成管理员可读的失败原因。
     *
     * <p>逐行导入的失败多来自唯一索引冲突，原始信息形如
     * {@code Duplicate entry 'x@y.com' for key 'users.uk_users_email'}，直接透出对使用者无意义。</p>
     */
    private static String friendlyImportReason(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("uk_users_username")) {
            return "用户名已存在";
        }
        if (msg.contains("uk_users_email")) {
            return "邮箱已被其他账号使用";
        }
        if (msg.contains("uk_users_phone")) {
            return "手机号已被其他账号使用";
        }
        if (e instanceof BusinessException) {
            return msg;
        }
        return "写入失败：" + (msg.length() > 80 ? msg.substring(0, 80) + "…" : msg);
    }

    /**
     * Excel 批量导入用户
     */
    @Operation(summary = "Excel 批量导入用户",
            description = "上传 .xlsx 文件批量导入用户（表头：用户名/姓名/邮箱/角色/初始密码/手机号/学号/专业/年级/院系，初始密码缺省随机生成 8 位并随响应返回，供管理员转告用户）")
    @PostMapping(value = "/admin/users/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdminUserImportResultVO> importUsersExcel(
            @RequestParam("file") MultipartFile file) throws IOException {
        adminScopeGuard.assertSchoolOnly();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 Excel 文件");
        }
        if (!ExcelUtil.isExcelFile(file.getOriginalFilename())) {
            throw new BusinessException("仅支持 .xlsx 格式的 Excel 文件");
        }
        ExcelUtil.UserImportResult result = ExcelUtil.readUsersWithGenerated(file);
        List<User> users = result.users();
        Map<String, String> generatedPasswords = result.generatedPasswords();
        if (users.isEmpty()) {
            throw new BusinessException("文件中没有可导入的用户数据");
        }
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        // 仅返回「未填密码、由系统随机生成」用户的临时密码（明文仅本次响应出现一次，供管理员转告）
        Map<String, String> tempPasswords = new LinkedHashMap<>();
        for (User user : users) {
            String username = user.getUsername();
            try {
                // 角色白名单校验：本路径不经过 AdminCreateUserDTO 的 @Pattern，
                // 缺失该校验时 "BOSS" 等非法角色会被直接写入数据库并通过登录鉴权。
                if (!VALID_ROLES.contains(user.getRole())) {
                    throw new BusinessException(
                            "角色不合法（应为 STUDENT/TEACHER/ADMIN），当前值：「" + user.getRole() + "」");
                }
                // 手机号格式校验（与 AdminCreateUserDTO 的 @Pattern 同口径；留空视为不填）
                if (user.getPhone() != null && !PHONE_PATTERN.matcher(user.getPhone()).matches()) {
                    throw new BusinessException(
                            "手机号格式不正确（应为 1 开头的 11 位数字），当前值：「" + user.getPhone() + "」");
                }
                user.setId(null);
                user.setPassword(passwordEncoder.encode(user.getPassword()));
                user.setIsActive(true);
                user.setIsVerified(false);
                userService.save(user);
                if (generatedPasswords.containsKey(username)) {
                    tempPasswords.put(username, generatedPasswords.get(username));
                }
                successCount++;
            } catch (Exception e) {
                log.warn("批量导入用户失败，已跳过 [{}]: {}", username, e.getMessage());
                failCount++;
                errors.add("用户名「" + username + "」：" + friendlyImportReason(e));
            }
        }
        return ApiResponse.success("导入完成",
                new AdminUserImportResultVO(successCount, failCount, users.size(), tempPasswords,
                        errors.isEmpty() ? null : errors));
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
        adminScopeGuard.assertSchoolOnly();
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
        adminScopeGuard.assertSchoolOnly();
        List<Map<String, Object>> logFileList = systemService.getLogFiles();
        return ApiResponse.success(logFileList);
    }

    /**
     * 获取系统健康状态
     */
    @GetMapping("/admin/health")
    public ApiResponse<Map<String, Object>> getHealth() {
        adminScopeGuard.assertSchoolOnly();
        Map<String, Object> healthInfo = systemService.getHealth();
        return ApiResponse.success(healthInfo);
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

    // ==================== 数据备份 ====================

    /**
     * 数据备份
     */
    @PostMapping("/admin/backup")
    public ApiResponse<String> createBackup() {
        adminScopeGuard.assertSchoolOnly();
        String backupFileName = systemService.createBackup();
        return ApiResponse.success("备份成功", backupFileName);
    }

    // ==================== 保留原有兼容接口 ====================

    /**
     * 获取系统概览统计数据（原接口，保留兼容）
     */
    @GetMapping("/admin/system/overview")
    public ApiResponse<Map<String, Object>> overview() {
        adminScopeGuard.assertSchoolOnly();
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
        adminScopeGuard.assertSchoolOnly();
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
        adminScopeGuard.assertSchoolOnly();
        List<Map<String, Object>> backupList = systemService.getBackups();
        return ApiResponse.success(backupList);
    }

    /**
     * 从指定备份文件恢复数据（原接口，保留兼容）
     */
    @PostMapping("/admin/system/backup/{fileName}/restore")
    public ApiResponse<Void> restoreBackup(@PathVariable String fileName) {
        adminScopeGuard.assertSchoolOnly();
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
        adminScopeGuard.assertSchoolOnly();
        String logContent = systemService.readLogFile(fileName, lines, keyword);
        // DR-33：必须用双参重载。ApiResponse.success(String) 与 success(T data) 并存时，
        // String 入参绑定到「仅提示语」的那个，日志内容会跑到 message 里、data 变 null；
        // 又因 spring.jackson.default-property-inclusion=non_null，data 键整体消失，
        // 调用方按契约读 res.data 恒为空。本文件其余 ApiResponse<String> 端点均已用双参形式。
        return ApiResponse.success("读取成功", logContent);
    }

    // ==================== AI 智能监控 ====================

    /**
     * AI 异常监控：汇总最近登录/操作日志，调用 LLM 检测异常（暴力破解、成绩异动等）
     * GET /admin/ai-monitor
     */
    @GetMapping("/admin/ai-monitor")
    public Callable<ApiResponse<Map<String, Object>>> aiMonitor() {
        adminScopeGuard.assertSchoolOnly();
        return () -> ApiResponse.success(systemService.aiMonitor());
    }
}