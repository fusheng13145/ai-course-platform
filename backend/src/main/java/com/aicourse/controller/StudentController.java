package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.AssignmentSubmitDTO;
import com.aicourse.dto.PracticeGenerateDTO;
import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 学生端接口控制器
 * 提供学习中心、作业管理、错题本、学习进度等学生专属RESTful API
 */
@RestController
@RequestMapping("/student")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "学生端")
public class StudentController {

    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final WrongQuestionService wrongQuestionService;
    private final LearningRecordService learningRecordService;

    public StudentController(CourseService courseService,
                             CourseEnrollmentService enrollmentService,
                             TaskService taskService,
                             SubmissionService submissionService,
                             WrongQuestionService wrongQuestionService,
                             LearningRecordService learningRecordService) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.wrongQuestionService = wrongQuestionService;
        this.learningRecordService = learningRecordService;
    }

    // ==================== 学习中心（5个接口） ====================

    /**
     * 获取我的课程列表
     */
    @GetMapping("/courses")
    public ApiResponse<PagedResponse<Course>> getMyCourses(
            Authentication auth,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        List<CourseEnrollment> enrollments = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getStudentId, userId)
                        .eq(CourseEnrollment::getIsActive, true));
        if (enrollments.isEmpty()) {
            return ApiResponse.success(PagedResponse.empty());
        }
        List<Long> courseIds = enrollments.stream()
                .map(CourseEnrollment::getCourseId)
                .collect(Collectors.toList());
        Page<Course> coursePage = courseService.page(Page.of(page, size),
                new LambdaQueryWrapper<Course>().in(Course::getId, courseIds));
        return ApiResponse.success(PagedResponse.of(coursePage));
    }

    /**
     * 获取课程学习详情
     */
    @GetMapping("/courses/{courseId}")
    public ApiResponse<Map<String, Object>> getCourseDetail(
            @PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        CourseEnrollment enrollment = enrollmentService.getOne(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getStudentId, userId)
                        .eq(CourseEnrollment::getCourseId, courseId));
        Map<String, Object> result = new HashMap<>();
        result.put("course", course);
        result.put("enrollment", enrollment);
        return ApiResponse.success(result);
    }

    // 进度不再由客户端写入（安全修复 P0-1）：学生可把任意课程进度改成 100%。
    // 进度改由后端基于任务点完成度聚合，详见 StudentLearningController GET /courses/{courseId}/progress-summary。

    /**
     * 加入课程
     */
    @PostMapping("/courses/{courseId}/enroll")
    public ApiResponse<Void> enrollCourse(@PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        // 课程存在性校验、重复加入校验与报名落库下沉至服务层（事务化）
        enrollmentService.enrollStudent(userId, courseId);
        return ApiResponse.success("加入成功", null);
    }

    /**
     * 退出课程
     */
    @DeleteMapping("/courses/{courseId}/enroll")
    public ApiResponse<Void> unenrollCourse(@PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        CourseEnrollment enrollment = enrollmentService.getOne(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getStudentId, userId)
                        .eq(CourseEnrollment::getCourseId, courseId));
        if (enrollment == null) {
            throw new BusinessException("未加入该课程");
        }
        enrollmentService.removeById(enrollment.getId());
        return ApiResponse.success("已退出课程", null);
    }

    // ==================== 作业管理（5个接口） ====================

    /**
     * 获取待完成作业
     */
    @GetMapping("/assignments/pending")
    public ApiResponse<List<Task>> getPendingAssignments(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<CourseEnrollment> enrollments = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getStudentId, userId)
                        .eq(CourseEnrollment::getIsActive, true));
        if (enrollments.isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }
        List<Long> courseIds = enrollments.stream()
                .map(CourseEnrollment::getCourseId).collect(Collectors.toList());
        List<Task> tasks = taskService.list(
                new LambdaQueryWrapper<Task>()
                        .in(Task::getCourseId, courseIds)
                        .eq(Task::getStatus, "PUBLISHED")
                        .orderByDesc(Task::getCreatedAt));
        // 过滤已提交的
        Set<Long> submittedTaskIds = submissionService.list(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getStudentId, userId))
                .stream().map(Submission::getTaskId).collect(Collectors.toSet());
        tasks = tasks.stream()
                .filter(t -> !submittedTaskIds.contains(t.getId()))
                .collect(Collectors.toList());
        return ApiResponse.success(tasks);
    }

    /**
     * 获取已完成作业
     */
    @GetMapping("/assignments/completed")
    public ApiResponse<List<Submission>> getCompletedAssignments(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<Submission> submissions = submissionService.list(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getStudentId, userId)
                        .orderByDesc(Submission::getCreatedAt));
        return ApiResponse.success(submissions);
    }

    /**
     * 获取作业详情
     */
    @GetMapping("/assignments/{assignmentId}")
    public ApiResponse<Map<String, Object>> getAssignmentDetail(
            @PathVariable Long assignmentId, Authentication auth) {
        Task task = taskService.getById(assignmentId);
        if (task == null) {
            throw new BusinessException("作业不存在");
        }
        Long userId = (Long) auth.getPrincipal();
        Submission submission = submissionService.getOne(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, assignmentId)
                        .eq(Submission::getStudentId, userId));
        Map<String, Object> result = new HashMap<>();
        result.put("assignment", task);
        result.put("submission", submission);
        return ApiResponse.success(result);
    }

    /**
     * 提交作业（统一收口到 SubmissionService，含发布校验/逾期扣分/查重触发，修复 P0-8 双轨问题）
     */
    @PostMapping("/assignments/{assignmentId}/submit")
    public ApiResponse<Submission> submitAssignment(
            @PathVariable Long assignmentId,
            @Valid @RequestBody AssignmentSubmitDTO dto,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Submission submission = submissionService.submitAssignment(assignmentId, userId, dto.getContent(), dto.getFileUrls());
        return ApiResponse.success("提交成功", submission);
    }

    /**
     * 获取作业批改结果
     */
    @GetMapping("/assignments/{assignmentId}/result")
    public ApiResponse<Submission> getAssignmentResult(
            @PathVariable Long assignmentId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Submission submission = submissionService.getOne(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, assignmentId)
                        .eq(Submission::getStudentId, userId));
        return ApiResponse.success(submission);
    }

    // ==================== 错题本（5个接口） ====================

    /**
     * 获取错题列表
     */
    @GetMapping("/wrong-questions")
    public ApiResponse<PagedResponse<WrongQuestion>> getWrongQuestions(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Page<WrongQuestion> result = wrongQuestionService.getWrongQuestionBook(userId, courseId, page, size);
        return ApiResponse.success(PagedResponse.of(result));
    }

    /**
     * 添加到错题本
     */
    @PostMapping("/wrong-questions")
    public ApiResponse<WrongQuestion> addWrongQuestion(
            @Valid @RequestBody Map<String, String> params, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long questionId = Long.valueOf(params.get("questionId"));
        Long courseId = params.get("courseId") != null ? Long.valueOf(params.get("courseId")) : null;
        String wrongAnswer = params.get("wrongAnswer");
        String sourceType = params.getOrDefault("sourceType", "PRACTICE");
        WrongQuestion wq = wrongQuestionService.recordWrongAnswer(userId, questionId, courseId, wrongAnswer, sourceType);
        return ApiResponse.success("添加成功", wq);
    }

    /**
     * 标记已掌握
     */
    @PutMapping("/wrong-questions/{id}/mastered")
    public ApiResponse<Void> markMastered(@PathVariable Long id) {
        wrongQuestionService.markMastered(id);
        return ApiResponse.success("已标记为掌握", null);
    }

    /**
     * 移除错题
     */
    @DeleteMapping("/wrong-questions/{id}")
    public ApiResponse<Void> removeWrongQuestion(@PathVariable Long id) {
        wrongQuestionService.removeById(id);
        return ApiResponse.success("已移除", null);
    }

    /**
     * 生成错题练习
     */
    @PostMapping("/wrong-questions/practice")
    public ApiResponse<List<Question>> generatePractice(
            @Valid @RequestBody PracticeGenerateDTO dto, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long courseId = dto.getCourseId();
        int count = dto.getCount() != null ? dto.getCount() : 10;
        List<Question> questions = wrongQuestionService.generatePracticePaper(userId, courseId, count);
        return ApiResponse.success(questions);
    }

    // ==================== 学习进度（3个接口） ====================

    /**
     * 获取学习概览
     */
    @GetMapping("/progress/overview")
    public ApiResponse<Map<String, Object>> getProgressOverview(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Map<String, Object> stats = learningRecordService.getLearningStats(userId);
        return ApiResponse.success(stats);
    }

    /**
     * 获取学习日历
     */
    @GetMapping("/progress/calendar")
    public ApiResponse<List<Map<String, Object>>> getProgressCalendar(
            Authentication auth,
            @RequestParam int year,
            @RequestParam int month) {
        Long userId = (Long) auth.getPrincipal();
        List<Map<String, Object>> data = learningRecordService.getMonthlyLearningData(userId, year, month);
        return ApiResponse.success(data);
    }

    /**
     * 获取学习统计
     */
    @GetMapping("/progress/statistics")
    public ApiResponse<List<Map<String, Object>>> getProgressStatistics(
            Authentication auth,
            @RequestParam(defaultValue = "7") int days) {
        Long userId = (Long) auth.getPrincipal();
        List<Map<String, Object>> trend = learningRecordService.getLearningTrend(userId, days);
        return ApiResponse.success(trend);
    }
}