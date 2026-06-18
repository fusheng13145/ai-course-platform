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
 * 学生端接口控制器
 * 提供学习中心、作业管理、错题本、学习进度等学生专属RESTful API
 */
@RestController
@RequestMapping("/student")
@PreAuthorize("hasRole('STUDENT')")
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

    /**
     * 更新学习进度
     */
    @PostMapping("/courses/{courseId}/progress")
    public ApiResponse<Void> updateProgress(
            @PathVariable Long courseId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        CourseEnrollment enrollment = enrollmentService.getOne(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getStudentId, userId)
                        .eq(CourseEnrollment::getCourseId, courseId));
        if (enrollment == null) {
            throw new BusinessException("未加入该课程");
        }
        if (body.containsKey("progress")) {
            enrollment.setProgress(java.math.BigDecimal.valueOf(Double.parseDouble(body.get("progress").toString())));
        }
        enrollmentService.updateById(enrollment);
        return ApiResponse.success("进度更新成功", null);
    }

    /**
     * 加入课程
     */
    @PostMapping("/courses/{courseId}/enroll")
    public ApiResponse<Void> enrollCourse(@PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        CourseEnrollment existing = enrollmentService.getOne(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getStudentId, userId)
                        .eq(CourseEnrollment::getCourseId, courseId));
        if (existing != null) {
            throw new BusinessException("已加入该课程");
        }
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId(userId);
        enrollment.setCourseId(courseId);
        enrollment.setIsActive(true);
        enrollment.setApprovalStatus("APPROVED");
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollmentService.save(enrollment);
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
     * 提交作业
     */
    @PostMapping("/assignments/{assignmentId}/submit")
    public ApiResponse<Submission> submitAssignment(
            @PathVariable Long assignmentId,
            @RequestBody Map<String, Object> body,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Task task = taskService.getById(assignmentId);
        if (task == null) {
            throw new BusinessException("作业不存在");
        }
        Submission submission = new Submission();
        submission.setTaskId(assignmentId);
        submission.setStudentId(userId);
        submission.setContent(body.get("content") != null ? body.get("content").toString() : null);
        submission.setStatus("SUBMITTED");
        submission.setSubmittedAt(LocalDateTime.now());
        submissionService.save(submission);
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
            @RequestBody Map<String, String> params, Authentication auth) {
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
            @RequestBody Map<String, Object> params, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long courseId = params.get("courseId") != null ? Long.valueOf(params.get("courseId").toString()) : null;
        int count = params.containsKey("count") ? Integer.parseInt(params.get("count").toString()) : 10;
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