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
}