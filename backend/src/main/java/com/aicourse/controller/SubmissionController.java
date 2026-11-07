package com.aicourse.controller;

import com.aicourse.constant.SubmissionStatus;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.common.RoleConstants;
import com.aicourse.dto.GradeDTO;
import com.aicourse.dto.SubmissionCreateDTO;
import com.aicourse.dto.SubmissionResubmitDTO;
import com.aicourse.entity.Submission;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.SubmissionAccessControl;
import com.aicourse.service.SubmissionService;
import com.aicourse.vo.PlagiarismCheckResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

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

    public SubmissionController(SubmissionService submissionService,
                                SubmissionAccessControl accessControl,
                                com.aicourse.service.PlagiarismCheckService plagiarismCheckService) {
        this.submissionService = submissionService;
        this.accessControl = accessControl;
        this.plagiarismCheckService = plagiarismCheckService;
    }

    /**
     * 提交作业
     * @param body 白名单 DTO（仅 taskId/content/fileUrls 可被学生写入）
     * @param auth 认证信息
     * @return 提交记录
     */
    @PostMapping
    public ApiResponse<Submission> submit(@Valid @RequestBody SubmissionCreateDTO body,
                                          Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        // 修复 D4：原实现直接 submissionService.save(...)，绕过一切校验，导致
        //   ① 不校验 task 是否存在/是否发布/是否过期；② 同一 (taskId, studentId) 可无限重复 POST 产生多行。
        // 现统一收口到 {@link SubmissionService#submitAssignment}（与 StudentController 的
        // /student/assignments/{id}/submit 同源，修复历史「P0-8 双轨问题」），由服务层集中实现：
        //   - 任务存在性校验（不存在抛「作业任务不存在」）
        //   - 发布态校验（未发布抛「该作业尚未发布」）
        //   - 提交次数上限（maxSubmissions>0 时超限拒绝）
        //   - 逾期策略：本项目的既有约定是「允许迟交但计逾期扣分」——过期不拒绝，而是置 isLate + latePenalty
        //     （见 SubmissionWriteComponent，配合 Task.latePenaltyPerDay / Submission.isLate 字段），故此处沿用该策略。
        //   - 重复提交语义：既有实现按「多次提交、attemptNumber 递增、最新一次为准」处理，并复用草稿行避免草稿+正式双行；
        //     submissions 表无 (task_id, student_id) 唯一约束，本次仅在应用层实现，未加 DB 迁移（待团队评审）。
        // 仍仅接收白名单 DTO，绝不使用 Submission 实体直接入参，防止注入 score/graderId/status 等敏感列。
        Submission submission = submissionService.submitAssignment(
                body.getTaskId(), userId, body.getContent(), body.getFileUrls());
        // 异步 AI 预批改：提交后自动触发，不阻塞学生提交响应；
        // 教师打开批阅页时可直接看到 AI 建议分数和评语
        final Long savedId = submission.getId();
        CompletableFuture.runAsync(() -> {
            try {
                submissionService.aiGradeSubmission(savedId);
                log.info("AI预批改完成: submissionId={}", savedId);
            } catch (Exception e) {
                log.warn("AI预批改失败（不影响提交流程）: submissionId={}", savedId, e);
            }
        });
        return ApiResponse.success("提交成功", submission);
    }

    /**
     * 查询当前用户的作业提交记录（按 studentId 归属过滤）
     */
    @GetMapping
    public ApiResponse<PagedResponse<Submission>> mySubmissions(
            Authentication auth,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<Submission> queryWrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getStudentId, userId)
                .orderByDesc(Submission::getCreatedAt);
        Page<Submission> submissionPage = submissionService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(submissionPage));
    }

    /**
     * 根据ID查询提交记录详情（对象级授权：学生仅本人，教师仅本人课程）
     */
    @GetMapping("/{id}")
    public ApiResponse<Submission> getById(@PathVariable Long id, Authentication auth) {
        accessControl.assertReadable(id, auth);
        return ApiResponse.success(submissionService.getById(id));
    }

    /**
     * 查询指定任务下的提交记录
     * 学生：仅返回本人提交；教师/管理员：仅返回其本人任教课程下的全部提交。
     */
    @GetMapping("/task/{taskId}")
    public ApiResponse<PagedResponse<Submission>> list(
            @PathVariable Long taskId,
            Authentication auth,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        accessControl.assertCanListTask(taskId, auth);
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<Submission> queryWrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, taskId);
        // 学生视角：强制仅本人提交，防止查看全班作业
        if (RoleConstants.STUDENT.equals(roleOf(auth))) {
            queryWrapper.eq(Submission::getStudentId, userId);
        }
        Page<Submission> submissionPage = submissionService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(submissionPage));
    }

    /**
     * 给提交记录评分（教师/管理员，且须拥有所属课程）
     */
    @PostMapping("/{id}/grade")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<Submission> grade(
            @PathVariable Long id,
            @Valid @RequestBody GradeDTO grade,
            Authentication auth) {
        accessControl.assertManageable(id, auth);
        Submission submission = submissionService.getById(id);
        if (submission == null) {
            throw new BusinessException(404, "提交记录不存在");
        }
        submission.setScore(grade.getScore());
        submission.setFeedback(grade.getFeedback());
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(LocalDateTime.now());
        submission.setGraderId((Long) auth.getPrincipal());
        submissionService.updateById(submission);
        return ApiResponse.success("评分成功", submission);
    }

    /**
     * 重新提交作业（仅提交者本人或任教教师）
     */
    @PostMapping("/{id}/resubmit")
    public ApiResponse<Submission> resubmit(@PathVariable Long id,
                                            @Valid @RequestBody SubmissionResubmitDTO body,
                                            Authentication auth) {
        accessControl.assertReadable(id, auth);
        Submission submission = submissionService.resubmit(id, body.getContent(), body.getFileUrls());
        return ApiResponse.success("重新提交成功", submission);
    }

    /**
     * 退回作业（教师/管理员，且须拥有所属课程）
     */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<Submission> returnSubmission(@PathVariable Long id,
                                                     @Valid @RequestBody java.util.Map<String, String> body,
                                                     Authentication auth) {
        accessControl.assertManageable(id, auth);
        String reason = body.get("reason");
        Submission submission = submissionService.returnSubmission(id, reason);
        return ApiResponse.success("退回成功", submission);
    }

    /**
     * AI智能评分（教师/管理员，且须拥有所属课程）
     */
    @PostMapping("/{id}/ai-grade")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    public ApiResponse<Submission> aiGrade(@PathVariable Long id, Authentication auth) {
        accessControl.assertManageable(id, auth);
        Submission submission = submissionService.aiGradeSubmission(id);
        return ApiResponse.success("AI评分完成", submission);
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
        Object result = submissionService.triggerPlagiarismCheck(id);
        return ApiResponse.success("查重检测完成", result);
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

    private String roleOf(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst().orElse("");
    }
}
