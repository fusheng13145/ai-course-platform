package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 作业任务接口控制器
 */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;
    private final SubmissionService submissionService;

    public TaskController(TaskService taskService, SubmissionService submissionService) {
        this.taskService = taskService;
        this.submissionService = submissionService;
    }

    /**
     * 分页查询任务（支持课程筛选）
     * @param page 当前页码
     * @param size 每页条数
     * @param courseId 课程ID（可选）
     * @return 分页任务数据
     */
    @GetMapping
    public ApiResponse<PagedResponse<Task>> listByParams(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId) {
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<Task>()
                .eq(courseId != null, Task::getCourseId, courseId);
        Page<Task> taskPage = taskService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(taskPage));
    }

    /**
     * 根据课程ID分页查询任务
     * @param courseId 课程ID
     * @param page 当前页码
     * @param size 每页条数
     * @return 分页任务数据
     */
    @GetMapping("/course/{courseId}")
    public ApiResponse<PagedResponse<Task>> listByCourse(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getCourseId, courseId);
        Page<Task> taskPage = taskService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(taskPage));
    }

    /**
     * 根据ID查询任务详情
     * @param id 任务ID
     * @return 任务信息
     */
    @GetMapping("/{id}")
    public ApiResponse<Task> getById(@PathVariable Long id) {
        Task task = taskService.getById(id);
        return ApiResponse.success(task);
    }

    /**
     * 新增任务（教师/管理员权限）
     * @param task 任务信息
     * @return 新增任务数据
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Task> create(@RequestBody Task task) {
        taskService.save(task);
        return ApiResponse.success("创建成功", task);
    }

    /**
     * 修改任务（教师/管理员权限）
     * @param id 任务ID
     * @param task 待更新任务数据
     * @return 更新后任务信息
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Task> update(@PathVariable Long id, @RequestBody Task task) {
        task.setId(id);
        taskService.updateById(task);
        Task updatedTask = taskService.getById(id);
        return ApiResponse.success("更新成功", updatedTask);
    }

    /**
     * 删除任务（教师/管理员权限）
     * @param id 任务ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        taskService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 发布任务（教师/管理员权限）
     * @param id 任务ID
     * @return 发布后任务信息
     */
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

    /**
     * 查询任务对应的所有提交记录
     * @param id 任务ID
     * @param page 当前页码
     * @param size 每页条数
     * @return 分页提交记录
     */
    @GetMapping("/{id}/submissions")
    public ApiResponse<PagedResponse<Submission>> submissions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<Submission> queryWrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, id);
        Page<Submission> submissionPage = submissionService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(submissionPage));
    }

    /**
     * 统计任务提交数据
     * @param id 任务ID
     * @return 统计信息：总提交数、已评分数、平均分
     */
    @GetMapping("/{id}/statistics")
    public ApiResponse<Map<String, Object>> statistics(@PathVariable Long id) {
        LambdaQueryWrapper<Submission> totalWrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, id);
        long total = submissionService.count(totalWrapper);

        LambdaQueryWrapper<Submission> gradedWrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, id)
                .eq(Submission::getStatus, "GRADED");
        long graded = submissionService.count(gradedWrapper);

        LambdaQueryWrapper<Submission> scoreWrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getTaskId, id)
                .isNotNull(Submission::getScore);
        List<Submission> scoreList = submissionService.list(scoreWrapper);

        // 修复 BigDecimal 到 double 的转换
        double avgScore = scoreList.stream()
                .mapToDouble(s -> s.getScore() != null ? s.getScore().doubleValue() : 0.0)
                .average()
                .orElse(0.0);
        double averageScore = Math.round(avgScore * 100.0) / 100.0;

        Map<String, Object> result = Map.of(
                "total", total,
                "graded", graded,
                "averageScore", averageScore
        );
        return ApiResponse.success(result);
    }
}