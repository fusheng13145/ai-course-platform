package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Submission;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.SubmissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 作业提交与评分接口控制器
 */
@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /**
     * 提交作业
     * @param submission 提交数据
     * @param auth 认证信息
     * @return 提交记录
     */
    @PostMapping
    public ApiResponse<Submission> submit(@RequestBody Submission submission, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        submission.setStudentId(userId);
        submission.setStatus("SUBMITTED");
        submission.setSubmittedAt(LocalDateTime.now());
        submissionService.save(submission);
        return ApiResponse.success("提交成功", submission);
    }

    /**
     * 查询当前用户的作业提交记录
     * @param auth 认证信息
     * @param page 当前页码
     * @param size 每页条数
     * @return 分页提交记录
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
     * 根据ID查询提交记录详情
     * @param id 提交记录ID
     * @return 提交记录信息
     */
    @GetMapping("/{id}")
    public ApiResponse<Submission> getById(@PathVariable Long id) {
        Submission submission = submissionService.getById(id);
        return ApiResponse.success(submission);
    }

    /**
     * 查询指定任务下的所有提交记录
     * @param taskId 任务ID
     * @param page 当前页码
     * @param size 每页条数
     * @return 分页提交记录
     */
    @GetMapping("/task/{taskId}")
    public ApiResponse<PagedResponse<Submission>> list(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<Submission> queryWrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, taskId);
        Page<Submission> submissionPage = submissionService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(submissionPage));
    }

    /**
     * 给提交记录评分（教师/管理员权限）
     * @param id 提交记录ID
     * @param grade 评分数据
     * @param auth 认证信息
     * @return 评分后的提交记录
     */
    @PostMapping("/{id}/grade")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Submission> grade(
            @PathVariable Long id,
            @RequestBody Submission grade,
            Authentication auth) {
        Submission submission = submissionService.getById(id);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }
        submission.setScore(grade.getScore());
        submission.setFeedback(grade.getFeedback());
        submission.setStatus("GRADED");
        submission.setGradedAt(LocalDateTime.now());
        submission.setGraderId((Long) auth.getPrincipal());
        submissionService.updateById(submission);
        return ApiResponse.success("评分成功", submission);
    }

    /**
     * 重新提交作业
     * @param id 提交记录ID
     * @param body 提交数据（content, fileUrls）
     * @return 重新提交记录
     */
    @PostMapping("/{id}/resubmit")
    public ApiResponse<Submission> resubmit(@PathVariable Long id,
                                            @RequestBody Map<String, Object> body) {
        String content = body.get("content") != null ? body.get("content").toString() : null;
        @SuppressWarnings("unchecked")
        List<String> fileUrls = body.get("fileUrls") != null ? (List<String>) body.get("fileUrls") : null;
        Submission submission = submissionService.resubmit(id, content, fileUrls);
        return ApiResponse.success("重新提交成功", submission);
    }

    /**
     * 退回作业（教师/管理员权限）
     * @param id 提交记录ID
     * @param body 包含退回原因
     * @return 退回后的提交记录
     */
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Submission> returnSubmission(@PathVariable Long id,
                                                     @RequestBody Map<String, String> body) {
        String reason = body.get("reason");
        Submission submission = submissionService.returnSubmission(id, reason);
        return ApiResponse.success("退回成功", submission);
    }

    /**
     * AI智能评分（教师/管理员权限）
     * @param id 提交记录ID
     * @return AI评分结果
     */
    @PostMapping("/{id}/ai-grade")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Submission> aiGrade(@PathVariable Long id) {
        Submission submission = submissionService.aiGradeSubmission(id);
        return ApiResponse.success("AI评分完成", submission);
    }

    /**
     * 查询查重检测结果
     * @param id 提交记录ID
     * @return 查重检测结果
     */
    @GetMapping("/{id}/plagiarism")
    public ApiResponse<Object> getPlagiarismResult(@PathVariable Long id) {
        // 简化处理：返回提示信息，实际应查询PlagiarismCheck表
        return ApiResponse.success("请使用 POST /check-plagiarism 接口触发查重检测");
    }

    /**
     * 手动触发查重检测（教师/管理员权限）
     * @param id 提交记录ID
     * @return 查重检测结果
     */
    @PostMapping("/{id}/check-plagiarism")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Object> checkPlagiarism(@PathVariable Long id) {
        Object result = submissionService.triggerPlagiarismCheck(id);
        return ApiResponse.success("查重检测完成", result);
    }

    /**
     * 记录查看次数
     * @param id 提交记录ID
     * @return 操作结果
     */
    @PostMapping("/{id}/view")
    public ApiResponse<Void> recordView(@PathVariable Long id) {
        submissionService.incrementViewCount(id);
        return ApiResponse.success(null);
    }
}