package com.aicourse.controller;


import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.GradeDTO;
import com.aicourse.dto.SubmissionCreateDTO;
import com.aicourse.dto.SubmissionResubmitDTO;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.Submission;




import com.aicourse.security.SubmissionAccessControl;


import com.aicourse.service.SubmissionService;


import com.aicourse.vo.PlagiarismCheckResultVO;
import com.aicourse.vo.SubmissionVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;




import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;



import lombok.extern.slf4j.Slf4j;
import com.aicourse.application.submission.SubmissionApplicationService;
import com.aicourse.vo.UnsubmittedStudentVO;

/**
 * 作业提交与评分接口控制器
 *
 * <p>安全加固说明：所有对象级接口均经 {@link SubmissionAccessControl} 校验资源归属，
 * 杜绝 IDOR/BOLA 与水平越权（任意学生枚举他人提交、教师互改他人课程）。</p>
 */
@RestController
@RequestMapping("/submissions")
@Tag(name = "作业提交")
@Slf4j
public class SubmissionController {

    private final SubmissionService submissionService;
    private final SubmissionAccessControl accessControl;
    private final com.aicourse.service.PlagiarismCheckService plagiarismCheckService;
    private final SubmissionApplicationService submissionApplicationService;

    public SubmissionController(SubmissionService submissionService,
                                SubmissionAccessControl accessControl,
                                com.aicourse.service.PlagiarismCheckService plagiarismCheckService,
                                SubmissionApplicationService submissionApplicationService) {
        this.submissionService = submissionService;
        this.accessControl = accessControl;
        this.plagiarismCheckService = plagiarismCheckService;
        this.submissionApplicationService = submissionApplicationService;
    }

    /**
     * 提交作业
     * @param body 白名单 DTO（仅 taskId/content/fileUrls 可被学生写入）
     * @param auth 认证信息
     * @return 提交记录
     */
    @PostMapping
    public ApiResponse<SubmissionVO> submit(@Valid @RequestBody SubmissionCreateDTO body,
                                          Authentication auth) {
        Submission submission = submissionApplicationService.submit(body, auth);
        return ApiResponse.success("提交成功", SubmissionVO.from(submission));
    }
    /**
     * 保存作业草稿（学生）。
     * <p>草稿与正式提交共用同一条 submission 行：草稿不计入成绩与次数统计，
     * 正式提交时由 SubmissionWriteComponent 复用该行转正（attempt_number 沿用草稿预占号）。</p>
     */
    @PostMapping("/draft")
    public ApiResponse<SubmissionVO> saveDraft(@Valid @RequestBody SubmissionCreateDTO body,
                                             Authentication auth) {
        Submission draft = submissionApplicationService.saveDraft(body, auth);
        return ApiResponse.success("草稿已保存", SubmissionVO.from(draft));
    }
    /**
     * 任务未提交学生名单（教师 / 管理员）。
     * <p>口径：课程在册学生（enrollment.isActive）− 已有非草稿提交的学生。</p>
     */
    @GetMapping("/task/{taskId}/unsubmitted")
    public ApiResponse<List<UnsubmittedStudentVO>> unsubmitted(@PathVariable Long taskId,
                                                               Authentication auth) {
        return ApiResponse.success(submissionApplicationService.unsubmittedStudents(taskId, auth));
    }

    /**
     * 一键催交：给未提交学生批量投递系统通知。
     * <p>直接走 notificationService 批量发送，不创建 Reminder 定时任务——催交是即时动作，
     * 建 PENDING reminder 反而要额外触发 send-due。</p>
     */
    @PostMapping("/task/{taskId}/remind")
    public ApiResponse<Map<String, Object>> remindUnsubmitted(@PathVariable Long taskId,
                                                              Authentication auth) {
        SubmissionApplicationService.ReminderOutcome outcome =
                submissionApplicationService.remindUnsubmitted(taskId, auth);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", outcome.count());
        return ApiResponse.success(outcome.message(), result);
    }


    /**
     * 查询当前用户的作业提交记录（按 studentId 归属过滤）
     */
    @GetMapping
    public ApiResponse<PagedResponse<SubmissionVO>> mySubmissions(
            Authentication auth,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(submissionApplicationService.mySubmissions(userId, page, size));
    }

    /**
     * 根据ID查询提交记录详情（对象级授权：学生仅本人，教师仅本人课程）
     */
    @GetMapping("/{id}")
    public ApiResponse<SubmissionVO> getById(@PathVariable Long id, Authentication auth) {
        accessControl.assertReadable(id, auth);
        return ApiResponse.success(SubmissionVO.from(submissionService.getById(id)));
    }

    /**
     * 查询指定任务下的提交记录
     * 学生：仅返回本人提交；教师/管理员：仅返回其本人任教课程下的全部提交。
     */
    @GetMapping("/task/{taskId}")
    public ApiResponse<PagedResponse<SubmissionVO>> list(
            @PathVariable Long taskId,
            Authentication auth,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        // 任务可列性授权 + 学生仅本人视角统一在 application 层（3E-05E）
        return ApiResponse.success(submissionApplicationService.listByTask(taskId, page, size, auth));
    }

    /**
     * 给提交记录评分（教师/管理员，且须拥有所属课程）。
     * <p>2026-11-14 P1-2 批阅语义统一：控制器此前直写实体（无事务、无逾期扣分/百分比/
     * reviewCount/通知），与 {@code SubmissionGradeComponent} 行为分叉。现统一委托
     * {@link SubmissionService#gradeSubmission}，落库口径单一化。</p>
     */
    @PostMapping("/{id}/grade")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<SubmissionVO> grade(
            @PathVariable Long id,
            @Valid @RequestBody GradeDTO grade,
            Authentication auth) {
        Submission submission = submissionApplicationService.grade(id, grade, auth);
        return ApiResponse.success("评分成功", SubmissionVO.from(submission));
    }

    /**
     * 重新提交作业（仅提交者本人或任教教师）
     */
    @PostMapping("/{id}/resubmit")
    public ApiResponse<SubmissionVO> resubmit(@PathVariable Long id,
                                            @Valid @RequestBody SubmissionResubmitDTO body,
                                            Authentication auth) {
        Submission submission = submissionApplicationService.resubmit(id, body, auth);
        return ApiResponse.success("重新提交成功", SubmissionVO.from(submission));
    }

    /**
     * 退回作业（教师/管理员，且须拥有所属课程）
     */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<SubmissionVO> returnSubmission(@PathVariable Long id,
                                                     @Valid @RequestBody java.util.Map<String, String> body,
                                                     Authentication auth) {
        String reason = body.get("reason");
        Submission submission = submissionApplicationService.returnSubmission(id, reason, auth);
        return ApiResponse.success("退回成功", SubmissionVO.from(submission));
    }

    /**
     * AI智能评分（教师/管理员，且须拥有所属课程）
     */
    @PostMapping("/{id}/ai-grade")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<SubmissionVO> aiGrade(@PathVariable Long id, Authentication auth) {
        Submission submission = submissionApplicationService.aiGrade(id, auth);
        return ApiResponse.success("AI评分完成", SubmissionVO.from(submission));
    }

    /**
     * 查询查重检测结果（对象级授权）
     * 优先返回已完成的查重任务结果；不存在时自动触发一次查重检测。
     */
    @GetMapping("/{id}/plagiarism")
    public ApiResponse<Object> getPlagiarismResult(@PathVariable Long id, Authentication auth) {
        accessControl.assertReadable(id, auth);
        List<com.aicourse.entity.PlagiarismCheck> checks = plagiarismCheckService.getSubmissionChecks(id);
        if (!checks.isEmpty()) {
            com.aicourse.entity.PlagiarismCheck latest = checks.get(0);
            if ("COMPLETED".equals(latest.getStatus())) {
                PlagiarismCheckResultVO result = plagiarismCheckService.getCheckResult(latest.getId());
                return ApiResponse.success("查重检测结果", result);
            }
            return ApiResponse.success("查重任务正在执行中，请稍后刷新查看", Map.of("checkId", latest.getId(), "status", latest.getStatus()));
        }
        return ApiResponse.success("该提交尚未进行查重检测，请使用 POST /submissions/{id}/check-plagiarism 接口触发查重", Collections.emptyMap());
    }

    /**
     * 手动触发查重检测（教师/管理员，且须拥有所属课程）
     */
    @PostMapping("/{id}/check-plagiarism")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<Object> checkPlagiarism(@PathVariable Long id, Authentication auth) {
        accessControl.assertManageable(id, auth);
        PlagiarismCheck check = submissionService.triggerPlagiarismCheck(id);
        // 返回带 matches/摘要的 VO：前端查重面板直接渲染明细，不必再补一次 GET
        return ApiResponse.success("查重检测完成", plagiarismCheckService.getCheckResult(check.getId()));
    }

    /**
     * 记录查看次数（对象级授权）
     */
    @PostMapping("/{id}/view")
    public ApiResponse<Void> recordView(@PathVariable Long id, Authentication auth) {
        accessControl.assertReadable(id, auth);
        submissionService.incrementViewCount(id);
        return ApiResponse.success(null);
    }

}

