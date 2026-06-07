package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Task;
import com.aicourse.entity.Submission;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.TaskService;
import com.aicourse.service.SubmissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/tasks")
public class TaskController {
    private final TaskService taskService;
    private final SubmissionService submissionService;

    public TaskController(TaskService taskService, SubmissionService submissionService) {
        this.taskService = taskService;
        this.submissionService = submissionService;
    }

    @GetMapping
    public ApiResponse<PagedResponse<Task>> listByParams(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) Long courseId) {
        return ApiResponse.success(PagedResponse.of(taskService.page(Page.of(page, size),
            new LambdaQueryWrapper<Task>().eq(courseId != null, Task::getCourseId, courseId))));
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<PagedResponse<Task>> listByCourse(@PathVariable Long courseId,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(PagedResponse.of(taskService.page(Page.of(page, size),
            new LambdaQueryWrapper<Task>().eq(Task::getCourseId, courseId))));
    }

    @GetMapping("/{id}")
    public ApiResponse<Task> getById(@PathVariable Long id) {
        return ApiResponse.success(taskService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Task> create(@RequestBody Task task, Authentication auth) {
        task.setCreatorId((Long) auth.getPrincipal());
        taskService.save(task);
        return ApiResponse.success("创建成功", task);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Task> update(@PathVariable Long id, @RequestBody Task task) {
        task.setId(id);
        taskService.updateById(task);
        return ApiResponse.success("更新成功", taskService.getById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        taskService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Task> publish(@PathVariable Long id) {
        Task task = taskService.getById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        task.setIsPublished(true);
        task.setStatus("PUBLISHED");
        taskService.updateById(task);
        return ApiResponse.success("发布成功", task);
    }

    @GetMapping("/{id}/submissions")
    public ApiResponse<PagedResponse<Submission>> submissions(@PathVariable Long id,
                                                                @RequestParam(defaultValue = "1") long page,
                                                                @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(PagedResponse.of(submissionService.page(Page.of(page, size),
            new LambdaQueryWrapper<Submission>().eq(Submission::getTaskId, id))));
    }

    @GetMapping("/{id}/statistics")
    public ApiResponse<Map<String, Object>> statistics(@PathVariable Long id) {
        long total = submissionService.count(new LambdaQueryWrapper<Submission>().eq(Submission::getTaskId, id));
        long graded = submissionService.count(
            new LambdaQueryWrapper<Submission>().eq(Submission::getTaskId, id).eq(Submission::getStatus, "GRADED"));
        double avgScore = submissionService.list(
            new LambdaQueryWrapper<Submission>().eq(Submission::getTaskId, id).isNotNull(Submission::getScore))
            .stream().mapToDouble(s -> s.getScore() != null ? s.getScore().doubleValue() : 0)
            .average().orElse(0);
        return ApiResponse.success(Map.of("total", total, "graded", graded, "averageScore", Math.round(avgScore * 100.0) / 100.0));
    }
}
