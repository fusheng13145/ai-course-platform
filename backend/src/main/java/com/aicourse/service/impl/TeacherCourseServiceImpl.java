package com.aicourse.service.impl;

import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 课程教学核心服务实现
 * 聚合多维数据，为教师端课程管理与学情统计提供一站式支撑，并引入 AI 超越能力。
 */
@Service
public class TeacherCourseServiceImpl implements TeacherCourseService {

    private final CourseService courseService;
    private final CourseChapterService chapterService;
    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final ExamPaperService examPaperService;
    private final QuestionService questionService;
    private final LearningRecordService learningRecordService;
    private final AIService aiService;
    private final UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TeacherCourseServiceImpl(
            CourseService courseService,
            CourseChapterService chapterService,
            CourseEnrollmentService enrollmentService,
            TaskService taskService,
            SubmissionService submissionService,
            ExamPaperService examPaperService,
            QuestionService questionService,
            LearningRecordService learningRecordService,
            AIService aiService,
            UserService userService) {
        this.courseService = courseService;
        this.chapterService = chapterService;
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.examPaperService = examPaperService;
        this.questionService = questionService;
        this.learningRecordService = learningRecordService;
        this.aiService = aiService;
        this.userService = userService;
    }

    // ===================== 工具方法 =====================

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private List<Long> courseIdsOfTeacher(Long teacherId) {
        return courseService.list(new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, teacherId))
                .stream().map(Course::getId).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /** 批量解析学生姓名，避免 N+1 */
    private Map<Long, String> userNameMap(Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) return Collections.emptyMap();
        List<User> users = userService.list(new LambdaQueryWrapper<User>().in(User::getId, studentIds));
        Map<Long, String> map = new HashMap<>(users.size());
        for (User u : users) {
            String name = (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : u.getUsername();
            map.put(u.getId(), name);
        }
        return map;
    }

    // ===================== 1. 单课程教学驾驶舱 =====================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getCourseTeachingDashboard(Long courseId, Long operatorId) {
        Course course = courseService.getById(courseId);
        if (course == null) throw new BusinessException("课程不存在");

        Map<String, Object> dashboard = new LinkedHashMap<>();
        try {
            // ---- 概览 ----
            long totalStudents = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));
            long activeStudents = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>()
                    .eq(CourseEnrollment::getCourseId, courseId).eq(CourseEnrollment::getIsActive, Boolean.TRUE));
            long pendingApproval = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>()
                    .eq(CourseEnrollment::getCourseId, courseId).eq(CourseEnrollment::getApprovalStatus, "PENDING"));

            // ---- 内容量 ----
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

            // ---- 学习指标 ----
            List<CourseEnrollment> enrollments = enrollmentService.list(
                    new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));
            double avgProgress = enrollments.stream()
                    .mapToDouble(e -> e.getProgress() != null ? e.getProgress().doubleValue() : 0D).average().orElse(0D);
            long completedCount = enrollments.stream()
                    .filter(e -> e.getProgress() != null && e.getProgress().compareTo(BigDecimal.valueOf(100)) >= 0).count();
            double completionRate = totalStudents > 0 ? round2(completedCount * 100.0 / totalStudents) : 0;

            List<CourseEnrollment> scored = enrollments.stream().filter(e -> e.getFinalScore() != null).collect(Collectors.toList());
            double avgScore = scored.stream().mapToDouble(e -> e.getFinalScore().doubleValue()).average().orElse(0D);
            long passCount = scored.stream().filter(e -> e.getFinalScore().doubleValue() >= 60).count();
            long excellentCount = scored.stream().filter(e -> e.getFinalScore().doubleValue() >= 90).count();
            double passRate = scored.isEmpty() ? 0 : round2(passCount * 100.0 / scored.size());
            double excellentRate = scored.isEmpty() ? 0 : round2(excellentCount * 100.0 / scored.size());

            // ---- 待批改 ----
            List<Long> taskIds = taskService.list(new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId))
                    .stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
            long pendingGrading = 0L;
            if (!taskIds.isEmpty()) {
                pendingGrading = submissionService.count(new LambdaQueryWrapper<Submission>()
                        .in(Submission::getTaskId, taskIds).eq(Submission::getStatus, "SUBMITTED"));
            }

            // ---- 14 天趋势（活跃学生 + 提交数）----
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

            // ---- 预警学生（进度<40% 或 成绩<60）----
            List<Map<String, Object>> atRisk = buildAtRisk(enrollments, null);

            // ---- 章节树 ----
            List<Map<String, Object>> chapterTree = getFullChapterTree(courseId);

            // ---- AI 学情解读（超越轴）----
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
                    "avgProgress", round2(avgProgress),
                    "completionRate", completionRate,
                    "avgScore", round2(avgScore),
                    "passRate", passRate,
                    "excellentRate", excellentRate));
            dashboard.put("grading", Map.of("pendingGrading", pendingGrading));
            dashboard.put("trend", trend);
            dashboard.put("atRiskStudents", atRisk);
            dashboard.put("atRiskCount", atRisk.size());
            dashboard.put("chapterTree", chapterTree);
            dashboard.put("aiInsight", aiInsight);
        } catch (Exception e) {
            dashboard.put("error", "课程驾驶舱加载异常: " + e.getMessage());
        }
        return dashboard;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> computeAtRisk(List<CourseEnrollment> enrollments) {
        return buildAtRisk(enrollments, null);
    }

    /** 由选课记录构建预警学生列表（courseName 可选，用于跨课聚合展示） */
    private List<Map<String, Object>> buildAtRisk(List<CourseEnrollment> enrollments, String courseName) {
        List<CourseEnrollment> risk = enrollments.stream()
                .filter(e -> {
                    boolean lowProgress = e.getProgress() != null && e.getProgress().doubleValue() < 40;
                    boolean lowScore = e.getFinalScore() != null && e.getFinalScore().doubleValue() < 60;
                    return lowProgress || lowScore;
                })
                .collect(Collectors.toList());
        // 优先级：进度与成绩双低 > 单低
        risk.sort((a, b) -> severity(b) - severity(a));

        Map<Long, String> names = userNameMap(risk.stream().map(CourseEnrollment::getStudentId).collect(Collectors.toSet()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseEnrollment e : risk) {
            Map<String, Object> item = new LinkedHashMap<>(8);
            item.put("studentId", e.getStudentId());
            item.put("studentName", names.getOrDefault(e.getStudentId(), String.valueOf(e.getStudentId())));
            item.put("progress", e.getProgress() != null ? round2(e.getProgress().doubleValue()) : 0);
            item.put("finalScore", e.getFinalScore() != null ? round2(e.getFinalScore().doubleValue()) : null);
            item.put("level", severity(e) >= 2 ? "HIGH" : "MEDIUM");
            if (courseName != null) item.put("courseName", courseName);
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
            String statsJson = objectMapper.writeValueAsString(stats);

            List<Map<String, String>> weakPoints = atRisk.stream().limit(8).map(r -> {
                Map<String, String> w = new LinkedHashMap<>();
                w.put("description", "学生「" + r.get("studentName") + "」进度" + r.get("progress") + "%、成绩" + r.get("finalScore") + "，需重点关注");
                w.put("severity", String.valueOf(r.get("level")));
                return w;
            }).collect(Collectors.toList());
            String weakJson = objectMapper.writeValueAsString(Map.of("weakPoints", weakPoints));

            String advice = aiService.generateLearningAdviceJson(statsJson, weakJson);
            Map<String, Object> parsed = tryParseJson(advice);
            if (parsed != null) return parsed;
            Map<String, Object> fallback = new LinkedHashMap<>(2);
            fallback.put("summary", advice);
            return fallback;
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>(2);
            fallback.put("summary", "暂无可用的 AI 学情解读（模型未配置或调用失败）。");
            return fallback;
        }
    }

    private Map<String, Object> tryParseJson(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.trim();
        // 去除 ```json ... ``` 围栏
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline >= 0) cleaned = cleaned.substring(firstNewline + 1);
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
            cleaned = cleaned.trim();
        }
        try {
            return objectMapper.readValue(cleaned, Map.class);
        } catch (Exception ex) {
            return null;
        }
    }

    // ===================== 2. AI 智能建课大纲 =====================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> generateCourseOutlineByAI(String topic, String category, String level,
                                                         int chapterCount, Long operatorId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (topic == null || topic.isBlank()) {
            throw new BusinessException("课程主题不能为空");
        }
        int n = (chapterCount <= 0) ? 6 : Math.min(chapterCount, 12);
        try {
            String prompt = String.format(
                    "你是一名资深课程设计师。请为高校课程《%s》设计一份课程大纲。" +
                            "课程分类：%s；难度：%s；建议章节数：%d。" +
                            "请严格只返回如下 JSON（不要包含任何解释或 Markdown 围栏）：" +
                            "{\"title\":\"课程标题\",\"description\":\"100字以内的课程简介\",\"chapters\":[" +
                            "{\"title\":\"章节标题\",\"summary\":\"章节要点\",\"suggestedTaskPoints\":[\"任务点1\",\"任务点2\"]}" +
                            "]}",
                    topic, (category == null ? "通用" : category), (level == null ? "入门" : level), n);

            String raw = aiService.chat(prompt, "你是一个专业的课程设计助手，输出必须是合法 JSON。", operatorId);
            Map<String, Object> outline = tryParseJson(raw);
            if (outline == null) {
                result.put("success", false);
                result.put("message", "AI 返回内容无法解析为大纲，请稍后重试");
                result.put("raw", raw);
                return result;
            }
            result.put("success", true);
            result.put("outline", outline);
            result.put("applicableCourseCategory", category);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "AI 建课调用失败: " + e.getMessage());
        }
        return result;
    }

    // ===================== 3. 课程克隆 =====================

    @Override
    @Transactional
    public Map<String, Object> cloneCourse(Long sourceCourseId, String newName, Long operatorId) {
        Course src = courseService.getById(sourceCourseId);
        if (src == null) throw new BusinessException("源课程不存在");
        if (newName == null || newName.isBlank()) newName = src.getName() + "（副本）";

        // 复制课程元信息（重置状态为草稿，归属到操作者）
        Course copy = new Course();
        copy.setName(newName);
        copy.setCode(src.getCode());
        copy.setDescription(src.getDescription());
        copy.setCredits(src.getCredits());
        copy.setTeacherId(operatorId);
        copy.setSemester(src.getSemester());
        copy.setYear(src.getYear());
        copy.setStatus("INACTIVE");
        copy.setApprovalStatus("DRAFT");
        copy.setEnrollmentType(src.getEnrollmentType());
        copy.setMaxStudents(src.getMaxStudents());
        copy.setCategory(src.getCategory());
        copy.setDifficulty(src.getDifficulty());
        copy.setPrice(src.getPrice());
        copy.setTags(src.getTags());
        copy.setStartDate(src.getStartDate());
        copy.setEndDate(src.getEndDate());
        copy.setCoverImage(src.getCoverImage());
        copy.setSyllabusUrl(src.getSyllabusUrl());
        courseService.save(copy);

        List<CourseChapter> chapters = chapterService.list(
                new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, sourceCourseId)
                        .orderByAsc(CourseChapter::getOrderIndex));
        int cloned = 0;
        for (CourseChapter c : chapters) {
            CourseChapter nc = new CourseChapter();
            nc.setCourseId(copy.getId());
            nc.setParentId(c.getParentId()); // 注意：跨课程克隆后父ID指向旧课程，需二次修正
            nc.setTitle(c.getTitle());
            nc.setContent(c.getContent());
            nc.setOrderIndex(c.getOrderIndex());
            nc.setDuration(c.getDuration());
            nc.setIsPublished(Boolean.FALSE);
            chapterService.save(nc);
            cloned++;
        }
        // 修正父子关系：按 (title, orderIndex) 映射到新课程内的新ID
        if (cloned > 0) {
            List<CourseChapter> newChapters = chapterService.list(
                    new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, copy.getId())
                            .orderByAsc(CourseChapter::getOrderIndex));
            Map<String, Long> keyToNewId = new HashMap<>();
            for (CourseChapter nc : newChapters) {
                keyToNewId.put(nc.getTitle() + "#" + nc.getOrderIndex(), nc.getId());
            }
            for (CourseChapter nc : newChapters) {
                if (nc.getParentId() != null) {
                    // 在源课程中找到对应父章节
                    CourseChapter srcParent = chapters.stream()
                            .filter(sc -> sc.getId().equals(nc.getParentId())).findFirst().orElse(null);
                    if (srcParent != null) {
                        Long newParentId = keyToNewId.get(srcParent.getTitle() + "#" + srcParent.getOrderIndex());
                        if (newParentId != null && !newParentId.equals(nc.getId())) {
                            nc.setParentId(newParentId);
                            chapterService.updateById(nc);
                        }
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("newCourseId", copy.getId());
        result.put("newCourseName", copy.getName());
        result.put("clonedChapters", cloned);
        return result;
    }

    // ===================== 4. 我的课程聚合指标 =====================

    @Override
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
            double completionRate = studentCount > 0 ? round2(completed * 100.0 / studentCount) : 0;

            List<Long> taskIds = taskService.list(new LambdaQueryWrapper<Task>().eq(Task::getCourseId, cid))
                    .stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
            long pendingGrading = 0L;
            if (!taskIds.isEmpty()) {
                pendingGrading = submissionService.count(new LambdaQueryWrapper<Submission>()
                        .in(Submission::getTaskId, taskIds).eq(Submission::getStatus, "SUBMITTED"));
            }

            Map<String, Object> item = new LinkedHashMap<>(10);
            item.put("courseId", cid);
            item.put("name", c.getName());
            item.put("code", c.getCode());
            item.put("coverImage", c.getCoverImage());
            item.put("status", c.getStatus());
            item.put("category", c.getCategory());
            item.put("studentCount", studentCount);
            item.put("avgProgress", round2(avgProgress));
            item.put("completionRate", completionRate);
            item.put("pendingGrading", pendingGrading);
            summaries.add(item);
        }
        return summaries;
    }

    // ===================== 5. 增强版教师仪表盘 =====================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getTeacherDashboardV2(Long teacherId) {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        try {
            List<Long> courseIds = courseIdsOfTeacher(teacherId);
            long courseCount = courseIds.size();

            long studentCount = 0L;
            if (!courseIds.isEmpty()) {
                studentCount = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>()
                        .in(CourseEnrollment::getCourseId, courseIds));
            }

            // 待批改（跨课程）
            long pendingGrading = 0L;
            if (!courseIds.isEmpty()) {
                List<Long> taskIds = taskService.list(new LambdaQueryWrapper<Task>().in(Task::getCourseId, courseIds))
                        .stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
                if (!taskIds.isEmpty()) {
                    pendingGrading = submissionService.count(new LambdaQueryWrapper<Submission>()
                            .in(Submission::getTaskId, taskIds).eq(Submission::getStatus, "SUBMITTED"));
                }
            }

            // 平均成绩（跨课程已批改提交）
            double avgScore = 0D;
            if (!courseIds.isEmpty()) {
                List<Long> taskIds = taskService.list(new LambdaQueryWrapper<Task>().in(Task::getCourseId, courseIds))
                        .stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
                if (!taskIds.isEmpty()) {
                    avgScore = submissionService.list(new LambdaQueryWrapper<Submission>()
                            .in(Submission::getTaskId, taskIds).isNotNull(Submission::getScore))
                            .stream().mapToDouble(s -> s.getScore() != null ? s.getScore().doubleValue() : 0D)
                            .average().orElse(0D);
                }
            }

            // 课程下钻卡片
            List<Map<String, Object>> courseCards = getMyCourseSummaries(teacherId);

            // 跨课程预警学生（聚合，取前 10）
            Map<Long, String> courseNameMap = new HashMap<>();
            final List<CourseEnrollment> allEnrollments;
            if (!courseIds.isEmpty()) {
                List<Course> teacherCourses = courseService.list(new LambdaQueryWrapper<Course>().in(Course::getId, courseIds));
                for (Course c : teacherCourses) courseNameMap.put(c.getId(), c.getName());
                allEnrollments = enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>().in(CourseEnrollment::getCourseId, courseIds));
            } else {
                allEnrollments = new ArrayList<>();
            }
            List<Map<String, Object>> atRisk = buildAtRisk(allEnrollments, null);
            // 注入课程名
            Map<Long, String> cNameById = courseNameMap;
            List<Map<String, Object>> atRiskWithCourse = atRisk.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>(r);
                // 通过 studentId 反查其所属课程（取第一条匹配）
                Long sid = (Long) r.get("studentId");
                String cname = allEnrollments.stream()
                        .filter(e -> e.getStudentId() != null && e.getStudentId().equals(sid))
                        .map(e -> cNameById.get(e.getCourseId()))
                        .filter(Objects::nonNull).findFirst().orElse("");
                m.put("courseName", cname);
                return m;
            }).limit(10).collect(Collectors.toList());

            // 任务点完成情况概览（已发布任务数 / 总提交数 / 提交率）
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
            // 预期提交数 = 已发布任务 × 学生数
            long expectedSubmissions = publishedTasks * studentCount;
            double submissionRate = expectedSubmissions > 0 ? round2(totalSubmissions * 100.0 / expectedSubmissions) : 0;

            // 平台均值对比（全平台已评分提交平均）
            double platformAvgScore = submissionService.list(new LambdaQueryWrapper<Submission>().isNotNull(Submission::getScore))
                    .stream().mapToDouble(s -> s.getScore() != null ? s.getScore().doubleValue() : 0D).average().orElse(0D);
            double scoreGap = round2(avgScore - platformAvgScore);

            // AI 教学总览
            Map<String, Object> aiOverview = buildTeacherAIOverview(courseCount, studentCount, avgScore,
                    pendingGrading, atRiskWithCourse, submissionRate);

            dashboard.put("courseCount", courseCount);
            dashboard.put("studentCount", studentCount);
            dashboard.put("pendingGrading", pendingGrading);
            dashboard.put("avgScore", round2(avgScore));
            dashboard.put("courseCards", courseCards);
            dashboard.put("atRiskStudents", atRiskWithCourse);
            dashboard.put("atRiskCount", atRiskWithCourse.size());
            dashboard.put("taskPointOverview", Map.of(
                    "publishedTasks", publishedTasks,
                    "totalSubmissions", totalSubmissions,
                    "expectedSubmissions", expectedSubmissions,
                    "submissionRate", submissionRate));
            dashboard.put("platformComparison", Map.of(
                    "platformAvgScore", round2(platformAvgScore),
                    "myAvgScore", round2(avgScore),
                    "scoreGap", scoreGap,
                    "trend", scoreGap >= 0 ? "ABOVE" : "BELOW"));
            dashboard.put("aiOverview", aiOverview);
        } catch (Exception e) {
            dashboard.put("error", "教师仪表盘加载异常: " + e.getMessage());
            dashboard.putIfAbsent("courseCount", 0L);
            dashboard.putIfAbsent("studentCount", 0L);
            dashboard.putIfAbsent("pendingGrading", 0L);
            dashboard.putIfAbsent("avgScore", 0D);
            dashboard.putIfAbsent("courseCards", new ArrayList<>());
            dashboard.putIfAbsent("atRiskStudents", new ArrayList<>());
            dashboard.putIfAbsent("aiOverview", Map.of("summary", "暂无可用的 AI 教学总览。"));
        }
        return dashboard;
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
            String statsJson = objectMapper.writeValueAsString(stats);

            List<Map<String, String>> weakPoints = atRisk.stream().limit(8).map(r -> {
                Map<String, String> w = new LinkedHashMap<>();
                w.put("description", "课程「" + r.get("courseName") + "」学生「" + r.get("studentName") +
                        "」进度" + r.get("progress") + "%、成绩" + r.get("finalScore"));
                w.put("severity", String.valueOf(r.get("level")));
                return w;
            }).collect(Collectors.toList());
            String weakJson = objectMapper.writeValueAsString(Map.of("weakPoints", weakPoints));

            String advice = aiService.generateLearningAdviceJson(statsJson, weakJson);
            Map<String, Object> parsed = tryParseJson(advice);
            if (parsed != null) return parsed;
            Map<String, Object> fallback = new LinkedHashMap<>(2);
            fallback.put("summary", advice);
            return fallback;
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>(2);
            fallback.put("summary", "暂无可用的 AI 教学总览（模型未配置或调用失败）。");
            return fallback;
        }
    }

    // ===================== 6. 完整章节树 =====================

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFullChapterTree(Long courseId) {
        List<CourseChapter> all = chapterService.list(new LambdaQueryWrapper<CourseChapter>()
                .eq(CourseChapter::getCourseId, courseId).orderByAsc(CourseChapter::getOrderIndex));

        long studentCount = enrollmentService.count(new LambdaQueryWrapper<CourseEnrollment>().eq(CourseEnrollment::getCourseId, courseId));

        // 课程内全部任务，按章节分组
        List<Task> courseTasks = taskService.list(new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId));
        Map<Long, List<Task>> tasksByChapter = courseTasks.stream()
                .filter(t -> t.getChapterId() != null)
                .collect(Collectors.groupingBy(Task::getChapterId));

        // 课程内任务提交数（按任务聚合，已提交/已批改均计为完成）
        final Map<Long, Long> submissionCountByTask;
        if (!courseTasks.isEmpty()) {
            List<Long> taskIds = courseTasks.stream().map(Task::getId).filter(Objects::nonNull).collect(Collectors.toList());
            if (!taskIds.isEmpty()) {
                List<Submission> subs = submissionService.list(new LambdaQueryWrapper<Submission>()
                        .in(Submission::getTaskId, taskIds)
                        .in(Submission::getStatus, "SUBMITTED", "GRADED"));
                submissionCountByTask = subs.stream()
                        .collect(Collectors.groupingBy(Submission::getTaskId, Collectors.counting()));
            } else {
                submissionCountByTask = Collections.emptyMap();
            }
        } else {
            submissionCountByTask = Collections.emptyMap();
        }

        Map<Long, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (CourseChapter c : all) {
            List<Task> chTasks = tasksByChapter.getOrDefault(c.getId(), Collections.emptyList());
            long taskCount = chTasks.size();
            long submitted = chTasks.stream()
                    .mapToLong(t -> submissionCountByTask.getOrDefault(t.getId(), 0L)).sum();
            long expected = taskCount * studentCount;
            double completionRate = expected > 0 ? round2(submitted * 100.0 / expected) : 0;

            List<Map<String, Object>> taskPoints = chTasks.stream().map(t -> {
                Map<String, Object> tp = new LinkedHashMap<>(6);
                tp.put("id", t.getId());
                tp.put("title", t.getTitle());
                tp.put("taskType", t.getTaskType());
                tp.put("isPublished", t.getIsPublished());
                long sc = submissionCountByTask.getOrDefault(t.getId(), 0L);
                tp.put("submittedCount", sc);
                tp.put("completionRate", studentCount > 0 ? round2(sc * 100.0 / studentCount) : 0);
                return tp;
            }).collect(Collectors.toList());

            Map<String, Object> node = new LinkedHashMap<>(10);
            node.put("id", c.getId());
            node.put("title", c.getTitle());
            node.put("content", c.getContent());
            node.put("orderIndex", c.getOrderIndex());
            node.put("duration", c.getDuration());
            node.put("isPublished", c.getIsPublished());
            node.put("parentId", c.getParentId());
            node.put("taskCount", taskCount);
            node.put("submittedCount", submitted);
            node.put("completionRate", completionRate);
            node.put("taskPoints", taskPoints);
            node.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put(c.getId(), node);
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (CourseChapter c : all) {
            Map<String, Object> node = nodeMap.get(c.getId());
            if (c.getParentId() != null && nodeMap.containsKey(c.getParentId())) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get(c.getParentId()).get("children");
                children.add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }
}
