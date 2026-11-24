package com.aicourse.application.admin;

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
import com.aicourse.vo.CourseStatisticsVO;
import com.aicourse.vo.RoleDistributionVO;
import com.aicourse.vo.StatisticsOverviewVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员数据统计应用服务（3E-05A）。
 *
 * <p>{@code AdminStatisticsController} 改造前是「每个端点各写一遍 if (collegeId == null) 校级口径
 * else 院系口径」：9 个端点里 7 个是这种双分支，共 20 处 {@code LambdaQueryWrapper}。这里把
 * 双分支收成 {@link #collegeFilter()} 一次取值 + 三个口径化助手：</p>
 *
 * <ul>
 *   <li>{@link #scopedCourseIds(Long)} —— 院系口径的课程ID集合（V13 起按 {@code courses.college_id}）；</li>
 *   <li>{@link #countUsers(Long, String)} —— 全校 / 本院 + 可选角色；</li>
 *   <li>{@link #listScopedCourses(Long)} —— 全校 / 本院课程实体。</li>
 * </ul>
 *
 * <p>两处「空集合直接给 0」的短路（选课数、活跃课程数）原样保留：院系没有课程时不去查
 * {@code IN ()} 空集，既省一次往返也避免方言差异。</p>
 *
 * <p>导出只在本层组装数据（{@link #exportReport()}），Excel 写入仍留在控制器——servlet 输出属 Web 关注点。</p>
 */
@Service
public class AdminStatisticsApplicationService {

    private final SystemService systemService;
    private final UserService userService;
    private final CourseService courseService;
    private final AnalyticsService analyticsService;
    private final CourseEnrollmentService enrollmentService;
    private final AdminScopeGuard adminScopeGuard;

    public AdminStatisticsApplicationService(SystemService systemService,
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

    // ===================== 概览 =====================

    /** 平台数据概览（院系管理员自动收敛到本院口径）。 */
    public StatisticsOverviewVO overview() {
        Long collegeId = collegeFilter();
        long totalUsers;
        long totalCourses;
        long totalEnrollments;
        long activeCourses;
        RoleDistributionVO roleDistribution;
        if (collegeId != null) {
            List<Long> courseIds = scopedCourseIds(collegeId);
            totalUsers = countUsers(collegeId, null);
            totalCourses = courseIds.size();
            totalEnrollments = courseIds.isEmpty() ? 0L : enrollmentService.count(
                    new LambdaQueryWrapper<CourseEnrollment>().in(CourseEnrollment::getCourseId, courseIds));
            roleDistribution = new RoleDistributionVO(
                    countUsers(collegeId, RoleConstants.STUDENT),
                    countUsers(collegeId, RoleConstants.TEACHER),
                    countUsers(collegeId, RoleConstants.ADMIN));
            activeCourses = courseIds.isEmpty() ? 0L : courseService.count(
                    new LambdaQueryWrapper<Course>().in(Course::getId, courseIds).eq(Course::getIsActive, true));
        } else {
            totalUsers = userService.count();
            totalCourses = courseService.count();
            totalEnrollments = enrollmentService.count();
            roleDistribution = new RoleDistributionVO(
                    countUsers(null, RoleConstants.STUDENT),
                    countUsers(null, RoleConstants.TEACHER),
                    countUsers(null, RoleConstants.ADMIN));
            activeCourses = courseService.count(new LambdaQueryWrapper<Course>().eq(Course::getIsActive, true));
        }
        return new StatisticsOverviewVO(totalUsers, totalCourses, totalEnrollments, roleDistribution, activeCourses);
    }

    /** 用户增长趋势。 */
    public List<Map<String, Object>> userGrowth(int days) {
        Long collegeId = collegeFilter();
        return collegeId == null
                ? analyticsService.getUserGrowthTrend(days)
                : analyticsService.getUserGrowthTrend(days, collegeId);
    }

    /** 课程统计（总量、活跃数、分类分布、热门 Top5）。 */
    public CourseStatisticsVO courseStatistics() {
        Long collegeId = collegeFilter();
        if (collegeId != null) {
            List<Course> allCourses = listScopedCourses(collegeId);
            long activeCourses = allCourses.stream().filter(c -> Boolean.TRUE.equals(c.getIsActive())).count();
            return new CourseStatisticsVO(allCourses.size(), activeCourses,
                    categoryDistribution(allCourses),
                    analyticsService.getCourseRanking(collegeId).stream().limit(5).collect(Collectors.toList()));
        }
        long totalCourses = courseService.count();
        long activeCourses = courseService.count(new LambdaQueryWrapper<Course>().eq(Course::getIsActive, true));
        List<Course> allCourses = courseService.list();
        return new CourseStatisticsVO(totalCourses, activeCourses, categoryDistribution(allCourses),
                analyticsService.getCourseRanking().stream().limit(5).collect(Collectors.toList()));
    }

    // ===================== AI 用量（四个维度，口径互不替代） =====================

    /** AI 使用统计（总口径）。 */
    public Map<String, Object> aiUsage() {
        Long collegeId = collegeFilter();
        return collegeId == null ? systemService.getAIUsageStats() : systemService.getAIUsageStats(collegeId);
    }

    /** 按课程聚合的 AI 用量（C-13：数据源是 {@code ai_call_logs}，与总口径不可互换）。 */
    public Map<String, Object> aiUsageByCourse(int days) {
        Long collegeId = collegeFilter();
        return collegeId == null
                ? systemService.getAIUsageByCourse(days)
                : systemService.getAIUsageByCourse(days, collegeId);
    }

    /** 按日聚合的 AI 用量（无调用日期不出行，由前端补零）。 */
    public Map<String, Object> aiUsageByDay(int days) {
        Long collegeId = collegeFilter();
        return collegeId == null
                ? systemService.getAIUsageByDay(days)
                : systemService.getAIUsageByDay(days, collegeId);
    }

    /** 按业务场景聚合的 AI 用量。 */
    public Map<String, Object> aiUsageByScene(int days) {
        Long collegeId = collegeFilter();
        return collegeId == null
                ? systemService.getAIUsageByScene(days)
                : systemService.getAIUsageByScene(days, collegeId);
    }

    // ===================== 导出 =====================

    /** 平台综合数据报表（多 Sheet 数据源；键名与既有导出保持一致）。 */
    public Map<String, Object> exportReport() {
        Long collegeId = collegeFilter();
        Map<String, Object> report = new HashMap<>();

        report.put("dashboard", collegeId == null
                ? analyticsService.getDashboard()
                : analyticsService.getDashboard(collegeId));
        report.put("aiUsage", collegeId == null
                ? systemService.getAIUsageStats()
                : systemService.getAIUsageStats(collegeId));

        List<Course> allCourses = listScopedCourses(collegeId);
        long activeCourses = allCourses.stream().filter(c -> Boolean.TRUE.equals(c.getIsActive())).count();
        Map<String, Object> courseStats = new HashMap<>();
        courseStats.put("totalCourses", allCourses.size());
        courseStats.put("activeCourses", activeCourses);
        courseStats.put("categoryDistribution", categoryDistribution(allCourses));
        report.put("courseStatistics", courseStats);

        Map<String, Object> userStats = new HashMap<>();
        // 校级口径沿用 no-arg count()（与既有导出用例的契约一致），院系口径才按学院过滤
        userStats.put("totalUsers", collegeId == null ? userService.count() : countUsers(collegeId, null));
        userStats.put("students", countUsers(collegeId, RoleConstants.STUDENT));
        userStats.put("teachers", countUsers(collegeId, RoleConstants.TEACHER));
        report.put("userStatistics", userStats);

        report.put("exportTime", LocalDateTime.now().toString());
        report.put("reportType", "comprehensive");
        report.put("status", "completed");
        return report;
    }

    // ===================== 口径助手 =====================

    /** 当前请求的学院口径；{@code null} 表示校级全校口径。 */
    private Long collegeFilter() {
        return adminScopeGuard.currentCollegeFilter();
    }

    /**
     * 院系口径下的课程ID集合（V13 起按 {@code courses.college_id}）。
     */
    private List<Long> scopedCourseIds(Long collegeId) {
        return courseService.list(new LambdaQueryWrapper<Course>().eq(Course::getCollegeId, collegeId))
                .stream().map(Course::getId).toList();
    }

    /** 全校（{@code collegeId == null}）或本院课程实体。 */
    private List<Course> listScopedCourses(Long collegeId) {
        if (collegeId == null) {
            return courseService.list();
        }
        List<Long> courseIds = scopedCourseIds(collegeId);
        return courseIds.isEmpty()
                ? List.of()
                : courseService.list(new LambdaQueryWrapper<Course>().in(Course::getId, courseIds));
    }

    /** 全校（{@code collegeId == null}）或本院用户计数；{@code role} 为空表示不限角色。 */
    private long countUsers(Long collegeId, String role) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (collegeId != null) {
            wrapper.eq(User::getCollegeId, collegeId);
        }
        if (role != null) {
            wrapper.eq(User::getRole, role);
        }
        return userService.count(wrapper);
    }

    private static Map<String, Long> categoryDistribution(List<Course> courses) {
        return courses.stream()
                .filter(c -> c.getCategory() != null)
                .collect(Collectors.groupingBy(Course::getCategory, Collectors.counting()));
    }
}
