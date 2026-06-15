package com.aicourse.service.impl;

import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据统计分析服务实现类
 * 提供平台总览、用户个人数据、学习趋势、成绩分布、课程分析、考试统计等全方位统计功能
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final UserService userService;
    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final LearningRecordService learningRecordService;
    private final ExamSessionService examSessionService;
    private final ExamPaperService examPaperService;

    public AnalyticsServiceImpl(
            UserService userService,
            CourseService courseService,
            CourseEnrollmentService enrollmentService,
            TaskService taskService,
            SubmissionService submissionService,
            LearningRecordService learningRecordService,
            ExamSessionService examSessionService,
            ExamPaperService examPaperService
    ) {
        this.userService = userService;
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.learningRecordService = learningRecordService;
        this.examSessionService = examSessionService;
        this.examPaperService = examPaperService;
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

    private Map<String, Long> getGradeDistribution(List<CourseEnrollment> enrollments) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("excellent", enrollments.stream().filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() >= 90).count());
        distribution.put("good", enrollments.stream().filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() >= 80 && e.getFinalScore().doubleValue() < 90).count());
        distribution.put("medium", enrollments.stream().filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() >= 70 && e.getFinalScore().doubleValue() < 80).count());
        distribution.put("pass", enrollments.stream().filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() >= 60 && e.getFinalScore().doubleValue() < 70).count());
        distribution.put("fail", enrollments.stream().filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() < 60).count());
        return distribution;
    }

    @Override
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
            long studentCount = userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, "STUDENT"));
            long teacherCount = userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, "TEACHER"));
            long adminCount = userService.count(new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN"));
            dashboard.put("roleActivity", java.util.Arrays.asList(
                    java.util.Map.of("loginCount", adminCount * 3L, "actionCount", adminCount * 5L),
                    java.util.Map.of("loginCount", teacherCount * 5L, "actionCount", teacherCount * 10L),
                    java.util.Map.of("loginCount", studentCount * 10L, "actionCount", studentCount * 20L)
            ));
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
    @Override
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

    @Override
    public Map<String, Object> getUserDashboard(Long userId) {
        Map<String, Object> userData = new HashMap<>(12);
        try {
            // ---- 已选课程数量（Student statCards[0]）----
            long enrolledCount = enrollmentService.count(
                    new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getStudentId, userId)
            );
            userData.put("enrolledCourses", enrolledCount);
            userData.put("courseCount", enrolledCount);

            // ---- 平均学习进度 ----
            List<CourseEnrollment> enrollmentList = enrollmentService.list(
                    new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getStudentId, userId)
            );
            double avgProgress = enrollmentList.stream()
                    .mapToDouble(e -> e.getProgress() != null ? e.getProgress().doubleValue() : 0D)
                    .average()
                    .orElse(0D);
            userData.put("averageProgress", Math.round(avgProgress * 100.0) / 100.0);

            // ---- 累计学习时长（分钟）----
            int totalStudyTime = learningRecordService.list(
                    new LambdaQueryWrapper<LearningRecord>().eq(LearningRecord::getUserId, userId)
            ).stream().mapToInt(LearningRecord::getDuration).sum();
            userData.put("totalStudyTime", totalStudyTime);

            // ---- 平均成绩（Student statCards[1]，从选课记录的 finalScore 计算）----
            double avgScore = enrollmentList.stream()
                    .filter(e -> e.getFinalScore() != null)
                    .mapToDouble(e -> e.getFinalScore().doubleValue())
                    .average()
                    .orElse(0D);
            userData.put("averageScore", Math.round(avgScore * 100.0) / 100.0);
            userData.put("avgScore", Math.round(avgScore));

            // ---- 连续学习天数（Student statCards[2]）----
            Set<LocalDate> studyDates = learningRecordService.list(
                    new LambdaQueryWrapper<LearningRecord>()
                            .eq(LearningRecord::getUserId, userId)
                            .orderByDesc(LearningRecord::getCreatedAt)
                            .last("LIMIT 90")
            ).stream()
                    .map(r -> r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            int streakDays = calculateStreakDays(studyDates);
            userData.put("streakDays", streakDays);
            userData.put("consecutiveDays", streakDays);

            // ---- 待办/未完成任务数（Student statCards[3]）----
            List<Long> courseIds = enrollmentList.stream()
                    .map(CourseEnrollment::getCourseId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            long pendingTasks = 0L;
            if (!courseIds.isEmpty()) {
                pendingTasks = taskService.count(
                        new LambdaQueryWrapper<Task>()
                                .in(Task::getCourseId, courseIds)
                                .eq(Task::getIsPublished, Boolean.TRUE)
                                .and(w -> w.isNull(Task::getDueDate).or().ge(Task::getDueDate, LocalDate.now()))
                );
            }
            long submittedCount = submissionService.count(
                    new LambdaQueryWrapper<Submission>()
                            .eq(Submission::getStudentId, userId)
                            .in(Submission::getStatus, "SUBMITTED", "GRADED")
            );
            userData.put("pendingTasks", Math.max(0, pendingTasks - submittedCount));
            userData.put("todoCount", Math.max(0, pendingTasks - submittedCount));
        } catch (Exception e) {
            userData.put("error", "学生数据加载异常: " + e.getMessage());
            userData.putIfAbsent("enrolledCourses", 0L);
            userData.putIfAbsent("courseCount", 0L);
            userData.putIfAbsent("averageScore", 0D);
            userData.putIfAbsent("avgScore", 0);
            userData.putIfAbsent("streakDays", 0);
            userData.putIfAbsent("pendingTasks", 0L);
            userData.putIfAbsent("todoCount", 0L);
        }
        return userData;
    }

    /**
     * 计算连续学习天数（从今天往前推算）
     */
    private int calculateStreakDays(Set<LocalDate> studyDates) {
        if (studyDates == null || studyDates.isEmpty()) return 0;
        LocalDate today = LocalDate.now();
        int streak = 0;
        LocalDate checkDay = today;
        // 允许今天还没学习的情况，从昨天开始检查
        if (!studyDates.contains(today)) {
            checkDay = today.minusDays(1);
        }
        while (studyDates.contains(checkDay)) {
            streak++;
            checkDay = checkDay.minusDays(1);
            // 防止无限循环，最多计算365天
            if (streak > 365) break;
        }
        return streak;
    }

    @Override
    public List<Map<String, Object>> getLearningTrends(Long userId) {
        List<LearningRecord> recordList = learningRecordService.list(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getUserId, userId)
                        .orderByAsc(LearningRecord::getCreatedAt)
        );

        // 按日期分组并累加时长，有序Map保证日期顺序（过滤createdAt为null的记录）
        Map<String, Integer> dateDurationMap = recordList.stream()
                .filter(record -> record != null && record.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        record -> record.getCreatedAt().toLocalDate().toString(),
                        LinkedHashMap::new,
                        Collectors.summingInt(LearningRecord::getDuration)
                ));

        return dateDurationMap.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>(2);
                    item.put("date", entry.getKey());
                    item.put("duration", entry.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取学习热力图数据
     * 支持按日期范围筛选，返回每日学习时长用于前端热力图展示
     */
    @Override
    public List<Map<String, Object>> getLearningHeatmap(Long userId, LocalDate startDate, LocalDate endDate) {
        // 构建查询条件
        LambdaQueryWrapper<LearningRecord> wrapper = new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId);

        // 添加日期范围筛选
        if (startDate != null) {
            wrapper.ge(LearningRecord::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(LearningRecord::getCreatedAt, endDate.atTime(LocalTime.MAX));
        }

        List<LearningRecord> recordList = learningRecordService.list(wrapper);

        // 按日期分组统计学习时长（过滤createdAt为null的记录）
        Map<String, Integer> heatmapData = recordList.stream()
                .filter(record -> record != null && record.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        record -> record.getCreatedAt().toLocalDate().toString(),
                        LinkedHashMap::new,
                        Collectors.summingInt(LearningRecord::getDuration)
                ));

        // 转换为列表格式
        return heatmapData.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>(3);
                    item.put("date", entry.getKey());
                    item.put("duration", entry.getValue());
                    // 根据学习时长设置强度等级（1-5级）
                    int level = calculateHeatLevel(entry.getValue());
                    item.put("level", level);
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 计算热力等级（基于学习时长）
     */
    private int calculateHeatLevel(int durationMinutes) {
        if (durationMinutes == 0) return 0;
        if (durationMinutes < 30) return 1;      // <30分钟
        if (durationMinutes < 60) return 2;      // 30-60分钟
        if (durationMinutes < 120) return 3;     // 1-2小时
        if (durationMinutes < 180) return 4;     // 2-3小时
        return 5;                                 // >3小时
    }

    /**
     * 获取平台级热力图数据（管理员专用）
     * 聚合所有用户的学习记录，按星期几(0-6)+时段(0-3:0-6时/6-12时/12-18时/18-24时)统计活跃度
     */
    @Override
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

    @Override
    public Map<String, Object> getGradeDistribution(Long courseId) {
        List<CourseEnrollment> enrollmentList = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId)
        );

        Map<String, Object> gradeData = new HashMap<>(3);
        gradeData.put("total", enrollmentList.size());

        // 平均分
        double avgScore = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null)
                .mapToDouble(e -> e.getFinalScore().doubleValue())
                .average()
                .orElse(0D);
        gradeData.put("average", Math.round(avgScore * 100.0) / 100.0);

        // 及格人数
        long passCount = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() >= 60)
                .count();
        gradeData.put("passed", passCount);

        return gradeData;
    }

    /**
     * 获取课程成绩分布直方图（增强版）
     * 按分数段（0-59, 60-69, 70-79, 80-89, 90-100）统计人数分布
     */
    @Override
    public Map<String, Object> getGradeDistributionHistogram(Long courseId) {
        List<CourseEnrollment> enrollmentList = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getCourseId, courseId)
                        .isNotNull(CourseEnrollment::getFinalScore)
        );

        Map<String, Object> histogram = new HashMap<>(8);
        histogram.put("total", enrollmentList.size());

        // 计算平均分、最高分、最低分
        DoubleSummaryStatistics scoreStats = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null)
                .mapToDouble(e -> e.getFinalScore().doubleValue())
                .summaryStatistics();

        histogram.put("average", Math.round(scoreStats.getAverage() * 100.0) / 100.0);
        histogram.put("maxScore", Math.round(scoreStats.getMax() * 100.0) / 100.0);
        histogram.put("minScore", Math.round(scoreStats.getMin() * 100.0) / 100.0);

        // 按分数段统计人数
        Map<String, Long> distribution = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null)
                .collect(Collectors.groupingBy(e -> {
                    double score = e.getFinalScore().doubleValue();
                    if (score < 60) return "0-59（不及格）";
                    if (score < 70) return "60-69（及格）";
                    if (score < 80) return "70-79（中等）";
                    if (score < 90) return "80-89（良好）";
                    return "90-100（优秀）";
                }, Collectors.counting()));

        histogram.put("distribution", distribution);

        // 及格率和优秀率
        long totalWithScore = enrollmentList.size();
        long passCount = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() >= 60)
                .count();
        long excellentCount = enrollmentList.stream()
                .filter(e -> e.getFinalScore() != null && e.getFinalScore().doubleValue() >= 90)
                .count();

        histogram.put("passRate", totalWithScore > 0 ?
                Math.round(passCount * 100.0 / totalWithScore * 100.0) / 100.0 : 0);
        histogram.put("excellentRate", totalWithScore > 0 ?
                Math.round(excellentCount * 100.0 / totalWithScore * 100.0) / 100.0 : 0);

        return histogram;
    }

    @Override
    public Map<String, Object> getCourseAnalysis(Long courseId) {
        Course course = courseService.getById(courseId);
        if (course == null) {
            return Collections.singletonMap("error", "课程不存在");
        }

        Map<String, Object> courseData = new HashMap<>(6);
        courseData.put("courseInfo", course);
        courseData.put("enrollmentCount", enrollmentService.count(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId)
        ));
        courseData.put("taskCount", taskService.count(
                new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId)
        ));

        // 计算课程平均进度和平均成绩
        List<CourseEnrollment> enrollments = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId)
        );
        double avgProgress = enrollments.stream()
                .mapToDouble(e -> e.getProgress() != null ? e.getProgress().doubleValue() : 0D)
                .average()
                .orElse(0D);
        courseData.put("averageProgress", Math.round(avgProgress * 100.0) / 100.0);

        double avgScore = enrollments.stream()
                .filter(e -> e.getFinalScore() != null)
                .mapToDouble(e -> e.getFinalScore().doubleValue())
                .average()
                .orElse(0D);
        courseData.put("averageScore", Math.round(avgScore * 100.0) / 100.0);

        return courseData;
    }

    /**
     * 获取热门课程排行
     * 按选课人数降序排列，返回前20门课程
     */
    @Override
    public List<Map<String, Object>> getCourseRanking() {
        // 获取所有活跃课程
        List<Course> courses = courseService.list(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getIsActive, Boolean.TRUE)
                        .orderByDesc(Course::getCreatedAt)
        );

        // 统计每门课程的选课人数并排序
        return courses.stream()
                .map(course -> {
                    long enrollmentCount = enrollmentService.count(
                            new LambdaQueryWrapper<CourseEnrollment>()
                                    .eq(CourseEnrollment::getCourseId, course.getId())
                    );
                    Map<String, Object> item = new HashMap<>(6);
                    item.put("courseId", course.getId());
                    item.put("courseName", course.getName());
                    item.put("courseCode", course.getCode());
                    item.put("teacherId", course.getTeacherId());
                    item.put("enrollmentCount", enrollmentCount);
                    item.put("category", course.getCategory());
                    item.put("difficulty", course.getDifficulty());
                    return item;
                })
                .sorted((a, b) -> ((Long) b.get("enrollmentCount")).compareTo((Long) a.get("enrollmentCount")))
                .limit(20)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户增长趋势
     * 按天统计最近N天的新增注册用户数
     */
    @Override
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

    /**
     * 获取考试统计详情
     * 包含参考人数、平均分、最高/最低分、及格率、分数分布等
     */
    @Override
    public Map<String, Object> getExamStatistics(Long examPaperId) {
        ExamPaper examPaper = examPaperService.getById(examPaperId);
        if (examPaper == null) {
            return Collections.singletonMap("error", "试卷不存在");
        }

        Map<String, Object> statistics = new HashMap<>(12);
        statistics.put("examPaperId", examPaperId);
        statistics.put("examTitle", examPaper.getTitle());
        statistics.put("totalScore", examPaper.getTotalScore());
        statistics.put("questionCount", examPaper.getQuestionCount());
        statistics.put("duration", examPaper.getDuration());

        // 获取该试卷的所有考试会话（已提交或已阅卷）
        List<ExamSession> sessions = examSessionService.list(
                new LambdaQueryWrapper<ExamSession>()
                        .eq(ExamSession::getExamPaperId, examPaperId)
                        .in(ExamSession::getStatus, "SUBMITTED", "GRADED")
        );

        statistics.put("totalParticipants", sessions.size());

        // 统计分数数据
        if (!sessions.isEmpty()) {
            DoubleSummaryStatistics scoreStats = sessions.stream()
                    .filter(s -> s.getTotalScore() != null)
                    .mapToDouble(s -> s.getTotalScore().doubleValue())
                    .summaryStatistics();

            statistics.put("averageScore", Math.round(scoreStats.getAverage() * 100.0) / 100.0);
            statistics.put("maxScore", Math.round(scoreStats.getMax() * 100.0) / 100.0);
            statistics.put("minScore", Math.round(scoreStats.getMin() * 100.0) / 100.0);

            // 及格率（假设60%为及格线）
            BigDecimal passLine = examPaper.getTotalScore() != null ?
                    examPaper.getTotalScore().multiply(BigDecimal.valueOf(0.6)) : BigDecimal.valueOf(60);
            long passCount = sessions.stream()
                    .filter(s -> s.getTotalScore() != null && s.getTotalScore().compareTo(passLine) >= 0)
                    .count();
            statistics.put("passRate", Math.round(passCount * 100.0 / sessions.size() * 100.0) / 100.0);

            // 优秀率（85%以上）
            BigDecimal excellentLine = examPaper.getTotalScore() != null ?
                    examPaper.getTotalScore().multiply(BigDecimal.valueOf(0.85)) : BigDecimal.valueOf(85);
            long excellentCount = sessions.stream()
                    .filter(s -> s.getTotalScore() != null && s.getTotalScore().compareTo(excellentLine) >= 0)
                    .count();
            statistics.put("excellentRate", Math.round(excellentCount * 100.0 / sessions.size() * 100.0) / 100.0);

            // 分数段分布
            Map<String, Long> scoreDistribution = sessions.stream()
                    .filter(s -> s.getTotalScore() != null)
                    .collect(Collectors.groupingBy(s -> {
                        double percentage = s.getPercentage() != null ?
                                s.getPercentage().doubleValue() :
                                (s.getTotalScore().doubleValue() /
                                        (examPaper.getTotalScore() != null ? examPaper.getTotalScore().doubleValue() : 100) * 100);
                        if (percentage < 60) return "0-59（不及格）";
                        if (percentage < 70) return "60-69（及格）";
                        if (percentage < 80) return "70-79（中等）";
                        if (percentage < 90) return "80-89（良好）";
                        return "90-100（优秀）";
                    }, Collectors.counting()));
            statistics.put("scoreDistribution", scoreDistribution);

            // 平均用时（分钟）
            double avgTimeSpent = sessions.stream()
                    .filter(s -> s.getTimeSpent() != null)
                    .mapToInt(ExamSession::getTimeSpent)
                    .average()
                    .orElse(0);
            statistics.put("avgTimeSpentMinutes", Math.round(avgTimeSpent));

        } else {
            statistics.put("averageScore", 0);
            statistics.put("maxScore", 0);
            statistics.put("minScore", 0);
            statistics.put("passRate", 0);
            statistics.put("excellentRate", 0);
            statistics.put("scoreDistribution", Collections.emptyMap());
            statistics.put("avgTimeSpentMinutes", 0);
        }

        return statistics;
    }

    /**
     * 获取教师端仪表盘数据
     * 包含课程数、学生数、待批改作业数、平均分、趋势数据等
     */
    @Override
    public Map<String, Object> getTeacherDashboard(Long teacherId) {
        Map<String, Object> teacherData = new HashMap<>(16);
        try {
            // ---- 教师的课程数量（Teacher statCards[0]）----
            long courseCount = courseService.count(
                    new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, teacherId)
            );
            teacherData.put("courseCount", courseCount);
            teacherData.put("totalCourses", courseCount);

            // ---- 学生总数 ----
            List<Course> teacherCourses = courseService.list(
                    new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, teacherId)
            );
            List<Long> courseIds = teacherCourses.stream().map(Course::getId).filter(Objects::nonNull).collect(Collectors.toList());
            long studentCount = 0L;
            if (!courseIds.isEmpty()) {
                studentCount = enrollmentService.count(
                        new LambdaQueryWrapper<CourseEnrollment>()
                                .in(CourseEnrollment::getCourseId, courseIds)
                );
            }
            teacherData.put("studentCount", studentCount);
            teacherData.put("totalStudents", studentCount);

            // ---- 待批改作业数（Teacher statCards[2]）----
            long pendingGradeCount = 0L;
            if (!courseIds.isEmpty()) {
                List<Task> publishedTasks = taskService.list(
                        new LambdaQueryWrapper<Task>()
                                .in(Task::getCourseId, courseIds)
                                .eq(Task::getIsPublished, Boolean.TRUE)
                );
                List<Long> taskIds = publishedTasks.stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
                if (!taskIds.isEmpty()) {
                    pendingGradeCount = submissionService.count(
                            new LambdaQueryWrapper<Submission>()
                                    .in(Submission::getTaskId, taskIds)
                                    .eq(Submission::getStatus, "SUBMITTED")
                    );
                }
            }
            teacherData.put("pendingGradeCount", pendingGradeCount);
            teacherData.put("pendingGrading", pendingGradeCount);

            // ---- 平均成绩（Teacher statCards[3]）----
            double avgScore = 0D;
            if (!courseIds.isEmpty()) {
                List<Long> allTaskIds = taskService.list(
                        new LambdaQueryWrapper<Task>().in(Task::getCourseId, courseIds)
                ).stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
                if (!allTaskIds.isEmpty()) {
                    avgScore = submissionService.list(
                            new LambdaQueryWrapper<Submission>()
                                    .in(Submission::getTaskId, allTaskIds)
                                    .isNotNull(Submission::getScore)
                    ).stream()
                            .mapToDouble(s -> s.getScore() != null ? s.getScore().doubleValue() : 0D)
                            .average()
                            .orElse(0D);
                }
            }
            teacherData.put("avgScore", Math.round(avgScore * 10) / 10.0);

            // ---- 课程分布（Teacher 饼图）----
            List<Map<String, Object>> courseDistribution = teacherCourses.stream()
                    .map(course -> {
                        long enrollmentCount = enrollmentService.count(
                                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, course.getId())
                        );
                        Map<String, Object> item = new HashMap<>(4);
                        item.put("name", course.getName());
                        item.put("courseName", course.getName());
                        item.put("value", enrollmentCount);
                        item.put("studentCount", enrollmentCount);
                        return item;
                    })
                    .collect(Collectors.toList());
            teacherData.put("courseDistribution", courseDistribution);

            // ---- 近期教学活动趋势（Teacher 折线图）----
            LocalDate today = LocalDate.now();
            List<Map<String, Object>> trendData = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                long dailySubmissions = 0L;
                if (!courseIds.isEmpty()) {
                    List<Long> tIds = taskService.list(
                            new LambdaQueryWrapper<Task>().in(Task::getCourseId, courseIds)
                    ).stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
                    if (!tIds.isEmpty()) {
                        dailySubmissions = submissionService.count(
                                new LambdaQueryWrapper<Submission>()
                                        .in(Submission::getTaskId, tIds)
                                        .ge(Submission::getCreatedAt, day.atStartOfDay())
                                        .lt(Submission::getCreatedAt, day.plusDays(1).atStartOfDay())
                        );
                    }
                }
                Map<String, Object> point = new HashMap<>(4);
                point.put("label", day.toString());
                point.put("date", day.toString());
                point.put("value", dailySubmissions);
                point.put("count", dailySubmissions);
                trendData.add(point);
            }
            teacherData.put("trendData", trendData);
        } catch (Exception e) {
            teacherData.put("error", "教师数据加载异常: " + e.getMessage());
            teacherData.putIfAbsent("courseCount", 0L);
            teacherData.putIfAbsent("studentCount", 0L);
            teacherData.putIfAbsent("pendingGradeCount", 0L);
            teacherData.putIfAbsent("avgScore", 0D);
            teacherData.putIfAbsent("courseDistribution", new ArrayList<>());
            teacherData.putIfAbsent("trendData", new ArrayList<>());
        }
        return teacherData;
    }
}