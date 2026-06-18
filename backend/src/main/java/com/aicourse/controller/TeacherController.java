package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师端接口控制器
 * 提供课程管理、作业管理、题库管理、学情分析等教师专属RESTful API
 */
@RestController
@RequestMapping("/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TeacherController {

    private final CourseService courseService;
    private final CourseChapterService chapterService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final QuestionService questionService;
    private final AnalyticsService analyticsService;
    private final CourseEnrollmentService enrollmentService;

    public TeacherController(CourseService courseService,
                             CourseChapterService chapterService,
                             TaskService taskService,
                             SubmissionService submissionService,
                             QuestionService questionService,
                             AnalyticsService analyticsService,
                             CourseEnrollmentService enrollmentService) {
        this.courseService = courseService;
        this.chapterService = chapterService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.questionService = questionService;
        this.analyticsService = analyticsService;
        this.enrollmentService = enrollmentService;
    }

    // ==================== 课程管理（8个接口） ====================

    @PostMapping("/courses")
    public ApiResponse<Course> createCourse(@RequestBody Course course, Authentication auth) {
        course.setTeacherId((Long) auth.getPrincipal());
        courseService.save(course);
        return ApiResponse.success("创建成功", course);
    }

    @GetMapping("/courses")
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
    public ApiResponse<Course> getCourseDetail(@PathVariable Long courseId) {
        Course course = courseService.getById(courseId);
        if (course == null) throw new BusinessException("课程不存在");
        return ApiResponse.success(course);
    }

    @PutMapping("/courses/{courseId}")
    public ApiResponse<Course> updateCourse(@PathVariable Long courseId, @RequestBody Course course) {
        course.setId(courseId);
        courseService.updateById(course);
        return ApiResponse.success("更新成功", courseService.getById(courseId));
    }

    @DeleteMapping("/courses/{courseId}")
    public ApiResponse<Void> deleteCourse(@PathVariable Long courseId) {
        courseService.removeById(courseId);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/courses/{courseId}/chapters")
    public ApiResponse<CourseChapter> addChapter(@PathVariable Long courseId,
                                                  @RequestBody CourseChapter chapter) {
        chapter.setCourseId(courseId);
        chapterService.save(chapter);
        return ApiResponse.success("章节添加成功", chapter);
    }

    @PutMapping("/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<CourseChapter> updateChapter(@PathVariable Long courseId,
                                                     @PathVariable Long chapterId,
                                                     @RequestBody CourseChapter chapter) {
        chapter.setId(chapterId);
        chapter.setCourseId(courseId);
        chapterService.updateById(chapter);
        return ApiResponse.success("章节更新成功", chapterService.getById(chapterId));
    }

    @DeleteMapping("/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(@PathVariable Long courseId,
                                            @PathVariable Long chapterId) {
        chapterService.removeById(chapterId);
        return ApiResponse.success("章节删除成功", null);
    }

    // ==================== 作业管理（8个接口） ====================

    @PostMapping("/assignments")
    public ApiResponse<Task> createAssignment(@RequestBody Task task) {
        taskService.save(task);
        return ApiResponse.success("创建成功", task);
    }

    @GetMapping("/assignments")
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
    public ApiResponse<Task> getAssignmentDetail(@PathVariable Long assignmentId) {
        Task task = taskService.getById(assignmentId);
        if (task == null) throw new BusinessException("作业不存在");
        return ApiResponse.success(task);
    }

    @PutMapping("/assignments/{assignmentId}")
    public ApiResponse<Task> updateAssignment(@PathVariable Long assignmentId, @RequestBody Task task) {
        task.setId(assignmentId);
        taskService.updateById(task);
        return ApiResponse.success("更新成功", taskService.getById(assignmentId));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public ApiResponse<Void> deleteAssignment(@PathVariable Long assignmentId) {
        taskService.removeById(assignmentId);
        return ApiResponse.success("删除成功", null);
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    public ApiResponse<PagedResponse<Submission>> getSubmissions(
            @PathVariable Long assignmentId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Page<Submission> submissionPage = submissionService.page(Page.of(page, size),
                new LambdaQueryWrapper<Submission>().eq(Submission::getTaskId, assignmentId));
        return ApiResponse.success(PagedResponse.of(submissionPage));
    }

    @PutMapping("/assignments/{assignmentId}/submissions/{submissionId}")
    public ApiResponse<Submission> gradeSubmission(
            @PathVariable Long assignmentId,
            @PathVariable Long submissionId,
            @RequestBody Submission grade,
            Authentication auth) {
        Submission submission = submissionService.getById(submissionId);
        if (submission == null) throw new BusinessException("提交记录不存在");
        submission.setScore(grade.getScore());
        submission.setFeedback(grade.getFeedback());
        submission.setStatus("GRADED");
        submission.setGradedAt(LocalDateTime.now());
        submission.setGraderId((Long) auth.getPrincipal());
        submissionService.updateById(submission);
        return ApiResponse.success("批改完成", submission);
    }

    @PutMapping("/assignments/{assignmentId}/publish")
    public ApiResponse<Task> publishAssignment(@PathVariable Long assignmentId) {
        Task task = taskService.publishTask(assignmentId);
        return ApiResponse.success("发布成功", task);
    }

    // ==================== 题库管理（7个接口） ====================

    @PostMapping("/questions")
    public ApiResponse<Question> createQuestion(@RequestBody Question question, Authentication auth) {
        question.setCreatedBy((Long) auth.getPrincipal());
        questionService.createQuestion(question);
        return ApiResponse.success("创建成功", question);
    }

    @GetMapping("/questions")
    public ApiResponse<PagedResponse<Question>> getQuestions(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty) {
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId)
                .eq(type != null, Question::getType, type)
                .eq(difficulty != null, Question::getDifficulty, difficulty);
        Page<Question> questionPage = questionService.page(Page.of(page, size), qw);
        return ApiResponse.success(PagedResponse.of(questionPage));
    }

    @GetMapping("/questions/{questionId}")
    public ApiResponse<Question> getQuestionDetail(@PathVariable Long questionId) {
        return ApiResponse.success(questionService.getById(questionId));
    }

    @PutMapping("/questions/{questionId}")
    public ApiResponse<Question> updateQuestion(@PathVariable Long questionId, @RequestBody Question question) {
        question.setId(questionId);
        questionService.updateById(question);
        return ApiResponse.success("更新成功", questionService.getById(questionId));
    }

    @DeleteMapping("/questions/{questionId}")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long questionId) {
        questionService.removeById(questionId);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/questions/import")
    public ApiResponse<Map<String, Object>> importQuestions(@RequestBody List<Question> questions) {
        int count = 0;
        for (Question q : questions) {
            questionService.save(q);
            count++;
        }
        return ApiResponse.success("导入完成", Map.of("importedCount", count));
    }

    @GetMapping("/questions/export")
    public ApiResponse<List<Question>> exportQuestions(@RequestParam(required = false) Long courseId) {
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<Question>()
                .eq(courseId != null, Question::getCourseId, courseId);
        return ApiResponse.success(questionService.list(qw));
    }

    // ==================== 学情分析（5个接口） ====================

    @GetMapping("/analytics/class/{courseId}")
    public ApiResponse<Map<String, Object>> getClassAnalytics(@PathVariable Long courseId) {
        Map<String, Object> data = analyticsService.getCourseAnalysis(courseId);
        return ApiResponse.success(data);
    }

    @GetMapping("/analytics/students/{studentId}")
    public ApiResponse<Map<String, Object>> getStudentAnalytics(@PathVariable Long studentId) {
        Map<String, Object> data = analyticsService.getUserDashboard(studentId);
        return ApiResponse.success(data);
    }

    @GetMapping("/analytics/assignments/{assignmentId}")
    public ApiResponse<Map<String, Object>> getAssignmentAnalytics(@PathVariable Long assignmentId) {
        Map<String, Object> stats = taskService.getTaskCompletionStats(assignmentId);
        return ApiResponse.success(stats);
    }

    @GetMapping("/analytics/knowledge-points")
    public ApiResponse<Map<String, Object>> getKnowledgePointAnalytics(
            @RequestParam(required = false) Long courseId) {
        Map<String, Object> data = analyticsService.getGradeDistribution(courseId);
        return ApiResponse.success(data);
    }

    @GetMapping("/analytics/report/export")
    public ApiResponse<Map<String, Object>> exportReport(@RequestParam Long courseId) {
        Map<String, Object> report = analyticsService.exportReport(courseId);
        return ApiResponse.success("报告导出成功", report);
    }
}