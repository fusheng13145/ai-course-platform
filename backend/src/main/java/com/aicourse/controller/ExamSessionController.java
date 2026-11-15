package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ApiResponse;
import com.aicourse.dto.ExamManualGradeDTO;
import com.aicourse.dto.ExamSubmitDTO;
import com.aicourse.entity.ExamSession;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.ExamSessionAccessControl;
import com.aicourse.service.ExamSessionService;
import com.aicourse.vo.ActiveExamSessionVO;
import com.aicourse.vo.AutoGradeResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.aicourse.util.TrustedClientIpResolver;
import java.util.List;
import java.util.Map;

/**
 * 考试会话接口控制器
 * 提供考试的开始、自动保存、提交、阅卷、成绩报告等RESTful API
 */
@RestController
@RequestMapping("/exam-sessions")
@Tag(name = "考试会话管理", description = "提供考试开始、答案保存、试卷提交、自动/人工阅卷、成绩报告等功能")
public class ExamSessionController {

    private static final Logger log = LoggerFactory.getLogger(ExamSessionController.class);

    private final ExamSessionService examSessionService;
    private final ExamSessionAccessControl examSessionAccessControl;
    private final CourseAccessControl courseAccessControl;
    /** 监考 IP 单源（2026-11-14 P1-5）：直连形态下 getRemoteAddr 即对端地址，但生产置于
     * 反代之后恒为代理 IP，「IP 变化」作弊检测（detectCheating）将天然失效。改与限流/节流
     * 同一 {@link TrustedClientIpResolver}，由 app.security.trust-proxy-header 单点决定。 */
    private final TrustedClientIpResolver clientIpResolver;

    public ExamSessionController(ExamSessionService examSessionService,
                                 ExamSessionAccessControl examSessionAccessControl,
                                 CourseAccessControl courseAccessControl,
                                 TrustedClientIpResolver clientIpResolver) {
        this.examSessionService = examSessionService;
        this.examSessionAccessControl = examSessionAccessControl;
        this.courseAccessControl = courseAccessControl;
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * 开始一场考试（增强版，含完整校验）
     */
    @PostMapping("/start")
    @Operation(summary = "开始考试", description = "学生开始一场新的考试，系统会校验时间窗口和尝试次数限制")
    public ApiResponse<ExamSession> start(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "包含examPaperId（试卷ID）的JSON对象"
            ) @Valid @RequestBody Map<String, Long> body,
            Authentication auth,
            HttpServletRequest request) {
        Long examPaperId = body.get("examPaperId");
        Long studentId = (Long) auth.getPrincipal();
        String ipAddress = clientIpResolver.resolve(request);
        String userAgent = request.getHeader("User-Agent");

        // 开考前置校验：学生必须是该试卷所属课程的选课学生（修复未选课学生越权开考）
        examSessionAccessControl.assertExamStartable(examPaperId, auth);

        ExamSession session = examSessionService.startExam(examPaperId, studentId, ipAddress, userAgent);
        return ApiResponse.success("考试开始成功", session);
    }

    /**
     * 获取本场考试的题目列表（含该会话独立的题目乱序与选项乱序）
     */
    @GetMapping("/{id}/questions")
    @Operation(summary = "获取考试题目", description = "返回本场考试会话的题目列表，已应用题目乱序与选项乱序（防作弊）")
    public ApiResponse<List<Map<String, Object>>> questions(
            @Parameter(description = "考试会话ID") @PathVariable Long id,
            Authentication auth) {
        examSessionAccessControl.assertReadable(id, auth);
        List<Map<String, Object>> questions = examSessionService.getSessionQuestions(id);
        return ApiResponse.success(questions);
    }

    /**
     * 自动保存作答答案
     */
    @PostMapping("/{id}/auto-save")
    @Operation(summary = "自动保存答案", description = "在考试过程中定时或手动自动保存作答进度，防止意外丢失")
    public ApiResponse<Void> autoSave(
            @Parameter(description = "考试会话ID") @PathVariable Long id,
            Authentication auth,
            @Valid @RequestBody ExamSubmitDTO body) {
        examSessionAccessControl.assertOwnedByStudent(id, auth);
        examSessionService.autoSaveAnswers(id, body.getAnswers());
        return ApiResponse.success("答案保存成功", null);
    }

    /**
     * 提交考试答卷（增强版）
     */
    @PostMapping("/{id}/submit")
    @Operation(summary = "提交试卷", description = "学生正式提交考试答卷，提交后不可修改")
    public ApiResponse<ExamSession> submit(
            @Parameter(description = "考试会话ID") @PathVariable Long id,
            Authentication auth,
            @Valid @RequestBody ExamSubmitDTO body) {
        examSessionAccessControl.assertOwnedByStudent(id, auth);
        ExamSession session = examSessionService.submitExam(id, body.getAnswers());
        return ApiResponse.success("提交成功", session);
    }

    /**
     * 强制交卷（管理员操作）
     */
    @PostMapping("/{id}/force-submit")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    @Operation(summary = "强制交卷", description = "管理员可强制结束学生的考试并收卷")
    public ApiResponse<ExamSession> forceSubmit(@Parameter(description = "考试会话ID") @PathVariable Long id,
                                                Authentication auth) {
        examSessionAccessControl.assertManageable(id, auth);
        ExamSession session = examSessionService.forceSubmit(id);
        return ApiResponse.success("强制交卷成功", session);
    }

    /**
     * 获取考试剩余时间(秒)
     */
    @GetMapping("/{id}/time-remaining")
    @Operation(summary = "获取剩余时间", description = "返回当前考试会话的剩余可用时间（秒）")
    public ApiResponse<Long> timeRemaining(@Parameter(description = "考试会话ID") @PathVariable Long id,
                                           Authentication auth) {
        examSessionAccessControl.assertReadable(id, auth);
        Long remainingTime = examSessionService.getRemainingTime(id);
        return ApiResponse.success(remainingTime);
    }

    /**
     * 触发自动阅卷
     */
    @PostMapping("/{id}/auto-grade")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    @Operation(summary = "自动阅卷", description = "对客观题（单选/多选/判断）进行自动判分，生成成绩报告")
    public ApiResponse<AutoGradeResultVO> autoGrade(@Parameter(description = "考试会话ID") @PathVariable Long id,
                                                    Authentication auth) {
        examSessionAccessControl.assertManageable(id, auth);
        AutoGradeResultVO result = examSessionService.autoGrade(id);
        return ApiResponse.success("自动阅卷完成", result);
    }

    /**
     * 人工评分（教师对主观题评分）
     */
    @PostMapping("/{id}/manual-grade")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    @Operation(summary = "人工评分", description = "教师对主观题进行人工打分和评语填写")
    public ApiResponse<Void> manualGrade(
            @Parameter(description = "考试会话ID") @PathVariable Long id,
            @Valid @RequestBody ExamManualGradeDTO body,
            Authentication auth) {
        examSessionAccessControl.assertManageable(id, auth);
        examSessionService.manualGrade(id, body.getQuestionId(), body.getScore(),
                body.getComment() != null ? body.getComment() : "");
        return ApiResponse.success("人工评分完成", null);
    }

    /**
     * 查询成绩报告
     */
    @GetMapping("/{id}/report")
    @Operation(summary = "获取成绩报告", description = "返回详细的考试成绩分析报告，包含各题得分、排名等")
    public ApiResponse<Map<String, Object>> report(@Parameter(description = "考试会话ID") @PathVariable Long id,
                                                   Authentication auth) {
        examSessionAccessControl.assertReadable(id, auth);
        Map<String, Object> report = examSessionService.generateScoreReport(id);
        return ApiResponse.success(report);
    }

    /**
     * 查询考试结果
     */
    @GetMapping("/{id}/result")
    @Operation(summary = "获取考试结果", description = "返回考试会话详情，包含成绩和状态信息")
    public ApiResponse<ExamSession> result(@Parameter(description = "考试会话ID") @PathVariable Long id,
                                          Authentication auth) {
        // 复用归属校验时加载的会话对象，避免重复查询
        ExamSession session = examSessionAccessControl.assertReadable(id, auth);
        return ApiResponse.success(session);
    }

    /**
     * 检测作弊嫌疑
     */
    @GetMapping("/{id}/cheating-detect")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "检测作弊嫌疑",
            description = "基于行为数据检测可能的作弊行为。data 定型为字符串数组（元素为中文可读描述，"
                    + "如「检测到IP地址变化：x -> y」），不提供 type/description/riskLevel 对象结构；"
                    + "无嫌疑时返回空数组")
    public ApiResponse<List<String>> detectCheating(@Parameter(description = "考试会话ID") @PathVariable Long id,
                                                    Authentication auth) {
        examSessionAccessControl.assertManageable(id, auth);
        List<String> suspicions = examSessionService.detectCheating(id);
        return ApiResponse.success(suspicions);
    }

    /**
     * 作弊检测与标记（综合检测并记录结果）
     */
    @PostMapping("/{id}/check-cheating")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    @Operation(summary = "作弊检测与标记", description = "执行全面作弊检测，将检测结果标记到会话记录中")
    public ApiResponse<Map<String, Object>> checkCheating(@Parameter(description = "考试会话ID") @PathVariable Long id,
                                                          Authentication auth) {
        examSessionAccessControl.assertManageable(id, auth);
        Map<String, Object> result = examSessionService.checkCheating(id);
        return ApiResponse.success(result);
    }

    /**
     * 获取正在进行的考试列表（教师/管理员）。
     * <p>对象级收口（2026-11-13）：原先对全体 TEACHER 开放跨课程在考学生名单。现按
     * {@link CourseAccessControl#readableCourseIds} 过滤——教师仅见自己课程的进行中会话
     * （按 VO 的 courseId 匹配）；管理员（readableCourseIds 返回 null）不限。</p>
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取进行中的考试列表",
            description = "返回正在进行中的考试会话（VO）：含 paperTitle、courseId、courseName、"
                    + "studentName 与同卷进行中的 participantCount，监考页无需再按 id 回查试卷与用户名单；"
                    + "教师仅返回自己课程的会话，管理员返回全部")
    public ApiResponse<List<ActiveExamSessionVO>> activeExams(Authentication auth) {
        List<ActiveExamSessionVO> activeExams = examSessionService.getActiveExams();
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        if (readableCourseIds != null) {
            // 教师：只保留本人课程下的会话（无课程归属的会话对教师不可见）
            activeExams = activeExams.stream()
                    .filter(vo -> vo.getCourseId() != null && readableCourseIds.contains(vo.getCourseId()))
                    .toList();
        }
        return ApiResponse.success(activeExams);
    }

    /**
     * 查询用户在指定试卷上的最新一次考试会话
     */
    @GetMapping("/paper/{examPaperId}")
    @Operation(summary = "获取最新考试会话", description = "返回当前用户在指定试卷上的最新一次考试记录")
    public ApiResponse<Object> mySession(
            @Parameter(description = "试卷ID") @PathVariable Long examPaperId,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ExamSession session = examSessionService.getLatestSession(examPaperId, userId);
        return ApiResponse.success(session);
    }

    /**
     * 查询用户所有考试会话记录（附课程维度排名）
     */
    @GetMapping("/my")
    @Operation(summary = "获取我的考试记录", description = "返回当前用户的所有历史考试会话记录列表，每条附课程维度排名（courseRank/courseTotalStudents）")
    public ApiResponse<Object> mySessions(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(examSessionService.getMySessionsWithCourseRank(userId));
    }
}
