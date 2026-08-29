package com.aicourse.service.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.constant.SubmissionStatus;

import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;
import org.springframework.cache.annotation.Cacheable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教学运行（教师）视角数据分析协作者
 * <p>承接原 {@link AnalyticsServiceImpl} 中教师视角与教学监管的统计职责：教师端仪表盘、
 * 学情报告导出、学习掉队预警聚合及其相关私有 helper。</p>
 * <p>其中 {@link #exportReport(Long)} 复用了 {@link CourseAnalyticsComponent#getCourseAnalysis(Long)}
 * 的课程级分析能力，因此本协作者注入并委派 {@link CourseAnalyticsComponent}（单向依赖，无环）。</p>
 */
@Component
public class TeachingAnalyticsComponent {

    private static final Logger log = LoggerFactory.getLogger(TeachingAnalyticsComponent.class);

    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final UserService userService;
    private final CourseAnalyticsComponent courseAnalytics;

    public TeachingAnalyticsComponent(
            CourseService courseService,
            CourseEnrollmentService enrollmentService,
            TaskService taskService,
            SubmissionService submissionService,
            UserService userService,
            CourseAnalyticsComponent courseAnalytics
    ) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.userService = userService;
        this.courseAnalytics = courseAnalytics;
    }

    /**
     * 获取教师端仪表盘数据
     * 包含课程数、学生数、待批改作业数、平均分、趋势数据等
     */
    @Cacheable(cacheNames = "analytics", key = "'teacher_' + #teacherId")
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
                                    .eq(Submission::getStatus, SubmissionStatus.SUBMITTED)
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
            // 优化：教师全部任务的 taskId 只查询一次，避免 7 天循环内每天全表拉取任务（原 N+1）
            List<Long> teacherTaskIds = new ArrayList<>();
            if (!courseIds.isEmpty()) {
                teacherTaskIds = taskService.list(
                        new LambdaQueryWrapper<Task>().in(Task::getCourseId, courseIds)
                ).stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
            }
            LocalDate today = LocalDate.now();
            List<Map<String, Object>> trendData = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                long dailySubmissions = 0L;
                if (!teacherTaskIds.isEmpty()) {
                    dailySubmissions = submissionService.count(
                            new LambdaQueryWrapper<Submission>()
                                    .in(Submission::getTaskId, teacherTaskIds)
                                    .ge(Submission::getCreatedAt, day.atStartOfDay())
                                    .lt(Submission::getCreatedAt, day.plusDays(1).atStartOfDay())
                    );
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
            log.error("查询Teaching分析失败: {}", e.getMessage(), e);

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

    /**
     * 导出学情分析报告（增强版，包含多维度分析）
     */
    public Map<String, Object> exportReport(Long courseId) {
        log.info("导出Teaching分析");

        Map<String, Object> report = courseAnalytics.getCourseAnalysis(courseId);
        Course course = courseService.getById(courseId);
        if (course == null) return report;

        // 1. 成绩分布统计
        List<CourseEnrollment> enrollments = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId)
        );
        Map<String, Long> scoreDistribution = new HashMap<>();
        scoreDistribution.put("excellent", 0L);  // 90-100
        scoreDistribution.put("good", 0L);       // 80-89
        scoreDistribution.put("average", 0L);    // 60-79
        scoreDistribution.put("fail", 0L);       // <60
        for (CourseEnrollment e : enrollments) {
            if (e.getFinalScore() == null) continue;
            double s = e.getFinalScore().doubleValue();
            if (s >= 90) scoreDistribution.merge("excellent", 1L, Long::sum);
            else if (s >= 80) scoreDistribution.merge("good", 1L, Long::sum);
            else if (s >= 60) scoreDistribution.merge("average", 1L, Long::sum);
            else scoreDistribution.merge("fail", 1L, Long::sum);
        }
        report.put("scoreDistribution", scoreDistribution);

        // 2. 作业提交率
        List<Task> tasks = taskService.list(
                new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId)
        );
        // 修复 N+1：一次性按 taskId IN (...) 统计，避免任务数越多查询越多的指数增长
        long totalSubmissions = 0;
        if (!tasks.isEmpty()) {
            List<Long> taskIds = tasks.stream().map(Task::getId).toList();
            totalSubmissions = submissionService.count(
                    new LambdaQueryWrapper<Submission>().in(Submission::getTaskId, taskIds)
            );
        }
        long expectedSubmissions = (long) tasks.size() * enrollments.size();
        double submissionRate = expectedSubmissions > 0
                ? Math.round(totalSubmissions * 10000.0 / expectedSubmissions) / 100.0 : 0;
        report.put("submissionRate", submissionRate);
        report.put("totalTasks", tasks.size());
        report.put("totalSubmissions", totalSubmissions);

        // 3. 学习进度统计
        double avgProgress = enrollments.stream()
                .mapToDouble(e -> e.getProgress() != null ? e.getProgress().doubleValue() : 0D)
                .average().orElse(0D);
        long completedStudents = enrollments.stream()
                .filter(e -> e.getProgress() != null && e.getProgress().doubleValue() >= 100D)
                .count();
        report.put("averageProgress", Math.round(avgProgress * 100.0) / 100.0);
        report.put("completedStudents", completedStudents);
        report.put("completionRate", enrollments.size() > 0
                ? Math.round(completedStudents * 10000.0 / enrollments.size()) / 100.0 : 0);

        // 4. 学生排名 Top10
        List<Map<String, Object>> studentRanking = enrollments.stream()
                .filter(e -> e.getFinalScore() != null)
                .sorted((a, b) -> b.getFinalScore().compareTo(a.getFinalScore()))
                .limit(10)
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("studentId", e.getStudentId());
                    item.put("score", e.getFinalScore());
                    item.put("progress", e.getProgress());
                    return item;
                })
                .collect(Collectors.toList());
        report.put("studentRanking", studentRanking);

        report.put("exportTime", LocalDateTime.now().toString());
        report.put("reportType", "学情分析报告");
        report.put("generatedBy", "AnalyticsService");
        return report;
    }

    /**
     * 全校/多维「学习掉队」预警聚合（教学运行监管核心）
     * 复用电课程教学服务的预警判定，并按维度下钻聚合。
     */
    public Map<String, Object> getAtRiskStudents(String groupBy, Long courseId,
                                              String department, String major, Long classId) {
        String dim = (groupBy == null || groupBy.isBlank()) ? "school" : groupBy.toLowerCase();
        // 1) 选课集合
        List<CourseEnrollment> all;
        if (courseId != null) {
            all = enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>()
                    .eq(CourseEnrollment::getCourseId, courseId));
        } else {
            all = enrollmentService.list();
        }
        // 2) 按院系/专业/班级过滤（经 users 映射）
        if (department != null || major != null || classId != null) {
            Set<Long> allowed = studentIdSetByProfile(department, major, classId);
            all = all.stream().filter(e -> allowed.contains(e.getStudentId())).collect(Collectors.toList());
        }
        int total = all.size();

        // 3) 学生画像映射（院系/专业/班级/课程名）
        Set<Long> stuIds = all.stream().map(CourseEnrollment::getStudentId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapOf(stuIds);
        Set<Long> courseIds = all.stream().map(CourseEnrollment::getCourseId).collect(Collectors.toSet());
        Map<Long, String> courseNameMap = courseNameMapOf(courseIds);

        // 4) 逐条判定 + 详情
        List<Map<String, Object>> details = new ArrayList<>();
        Map<String, long[]> groupAgg = new LinkedHashMap<>(); // key -> [total, atRisk]
        for (CourseEnrollment e : all) {
            boolean lowProgress = e.getProgress() != null && e.getProgress().doubleValue() < 40;
            boolean lowScore = e.getFinalScore() != null && e.getFinalScore().doubleValue() < 60;
            boolean atRisk = lowProgress || lowScore;

            String gKey = groupKey(dim, e, userMap.get(e.getStudentId()), courseNameMap);
            long[] agg = groupAgg.computeIfAbsent(gKey, k -> new long[2]);
            agg[0]++;
            if (atRisk) {
                agg[1]++;
                Map<String, Object> d = new LinkedHashMap<>(8);
                User u = userMap.get(e.getStudentId());
                d.put("studentId", e.getStudentId());
                d.put("studentName", u != null && u.getFullName() != null ? u.getFullName() : String.valueOf(e.getStudentId()));
                d.put("courseId", e.getCourseId());
                d.put("courseName", courseNameMap.getOrDefault(e.getCourseId(), ""));
                d.put("progress", e.getProgress() != null ? round2(e.getProgress().doubleValue()) : 0);
                d.put("finalScore", e.getFinalScore() != null ? round2(e.getFinalScore().doubleValue()) : null);
                int sev = (lowProgress ? 1 : 0) + (lowScore ? 1 : 0);
                d.put("level", sev >= 2 ? "HIGH" : "MEDIUM");
                if (u != null) {
                    d.put("department", u.getDepartment());
                    d.put("major", u.getMajor());
                    d.put("grade", u.getGrade());
                    d.put("className", u.getClassName());
                }
                details.add(d);
            }
        }

        // 5) 分组聚合结果
        List<Map<String, Object>> groups = new ArrayList<>();
        if (!"school".equals(dim)) {
            for (Map.Entry<String, long[]> en : groupAgg.entrySet()) {
                long[] a = en.getValue();
                Map<String, Object> g = new LinkedHashMap<>(5);
                g.put("key", en.getKey());
                g.put("label", en.getKey());
                g.put("total", a[0]);
                g.put("atRisk", a[1]);
                g.put("atRiskRate", a[0] > 0 ? Math.round(a[1] * 10000.0 / a[0]) / 100.0 : 0);
                groups.add(g);
            }
            groups.sort((x, y) -> ((Long) y.get("atRisk")).compareTo((Long) x.get("atRisk")));
        }

        // 详情按严重度排序并截断，避免超大负载
        details.sort((x, y) -> y.get("level").toString().compareTo(x.get("level").toString()));
        if (details.size() > 200) details = details.subList(0, 200);

        int atRisk = details.size();
        Map<String, Object> result = new LinkedHashMap<>(6);
        result.put("totalStudents", total);
        result.put("atRiskCount", atRisk);
        result.put("atRiskRate", total > 0 ? Math.round(atRisk * 10000.0 / total) / 100.0 : 0);
        result.put("groupBy", dim);
        result.put("groups", groups);
        result.put("details", details);
        return result;
    }

    private Set<Long> studentIdSetByProfile(String department, String major, Long classId) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (department != null) qw.eq(User::getDepartment, department);
        if (major != null) qw.eq(User::getMajor, major);
        if (classId != null) qw.eq(User::getClassId, classId);
        return userService.list(qw).stream().map(User::getId).collect(Collectors.toSet());
    }

    private Map<Long, User> userMapOf(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        return userService.listByIds(ids).stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private Map<Long, String> courseNameMapOf(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();
        return courseService.listByIds(ids).stream()
                .collect(Collectors.toMap(Course::getId, c -> c.getName() != null ? c.getName() : "", (a, b) -> a));
    }

    private String groupKey(String dim, CourseEnrollment e, User u, Map<Long, String> courseNameMap) {
        switch (dim) {
            case "course":
                return courseNameMap.getOrDefault(e.getCourseId(), "课程#" + e.getCourseId());
            case "department":
                return u != null && u.getDepartment() != null ? u.getDepartment() : "未知院系";
            case "major":
                return u != null && u.getMajor() != null ? u.getMajor() : "未知专业";
            case "class":
                return u != null && u.getClassName() != null ? u.getClassName() : "未知班级";
            default:
                return "全校";
        }
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
