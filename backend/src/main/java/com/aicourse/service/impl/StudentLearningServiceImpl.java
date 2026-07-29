package com.aicourse.service.impl;

import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.TaskCompletionMapper;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * 学生端学习核心服务实现
 * 对标超星学习通：学生首页驾驶舱、章节任务点学习中心（闯关解锁）、任务点完成态、成绩总览。
 */
@Service
@Slf4j
public class StudentLearningServiceImpl implements StudentLearningService {


    /**
     * 视频进度防拖拽容差（秒）：允许的位置/时长增量相对真实经过时长的额外余量，
     * 与 VideoLearningRecordServiceImpl.SEEK_TOLERANCE_SEC 保持一致，避免正常播放边界抖动被误判。
     */
    private static final long VIDEO_SEEK_TOLERANCE_SEC = 3L;

    private static final Comparator<CourseChapter> CHAPTER_ORDER = (a, b) -> {
        int oa = a.getOrderIndex() == null ? 0 : a.getOrderIndex();
        int ob = b.getOrderIndex() == null ? 0 : b.getOrderIndex();
        if (oa != ob) return Integer.compare(oa, ob);
        return Long.compare(a.getId(), b.getId());
    };

    private final CourseEnrollmentService enrollmentService;
    private final CourseService courseService;
    private final CourseChapterService courseChapterService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final TaskCompletionMapper taskCompletionMapper;
    private final NotificationService notificationService;
    private final SignInService signInService;
    private final ExamPaperService examPaperService;
    private final ExamSessionService examSessionService;
    private final AnalyticsService analyticsService;
    private final LearningRecordService learningRecordService;
    private final QuestionService questionService;
    private final WrongQuestionService wrongQuestionService;
    private final VideoLearningRecordService videoLearningRecordService;

    public StudentLearningServiceImpl(CourseEnrollmentService enrollmentService,
                                      CourseService courseService,
                                      CourseChapterService courseChapterService,
                                      TaskService taskService,
                                      SubmissionService submissionService,
                                      TaskCompletionMapper taskCompletionMapper,
                                      NotificationService notificationService,
                                      SignInService signInService,
                                      ExamPaperService examPaperService,
                                      ExamSessionService examSessionService,
                                      AnalyticsService analyticsService,
                                      LearningRecordService learningRecordService,
                                      QuestionService questionService,
                                      WrongQuestionService wrongQuestionService,
                                      VideoLearningRecordService videoLearningRecordService) {
        this.enrollmentService = enrollmentService;
        this.courseService = courseService;
        this.courseChapterService = courseChapterService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.taskCompletionMapper = taskCompletionMapper;
        this.notificationService = notificationService;
        this.signInService = signInService;
        this.examPaperService = examPaperService;
        this.examSessionService = examSessionService;
        this.analyticsService = analyticsService;
        this.learningRecordService = learningRecordService;
        this.questionService = questionService;
        this.wrongQuestionService = wrongQuestionService;
        this.videoLearningRecordService = videoLearningRecordService;
    }

    /**
     * 自愈合建表：避免依赖脆弱的 DB 迁移；若表已存在则幂等跳过。
     */
    @PostConstruct
    public void ensureTable() {
        try {
            taskCompletionMapper.createTableIfNotExists();
            log.info("task_completions 表已就绪（自愈合建表）");
        } catch (Exception e) {
            log.warn("task_completions 自愈合建表失败（可忽略，若表已存在）: {}", e.getMessage());
        }
    }

    // ===================== 学生首页/学习空间驾驶舱 =====================

    @Override
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
            // 批量预取课程信息，消除 N+1（原每次循环 getById）
            Set<Long> cids = enrollments.stream().map(CourseEnrollment::getCourseId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, Course> courseMap = cids.isEmpty() ? Collections.emptyMap()
                    : courseService.listByIds(cids).stream()
                    .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));
            for (CourseEnrollment en : enrollments) {
                if (en.getCourseId() == null) continue;
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
                                .in(Submission::getStatus, "SUBMITTED", "GRADED", "RETURNED"))
                        .stream().map(Submission::getTaskId).collect(Collectors.toSet());
                for (Task t : tasks) {
                    if (submitted.contains(t.getId())) continue;
                    if (t.getDueDate() != null && t.getDueDate().isBefore(now)) continue;
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
                for (ExamPaper p : papers) {
                    if (p.getStartTime() == null || p.getStartTime().isAfter(now)) continue;
                    if (p.getEndTime() != null && p.getEndTime().isBefore(now)) continue;
                    boolean taken = examSessionService.count(new LambdaQueryWrapper<ExamSession>()
                            .eq(ExamSession::getExamPaperId, p.getId())
                            .eq(ExamSession::getStudentId, userId)) > 0;
                    if (taken) continue;
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
                for (SignIn s : signs) {
                    try {
                        SignInRecord rec = signInService.myRecord(s.getId(), userId);
                        if (rec != null) continue;
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
            result.put("unreadCount", 0L);
            result.put("unreadByCategory", Collections.emptyMap());
        }

        // 4) 学情 / 统计（轻量，失败兜底）
        try {
            result.put("learningStats", learningRecordService.getLearningStats(userId));
        } catch (Exception ignore) {
            log.warn("获取学习统计失败: userId={}", userId, ignore);
        }
        try {
            result.put("analytics", analyticsService.getUserDashboard(userId));
        } catch (Exception ignore) {
            log.warn("获取学情分析失败: userId={}", userId, ignore);
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

    // ===================== 章节任务点学习中心 =====================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCourseLearningCenter(Long userId, Long courseId) {
        assertEnrolled(userId, courseId);
        Course course = courseService.getById(courseId);

        List<CourseChapter> allChapters = courseChapterService.list(
                new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, courseId));
        List<Task> allTasks = taskService.list(
                new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId).isNotNull(Task::getChapterId));

        Map<Long, List<Task>> tasksByChapter = allTasks.stream()
                .filter(t -> t.getChapterId() != null)
                .collect(Collectors.groupingBy(Task::getChapterId));
        // 批量预取完成态（消除原 isTaskCompleted() 逐条查库 N+1）
        Set<Long> taskIds = allTasks.stream().map(Task::getId).collect(Collectors.toSet());
        Set<Long> submittedTaskIds = taskIds.isEmpty() ? Collections.emptySet()
                : submissionService.list(new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getStudentId, userId)
                        .in(Submission::getTaskId, taskIds)
                        .in(Submission::getStatus, "SUBMITTED", "GRADED", "RETURNED"))
                .stream().map(Submission::getTaskId).collect(Collectors.toSet());
        Set<Long> completedTcTaskIds = taskIds.isEmpty() ? Collections.emptySet()
                : taskCompletionMapper.selectList(new LambdaQueryWrapper<TaskCompletion>()
                        .eq(TaskCompletion::getStudentId, userId)
                        .in(TaskCompletion::getTaskId, taskIds)
                        .eq(TaskCompletion::getCompleted, true))
                .stream().map(TaskCompletion::getTaskId).collect(Collectors.toSet());
        Map<Long, Boolean> completedMap = new HashMap<>();
        for (Task t : allTasks) {
            boolean done = submittedTaskIds.contains(t.getId());
            if (!done && completedTcTaskIds.contains(t.getId())) done = true;
            completedMap.put(t.getId(), done);
        }

        Map<Long, List<CourseChapter>> childrenMap = allChapters.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() != 0)
                .collect(Collectors.groupingBy(CourseChapter::getParentId));
        List<CourseChapter> top = allChapters.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .sorted(CHAPTER_ORDER)
                .collect(Collectors.toList());

        boolean[] prevAllDone = {true};
        List<Map<String, Object>> tree = new ArrayList<>();
        int[] counters = {0, 0};
        buildLevel(top, childrenMap, tasksByChapter, completedMap, prevAllDone, tree, counters);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", courseId);
        result.put("courseName", course != null ? course.getName() : null);
        result.put("chapters", tree);
        result.put("totalTasks", counters[0]);
        result.put("completedTasks", counters[1]);
        result.put("completionPercent", counters[0] == 0 ? 100 : Math.round(counters[1] * 100.0 / counters[0]));
        return result;
    }

    private void buildLevel(List<CourseChapter> level, Map<Long, List<CourseChapter>> childrenMap,
                            Map<Long, List<Task>> tasksByChapter, Map<Long, Boolean> completedMap,
                            boolean[] prevAllDone, List<Map<String, Object>> outTree, int[] counters) {
        for (CourseChapter ch : level) {
            boolean chapterUnlocked = prevAllDone[0];
            List<Task> chTasks = tasksByChapter.getOrDefault(ch.getId(), Collections.emptyList());
            chTasks.sort(Comparator.comparing(Task::getId));
            List<Map<String, Object>> taskMaps = new ArrayList<>();
            boolean prevTaskDone = true;
            int done = 0;
            for (int i = 0; i < chTasks.size(); i++) {
                Task t = chTasks.get(i);
                boolean c = completedMap.getOrDefault(t.getId(), false);
                if (c) done++;
                boolean unlocked = chapterUnlocked && (i == 0 || prevTaskDone);
                Map<String, Object> tm = new LinkedHashMap<>();
                tm.put("id", t.getId());
                tm.put("title", t.getTitle());
                tm.put("taskType", t.getTaskType());
                tm.put("status", t.getStatus());
                tm.put("isPublished", t.getIsPublished());
                tm.put("dueDate", t.getDueDate() != null ? t.getDueDate().toString() : null);
                tm.put("completed", c);
                tm.put("unlocked", unlocked);
                taskMaps.add(tm);
                prevTaskDone = c;
                counters[0]++;
                if (c) counters[1]++;
            }
            boolean allCompleted = chTasks.isEmpty() || done == chTasks.size();
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", ch.getId());
            node.put("title", ch.getTitle());
            node.put("orderIndex", ch.getOrderIndex());
            node.put("isPublished", ch.getIsPublished());
            node.put("hasContent", ch.getContent() != null && !ch.getContent().isEmpty());
            node.put("unlocked", chapterUnlocked);
            node.put("totalTasks", chTasks.size());
            node.put("completedTasks", done);
            node.put("allCompleted", allCompleted);
            node.put("tasks", taskMaps);
            List<Map<String, Object>> childTree = new ArrayList<>();
            List<CourseChapter> children = childrenMap.getOrDefault(ch.getId(), Collections.emptyList());
            children.sort(CHAPTER_ORDER);
            if (!children.isEmpty()) {
                buildLevel(children, childrenMap, tasksByChapter, completedMap, new boolean[]{allCompleted}, childTree, counters);
            }
            node.put("children", childTree);
            outTree.add(node);
            prevAllDone[0] = allCompleted;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCourseProgressSummary(Long userId, Long courseId) {
        assertEnrolled(userId, courseId);
        Map<String, Object> center = getCourseLearningCenter(userId, courseId);
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) center.get("chapters");
        List<Map<String, Object>> summary = new ArrayList<>();
        if (chapters != null) collectSummary(chapters, summary);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", courseId);
        result.put("chapters", summary);
        result.put("totalTasks", center.get("totalTasks"));
        result.put("completedTasks", center.get("completedTasks"));
        result.put("completionPercent", center.get("completionPercent"));
        return result;
    }

    @SuppressWarnings("unchecked")
    private void collectSummary(List<Map<String, Object>> chapters, List<Map<String, Object>> out) {
        for (Map<String, Object> ch : chapters) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chapterId", ch.get("id"));
            m.put("title", ch.get("title"));
            m.put("totalTasks", ch.get("totalTasks"));
            m.put("completedTasks", ch.get("completedTasks"));
            m.put("percent", ((Number) ch.get("totalTasks")).intValue() == 0 ? 100
                    : Math.round(((Number) ch.get("completedTasks")).intValue() * 100.0 / ((Number) ch.get("totalTasks")).intValue()));
            out.add(m);
            List<Map<String, Object>> children = (List<Map<String, Object>>) ch.get("children");
            if (children != null && !children.isEmpty()) collectSummary(children, out);
        }
    }

    // ===================== 任务点完成态 =====================

    @Override
    public Map<String, Object> markTaskComplete(Long userId, Long courseId, Long taskId, String completionType) {
        assertEnrolled(userId, courseId);
        Task task = taskService.getById(taskId);
        if (task == null) throw new BusinessException("任务点不存在");
        String ct = completionType != null ? completionType : (task.getTaskType() != null ? task.getTaskType() : "MANUAL");
        upsertCompletion(userId, courseId, taskId, ct, x -> {
            x.setCompleted(true);
            x.setCompletedAt(LocalDateTime.now());
        });
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("taskId", taskId);
        r.put("completed", true);
        r.put("completionType", ct);
        return r;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getVideoState(Long userId, Long courseId, Long taskId) {
        TaskCompletion tc = getOneCompletion(taskId, userId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("taskId", taskId);
        r.put("positionSeconds", tc != null && tc.getLastPosition() != null ? tc.getLastPosition() : 0);
        r.put("watchedDuration", tc != null && tc.getWatchedDuration() != null ? tc.getWatchedDuration() : 0);
        r.put("totalDuration", tc != null && tc.getTotalDuration() != null ? tc.getTotalDuration() : 0);
        r.put("completed", tc != null && Boolean.TRUE.equals(tc.getCompleted()));
        return r;
    }

    @Override
    public Map<String, Object> reportVideoHeartbeat(Long userId, Long courseId, Long taskId, Long resourceId,
                                                    Integer position, Integer watchedDuration,
                                                    Integer totalDuration, Double playbackSpeed) {
        assertEnrolled(userId, courseId);
        Task task = taskService.getById(taskId);
        if (task == null) throw new BusinessException("任务点不存在");

        // ===== 视频进度唯一真相源收口（#65）=====
        // 计算「有效观看时长 + 续播位置 + 完播判定」的权威值：
        // 1) 提供 resourceId 时，委托 VideoLearningRecordService 进行反拖拽累计（P0-5 完整性逻辑），
        //    其结果作为权威值回写 task_completions，使两表打通且以 task_completions 为学习中心真相源；
        // 2) 未提供 resourceId 时，退化为基于 task_completions 自身存储状态的反拖拽累计（基础完整性）。
        int validWatched;
        int lastPosition;
        int totalDur;
        boolean videoCompleted;

        if (resourceId != null) {
            VideoLearningRecord rec = videoLearningRecordService.updateProgress(
                    userId, resourceId, position, totalDuration, playbackSpeed);
            validWatched = rec.getWatchDuration() != null ? rec.getWatchDuration() : 0;
            lastPosition = rec.getLastPosition() != null ? rec.getLastPosition() : 0;
            totalDur = rec.getTotalDuration() != null ? rec.getTotalDuration() : 0;
            videoCompleted = rec.getCompleteCount() != null && rec.getCompleteCount() > 0;
        } else {
            // 退化路径：以 task_completions 自身存储状态做反拖拽累计
            TaskCompletion existing = getOneCompletion(taskId, userId);
            int oldPos = existing != null && existing.getLastPosition() != null ? existing.getLastPosition() : 0;
            int oldWd = existing != null && existing.getWatchedDuration() != null ? existing.getWatchedDuration() : 0;
            LocalDateTime prevUpdate = existing != null
                    ? (existing.getUpdatedAt() != null ? existing.getUpdatedAt() : existing.getCreatedAt())
                    : null;
            long realElapsed = prevUpdate != null
                    ? Math.max(0, ChronoUnit.SECONDS.between(prevUpdate, LocalDateTime.now()))
                    : 0;
            int pos = position != null ? position : 0;
            int claimedWd = watchedDuration != null ? watchedDuration : 0;
            int posDelta = pos - oldPos;
            int claimedInc = claimedWd - oldWd;
            // 有效增量 = max(0, min(max(位置增量, 声称时长增量), 真实经过时长 + 容差))
            int validInc = (int) Math.max(0, Math.min(Math.max(posDelta, claimedInc), realElapsed + VIDEO_SEEK_TOLERANCE_SEC));
            validWatched = oldWd + validInc;
            lastPosition = pos;
            totalDur = totalDuration != null ? totalDuration : 0;
            videoCompleted = totalDur > 0 && validWatched >= totalDur * 0.9;
        }

        final int wd = validWatched;
        final int pos = lastPosition;
        final int td = totalDur;
        final boolean done = videoCompleted;
        upsertCompletion(userId, courseId, taskId, "VIDEO", x -> {
            x.setLastPosition(pos);
            x.setWatchedDuration(wd);
            x.setTotalDuration(td);
            if (td > 0 && wd >= td * 0.9) {
                x.setCompleted(true);
                x.setCompletedAt(LocalDateTime.now());
            } else if (done) {
                x.setCompleted(true);
                x.setCompletedAt(LocalDateTime.now());
            }
        });
        return getVideoState(userId, courseId, taskId);
    }

    @Override
    public Map<String, Object> submitQuiz(Long userId, Long courseId, Long taskId, Map<Long, String> answers) {
        assertEnrolled(userId, courseId);
        Task task = taskService.getById(taskId);
        if (task == null) throw new BusinessException("任务点不存在");
        int total = 0, correct = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        if (answers != null) {
            // 批量预取题目信息，消除 N+1（原每次循环 getById）
            Set<Long> qids = answers.keySet();
            Map<Long, Question> questionMap = qids.isEmpty() ? Collections.emptyMap()
                    : questionService.listByIds(qids).stream()
                    .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));
            for (Map.Entry<Long, String> e : answers.entrySet()) {
                Long qid = e.getKey();
                String ans = e.getValue();
                Question q = questionMap.get(qid);
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("questionId", qid);
                if (q != null) {
                    boolean ok = normalize(q.getCorrectAnswers()).equals(normalize(ans));
                    d.put("correct", ok);
                    d.put("correctAnswer", q.getCorrectAnswers());
                    d.put("explanation", q.getExplanation());
                    total++;
                    if (ok) correct++;
                    else if (isObjectiveQuestion(q.getType())) {
                        // 修复 P1-3：练习/测验(quiz) 答错自动收录错题本，与考试(EXAM)对齐。
                        // 仅客观题（单选/多选/判断）可可靠判定对错，主观题不参与自动收录。
                        try {
                            wrongQuestionService.recordWrongAnswer(userId, qid, courseId, ans, "QUIZ");
                        } catch (Exception ignore) {
                            log.warn("错题收录失败: questionId={}, userId={}", qid, userId, ignore);
                        }
                    }
                } else {
                    d.put("correct", false);
                    d.put("correctAnswer", null);
                    d.put("explanation", null);
                }
                details.add(d);
            }
        }
        BigDecimal score = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(Math.round(correct * 100.0 / total));
        upsertCompletion(userId, courseId, taskId, "QUIZ", x -> {
            x.setScore(score);
            x.setCompleted(true);
            x.setCompletedAt(LocalDateTime.now());
        });
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("taskId", taskId);
        r.put("total", total);
        r.put("correct", correct);
        r.put("score", score);
        r.put("details", details);
        return r;
    }

    @Override
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
                if (c == null) continue;
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

    // ===================== 私有工具 =====================

    private void assertEnrolled(Long userId, Long courseId) {
        CourseEnrollment en = enrollmentService.getOne(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, userId)
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getIsActive, true));
        if (en == null) throw new BusinessException("未加入该课程");
    }

    private boolean isTaskCompleted(Task task, Long userId) {
        String type = task.getTaskType();
        if ("ASSIGNMENT".equals(type)) {
            return submissionService.count(new LambdaQueryWrapper<Submission>()
                    .eq(Submission::getTaskId, task.getId())
                    .eq(Submission::getStudentId, userId)
                    .in(Submission::getStatus, "SUBMITTED", "GRADED", "RETURNED")) > 0;
        }
        if ("QUIZ".equals(type)) {
            boolean sub = submissionService.count(new LambdaQueryWrapper<Submission>()
                    .eq(Submission::getTaskId, task.getId())
                    .eq(Submission::getStudentId, userId)
                    .in(Submission::getStatus, "SUBMITTED", "GRADED", "RETURNED")) > 0;
            if (sub) return true;
        }
        return taskCompletionCompleted(task.getId(), userId);
    }

    private boolean taskCompletionCompleted(Long taskId, Long userId) {
        return taskCompletionMapper.selectOne(new LambdaQueryWrapper<TaskCompletion>()
                .eq(TaskCompletion::getStudentId, userId)
                .eq(TaskCompletion::getTaskId, taskId)
                .eq(TaskCompletion::getCompleted, true)) != null;
    }

    private TaskCompletion getOneCompletion(Long taskId, Long userId) {
        return taskCompletionMapper.selectOne(new LambdaQueryWrapper<TaskCompletion>()
                .eq(TaskCompletion::getStudentId, userId)
                .eq(TaskCompletion::getTaskId, taskId));
    }

    private TaskCompletion upsertCompletion(Long userId, Long courseId, Long taskId,
                                             String completionType, Consumer<TaskCompletion> modifier) {
        TaskCompletion tc = getOneCompletion(taskId, userId);
        boolean isNew = false;
        if (tc == null) {
            tc = new TaskCompletion();
            tc.setStudentId(userId);
            tc.setTaskId(taskId);
            tc.setCourseId(courseId);
            Task t = taskService.getById(taskId);
            if (t != null) {
                tc.setChapterId(t.getChapterId());
                tc.setTaskType(t.getTaskType());
            }
            isNew = true;
        }
        if (completionType != null) tc.setCompletionType(completionType);
        modifier.accept(tc);
        if (isNew) taskCompletionMapper.insert(tc);
        else taskCompletionMapper.updateById(tc);
        return tc;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return Arrays.stream(s.split("[,，;；]"))
                .map(t -> t.trim().toUpperCase())
                .filter(t -> !t.isEmpty())
                .sorted()
                .collect(Collectors.joining(","));
    }

    /**
     * 判断题目是否为可自动判定的客观题。
     * 题目 type 字段存在两套命名约定（历史遗留）：
     *   - API/DTO/AI 生成：SINGLE_CHOICE / MULTIPLE_CHOICE / TRUE_FALSE / SHORT_ANSWER / FILL_BLANK
     *   - QuestionType 枚举名：SINGLE / MULTIPLE / JUDGE / ESSAY / FILL
     * 两套都兼容，避免任一套约定下漏收录。
     * 仅单选/多选/判断可基于 correctAnswers 可靠判定对错，简答/填空不自动收录错题。
     */
    private boolean isObjectiveQuestion(String type) {
        if (type == null) return false;
        String t = type.trim().toUpperCase();
        return "SINGLE".equals(t) || "SINGLE_CHOICE".equals(t)
                || "MULTIPLE".equals(t) || "MULTIPLE_CHOICE".equals(t)
                || "JUDGE".equals(t) || "TRUE_FALSE".equals(t);
    }
}
