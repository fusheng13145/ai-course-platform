package com.aicourse.service.component.student;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.entity.SignIn;
import com.aicourse.entity.SignInRecord;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.constant.SubmissionStatus;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.ExamPaperService;
import com.aicourse.service.ExamSessionService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.SignInService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生驾驶舱/成绩协作者：学习空间驾驶舱（课程/待办/消息/学情聚合）与成绩总览（作业/考试明细）。
 * <p>由 {@code StudentLearningServiceImpl} 门面委托调用。</p>
 */
@Component
public class StudentDashboardComponent {

    private static final Logger log = LoggerFactory.getLogger(StudentDashboardComponent.class);

    private final CourseEnrollmentService enrollmentService;
    private final CourseService courseService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final ExamPaperService examPaperService;
    private final ExamSessionService examSessionService;
    private final SignInService signInService;
    private final NotificationService notificationService;
    private final LearningRecordService learningRecordService;
    private final AnalyticsService analyticsService;

    public StudentDashboardComponent(CourseEnrollmentService enrollmentService,
                                     CourseService courseService,
                                     TaskService taskService,
                                     SubmissionService submissionService,
                                     ExamPaperService examPaperService,
                                     ExamSessionService examSessionService,
                                     SignInService signInService,
                                     NotificationService notificationService,
                                     LearningRecordService learningRecordService,
                                     AnalyticsService analyticsService) {
        this.enrollmentService = enrollmentService;
        this.courseService = courseService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.examPaperService = examPaperService;
        this.examSessionService = examSessionService;
        this.signInService = signInService;
        this.notificationService = notificationService;
        this.learningRecordService = learningRecordService;
        this.analyticsService = analyticsService;
    }

    /** 学生首页/学习空间驾驶舱 */
    @Transactional(readOnly = true)
    public Map<String, Object> getStudentDashboard(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1) 我的课程（含进度）
        List<Map<String, Object>> courseList = new ArrayList<>();
        Set<Long> courseIds = new HashSet<>();
        try {
            List<CourseEnrollment> enrollments = enrollmentService.list(
                    new LambdaQueryWrapper<CourseEnrollment>()
                            .eq(CourseEnrollment::getStudentId, userId)
                            .eq(CourseEnrollment::getIsActive, true));
            // 批量预取课程信息，消除 N+1
            Set<Long> cids = enrollments.stream().map(CourseEnrollment::getCourseId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, Course> courseMap = cids.isEmpty() ? Collections.emptyMap()
                    : courseService.listByIds(cids).stream()
                    .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));
            for (CourseEnrollment en : enrollments) {
                if (en.getCourseId() == null) {
                    continue;
                }
                courseIds.add(en.getCourseId());
                Course c = courseMap.get(en.getCourseId());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("courseId", en.getCourseId());
                m.put("courseName", c != null ? c.getName() : null);
                m.put("cover", c != null ? c.getCoverImage() : null);
                m.put("progress", en.getProgress());
                m.put("finalScore", en.getFinalScore());
                courseList.add(m);
            }
        } catch (Exception e) {
            log.warn("dashboard 课程聚合失败: {}", e.getMessage());
        }
        result.put("courses", courseList);
        result.put("courseCount", courseList.size());

        // 2) 待办：作业 / 考试 / 签到
        Map<String, Object> todos = new LinkedHashMap<>();
        List<Map<String, Object>> pendingAssignments = new ArrayList<>();
        List<Map<String, Object>> upcomingExams = new ArrayList<>();
        List<Map<String, Object>> pendingSignIns = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        try {
            if (!courseIds.isEmpty()) {
                // 待完成作业（已发布且未提交）
                List<Task> tasks = taskService.list(new LambdaQueryWrapper<Task>()
                        .in(Task::getCourseId, courseIds)
                        .eq(Task::getTaskType, "ASSIGNMENT")
                        .eq(Task::getIsPublished, true));
                Set<Long> submitted = submissionService.list(new LambdaQueryWrapper<Submission>()
                                .eq(Submission::getStudentId, userId)
                                .in(Submission::getStatus, SubmissionStatus.SUBMITTED, SubmissionStatus.GRADED, SubmissionStatus.RETURNED))
                        .stream().map(Submission::getTaskId).collect(Collectors.toSet());
                for (Task t : tasks) {
                    if (submitted.contains(t.getId())) {
                        continue;
                    }
                    if (t.getDueDate() != null && t.getDueDate().isBefore(now)) {
                        continue;
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("taskId", t.getId());
                    m.put("title", t.getTitle());
                    m.put("courseId", t.getCourseId());
                    m.put("dueDate", t.getDueDate() != null ? t.getDueDate().toString() : null);
                    pendingAssignments.add(m);
                }

                // 即将开始的考试（进行时间窗内且未考）
                List<ExamPaper> papers = examPaperService.list(new LambdaQueryWrapper<ExamPaper>()
                        .in(ExamPaper::getCourseId, courseIds)
                        .in(ExamPaper::getStatus, "PUBLISHED", "OPEN"));
                // 修复 N+1：循环外一次批量查询该学生全部考试会话
                Set<Long> takenExamPaperIds = examSessionService.list(new LambdaQueryWrapper<ExamSession>()
                                .eq(ExamSession::getStudentId, userId)
                                .select(ExamSession::getExamPaperId))
                        .stream()
                        .map(ExamSession::getExamPaperId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                for (ExamPaper p : papers) {
                    if (p.getStartTime() == null || p.getStartTime().isAfter(now)) {
                        continue;
                    }
                    if (p.getEndTime() != null && p.getEndTime().isBefore(now)) {
                        continue;
                    }
                    if (takenExamPaperIds.contains(p.getId())) {
                        continue;
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("examPaperId", p.getId());
                    m.put("title", p.getTitle());
                    m.put("courseId", p.getCourseId());
                    m.put("startTime", p.getStartTime() != null ? p.getStartTime().toString() : null);
                    m.put("endTime", p.getEndTime() != null ? p.getEndTime().toString() : null);
                    m.put("duration", p.getDuration());
                    upcomingExams.add(m);
                }

                // 进行中的签到（本人尚未签）
                List<SignIn> signs = signInService.list(new LambdaQueryWrapper<SignIn>()
                        .in(SignIn::getCourseId, courseIds)
                        .eq(SignIn::getStatus, "ONGOING"));
                // 修复 N+1：循环外一次批量查询该学生全部签到记录
                Set<Long> signedSignInIds = signInService.listMyRecords(userId).stream()
                        .map(SignInRecord::getSignInId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                for (SignIn s : signs) {
                    if (signedSignInIds.contains(s.getId())) {
                        continue;
                    }
                    try {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("signInId", s.getId());
                        m.put("title", s.getTitle());
                        m.put("type", s.getType());
                        m.put("courseId", s.getCourseId());
                        m.put("closedAt", s.getClosedAt() != null ? s.getClosedAt().toString() : null);
                        pendingSignIns.add(m);
                    } catch (Exception ignore) {
                        log.warn("单条签到异常: signInId={}", s.getId(), ignore);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("dashboard 待办聚合失败: {}", e.getMessage());
        }
        todos.put("assignments", pendingAssignments);
        todos.put("exams", upcomingExams);
        todos.put("signIns", pendingSignIns);
        result.put("todos", todos);

        // 3) 消息
        try {
            result.put("unreadCount", notificationService.getUnreadCount(userId));
            result.put("unreadByCategory", notificationService.getUnreadGroupedByCategory(userId));
        } catch (Exception e) {
            log.error("查询学生学习失败: {}", e.getMessage(), e);
            result.put("unreadCount", 0L);
            result.put("unreadByCategory", Collections.emptyMap());
        }

        // 4) 学情 / 统计（轻量，失败兜底）
        try {
            result.put("learningStats", learningRecordService.getLearningStats(userId));
        } catch (Exception ignore) {
            log.warn("获取学习统计失败: userId={}", userId, ignore);
            result.put("learningStats", Collections.emptyMap());
        }
        try {
            result.put("analytics", analyticsService.getUserDashboard(userId));
        } catch (Exception ignore) {
            log.warn("获取学情分析失败: userId={}", userId, ignore);
            result.put("analytics", Collections.emptyMap());
        }

        // 5) 概览数字
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("courseCount", courseList.size());
        summary.put("pendingAssignmentCount", pendingAssignments.size());
        summary.put("upcomingExamCount", upcomingExams.size());
        summary.put("pendingSignInCount", pendingSignIns.size());
        summary.put("unreadCount", result.get("unreadCount"));
        result.put("summary", summary);

        return result;
    }

    /** 学生成绩总览（按课程聚合作业/考试明细） */
    @Transactional(readOnly = true)
    public Map<String, Object> getGrades(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> courses = new ArrayList<>();
        try {
            List<CourseEnrollment> enrollments = enrollmentService.list(
                    new LambdaQueryWrapper<CourseEnrollment>()
                            .eq(CourseEnrollment::getStudentId, userId)
                            .eq(CourseEnrollment::getIsActive, true));
            Set<Long> courseIds = enrollments.stream().map(CourseEnrollment::getCourseId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, Course> courseMap = courseIds.isEmpty() ? Collections.emptyMap()
                    : courseService.listByIds(courseIds).stream().collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));
            Map<Long, CourseEnrollment> enrollMap = enrollments.stream()
                    .collect(Collectors.toMap(CourseEnrollment::getCourseId, e -> e, (a, b) -> a));

            List<Submission> subs = submissionService.list(
                    new LambdaQueryWrapper<Submission>().eq(Submission::getStudentId, userId));
            Set<Long> taskIds = subs.stream().map(Submission::getTaskId).collect(Collectors.toSet());
            Map<Long, Task> taskMap = taskIds.isEmpty() ? Collections.emptyMap()
                    : taskService.listByIds(taskIds).stream().collect(Collectors.toMap(Task::getId, t -> t, (a, b) -> a));

            List<ExamSession> sessions = examSessionService.list(
                    new LambdaQueryWrapper<ExamSession>().eq(ExamSession::getStudentId, userId));
            Set<Long> paperIds = sessions.stream().map(ExamSession::getExamPaperId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, ExamPaper> paperMap = paperIds.isEmpty() ? Collections.emptyMap()
                    : examPaperService.listByIds(paperIds).stream().collect(Collectors.toMap(ExamPaper::getId, p -> p, (a, b) -> a));

            for (Long cid : courseIds) {
                Course c = courseMap.get(cid);
                if (c == null) {
                    continue;
                }
                List<Map<String, Object>> assignments = new ArrayList<>();
                for (Submission sub : subs) {
                    Task t = taskMap.get(sub.getTaskId());
                    if (t != null && cid.equals(t.getCourseId())) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("submissionId", sub.getId());
                        m.put("taskId", sub.getTaskId());
                        m.put("title", t.getTitle());
                        m.put("score", sub.getScore());
                        m.put("maxScore", t.getMaxScore());
                        m.put("status", sub.getStatus());
                        m.put("submittedAt", sub.getSubmittedAt() != null ? sub.getSubmittedAt().toString() : null);
                        assignments.add(m);
                    }
                }
                List<Map<String, Object>> exams = new ArrayList<>();
                for (ExamSession s : sessions) {
                    ExamPaper p = paperMap.get(s.getExamPaperId());
                    if (p != null && cid.equals(p.getCourseId())) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("sessionId", s.getId());
                        m.put("title", p.getTitle());
                        m.put("score", s.getPercentage());
                        m.put("totalScore", p.getTotalScore());
                        m.put("status", s.getStatus());
                        m.put("submittedAt", s.getSubmitTime() != null ? s.getSubmitTime().toString() : null);
                        exams.add(m);
                    }
                }
                CourseEnrollment en = enrollMap.get(cid);
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("courseId", cid);
                cm.put("courseName", c.getName());
                cm.put("finalScore", en != null ? en.getFinalScore() : null);
                cm.put("progress", en != null ? en.getProgress() : null);
                cm.put("assignments", assignments);
                cm.put("exams", exams);
                courses.add(cm);
            }
        } catch (Exception e) {
            log.warn("getGrades 失败: {}", e.getMessage());
        }
        result.put("courses", courses);
        return result;
    }
}
