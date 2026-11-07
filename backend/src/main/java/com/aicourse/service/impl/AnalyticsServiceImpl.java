package com.aicourse.service.impl;
import com.aicourse.service.component.analytics.AdminAnalyticsComponent;
import com.aicourse.service.component.analytics.CourseAnalyticsComponent;
import com.aicourse.service.component.analytics.LearnerAnalyticsComponent;
import com.aicourse.service.component.analytics.TeachingAnalyticsComponent;


import com.aicourse.constant.SubmissionStatus;

import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.aicourse.vo.CourseRiskPredictionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据统计分析服务实现类（门面）
 * <p>统计职责已按视角拆分至四个协作者组件，本类仅做委派与课程级学情的兜底承载：
 * <ul>
 *   <li>{@link AdminAnalyticsComponent}：平台/管理员视角（看板、总览、平台热力图、用户增长）</li>
 *   <li>{@link CourseAnalyticsComponent}：课程级统计（成绩分布、课程分析、排行、考试统计）</li>
 *   <li>{@link LearnerAnalyticsComponent}：学习者视角（个人看板、学习趋势、热力图）</li>
 *   <li>{@link TeachingAnalyticsComponent}：教学运行视角（教师看板、学情报告导出、掉队预警）</li>
 * </ul>
 * 课程级学生学情（{@link #getStudentCourseProgressList} / {@link #getStudentCourseDetail}）
 * 暂无组件承接，保留在本门面内。</p>
 */
@Service
@Slf4j
public class AnalyticsServiceImpl implements AnalyticsService {


    // ---- 组件委派（Analytics 组件化已落地：Admin/Course/Learner/Teaching）----
    private final AdminAnalyticsComponent adminAnalyticsComponent;
    private final CourseAnalyticsComponent courseAnalyticsComponent;
    private final LearnerAnalyticsComponent learnerAnalyticsComponent;
    private final TeachingAnalyticsComponent teachingAnalyticsComponent;
    private final LearningRiskService learningRiskService;

    // ---- 课程级学生学情（组件未承接，保留在门面）----
    private final UserService userService;
    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final com.aicourse.mapper.TaskCompletionMapper taskCompletionMapper;
    private final SubmissionService submissionService;
    private final ExamSessionService examSessionService;
    private final ExamPaperService examPaperService;
    private final CourseChapterService courseChapterService;

    public AnalyticsServiceImpl(
            AdminAnalyticsComponent adminAnalyticsComponent,
            CourseAnalyticsComponent courseAnalyticsComponent,
            LearnerAnalyticsComponent learnerAnalyticsComponent,
            TeachingAnalyticsComponent teachingAnalyticsComponent,
            LearningRiskService learningRiskService,
            UserService userService,
            CourseEnrollmentService enrollmentService,
            TaskService taskService,
            com.aicourse.mapper.TaskCompletionMapper taskCompletionMapper,
            SubmissionService submissionService,
            ExamSessionService examSessionService,
            ExamPaperService examPaperService,
            CourseChapterService courseChapterService
    ) {
        this.adminAnalyticsComponent = adminAnalyticsComponent;
        this.courseAnalyticsComponent = courseAnalyticsComponent;
        this.learnerAnalyticsComponent = learnerAnalyticsComponent;
        this.teachingAnalyticsComponent = teachingAnalyticsComponent;
        this.learningRiskService = learningRiskService;
        this.userService = userService;
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.taskCompletionMapper = taskCompletionMapper;
        this.submissionService = submissionService;
        this.examSessionService = examSessionService;
        this.examPaperService = examPaperService;
        this.courseChapterService = courseChapterService;
    }

    // ===================== 平台/管理员视角（委托 AdminAnalyticsComponent） =====================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard() {
        return adminAnalyticsComponent.getDashboard();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard(Long collegeId) {
        return adminAnalyticsComponent.getDashboard(collegeId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPlatformOverview() {
        return adminAnalyticsComponent.getPlatformOverview();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getPlatformOverview(Long collegeId) {
        return adminAnalyticsComponent.getPlatformOverview(collegeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPlatformHeatmap(int days) {
        return adminAnalyticsComponent.getPlatformHeatmap(days);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPlatformHeatmap(int days, Long collegeId) {
        return adminAnalyticsComponent.getPlatformHeatmap(days, collegeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserGrowthTrend(int days) {
        return adminAnalyticsComponent.getUserGrowthTrend(days);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserGrowthTrend(int days, Long collegeId) {
        return adminAnalyticsComponent.getUserGrowthTrend(days, collegeId);
    }

    // ===================== 课程级统计（委托 CourseAnalyticsComponent） =====================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getGradeDistribution(Long courseId) {
        return courseAnalyticsComponent.getGradeDistribution(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getGradeDistributionHistogram(Long courseId) {
        return courseAnalyticsComponent.getGradeDistributionHistogram(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCourseAnalysis(Long courseId) {
        return courseAnalyticsComponent.getCourseAnalysis(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCourseRanking() {
        return courseAnalyticsComponent.getCourseRanking();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTeachingSummary() {
        return teachingAnalyticsComponent.getTeachingSummary();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCourseRanking(Long collegeId) {
        return courseAnalyticsComponent.getCourseRanking(collegeId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getExamStatistics(Long examPaperId) {
        return courseAnalyticsComponent.getExamStatistics(examPaperId);
    }

    // ===================== 学习者视角（委托 LearnerAnalyticsComponent） =====================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getUserDashboard(Long userId) {
        return learnerAnalyticsComponent.getUserDashboard(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLearningTrends(Long userId) {
        return learnerAnalyticsComponent.getLearningTrends(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLearningHeatmap(Long userId, LocalDate startDate, LocalDate endDate) {
        return learnerAnalyticsComponent.getLearningHeatmap(userId, startDate, endDate);
    }

    // ===================== 教学运行视角（委托 TeachingAnalyticsComponent） =====================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherDashboard(Long teacherId) {
        return teachingAnalyticsComponent.getTeacherDashboard(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAtRiskStudents(String groupBy, Long courseId, String department, String major, Long classId) {
        // 委托给 TeachingAnalyticsComponent 的完整实现（进度<40% 或 成绩<60 判定 + 多维下钻聚合），
        // 异常直接向上传播，由全局异常处理器统一响应，不再吞异常后返回 200 + error 字段。
        return teachingAnalyticsComponent.getAtRiskStudents(groupBy, courseId, department, major, classId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAtRiskStudents(String groupBy, Long courseId, String department, String major,
                                                 Long classId, Long collegeId) {
        return teachingAnalyticsComponent.getAtRiskStudents(
                groupBy, courseId, department, major, classId, collegeId);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseRiskPredictionVO predictCourseRisk(Long courseId) {
        return learningRiskService.predictCourseRisk(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportReport(Long courseId) {
        // 委托 TeachingAnalyticsComponent 增强版导出（基于课程分析 + 成绩分布/作业提交率/学习进度/学生排名），
        // 前端对导出响应仅 JSON 原样落盘，字段超集不破坏消费。
        return teachingAnalyticsComponent.exportReport(courseId);
    }

    // ===================== 课程级学生学情（保留在门面，暂无组件承接） =====================

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getStudentCourseProgressList(Long courseId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<CourseEnrollment> enrollments = enrollmentService.list(
                    new LambdaQueryWrapper<CourseEnrollment>()
                            .eq(CourseEnrollment::getCourseId, courseId)
                            .eq(CourseEnrollment::getIsActive, true));
            if (enrollments.isEmpty()) return result;

            Set<Long> studentIds = enrollments.stream().map(CourseEnrollment::getStudentId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, User> userMap = studentIds.isEmpty() ? Collections.emptyMap()
                    : userService.listByIds(studentIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
            Map<Long, CourseEnrollment> enrollMap = enrollments.stream()
                    .filter(e -> e.getStudentId() != null)
                    .collect(Collectors.toMap(CourseEnrollment::getStudentId, e -> e, (a, b) -> a));

            // 课程任务点（视频/文档/测验/作业等全部任务）
            List<Task> courseTasks = taskService.list(
                    new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId));
            long totalTasks = courseTasks.size();
            Set<Long> taskIds = courseTasks.stream().map(Task::getId).collect(Collectors.toSet());

            // 批量拉取全量数据，内存聚合，避免按学生循环查库（N+1）
            List<com.aicourse.entity.TaskCompletion> completions = taskIds.isEmpty() ? Collections.emptyList()
                    : taskCompletionMapper.selectList(new LambdaQueryWrapper<com.aicourse.entity.TaskCompletion>()
                            .in(com.aicourse.entity.TaskCompletion::getTaskId, taskIds)
                            .eq(com.aicourse.entity.TaskCompletion::getCompleted, true));
            List<Submission> submissions = taskIds.isEmpty() || studentIds.isEmpty() ? Collections.emptyList()
                    : submissionService.list(new LambdaQueryWrapper<Submission>()
                            .in(Submission::getTaskId, taskIds)
                            .in(Submission::getStudentId, studentIds));
            List<ExamSession> sessions = Collections.emptyList();
            if (!studentIds.isEmpty()) {
                try {
                    List<ExamPaper> courseExamPapers = examPaperService.list(
                            new LambdaQueryWrapper<ExamPaper>().eq(ExamPaper::getCourseId, courseId));
                    Set<Long> courseExamPaperIds = courseExamPapers == null ? Collections.emptySet()
                            : courseExamPapers.stream()
                                    .map(ExamPaper::getId)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet());
                    if (!courseExamPaperIds.isEmpty()) {
                        List<ExamSession> queriedSessions = examSessionService.list(new LambdaQueryWrapper<ExamSession>()
                                .in(ExamSession::getStudentId, studentIds)
                                .in(ExamSession::getExamPaperId, courseExamPaperIds)
                                .orderByDesc(ExamSession::getCreatedAt));
                        sessions = queriedSessions == null ? Collections.emptyList() : queriedSessions;
                    }
                } catch (RuntimeException examError) {
                    // 课程考试统计故障不应连带丢掉仍可用的学生、作业与进度数据。
                    log.warn("课程考试均分暂不可用: courseId={}, {}", courseId, examError.getMessage());
                }
            }

            for (Long sid : studentIds) {
                Map<String, Object> item = new LinkedHashMap<>();
                User u = userMap.get(sid);
                CourseEnrollment en = enrollMap.get(sid);

                // 完成任务点 = 完成的 task_completions ∪ 已提交/已批改作业
                Set<Long> done = completions.stream()
                        .filter(c -> sid.equals(c.getStudentId()))
                        .map(com.aicourse.entity.TaskCompletion::getTaskId)
                        .collect(Collectors.toSet());
                submissions.stream()
                        .filter(s -> sid.equals(s.getStudentId())
                                && isSubmittedStatus(s.getStatus()))
                        .map(Submission::getTaskId)
                        .forEach(done::add);

                // 作业均分（仅统计已提交/已批改且批改有分的提交）
                List<Submission> myScored = submissions.stream()
                        .filter(s -> sid.equals(s.getStudentId())
                                && isSubmittedStatus(s.getStatus())
                                && s.getScore() != null)
                        .collect(Collectors.toList());
                Double avgAssignment = myScored.isEmpty() ? null
                        : Math.round(myScored.stream()
                                .mapToDouble(s -> s.getScore().doubleValue()).average().orElse(0D) * 100.0) / 100.0;

                // 考试均分（percentage 为百分制成绩）
                List<ExamSession> mySessions = sessions.stream()
                        .filter(s -> sid.equals(s.getStudentId()) && s.getPercentage() != null)
                        .collect(Collectors.toList());
                Double avgExam = mySessions.isEmpty() ? null
                        : Math.round(mySessions.stream()
                                .mapToDouble(s -> s.getPercentage().doubleValue()).average().orElse(0D) * 100.0) / 100.0;

                // 最后活跃时间（完成记录/提交/考试会话的最大时间）
                LocalDateTime lastActive = null;
                for (com.aicourse.entity.TaskCompletion c : completions) {
                    if (!sid.equals(c.getStudentId())) continue;
                    LocalDateTime t = c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getCreatedAt();
                    if (t != null && (lastActive == null || t.isAfter(lastActive))) lastActive = t;
                }
                for (Submission s : submissions) {
                    if (!sid.equals(s.getStudentId())) continue;
                    LocalDateTime t = s.getUpdatedAt() != null ? s.getUpdatedAt() : s.getSubmittedAt();
                    if (t != null && (lastActive == null || t.isAfter(lastActive))) lastActive = t;
                }
                for (ExamSession s : mySessions) {
                    LocalDateTime t = s.getUpdatedAt() != null ? s.getUpdatedAt() : s.getCreatedAt();
                    if (t != null && (lastActive == null || t.isAfter(lastActive))) lastActive = t;
                }

                item.put("studentId", sid);
                item.put("studentName", u != null ? u.getFullName() : null);
                item.put("studentNo", u != null ? u.getStudentId() : null);
                item.put("avatarUrl", u != null ? u.getAvatarUrl() : null);
                item.put("progress", en != null ? en.getProgress() : null);
                item.put("totalTasks", totalTasks);
                item.put("completedTasks", done.size());
                item.put("avgAssignmentScore", avgAssignment);
                item.put("avgExamScore", avgExam);
                item.put("lastActiveAt", lastActive != null ? lastActive.toString() : null);
                result.add(item);
            }
            // 按学习进度降序，进度相同时按完成任务数降序
            result.sort((a, b) -> {
                int cmp = Double.compare(toDouble(b.get("progress")), toDouble(a.get("progress")));
                if (cmp != 0) return cmp;
                return Long.compare(toLong(b.get("completedTasks")), toLong(a.get("completedTasks")));
            });
        } catch (Exception e) {
            log.warn("getStudentCourseProgressList 失败: courseId={}, {}", courseId, e.getMessage());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getStudentCourseDetail(Long courseId, Long studentId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            User student = userService.getById(studentId);
            result.put("studentId", studentId);
            result.put("studentName", student != null ? student.getFullName() : null);
            result.put("studentNo", student != null ? student.getStudentId() : null);

            CourseEnrollment en = enrollmentService.getOne(new LambdaQueryWrapper<CourseEnrollment>()
                    .eq(CourseEnrollment::getCourseId, courseId)
                    .eq(CourseEnrollment::getStudentId, studentId));
            result.put("progress", en != null ? en.getProgress() : null);
            result.put("finalScore", en != null ? en.getFinalScore() : null);

            // ---- 1) 章节任务点完成明细（按章节分组） ----
            List<CourseChapter> chapters = courseChapterService.list(
                    new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, courseId));
            chapters.sort(Comparator.comparingInt((CourseChapter c) ->
                            c.getOrderIndex() == null ? 0 : c.getOrderIndex())
                    .thenComparingLong(CourseChapter::getId));
            List<Task> tasks = taskService.list(
                    new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId));
            Set<Long> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());

            Set<Long> done = taskIds.isEmpty() ? Collections.emptySet()
                    : taskCompletionMapper.selectList(new LambdaQueryWrapper<com.aicourse.entity.TaskCompletion>()
                            .in(com.aicourse.entity.TaskCompletion::getTaskId, taskIds)
                            .eq(com.aicourse.entity.TaskCompletion::getStudentId, studentId)
                            .eq(com.aicourse.entity.TaskCompletion::getCompleted, true))
                    .stream().map(com.aicourse.entity.TaskCompletion::getTaskId).collect(Collectors.toSet());
            List<Submission> mySubmissions = taskIds.isEmpty() ? Collections.emptyList()
                    : submissionService.list(new LambdaQueryWrapper<Submission>()
                            .in(Submission::getTaskId, taskIds)
                            .eq(Submission::getStudentId, studentId));
            mySubmissions.stream()
                    .filter(s -> isSubmittedStatus(s.getStatus()))
                    .map(Submission::getTaskId).forEach(done::add);

            List<Map<String, Object>> chapterList = new ArrayList<>();
            Map<Long, List<Task>> tasksByChapter = tasks.stream()
                    .filter(t -> t.getChapterId() != null)
                    .collect(Collectors.groupingBy(Task::getChapterId));
            for (CourseChapter ch : chapters) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("chapterId", ch.getId());
                cm.put("title", ch.getTitle());
                List<Task> chTasks = tasksByChapter.getOrDefault(ch.getId(), Collections.emptyList());
                chTasks.sort(Comparator.comparingLong(Task::getId));
                List<Map<String, Object>> taskList = new ArrayList<>();
                for (Task t : chTasks) {
                    Map<String, Object> tm = new LinkedHashMap<>();
                    tm.put("taskId", t.getId());
                    tm.put("title", t.getTitle());
                    tm.put("taskType", t.getTaskType());
                    tm.put("completed", done.contains(t.getId()));
                    tm.put("dueDate", t.getDueDate() != null ? t.getDueDate().toString() : null);
                    taskList.add(tm);
                }
                cm.put("tasks", taskList);
                cm.put("totalTasks", chTasks.size());
                cm.put("completedTasks", (int) chTasks.stream().filter(t -> done.contains(t.getId())).count());
                chapterList.add(cm);
            }
            result.put("chapters", chapterList);
            result.put("totalTasks", tasks.size());
            result.put("completedTasks", done.size());

            // ---- 2) 作业成绩 ----
            List<Map<String, Object>> assignments = new ArrayList<>();
            Map<Long, Task> taskMap = tasks.stream()
                    .collect(Collectors.toMap(Task::getId, t -> t, (a, b) -> a));
            for (Submission s : mySubmissions) {
                Task t = taskMap.get(s.getTaskId());
                if (t == null || !"ASSIGNMENT".equals(t.getTaskType())) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("submissionId", s.getId());
                m.put("taskId", s.getTaskId());
                m.put("title", t.getTitle());
                m.put("score", s.getScore());
                m.put("maxScore", s.getMaxScore());
                m.put("status", s.getStatus());
                m.put("feedback", s.getFeedback());
                m.put("isLate", s.getIsLate());
                m.put("submittedAt", s.getSubmittedAt() != null ? s.getSubmittedAt().toString() : null);
                assignments.add(m);
            }
            result.put("assignments", assignments);

            // ---- 3) 考试成绩 ----
            List<Map<String, Object>> exams = new ArrayList<>();
            List<ExamSession> sessions = examSessionService.list(
                    new LambdaQueryWrapper<ExamSession>()
                            .eq(ExamSession::getStudentId, studentId)
                            .orderByDesc(ExamSession::getCreatedAt));
            Set<Long> paperIds = sessions.stream().map(ExamSession::getExamPaperId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, ExamPaper> paperMap = paperIds.isEmpty() ? Collections.emptyMap()
                    : examPaperService.listByIds(paperIds).stream()
                    .collect(Collectors.toMap(ExamPaper::getId, p -> p, (a, b) -> a));
            for (ExamSession s : sessions) {
                ExamPaper p = paperMap.get(s.getExamPaperId());
                if (p == null || !courseId.equals(p.getCourseId())) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("sessionId", s.getId());
                m.put("examPaperId", s.getExamPaperId());
                m.put("title", p.getTitle());
                m.put("percentage", s.getPercentage());
                m.put("totalScore", p.getTotalScore());
                m.put("status", s.getStatus());
                m.put("submitTime", s.getSubmitTime() != null ? s.getSubmitTime().toString() : null);
                exams.add(m);
            }
            result.put("exams", exams);
        } catch (Exception e) {
            log.warn("getStudentCourseDetail 失败: courseId={}, studentId={}, {}", courseId, studentId, e.getMessage());
        }
        return result;
    }

    private boolean isSubmittedStatus(String status) {
        return SubmissionStatus.SUBMITTED.equals(status) || SubmissionStatus.GRADED.equals(status) || SubmissionStatus.RETURNED.equals(status);
    }

    private double toDouble(Object v) {
        return v == null ? 0D : Double.parseDouble(String.valueOf(v));
    }

    private long toLong(Object v) {
        return v == null ? 0L : Long.parseLong(String.valueOf(v));
    }
}
