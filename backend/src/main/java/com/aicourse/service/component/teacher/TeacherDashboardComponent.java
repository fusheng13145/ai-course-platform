package com.aicourse.service.component.teacher;

import com.aicourse.constant.SubmissionStatus;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.LearningRecord;
import com.aicourse.entity.Question;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.AIService;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 教师驾驶舱/仪表盘协作者：单课程教学驾驶舱、预警学生聚合、我的课程指标、增强版仪表盘（含 AI 学情解读）。
 * <p>由 {@code TeacherCourseServiceImpl} 门面委托调用。</p>
 */
@Component
public class TeacherDashboardComponent {

    private static final Logger log = LoggerFactory.getLogger(TeacherDashboardComponent.class);

    /** AI 教学总览的独立入口（首页聚合只回它的地址，不在这里调用模型）。 */
    public static final String AI_OVERVIEW_ENDPOINT = "/api/teacher/dashboard/ai-overview";

    private final CourseService courseService;
    private final CourseChapterService chapterService;
    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final ExamPaperService examPaperService;
    private final QuestionService questionService;
    private final LearningRecordService learningRecordService;
    private final AIService aiService;
    private final TeacherCourseComponent support;
    private final TeacherChapterComponent chapterComponent;

    public TeacherDashboardComponent(CourseService courseService,
                                     CourseChapterService chapterService,
                                     CourseEnrollmentService enrollmentService,
                                     TaskService taskService,
                                     SubmissionService submissionService,
                                     ExamPaperService examPaperService,
                                     QuestionService questionService,
                                     LearningRecordService learningRecordService,
                                     AIService aiService,
                                     TeacherCourseComponent support,
                                     TeacherChapterComponent chapterComponent) {
        this.courseService = courseService;
        this.chapterService = chapterService;
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.examPaperService = examPaperService;
        this.questionService = questionService;
        this.learningRecordService = learningRecordService;
        this.aiService = aiService;
        this.support = support;
        this.chapterComponent = chapterComponent;
    }

    /** 单课程教学驾驶舱 */
    @Transactional(readOnly = true)
    public Map<String, Object> getCourseTeachingDashboard(Long courseId, Long operatorId) {
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }

        Map<String, Object> dashboard = new LinkedHashMap<>();
        try {
            long totalStudents = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));
            long activeStudents = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>()
                    .eq(CourseEnrollment::getCourseId, courseId).eq(CourseEnrollment::getIsActive, Boolean.TRUE));
            long pendingApproval = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>()
                    .eq(CourseEnrollment::getCourseId, courseId).eq(CourseEnrollment::getApprovalStatus, "PENDING"));

            long chapterCount = chapterService.count(new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, courseId));
            long publishedChapterCount = chapterService.count(new LambdaQueryWrapper<CourseChapter>()
                    .eq(CourseChapter::getCourseId, courseId).eq(CourseChapter::getIsPublished, Boolean.TRUE));
            long taskCount = taskService.count(new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId));
            long taskPointsBound = taskService.count(new LambdaQueryWrapper<Task>()
                    .eq(Task::getCourseId, courseId).isNotNull(Task::getChapterId));
            long publishedTaskCount = taskService.count(new LambdaQueryWrapper<Task>()
                    .eq(Task::getCourseId, courseId).eq(Task::getIsPublished, Boolean.TRUE));
            long examCount = examPaperService.count(new LambdaQueryWrapper<ExamPaper>().eq(ExamPaper::getCourseId, courseId));
            long questionCount = questionService.count(new LambdaQueryWrapper<Question>().eq(Question::getCourseId, courseId));

            List<CourseEnrollment> enrollments = enrollmentService.list(
                    new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));
            double avgProgress = enrollments.stream()
                    .mapToDouble(e -> e.getProgress() != null ? e.getProgress().doubleValue() : 0D).average().orElse(0D);
            long completedCount = enrollments.stream()
                    .filter(e -> e.getProgress() != null && e.getProgress().compareTo(BigDecimal.valueOf(100)) >= 0).count();
            double completionRate = totalStudents > 0 ? support.round2(completedCount * 100.0 / totalStudents) : 0;

            List<CourseEnrollment> scored = enrollments.stream().filter(e -> e.getFinalScore() != null).collect(Collectors.toList());
            double avgScore = scored.stream().mapToDouble(e -> e.getFinalScore().doubleValue()).average().orElse(0D);
            long passCount = scored.stream().filter(e -> e.getFinalScore().doubleValue() >= 60).count();
            long excellentCount = scored.stream().filter(e -> e.getFinalScore().doubleValue() >= 90).count();
            double passRate = scored.isEmpty() ? 0 : support.round2(passCount * 100.0 / scored.size());
            double excellentRate = scored.isEmpty() ? 0 : support.round2(excellentCount * 100.0 / scored.size());

            List<Long> taskIds = taskService.list(new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId))
                    .stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
            long pendingGrading = 0L;
            if (!taskIds.isEmpty()) {
                pendingGrading = submissionService.count(new LambdaQueryWrapper<Submission>()
                        .in(Submission::getTaskId, taskIds).eq(Submission::getStatus, SubmissionStatus.SUBMITTED));
            }

            List<Map<String, Object>> trend = new ArrayList<>();
            LocalDate today = LocalDate.now();
            for (int i = 13; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                LocalDateTime start = day.atStartOfDay();
                LocalDateTime end = day.plusDays(1).atStartOfDay();
                long active = learningRecordService.list(new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getCourseId, courseId)
                        .ge(LearningRecord::getCreatedAt, start).lt(LearningRecord::getCreatedAt, end))
                        .stream().map(LearningRecord::getUserId).filter(Objects::nonNull).distinct().count();
                long subs = 0L;
                if (!taskIds.isEmpty()) {
                    subs = submissionService.count(new LambdaQueryWrapper<Submission>()
                            .in(Submission::getTaskId, taskIds)
                            .ge(Submission::getCreatedAt, start).lt(Submission::getCreatedAt, end));
                }
                Map<String, Object> point = new LinkedHashMap<>(3);
                point.put("date", day.toString());
                point.put("activeStudents", active);
                point.put("submissions", subs);
                trend.add(point);
            }

            List<Map<String, Object>> atRisk = buildAtRisk(enrollments, null);
            List<Map<String, Object>> chapterTree = chapterComponent.getFullChapterTree(courseId);
            Map<String, Object> aiInsight = buildCourseAIInsight(course, totalStudents, avgProgress,
                    completionRate, avgScore, passRate, pendingGrading, atRisk);

            dashboard.put("courseId", courseId);
            dashboard.put("courseName", course.getName());
            dashboard.put("courseInfo", course);
            dashboard.put("overview", Map.of(
                    "totalStudents", totalStudents,
                    "activeStudents", activeStudents,
                    "pendingApproval", pendingApproval));
            dashboard.put("content", Map.of(
                    "chapterCount", chapterCount,
                    "publishedChapterCount", publishedChapterCount,
                    "taskCount", taskCount,
                    "taskPointsBoundToChapters", taskPointsBound,
                    "taskPointsTotal", taskCount,
                    "publishedTaskCount", publishedTaskCount,
                    "examCount", examCount,
                    "questionCount", questionCount));
            dashboard.put("learning", Map.of(
                    "avgProgress", support.round2(avgProgress),
                    "completionRate", completionRate,
                    "avgScore", support.round2(avgScore),
                    "passRate", passRate,
                    "excellentRate", excellentRate));
            dashboard.put("grading", Map.of("pendingGrading", pendingGrading));
            dashboard.put("trend", trend);
            dashboard.put("atRiskStudents", atRisk);
            dashboard.put("atRiskCount", atRisk.size());
            dashboard.put("chapterTree", chapterTree);
            dashboard.put("aiInsight", aiInsight);
        } catch (Exception e) {
            log.error("查询教师课程失败: {}", e.getMessage(), e);
            dashboard.put("error", "课程驾驶舱加载异常: " + e.getMessage());
        }
        return dashboard;
    }

    /** 由选课记录构建预警学生列表 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> computeAtRisk(List<CourseEnrollment> enrollments) {
        return buildAtRisk(enrollments, null);
    }

    /** 我的课程聚合指标 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMyCourseSummaries(Long teacherId) {
        List<Course> courses = courseService.list(
                new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, teacherId)
                        .orderByDesc(Course::getUpdatedAt));
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Course c : courses) {
            Long cid = c.getId();
            long studentCount = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, cid));
            List<CourseEnrollment> enrollments = enrollmentService.list(
                    new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, cid));
            double avgProgress = enrollments.stream()
                    .mapToDouble(e -> e.getProgress() != null ? e.getProgress().doubleValue() : 0D).average().orElse(0D);
            long completed = enrollments.stream()
                    .filter(e -> e.getProgress() != null && e.getProgress().compareTo(BigDecimal.valueOf(100)) >= 0).count();
            double completionRate = studentCount > 0 ? support.round2(completed * 100.0 / studentCount) : 0;

            List<Long> taskIds = taskService.list(new LambdaQueryWrapper<Task>().eq(Task::getCourseId, cid))
                    .stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
            long pendingGrading = 0L;
            if (!taskIds.isEmpty()) {
                pendingGrading = submissionService.count(new LambdaQueryWrapper<Submission>()
                        .in(Submission::getTaskId, taskIds).eq(Submission::getStatus, SubmissionStatus.SUBMITTED));
            }

            Map<String, Object> item = new LinkedHashMap<>(10);
            item.put("courseId", cid);
            item.put("name", c.getName());
            item.put("code", c.getCode());
            item.put("coverImage", c.getCoverImage());
            item.put("status", c.getStatus());
            item.put("category", c.getCategory());
            item.put("studentCount", studentCount);
            item.put("avgProgress", support.round2(avgProgress));
            item.put("completionRate", completionRate);
            item.put("pendingGrading", pendingGrading);
            summaries.add(item);
        }
        return summaries;
    }

    /** 增强版教师仪表盘（跨课程聚合 + AI 教学总览） */
    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherDashboardV2(Long teacherId) {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        try {
            List<Long> courseIds = support.courseIdsOfTeacher(teacherId);
            long courseCount = courseIds.size();

            long studentCount = 0L;
            if (!courseIds.isEmpty()) {
                studentCount = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>()
                        .in(CourseEnrollment::getCourseId, courseIds));
            }

            long pendingGrading = 0L;
            double avgScore = 0D;
            if (!courseIds.isEmpty()) {
                List<Long> taskIds = taskService.list(new LambdaQueryWrapper<Task>().in(Task::getCourseId, courseIds))
                        .stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
                if (!taskIds.isEmpty()) {
                    pendingGrading = submissionService.count(new LambdaQueryWrapper<Submission>()
                            .in(Submission::getTaskId, taskIds).eq(Submission::getStatus, SubmissionStatus.SUBMITTED));
                    avgScore = submissionService.list(new LambdaQueryWrapper<Submission>()
                            .in(Submission::getTaskId, taskIds).isNotNull(Submission::getScore))
                            .stream().mapToDouble(s -> s.getScore() != null ? s.getScore().doubleValue() : 0D)
                            .average().orElse(0D);
                }
            }

            List<Map<String, Object>> courseCards = getMyCourseSummaries(teacherId);

            Map<Long, String> courseNameMap = new HashMap<>();
            final List<CourseEnrollment> allEnrollments;
            if (!courseIds.isEmpty()) {
                List<Course> teacherCourses = courseService.list(new LambdaQueryWrapper<Course>().in(Course::getId, courseIds));
                for (Course c : teacherCourses) {
                    courseNameMap.put(c.getId(), c.getName());
                }
                allEnrollments = enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>().in(CourseEnrollment::getCourseId, courseIds));
            } else {
                allEnrollments = new ArrayList<>();
            }
            List<Map<String, Object>> atRisk = buildAtRisk(allEnrollments, null);
            Map<Long, String> cNameById = courseNameMap;
            List<Map<String, Object>> atRiskWithCourse = atRisk.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>(r);
                Long sid = (Long) r.get("studentId");
                String cname = allEnrollments.stream()
                        .filter(e -> e.getStudentId() != null && e.getStudentId().equals(sid))
                        .map(e -> cNameById.get(e.getCourseId()))
                        .filter(Objects::nonNull).findFirst().orElse("");
                m.put("courseName", cname);
                return m;
            }).limit(10).collect(Collectors.toList());

            long publishedTasks = 0L;
            long totalSubmissions = 0L;
            if (!courseIds.isEmpty()) {
                List<Long> taskIds = taskService.list(new LambdaQueryWrapper<Task>()
                        .in(Task::getCourseId, courseIds).eq(Task::getIsPublished, Boolean.TRUE))
                        .stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
                publishedTasks = taskIds.size();
                if (!taskIds.isEmpty()) {
                    totalSubmissions = submissionService.count(new LambdaQueryWrapper<Submission>().in(Submission::getTaskId, taskIds));
                }
            }
            long expectedSubmissions = publishedTasks * studentCount;
            double submissionRate = expectedSubmissions > 0 ? support.round2(totalSubmissions * 100.0 / expectedSubmissions) : 0;

            double platformAvgScore = submissionService.list(new LambdaQueryWrapper<Submission>().isNotNull(Submission::getScore))
                    .stream().mapToDouble(s -> s.getScore() != null ? s.getScore().doubleValue() : 0D).average().orElse(0D);
            double scoreGap = support.round2(avgScore - platformAvgScore);

            // B-13 ⑯：AI 派生指标不再进首页同步聚合。原先这里串了一次 LLM 调用
            // （buildTeacherAIOverview → aiService.generateLearningAdviceJson），
            // 模型慢/不可用时整个 /teacher/dashboard/v2 被拖住，任何首页消费方只能绕行。
            // 现在聚合只回延迟标记，AI 总览改由 GET /teacher/dashboard/ai-overview 按需取。
            Map<String, Object> aiOverview = deferredAiOverview();

            dashboard.put("courseCount", courseCount);
            dashboard.put("studentCount", studentCount);
            dashboard.put("pendingGrading", pendingGrading);
            dashboard.put("avgScore", support.round2(avgScore));
            dashboard.put("courseCards", courseCards);
            dashboard.put("atRiskStudents", atRiskWithCourse);
            dashboard.put("atRiskCount", atRiskWithCourse.size());
            dashboard.put("taskPointOverview", Map.of(
                    "publishedTasks", publishedTasks,
                    "totalSubmissions", totalSubmissions,
                    "expectedSubmissions", expectedSubmissions,
                    "submissionRate", submissionRate));
            dashboard.put("platformComparison", Map.of(
                    "platformAvgScore", support.round2(platformAvgScore),
                    "myAvgScore", support.round2(avgScore),
                    "scoreGap", scoreGap,
                    "trend", scoreGap >= 0 ? "ABOVE" : "BELOW"));
            dashboard.put("aiOverview", aiOverview);
        } catch (Exception e) {
            log.error("查询教师课程失败: {}", e.getMessage(), e);
            dashboard.put("error", "教师仪表盘加载异常: " + e.getMessage());
            dashboard.putIfAbsent("courseCount", 0L);
            dashboard.putIfAbsent("studentCount", 0L);
            dashboard.putIfAbsent("pendingGrading", 0L);
            dashboard.putIfAbsent("avgScore", 0D);
            dashboard.putIfAbsent("courseCards", new ArrayList<>());
            dashboard.putIfAbsent("atRiskStudents", new ArrayList<>());
            dashboard.putIfAbsent("aiOverview", deferredAiOverview());
        }
        return dashboard;
    }

    /**
     * 教师 AI 教学总览（B-13 ⑯：从首页同步聚合中摘出后的独立入口）。
     * <p>复用 {@link #getTeacherDashboardV2} 的统计口径（不再重算一套，避免两处统计分叉），
     * 只在拿到数字后补上这一次模型调用；调用方应容忍它比聚合慢或被降级。</p>
     */
    public Map<String, Object> getTeacherAIOverview(Long teacherId) {
        Map<String, Object> dashboard = getTeacherDashboardV2(teacherId);
        List<Map<String, Object>> atRisk = asMapList(dashboard.get("atRiskStudents"));
        return buildTeacherAIOverview(
                asLong(dashboard.get("courseCount")),
                asLong(dashboard.get("studentCount")),
                asDouble(dashboard.get("avgScore")),
                asLong(dashboard.get("pendingGrading")),
                atRisk,
                asDouble(taskOverviewValue(dashboard.get("taskPointOverview"), "submissionRate")));
    }

    /** 聚合里代表「AI 总览稍后单独取」的占位：显式可判，不伪装成空内容。 */
    private Map<String, Object> deferredAiOverview() {
        Map<String, Object> m = new LinkedHashMap<>(4);
        m.put("deferred", true);
        m.put("endpoint", AI_OVERVIEW_ENDPOINT);
        m.put("reason", "AI 总览依赖模型调用，已从首页同步聚合中摘出，按需单独获取");
        return m;
    }

    private Object taskOverviewValue(Object taskPointOverview, String key) {
        if (taskPointOverview instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    private long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private double asDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0D;
    }

    private List<Map<String, Object>> asMapList(Object v) {
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Map<?, ?> map) {
                Map<String, Object> item = new LinkedHashMap<>();
                map.forEach((k, val) -> item.put(String.valueOf(k), val));
                result.add(item);
            }
        }
        return result;
    }

    private List<Map<String, Object>> buildAtRisk(List<CourseEnrollment> enrollments, String courseName) {
        List<CourseEnrollment> risk = enrollments.stream()
                .filter(e -> {
                    boolean lowProgress = e.getProgress() != null && e.getProgress().doubleValue() < 40;
                    boolean lowScore = e.getFinalScore() != null && e.getFinalScore().doubleValue() < 60;
                    return lowProgress || lowScore;
                })
                .collect(Collectors.toList());
        risk.sort((a, b) -> severity(b) - severity(a));

        Map<Long, String> names = support.userNameMap(risk.stream().map(CourseEnrollment::getStudentId).collect(Collectors.toSet()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseEnrollment e : risk) {
            Map<String, Object> item = new LinkedHashMap<>(8);
            item.put("studentId", e.getStudentId());
            item.put("studentName", names.getOrDefault(e.getStudentId(), String.valueOf(e.getStudentId())));
            item.put("progress", e.getProgress() != null ? support.round2(e.getProgress().doubleValue()) : 0);
            item.put("finalScore", e.getFinalScore() != null ? support.round2(e.getFinalScore().doubleValue()) : null);
            item.put("level", severity(e) >= 2 ? "HIGH" : "MEDIUM");
            if (courseName != null) {
                item.put("courseName", courseName);
            }
            result.add(item);
        }
        return result;
    }

    private int severity(CourseEnrollment e) {
        boolean lowProgress = e.getProgress() != null && e.getProgress().doubleValue() < 40;
        boolean lowScore = e.getFinalScore() != null && e.getFinalScore().doubleValue() < 60;
        return (lowProgress ? 1 : 0) + (lowScore ? 1 : 0);
    }

    private Map<String, Object> buildCourseAIInsight(Course course, long totalStudents, double avgProgress,
                                                     double completionRate, double avgScore, double passRate,
                                                     long pendingGrading, List<Map<String, Object>> atRisk) {
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("courseName", course.getName());
            stats.put("studentCount", totalStudents);
            stats.put("avgProgress", avgProgress);
            stats.put("completionRate", completionRate);
            stats.put("avgScore", avgScore);
            stats.put("passRate", passRate);
            stats.put("pendingGrading", pendingGrading);
            stats.put("atRiskCount", atRisk.size());
            String statsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(stats);

            List<Map<String, String>> weakPoints = atRisk.stream().limit(8).map(r -> {
                Map<String, String> w = new LinkedHashMap<>();
                w.put("description", "学生「" + r.get("studentName") + "」进度" + r.get("progress") + "%、成绩" + r.get("finalScore") + "，需重点关注");
                w.put("severity", String.valueOf(r.get("level")));
                return w;
            }).collect(Collectors.toList());
            String weakJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("weakPoints", weakPoints));

            String advice = aiService.generateLearningAdviceJson(statsJson, weakJson);
            Map<String, Object> parsed = support.tryParseJson(advice);
            if (parsed != null) {
                return parsed;
            }
            Map<String, Object> fallback = new LinkedHashMap<>(2);
            fallback.put("summary", advice);
            return fallback;
        } catch (Exception e) {
            log.error("计算教师课程失败: {}", e.getMessage(), e);
            Map<String, Object> fallback = new LinkedHashMap<>(2);
            fallback.put("summary", "暂无可用的 AI 学情解读（模型未配置或调用失败）。");
            return fallback;
        }
    }

    private Map<String, Object> buildTeacherAIOverview(long courseCount, long studentCount, double avgScore,
                                                       long pendingGrading, List<Map<String, Object>> atRisk,
                                                       double submissionRate) {
        try {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("courseCount", courseCount);
            stats.put("studentCount", studentCount);
            stats.put("avgScore", avgScore);
            stats.put("pendingGrading", pendingGrading);
            stats.put("submissionRate", submissionRate);
            stats.put("atRiskCount", atRisk.size());
            String statsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(stats);

            List<Map<String, String>> weakPoints = atRisk.stream().limit(8).map(r -> {
                Map<String, String> w = new LinkedHashMap<>();
                w.put("description", "课程「" + r.get("courseName") + "」学生「" + r.get("studentName") +
                        "」进度" + r.get("progress") + "%、成绩" + r.get("finalScore"));
                w.put("severity", String.valueOf(r.get("level")));
                return w;
            }).collect(Collectors.toList());
            String weakJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("weakPoints", weakPoints));

            String advice = aiService.generateLearningAdviceJson(statsJson, weakJson);
            Map<String, Object> parsed = support.tryParseJson(advice);
            if (parsed != null) {
                return parsed;
            }
            Map<String, Object> fallback = new LinkedHashMap<>(2);
            fallback.put("summary", advice);
            return fallback;
        } catch (Exception e) {
            log.error("查询教师课程失败: {}", e.getMessage(), e);
            Map<String, Object> fallback = new LinkedHashMap<>(2);
            fallback.put("summary", "暂无可用的 AI 教学总览（模型未配置或调用失败）。");
            return fallback;
        }
    }
}
