package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.AssignmentSubmitDTO;
import com.aicourse.dto.PracticeGenerateDTO;
import com.aicourse.entity.*;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.*;
import com.aicourse.vo.AssignmentDetailVO;
import com.aicourse.vo.CourseDetailVO;
import com.aicourse.vo.DailyLearningStatVO;
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

    private static final Logger log = LoggerFactory.getLogger(StudentController.class);

    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final WrongQuestionService wrongQuestionService;
    private final LearningRecordService learningRecordService;
    private final CourseAccessControl courseAccessControl;

    public StudentController(CourseService courseService,
                             CourseEnrollmentService enrollmentService,
                             TaskService taskService,
                             SubmissionService submissionService,
                             WrongQuestionService wrongQuestionService,
                             LearningRecordService learningRecordService,
                             CourseAccessControl courseAccessControl) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.wrongQuestionService = wrongQuestionService;
        this.learningRecordService = learningRecordService;
        this.courseAccessControl = courseAccessControl;
    }

    // ==================== 学习中心（5个接口） ====================

    /**
     * 获取我的课程列表（仅审批通过且有效的选课，V14 / WP5）
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
                        .eq(CourseEnrollment::getIsActive, true)
                        .eq(CourseEnrollment::getApprovalStatus, "APPROVED"));
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
    public ApiResponse<CourseDetailVO> getCourseDetail(
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
        return ApiResponse.success(new CourseDetailVO(course, enrollment));
    }

    // 进度不再由客户端写入（安全修复 P0-1）：学生可把任意课程进度改成 100%。
    // 进度改由后端基于任务点完成度聚合，详见 StudentLearningController GET /courses/{courseId}/progress-summary。

    /**
     * 加入课程（可指定教学班；未指定时自动选中课程下唯一启用的教学班）
     */
    @PostMapping("/courses/{courseId}/enroll")
    public ApiResponse<Void> enrollCourse(@PathVariable Long courseId,
                                          @RequestParam(required = false) Long teachingClassId,
                                          Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        // 课程存在性校验、重复加入校验、教学班解析与报名落库下沉至服务层（事务化）
        enrollmentService.enrollStudent(userId, courseId, teachingClassId);
        return ApiResponse.success("加入成功", null);
    }

    /**
     * 退出课程。
     * <p>2026-11-14 P1-3 退课语义统一：学生端此前硬删选课行（{@code removeById}），
     * 而服务层 {@code unenroll} 为软删（is_active=false，容量统计与历史留痕都依赖软删行）——
     * 学生退课绕过了容量口径并物理消失记录。现两入口统一走服务层软删。</p>
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
        enrollmentService.unenroll(enrollment.getId(), userId);
        return ApiResponse.success("已退出课程", null);
    }

    // ==================== 作业管理（5个接口） ====================

    /**
     * 获取待完成作业
     * @param courseId 课程ID（可选，指定时仅返回该课程的待完成作业）
     */
    @GetMapping("/assignments/pending")
    public ApiResponse<List<Task>> getPendingAssignments(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        // V14 口径对齐（2026-11-14）：有效选课 = is_active 且审批通过（此前漏了 APPROVED 过滤）
        List<CourseEnrollment> enrollments = enrollmentService.list(
                new LambdaQueryWrapper<CourseEnrollment>()
                        .eq(CourseEnrollment::getStudentId, userId)
                        .eq(CourseEnrollment::getIsActive, true)
                        .eq(CourseEnrollment::getApprovalStatus, "APPROVED"));
        if (enrollments.isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }
        List<Long> courseIds = enrollments.stream()
                .map(CourseEnrollment::getCourseId).collect(Collectors.toList());
        List<Task> tasks = taskService.list(
                new LambdaQueryWrapper<Task>()
                        // 指定课程时按课程精确过滤，否则在已加入课程范围内查询
                        .eq(courseId != null, Task::getCourseId, courseId)
                        .in(courseId == null, Task::getCourseId, courseIds)
                        .eq(Task::getStatus, "PUBLISHED")
                        .orderByDesc(Task::getCreatedAt));
        // 2026-11-14 P1-4：按「最新一次提交的状态」判定待办——此前只要有任意提交行即从
        // 待办剔除，被退回（RETURNED）待重做的作业从待办消失。现口径：无提交，或
        // 最新一次提交为 RETURNED（允许重交）→ 仍属待办；SUBMITTED/GRADED → 完成。
        Map<Long, Submission> latestByTask = new HashMap<>();
        for (Submission s : submissionService.list(
                new LambdaQueryWrapper<Submission>().eq(Submission::getStudentId, userId))) {
            Submission cur = latestByTask.get(s.getTaskId());
            int a = s.getAttemptNumber() != null ? s.getAttemptNumber() : 0;
            int b = cur != null && cur.getAttemptNumber() != null ? cur.getAttemptNumber() : -1;
            if (cur == null || a >= b) {
                latestByTask.put(s.getTaskId(), s);
            }
        }
        Set<Long> finishedTaskIds = latestByTask.values().stream()
                .filter(s -> s.getStatus() != null
                        && ("SUBMITTED".equals(s.getStatus()) || "GRADED".equals(s.getStatus())))
                .map(Submission::getTaskId)
                .collect(Collectors.toSet());
        tasks = tasks.stream()
                .filter(t -> !finishedTaskIds.contains(t.getId()))
                .collect(Collectors.toList());
        return ApiResponse.success(tasks);
    }

    /**
     * 获取已完成作业
     * @param courseId 课程ID（可选，指定时仅返回该课程的已提交作业）
     */
    @GetMapping("/assignments/completed")
    public ApiResponse<List<Submission>> getCompletedAssignments(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<Submission> submissions = submissionService.list(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getStudentId, userId)
                        .orderByDesc(Submission::getCreatedAt));
        // 指定课程时按作业归属课程过滤（作业→课程）
        if (courseId != null && !submissions.isEmpty()) {
            Set<Long> taskIds = submissions.stream()
                    .map(Submission::getTaskId).collect(Collectors.toSet());
            Map<Long, Long> taskCourseMap = taskService.listByIds(taskIds).stream()
                    .collect(Collectors.toMap(Task::getId, Task::getCourseId, (a, b) -> a));
            submissions = submissions.stream()
                    .filter(s -> courseId.equals(taskCourseMap.get(s.getTaskId())))
                    .collect(Collectors.toList());
        }
        return ApiResponse.success(submissions);
    }

    /**
     * 获取作业详情
     */
    @GetMapping("/assignments/{assignmentId}")
    public ApiResponse<AssignmentDetailVO> getAssignmentDetail(
            @PathVariable Long assignmentId, Authentication auth) {
        Task task = taskService.getById(assignmentId);
        if (task == null) {
            throw new BusinessException("作业不存在");
        }
        courseAccessControl.assertCanReadTask(task, auth);
        Long userId = (Long) auth.getPrincipal();
        Submission submission = submissionService.getByTaskAndStudent(assignmentId, userId);
        return ApiResponse.success(new AssignmentDetailVO(task, submission));
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
        Submission submission = submissionService.getByTaskAndStudent(assignmentId, userId);
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
     * 标记已掌握（修复 P1-2：带归属校验，防止越权操作他人错题）
     */
    @PutMapping("/wrong-questions/{id}/mastered")
    public ApiResponse<Void> markMastered(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        wrongQuestionService.markMasteredForStudent(id, userId);
        return ApiResponse.success("已标记为掌握", null);
    }

    /**
     * 移除错题（修复 P1-2：带归属校验，防止越权删除他人错题）
     */
    @DeleteMapping("/wrong-questions/{id}")
    public ApiResponse<Void> removeWrongQuestion(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        wrongQuestionService.deleteForStudent(id, userId);
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
    public ApiResponse<List<DailyLearningStatVO>> getProgressCalendar(
            Authentication auth,
            @RequestParam int year,
            @RequestParam int month) {
        Long userId = (Long) auth.getPrincipal();
        List<DailyLearningStatVO> data = learningRecordService.getMonthlyLearningData(userId, year, month);
        return ApiResponse.success(data);
    }

    /**
     * 获取学习统计
     */
    @GetMapping("/progress/statistics")
    public ApiResponse<List<DailyLearningStatVO>> getProgressStatistics(
            Authentication auth,
            @RequestParam(defaultValue = "7") int days) {
        Long userId = (Long) auth.getPrincipal();
        List<DailyLearningStatVO> trend = learningRecordService.getLearningTrend(userId, days);
        return ApiResponse.success(trend);
    }
}
