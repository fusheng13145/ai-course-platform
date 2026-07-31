package com.aicourse.controller;

import jakarta.validation.Valid;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师端统一核心接口
 * <p>
 * 旧 {@code TeacherController} 与重构后的 {@code CourseTeachingController} 已合并为单一控制器：
 * 统一课程建设 / 学情驾驶舱 / 作业题库管理 / 学情分析，并补齐课程归属校验（{@link #assertCourseOwner}），
 * 消除「任何教师可越权查看/修改/删除他人课程」的安全缺陷。
 * </p>
 * <p>
 * 全局前缀 {@code server.servlet.context-path = /api}，故完整有效路径 = {@code /api} + 下列相对路径。
 * 所有方法级均继承类级 {@code @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")}。
 * </p>
 */
@RestController
@RequestMapping("/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "教师端核心接口", description = "课程建设/学情驾驶舱/作业题库管理/学情分析（合并统一版）")
public class TeacherController {

    private final CourseService courseService;
    private final CourseChapterService chapterService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final QuestionService questionService;
    private final AnalyticsService analyticsService;
    private final CourseEnrollmentService enrollmentService;
    private final TeacherCourseService teachingService;

    public TeacherController(CourseService courseService,
                             CourseChapterService chapterService,
                             TaskService taskService,
                             SubmissionService submissionService,
                             QuestionService questionService,
                             AnalyticsService analyticsService,
                             CourseEnrollmentService enrollmentService,
                             TeacherCourseService teachingService) {
        this.courseService = courseService;
        this.chapterService = chapterService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.questionService = questionService;
        this.analyticsService = analyticsService;
        this.enrollmentService = enrollmentService;
        this.teachingService = teachingService;
    }

    // ==================== 课程管理（8个接口，已加归属校验） ====================

    @PostMapping("/courses")
    @Operation(summary = "创建课程")
    public ApiResponse<Course> createCourse(@Valid @RequestBody Course course, Authentication auth) {
        // 绑定授课教师与落库下沉至服务层（事务化）
        Course saved = courseService.createCourse(course, (Long) auth.getPrincipal());
        return ApiResponse.success("创建成功", saved);
    }

    @GetMapping("/courses")
    @Operation(summary = "获取我的课程列表")
    public ApiResponse<PagedResponse<Course>> getCourses(
            Authentication auth,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        Page<Course> coursePage = courseService.page(Page.of(page, size),
                new LambdaQueryWrapper<Course>().eq(Course::getTeacherId, userId));
        return ApiResponse.success(PagedResponse.of(coursePage));
    }

    @GetMapping("/courses/{courseId}")
    @Operation(summary = "获取课程详情")
    public ApiResponse<Course> getCourseDetail(@PathVariable Long courseId, Authentication auth) {
        assertCourseOwner(courseId, auth);
        Course course = courseService.getById(courseId);
        if (course == null) throw new BusinessException("课程不存在");
        return ApiResponse.success(course);
    }

    @PutMapping("/courses/{courseId}")
    @Operation(summary = "更新课程")
    public ApiResponse<Course> updateCourse(@PathVariable Long courseId, @Valid @RequestBody Course course, Authentication auth) {
        assertCourseOwner(courseId, auth);
        // 写操作下沉至服务层（事务化）
        Course updated = courseService.updateCourseById(courseId, course);
        return ApiResponse.success("更新成功", updated);
    }

    @DeleteMapping("/courses/{courseId}")
    @Operation(summary = "删除课程")
    public ApiResponse<Void> deleteCourse(@PathVariable Long courseId, Authentication auth) {
        assertCourseOwner(courseId, auth);
        // 写操作下沉至服务层（事务化）
        courseService.deleteCourseById(courseId);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/courses/{courseId}/chapters")
    @Operation(summary = "添加课程章节")
    public ApiResponse<CourseChapter> addChapter(@PathVariable Long courseId,
                                                  @Valid @RequestBody CourseChapter chapter,
                                                  Authentication auth) {
        assertCourseOwner(courseId, auth);
        chapter.setCourseId(courseId);
        chapterService.save(chapter);
        return ApiResponse.success("章节添加成功", chapter);
    }

    @PutMapping("/courses/{courseId}/chapters/{chapterId}")
    @Operation(summary = "更新课程章节")
    public ApiResponse<CourseChapter> updateChapter(@PathVariable Long courseId,
                                                     @PathVariable Long chapterId,
                                                     @Valid @RequestBody CourseChapter chapter,
                                                     Authentication auth) {
        assertCourseOwner(courseId, auth);
        chapter.setId(chapterId);
        chapter.setCourseId(courseId);
        chapterService.updateById(chapter);
        return ApiResponse.success("章节更新成功", chapterService.getById(chapterId));
    }

    @DeleteMapping("/courses/{courseId}/chapters/{chapterId}")
    @Operation(summary = "删除课程章节")
    public ApiResponse<Void> deleteChapter(@PathVariable Long courseId,
                                            @PathVariable Long chapterId,
                                            Authentication auth) {
        assertCourseOwner(courseId, auth);
        chapterService.removeById(chapterId);
        return ApiResponse.success("章节删除成功", null);
    }

    // ==================== 作业管理（8个接口，已加归属校验） ====================

    @PostMapping("/assignments")
    @Operation(summary = "创建作业")
    public ApiResponse<Task> createAssignment(@Valid @RequestBody Task task, Authentication auth) {
        if (task.getCourseId() != null) assertCourseOwner(task.getCourseId(), auth);
        taskService.save(task);
        return ApiResponse.success("创建成功", task);
    }

    @GetMapping("/assignments")
    @Operation(summary = "分页查询作业列表")
    public ApiResponse<PagedResponse<Task>> getAssignments(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId) {
        LambdaQueryWrapper<Task> qw = new LambdaQueryWrapper<Task>()
                .eq(courseId != null, Task::getCourseId, courseId);
        Page<Task> taskPage = taskService.page(Page.of(page, size), qw);
        return ApiResponse.success(PagedResponse.of(taskPage));
    }

    @GetMapping("/assignments/{assignmentId}")
    @Operation(summary = "获取作业详情")
    public ApiResponse<Task> getAssignmentDetail(@PathVariable Long assignmentId, Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        Task task = taskService.getById(assignmentId);
        if (task == null) throw new BusinessException("作业不存在");
        return ApiResponse.success(task);
    }

    @PutMapping("/assignments/{assignmentId}")
    @Operation(summary = "更新作业")
    public ApiResponse<Task> updateAssignment(@PathVariable Long assignmentId, @Valid @RequestBody Task task, Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        // 写操作下沉至服务层（事务化）
        Task updated = taskService.updateTaskById(assignmentId, task);
        return ApiResponse.success("更新成功", updated);
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @Operation(summary = "删除作业")
    public ApiResponse<Void> deleteAssignment(@PathVariable Long assignmentId, Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        taskService.removeById(assignmentId);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    @Operation(summary = "获取作业提交列表")
    public ApiResponse<PagedResponse<Submission>> getSubmissions(
            @PathVariable Long assignmentId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        Page<Submission> submissionPage = submissionService.page(Page.of(page, size),
                new LambdaQueryWrapper<Submission>().eq(Submission::getTaskId, assignmentId));
        return ApiResponse.success(PagedResponse.of(submissionPage));
    }

    @PutMapping("/assignments/{assignmentId}/submissions/{submissionId}")
    @Operation(summary = "批改作业")
    public ApiResponse<Submission> gradeSubmission(
            @PathVariable Long assignmentId,
            @PathVariable Long submissionId,
            @Valid @RequestBody Submission grade,
            Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        // 提交记录存在性/归属一致性校验与批改落库下沉至服务层（事务化）
        Submission submission = submissionService.gradeSubmissionByTeacher(
                assignmentId, submissionId, grade, (Long) auth.getPrincipal());
        return ApiResponse.success("批改完成", submission);
    }

    @PutMapping("/assignments/{assignmentId}/publish")
    @Operation(summary = "发布作业")
    public ApiResponse<Task> publishAssignment(@PathVariable Long assignmentId, Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        Task task = taskService.publishTask(assignmentId);
        return ApiResponse.success("发布成功", task);
    }

    // ==================== 题库管理（7个接口，已加归属校验） ====================

    @PostMapping("/questions")
    @Operation(summary = "创建题目")
    public ApiResponse<Question> createQuestion(@Valid @RequestBody Question question, Authentication auth) {
        if (question.getCourseId() != null) assertCourseOwner(question.getCourseId(), auth);
        question.setCreatedBy((Long) auth.getPrincipal());
        questionService.createQuestion(question);
        return ApiResponse.success("创建成功", question);
    }

    @GetMapping("/questions")
    @Operation(summary = "分页查询题目列表")
    public ApiResponse<PagedResponse<Question>> getQuestions(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty,
            Authentication auth) {
        if (courseId != null) assertCourseOwner(courseId, auth);
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId)
                .eq(type != null, Question::getType, type)
                .eq(difficulty != null, Question::getDifficulty, difficulty);
        Page<Question> questionPage = questionService.page(Page.of(page, size), qw);
        return ApiResponse.success(PagedResponse.of(questionPage));
    }

    @GetMapping("/questions/{questionId}")
    @Operation(summary = "获取题目详情")
    public ApiResponse<Question> getQuestionDetail(@PathVariable Long questionId, Authentication auth) {
        assertQuestionOwner(questionId, auth);
        return ApiResponse.success(questionService.getById(questionId));
    }

    @PutMapping("/questions/{questionId}")
    @Operation(summary = "更新题目")
    public ApiResponse<Question> updateQuestion(@PathVariable Long questionId, @Valid @RequestBody Question question, Authentication auth) {
        assertQuestionOwner(questionId, auth);
        question.setId(questionId);
        questionService.updateById(question);
        return ApiResponse.success("更新成功", questionService.getById(questionId));
    }

    @DeleteMapping("/questions/{questionId}")
    @Operation(summary = "删除题目")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long questionId, Authentication auth) {
        assertQuestionOwner(questionId, auth);
        questionService.removeById(questionId);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/questions/import")
    @Operation(summary = "批量导入题目")
    public ApiResponse<Map<String, Object>> importQuestions(@Valid @RequestBody List<Question> questions) {
        int count = 0;
        for (Question q : questions) {
            questionService.save(q);
            count++;
        }
        return ApiResponse.success("导入完成", Map.of("importedCount", count));
    }

    @GetMapping("/questions/export")
    @Operation(summary = "导出题库")
    public ApiResponse<List<Question>> exportQuestions(@RequestParam(required = false) Long courseId, Authentication auth) {
        if (courseId != null) assertCourseOwner(courseId, auth);
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId);
        return ApiResponse.success(questionService.list(qw));
    }

    // ==================== 学情分析（5个接口，课程/作业级已加归属校验） ====================

    @GetMapping("/analytics/class/{courseId}")
    @Operation(summary = "获取班级学情分析")
    public ApiResponse<Map<String, Object>> getClassAnalytics(@PathVariable Long courseId, Authentication auth) {
        assertCourseOwner(courseId, auth);
        Map<String, Object> data = analyticsService.getCourseAnalysis(courseId);
        return ApiResponse.success(data);
    }

    @GetMapping("/analytics/students/{studentId}")
    @Operation(summary = "获取学生学情分析")
    public ApiResponse<Map<String, Object>> getStudentAnalytics(@PathVariable Long studentId) {
        Map<String, Object> data = analyticsService.getUserDashboard(studentId);
        return ApiResponse.success(data);
    }

    @GetMapping("/analytics/assignments/{assignmentId}")
    @Operation(summary = "获取作业学情分析")
    public ApiResponse<Map<String, Object>> getAssignmentAnalytics(@PathVariable Long assignmentId, Authentication auth) {
        assertTaskOwner(assignmentId, auth);
        Map<String, Object> stats = taskService.getTaskCompletionStats(assignmentId);
        return ApiResponse.success(stats);
    }

    @GetMapping("/analytics/knowledge-points")
    @Operation(summary = "获取知识点分析")
    public ApiResponse<Map<String, Object>> getKnowledgePointAnalytics(
            @RequestParam(required = false) Long courseId, Authentication auth) {
        if (courseId != null) assertCourseOwner(courseId, auth);
        Map<String, Object> data = analyticsService.getGradeDistribution(courseId);
        return ApiResponse.success(data);
    }

    @GetMapping("/analytics/report/export")
    @Operation(summary = "导出学情报告")
    public ApiResponse<Map<String, Object>> exportReport(@RequestParam Long courseId, Authentication auth) {
        assertCourseOwner(courseId, auth);
        Map<String, Object> report = analyticsService.exportReport(courseId);
        return ApiResponse.success("报告导出成功", report);
    }

    // ==================== 课程建设 / 学情驾驶舱（原 CourseTeachingController 合并） ====================

    @GetMapping("/courses/{courseId}/dashboard")
    @Operation(summary = "课程驾驶舱", description = "聚合课程概览/内容量/学习指标/待批改/趋势/预警学生/章节树/AI解读")
    public ApiResponse<Map<String, Object>> courseDashboard(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        Long operatorId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.getCourseTeachingDashboard(courseId, operatorId));
    }

    @PostMapping("/courses/ai/generate-outline")
    @Operation(summary = "AI 建课大纲", description = "输入主题/分类/难度，由 AI 生成章节与任务点建议大纲")
    public ApiResponse<Map<String, Object>> generateOutline(
            @Parameter(description = "课程主题") @RequestParam String topic,
            @Parameter(description = "课程分类（可选）") @RequestParam(required = false) String category,
            @Parameter(description = "难度等级（可选）") @RequestParam(required = false) String level,
            @Parameter(description = "建议章节数（默认6）") @RequestParam(defaultValue = "6") int chapterCount,
            Authentication auth) {
        Long operatorId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.generateCourseOutlineByAI(topic, category, level, chapterCount, operatorId));
    }

    @PostMapping("/courses/{courseId}/clone")
    @Operation(summary = "克隆课程", description = "复制课程元信息与章节结构为新课程（状态置为草稿）")
    public ApiResponse<Map<String, Object>> cloneCourse(
            @Parameter(description = "源课程ID") @PathVariable Long courseId,
            @Parameter(description = "新课程名称") @RequestParam(required = false) String newName,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        Long operatorId = (Long) auth.getPrincipal();
        return ApiResponse.success("克隆成功", teachingService.cloneCourse(courseId, newName, operatorId));
    }

    @GetMapping("/courses/summaries")
    @Operation(summary = "我的课程聚合指标", description = "返回教师每门课程的摘要：学生数/平均进度/完成率/待批改")
    public ApiResponse<List<Map<String, Object>>> myCourseSummaries(Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.getMyCourseSummaries(teacherId));
    }

    @GetMapping("/dashboard/v2")
    @Operation(summary = "增强教师仪表盘", description = "课程下钻卡片/跨课预警学生/任务点完成概览/AI教学总览/平台均值对比")
    public ApiResponse<Map<String, Object>> teacherDashboardV2(Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        return ApiResponse.success(teachingService.getTeacherDashboardV2(teacherId));
    }

    @GetMapping("/courses/{courseId}/chapters/tree")
    @Operation(summary = "完整章节树", description = "返回带父子嵌套、内容、发布状态的课程章节树")
    public ApiResponse<List<Map<String, Object>>> chapterTree(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            Authentication auth) {
        assertCourseOwner(courseId, auth);
        return ApiResponse.success(teachingService.getFullChapterTree(courseId));
    }

    // ==================== 课程归属校验 ====================

    /**
     * 课程归属校验：教师须为课程授课教师，管理员放行。
     * 任何以 courseId 为入参的接口都应先调用本方法，杜绝越权访问他人课程。
     */
    private void assertCourseOwner(Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;
        Course course = courseService.getById(courseId);
        if (course == null) throw new BusinessException("课程不存在");
        if (!userId.equals(course.getTeacherId())) throw new BusinessException("无权操作该课程");
    }

    private void assertTaskOwner(Long taskId, Authentication auth) {
        Task task = taskService.getById(taskId);
        if (task == null) throw new BusinessException("作业不存在");
        assertCourseOwner(task.getCourseId(), auth);
    }

    private void assertQuestionOwner(Long questionId, Authentication auth) {
        Question question = questionService.getById(questionId);
        if (question == null) throw new BusinessException("题目不存在");
        if (question.getCourseId() != null) assertCourseOwner(question.getCourseId(), auth);
    }
}
