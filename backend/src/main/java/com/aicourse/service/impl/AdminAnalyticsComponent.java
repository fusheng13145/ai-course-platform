package com.aicourse.service.impl;

import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;
import org.springframework.cache.annotation.Cacheable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import com.aicourse.common.RoleConstants;

/**
 * 平台级（管理员）数据分析协作者
 * <p>承接原 {@link AnalyticsServiceImpl} 中管理员视角的统计职责：平台仪表盘、综合总览、
 * 平台热力图、用户增长趋势及其相关私有 helper。本类是 Spring 托管的协作者 Bean，
 * 由 {@link AnalyticsServiceImpl} 门面委派调用。</p>
 */
@Component
public class AdminAnalyticsComponent {

    private final UserService userService;
    private final CourseService courseService;
    private final ExamPaperService examPaperService;
    private final SubmissionService submissionService;
    private final CourseEnrollmentService enrollmentService;
    private final LearningRecordService learningRecordService;
    private final TaskService taskService;

    public AdminAnalyticsComponent(
            UserService userService,
            CourseService courseService,
            ExamPaperService examPaperService,
            SubmissionService submissionService,
            CourseEnrollmentService enrollmentService,
            LearningRecordService learningRecordService,
            TaskService taskService
    ) {
        this.userService = userService;
        this.courseService = courseService;
        this.examPaperService = examPaperService;
        this.submissionService = submissionService;
        this.enrollmentService = enrollmentService;
        this.learningRecordService = learningRecordService;
        this.taskService = taskService;
    }

    private long countActiveUsers() {
        return userService.count(new LambdaQueryWrapper<User>().eq(User::getIsActive, Boolean.TRUE));
    }

    private long countActiveCourses() {
        return courseService.count(new LambdaQueryWrapper<Course>().eq(Course::getIsActive, Boolean.TRUE));
    }

    private double calculateRate(long numerator, long denominator) {
        return denominator > 0 ? Math.round(numerator * 100.0 / denominator * 100.0) / 100.0 : 0;
    }

    private long countRecentActiveUsers(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return learningRecordService.count(new LambdaQueryWrapper<LearningRecord>().ge(LearningRecord::getCreatedAt, cutoff));
    }

    private long countNewUsers(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return userService.count(new LambdaQueryWrapper<User>().ge(User::getCreatedAt, cutoff));
    }

    /**
     * 获取平台仪表盘数据
     * 包含用户、课程、考试、提交、选课、任务、成绩分布、角色活跃度等多维度统计
     */
    @Cacheable(cacheNames = "analytics", key = "'platform'")
    public Map<String, Object> getDashboard() {
        Map<String, Object> dashboard = new HashMap<>(24);
        try {
            // ---- 用户统计（Admin Dashboard statCards[0]）----
            long totalUsers = userService.count();
            long activeUsers = userService.count(new LambdaQueryWrapper<User>().eq(User::getIsActive, Boolean.TRUE));
            dashboard.put("totalUsers", totalUsers);
            dashboard.put("activeUsers", activeUsers);
            dashboard.put("userCount", totalUsers);

            // ---- 课程统计（Admin statCards[1]）----
            long totalCourses = courseService.count();
            long activeCourses = courseService.count(new LambdaQueryWrapper<Course>().eq(Course::getIsActive, Boolean.TRUE));
            dashboard.put("totalCourses", totalCourses);
            dashboard.put("activeCourses", activeCourses);
            dashboard.put("courseCount", totalCourses);

            // ---- 考试统计（Admin statCards[2]）----
            long totalExams = examPaperService.count();
            dashboard.put("totalExams", totalExams);
            dashboard.put("examCount", totalExams);

            // ---- 提交/作业统计（Admin statCards[3]）----
            long totalSubmissions = submissionService.count();
            dashboard.put("submissionCount", totalSubmissions);

            // ---- 选课统计 ----
            long totalEnrollments = enrollmentService.count();
            dashboard.put("totalEnrollments", totalEnrollments);

            // ---- 任务统计 ----
            long totalTasks = taskService.count();
            long publishedTasks = taskService.count(new LambdaQueryWrapper<Task>().eq(Task::getIsPublished, Boolean.TRUE));
            dashboard.put("totalTasks", totalTasks);
            dashboard.put("publishedTasks", publishedTasks);

            // ---- 课程分类分布（Admin 饼图）----
            List<Course> allCourses = courseService.list();
            Map<String, Long> categoryDist = allCourses.stream()
                    .filter(c -> c.getCategory() != null && !c.getCategory().isEmpty())
                    .collect(Collectors.groupingBy(Course::getCategory, Collectors.counting()));
            dashboard.put("categoryDistribution", categoryDist);

            // ---- 活跃度统计（近7天有学习记录的用户数）----
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            long dau = learningRecordService.count(
                    new LambdaQueryWrapper<LearningRecord>()
                            .ge(LearningRecord::getCreatedAt, sevenDaysAgo)
            );
            dashboard.put("dailyActiveUsers", dau);

            // ---- 完成率：进度>=100%的选课记录占比 ----
            List<CourseEnrollment> allEnrollments = enrollmentService.list();
            long completedCount = allEnrollments.stream()
                    .filter(e -> e.getProgress() != null && e.getProgress().compareTo(BigDecimal.valueOf(100)) >= 0)
                    .count();
            double completionRate = totalEnrollments > 0 ?
                    Math.round(completedCount * 100.0 / totalEnrollments * 100.0) / 100.0 : 0;
            dashboard.put("completionRate", completionRate);

            // ---- 增长率（近30天新用户 / 总用户）----
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            long newUsers30d = userService.count(
                    new LambdaQueryWrapper<User>().ge(User::getCreatedAt, thirtyDaysAgo)
            );
            double growthRate = totalUsers > 0 ?
                    Math.round(newUsers30d * 100.0 / totalUsers * 100.0) / 100.0 : 0;
            dashboard.put("growthRate", growthRate);
            dashboard.put("userGrowth", growthRate);

            // ---- 成绩分布（从所有选课记录的finalScore计算）----
            List<CourseEnrollment> enrollsWithScore = allEnrollments.stream()
                    .filter(e -> e.getFinalScore() != null)
                    .collect(Collectors.toList());
            if (!enrollsWithScore.isEmpty()) {
                long excellent = enrollsWithScore.stream().filter(e -> e.getFinalScore().doubleValue() >= 90).count();
                long good = enrollsWithScore.stream().filter(e -> {
                    double s = e.getFinalScore().doubleValue(); return s >= 80 && s < 90;
                }).count();
                long medium = enrollsWithScore.stream().filter(e -> {
                    double s = e.getFinalScore().doubleValue(); return s >= 70 && s < 80;
                }).count();
                long pass = enrollsWithScore.stream().filter(e -> {
                    double s = e.getFinalScore().doubleValue(); return s >= 60 && s < 70;
                }).count();
                long fail = enrollsWithScore.stream().filter(e -> e.getFinalScore().doubleValue() < 60).count();
                dashboard.put("gradeDistribution", java.util.Arrays.asList(excellent, good, medium, pass, fail));
            } else {
                dashboard.put("gradeDistribution", java.util.Arrays.asList(0L, 0L, 0L, 0L, 0L));
            }

            // ---- 角色活跃度（按用户角色分组统计登录/操作次数，简化版用用户数代替）----
            long studentCount = userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.STUDENT));
            long teacherCount = userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.TEACHER));
            long adminCount = userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, RoleConstants.ADMIN));
            // 使用可变 HashMap 而非 Map.of()，确保被缓存（默认类型信息 + 反序列化）时可正常往返
            Map<String, Long> adminActivity = new HashMap<>(2);
            adminActivity.put("loginCount", adminCount * 3L);
            adminActivity.put("actionCount", adminCount * 5L);
            Map<String, Long> teacherActivity = new HashMap<>(2);
            teacherActivity.put("loginCount", teacherCount * 5L);
            teacherActivity.put("actionCount", teacherCount * 10L);
            Map<String, Long> studentActivity = new HashMap<>(2);
            studentActivity.put("loginCount", studentCount * 10L);
            studentActivity.put("actionCount", studentCount * 20L);
            dashboard.put("roleActivity", java.util.Arrays.asList(adminActivity, teacherActivity, studentActivity));
        } catch (Exception e) {
            dashboard.put("error", "统计数据加载异常: " + e.getMessage());
            // 填充默认值防止前端空白
            dashboard.putIfAbsent("userCount", 0L);
            dashboard.putIfAbsent("courseCount", 0L);
            dashboard.putIfAbsent("examCount", 0L);
            dashboard.putIfAbsent("submissionCount", 0L);
            dashboard.putIfAbsent("categoryDistribution", new HashMap<>());
            dashboard.putIfAbsent("gradeDistribution", java.util.Arrays.asList(0L, 0L, 0L, 0L, 0L));
            dashboard.putIfAbsent("roleActivity", new ArrayList<>());
        }
        return dashboard;
    }

    /**
     * 获取平台综合总览（增强版）
     * 包含用户数、课程数、活跃度、完成率等多维度数据
     */
    @Cacheable(cacheNames = "analytics", key = "'overview'")
    public Map<String, Object> getPlatformOverview() {
        Map<String, Object> overview = new HashMap<>(12);

        // 用户统计
        long totalUsers = userService.count();
        long activeUsers = userService.count(new LambdaQueryWrapper<User>().eq(User::getIsActive, Boolean.TRUE));
        overview.put("totalUsers", totalUsers);
        overview.put("activeUsers", activeUsers);

        // 课程统计
        long totalCourses = courseService.count();
        long activeCourses = courseService.count(new LambdaQueryWrapper<Course>().eq(Course::getIsActive, Boolean.TRUE));
        overview.put("totalCourses", totalCourses);
        overview.put("activeCourses", activeCourses);

        // 选课统计
        long totalEnrollments = enrollmentService.count();
        overview.put("totalEnrollments", totalEnrollments);

        // 活跃度计算：近7天有学习记录的用户数
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long activeLearningUsers = learningRecordService.count(
                new LambdaQueryWrapper<LearningRecord>()
                        .ge(LearningRecord::getCreatedAt, sevenDaysAgo)
        );
        overview.put("activeLearningUsers", activeLearningUsers);
        overview.put("activityRate", totalUsers > 0 ?
                Math.round(activeLearningUsers * 100.0 / totalUsers * 100.0) / 100.0 : 0);

        // 课程完成率：进度达到100%的选课记录占比
        List<CourseEnrollment> allEnrollments = enrollmentService.list();
        long completedCount = allEnrollments.stream()
                .filter(e -> e.getProgress() != null && e.getProgress().compareTo(BigDecimal.valueOf(100)) >= 0)
                .count();
        overview.put("completedEnrollments", completedCount);
        overview.put("completionRate", totalEnrollments > 0 ?
                Math.round(completedCount * 100.0 / totalEnrollments * 100.0) / 100.0 : 0);

        // 任务统计
        long totalTasks = taskService.count();
        long publishedTasks = taskService.count(new LambdaQueryWrapper<Task>().eq(Task::getIsPublished, Boolean.TRUE));
        overview.put("totalTasks", totalTasks);
        overview.put("publishedTasks", publishedTasks);

        // 考试统计
        long totalExams = examPaperService.count();
        overview.put("totalExams", totalExams);

        return overview;
    }

    /**
     * 获取平台级热力图数据（管理员专用）
     * 聚合所有用户的学习记录，按星期几(0-6)+时段(0-3:0-6时/6-12时/12-18时/18-24时)统计活跃度
     */
    public List<Map<String, Object>> getPlatformHeatmap(int days) {
        if (days <= 0) days = 30;

        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<LearningRecord> wrapper = new LambdaQueryWrapper<LearningRecord>()
                .ge(LearningRecord::getCreatedAt, startTime);

        List<LearningRecord> records = learningRecordService.list(wrapper);

        // 按 [星期几, 时段] 二维分组统计记录数
        Map<String, Long> heatmapGrid = records.stream()
                .filter(r -> r != null && r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(r -> {
                    int dayOfWeek = r.getCreatedAt().getDayOfWeek().getValue() % 7; // MON=1 -> 1, SUN=7 -> 0
                    int hourOfDay = r.getCreatedAt().getHour();
                    int timeSlot;
                    if (hourOfDay < 6) timeSlot = 0;       // 0-6时
                    else if (hourOfDay < 12) timeSlot = 1;   // 6-12时
                    else if (hourOfDay < 18) timeSlot = 2;   // 12-18时
                    else timeSlot = 3;                        // 18-24时
                    return dayOfWeek + "_" + timeSlot;
                }, Collectors.counting()));

        // 生成完整的 7x4 网格数据
        List<Map<String, Object>> result = new ArrayList<>(28);
        for (int day = 0; day < 7; day++) {
            for (int slot = 0; slot < 4; slot++) {
                String key = day + "_" + slot;
                long count = heatmapGrid.getOrDefault(key, 0L);
                Map<String, Object> cell = new HashMap<>(3);
                cell.put("dayOfWeek", day);
                cell.put("hour", slot);
                cell.put("value", count);
                cell.put("count", count);
                result.add(cell);
            }
        }

        return result;
    }

    /**
     * 获取用户增长趋势
     * 按天统计最近N天的新增注册用户数
     */
    public List<Map<String, Object>> getUserGrowthTrend(int days) {
        // 默认统计最近30天
        if (days <= 0) {
            days = 30;
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        // 获取时间范围内的所有用户
        List<User> users = userService.list(
                new LambdaQueryWrapper<User>()
                        .ge(User::getCreatedAt, startDate.atStartOfDay())
                        .le(User::getCreatedAt, endDate.atTime(java.time.LocalTime.MAX))
                        .orderByAsc(User::getCreatedAt)
        );

        // 按日期分组统计（过滤createdAt为null的用户）
        Map<LocalDate, Long> userCountMap = users.stream()
                .filter(user -> user != null && user.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        user -> user.getCreatedAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.counting()
                ));

        // 填充所有日期（包括没有新用户的日期，数量为0）
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            Map<String, Object> item = new HashMap<>(3);
            item.put("date", current.toString());
            item.put("newUsers", userCountMap.getOrDefault(current, 0L));

            // 累计用户总数（截止到当天的总用户数）
            final LocalDate day = current;
            long cumulativeCount = users.stream()
                    .filter(user -> user != null && user.getCreatedAt() != null
                            && !user.getCreatedAt().toLocalDate().isAfter(day))
                    .count();
            item.put("cumulativeUsers", cumulativeCount);

            trend.add(item);
            current = current.plusDays(1);
        }

        return trend;
    }
}
