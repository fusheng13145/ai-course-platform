package com.aicourse.controller;

import com.aicourse.application.student.StudentLearningApplicationService;
import com.aicourse.application.submission.SubmissionApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.AssignmentSubmitDTO;
import com.aicourse.dto.PracticeGenerateDTO;
import com.aicourse.entity.Question;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.LearningRecordService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.vo.AssignmentDetailVO;
import com.aicourse.vo.CourseVOs;
import com.aicourse.vo.DailyLearningStatVO;
import com.aicourse.vo.SubmissionVO;
import com.aicourse.vo.TaskVO;
import com.aicourse.vo.TeachingClassOptionVO;
import com.aicourse.vo.WrongQuestionVOs;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 学生端接口控制器
 * 提供学习中心、作业管理、错题本、学习进度等学生专属RESTful API
 */
@RestController
@RequestMapping("/student")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "学生端")
public class StudentController {

    private final CourseEnrollmentService enrollmentService;
    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final WrongQuestionService wrongQuestionService;
    private final LearningRecordService learningRecordService;
    private final CourseAccessControl courseAccessControl;
    private final SubmissionApplicationService submissionApplicationService;
    private final StudentLearningApplicationService studentLearningApplicationService;

    public StudentController(CourseEnrollmentService enrollmentService,
                             TaskService taskService,
                             SubmissionService submissionService,
                             WrongQuestionService wrongQuestionService,
                             LearningRecordService learningRecordService,
                             CourseAccessControl courseAccessControl,
                             SubmissionApplicationService submissionApplicationService,
                             StudentLearningApplicationService studentLearningApplicationService) {
        this.enrollmentService = enrollmentService;
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.wrongQuestionService = wrongQuestionService;
        this.learningRecordService = learningRecordService;
        this.courseAccessControl = courseAccessControl;
        this.submissionApplicationService = submissionApplicationService;
        this.studentLearningApplicationService = studentLearningApplicationService;
    }

    // ==================== 学习中心（5个接口） ====================

    /**
     * 获取我的课程列表（仅审批通过且有效的选课，V14 / WP5）
     */
    @GetMapping("/courses")
    public ApiResponse<PagedResponse<CourseVOs.CourseVO>> getMyCourses(
            Authentication auth,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(studentLearningApplicationService.myCourses(userId, page, size));
    }

    /**
     * 获取课程学习详情
     */
    @GetMapping("/courses/{courseId}")
    public ApiResponse<com.aicourse.vo.CourseDetailVO> getCourseDetail(
            @PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(studentLearningApplicationService.courseDetail(userId, courseId));
    }

    // 进度不再由客户端写入（安全修复 P0-1）：学生可把任意课程进度改成 100%。
    // 进度改由后端基于任务点完成度聚合，详见 StudentLearningController GET /courses/{courseId}/progress-summary。

    /**
     * 加入课程前可选的启用教学班（V28：学生按教学班选课）。
     */
    @GetMapping("/courses/{courseId}/teaching-classes")
    public ApiResponse<List<TeachingClassOptionVO>> availableTeachingClasses(@PathVariable Long courseId) {
        return ApiResponse.success(studentLearningApplicationService.availableTeachingClasses(courseId));
    }

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
        // 统一状态机：按课程找到有效记录后置 CANCELLED，避免按 id 绕开归属或状态校验
        enrollmentService.cancelByCourse(userId, courseId, "学生主动退课");
        return ApiResponse.success("已退出课程", null);
    }

    // ==================== 作业管理（5个接口） ====================

    /**
     * 获取待完成作业
     * @param courseId 课程ID（可选，指定时仅返回该课程的待完成作业）
     */
    @GetMapping("/assignments/pending")
    public ApiResponse<List<TaskVO>> getPendingAssignments(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(studentLearningApplicationService.pendingAssignments(userId, courseId));
    }

    /**
     * 获取已完成作业
     * @param courseId 课程ID（可选，指定时仅返回该课程的已提交作业）
     */
    @GetMapping("/assignments/completed")
    public ApiResponse<List<SubmissionVO>> getCompletedAssignments(
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(studentLearningApplicationService.completedAssignments(userId, courseId));
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
    public ApiResponse<SubmissionVO> submitAssignment(
            @PathVariable Long assignmentId,
            @Valid @RequestBody AssignmentSubmitDTO dto,
            Authentication auth) {
        // 与 POST /submissions 同一段用例编排（application 层）：任务存在性 + 课程可读 + 异步预批改。
        Submission submission = submissionApplicationService.submitForAssignment(assignmentId, dto, auth);
        return ApiResponse.success("提交成功", SubmissionVO.from(submission));
    }

    /**
     * 获取作业批改结果
     */
    @GetMapping("/assignments/{assignmentId}/result")
    public ApiResponse<SubmissionVO> getAssignmentResult(
            @PathVariable Long assignmentId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Submission submission = submissionService.getByTaskAndStudent(assignmentId, userId);
        return ApiResponse.success(SubmissionVO.from(submission));
    }

    // ==================== 错题本（5个接口） ====================

    /**
     * 获取错题列表
     */
    @GetMapping("/wrong-questions")
    public ApiResponse<PagedResponse<WrongQuestionVOs.WrongQuestionVO>> getWrongQuestions(
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(studentLearningApplicationService.wrongQuestionPage(userId, courseId, page, size));
    }

    /**
     * 添加到错题本
     */
    @PostMapping("/wrong-questions")
    public ApiResponse<WrongQuestionVOs.WrongQuestionVO> addWrongQuestion(
            @Valid @RequestBody Map<String, String> params, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long questionId = Long.valueOf(params.get("questionId"));
        Long courseId = params.get("courseId") != null ? Long.valueOf(params.get("courseId")) : null;
        String wrongAnswer = params.get("wrongAnswer");
        String sourceType = params.getOrDefault("sourceType", "PRACTICE");
        WrongQuestion wq = wrongQuestionService.recordWrongAnswer(userId, questionId, courseId, wrongAnswer, sourceType);
        return ApiResponse.success("添加成功", WrongQuestionVOs.WrongQuestionVO.from(wq));
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
    public ApiResponse<List<WrongQuestionVOs.PracticeQuestionVO>> generatePractice(
            @Valid @RequestBody PracticeGenerateDTO dto, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long courseId = dto.getCourseId();
        int count = dto.getCount() != null ? dto.getCount() : 10;
        List<Question> questions = wrongQuestionService.generatePracticePaper(userId, courseId, count);
        return ApiResponse.success(WrongQuestionVOs.PracticeQuestionVO.from(questions));
    }

    // ==================== 学习进度（3个接口） ====================

    /**
     * 获取学习概览
     */
    @GetMapping("/progress/overview")
    public ApiResponse<Map<String, Object>> getProgressOverview(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(learningRecordService.getLearningStats(userId));
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
        return ApiResponse.success(learningRecordService.getMonthlyLearningData(userId, year, month));
    }

    /**
     * 获取学习统计
     */
    @GetMapping("/progress/statistics")
    public ApiResponse<List<DailyLearningStatVO>> getProgressStatistics(
            Authentication auth,
            @RequestParam(defaultValue = "7") int days) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(learningRecordService.getLearningTrend(userId, days));
    }
}
