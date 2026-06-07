package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Submission;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.SubmissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/submissions")
public class SubmissionController {
    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public ApiResponse<Submission> submit(@RequestBody Submission submission, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        submission.setStudentId(userId);
        submission.setStatus("SUBMITTED");
        submission.setSubmittedAt(LocalDateTime.now());
        submissionService.save(submission);
        return ApiResponse.success("提交成功", submission);
    }

    @GetMapping("/{id}")
    public ApiResponse<Submission> getById(@PathVariable Long id) {
        return ApiResponse.success(submissionService.getById(id));
    }

    @GetMapping("/task/{taskId}")
    public ApiResponse<PagedResponse<Submission>> list(@PathVariable Long taskId,
                                                         @RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(PagedResponse.of(submissionService.page(Page.of(page, size),
            new LambdaQueryWrapper<Submission>().eq(Submission::getTaskId, taskId))));
    }

    @PostMapping("/{id}/grade")
    public ApiResponse<Submission> grade(@PathVariable Long id, @RequestBody Submission grade, Authentication auth) {
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
