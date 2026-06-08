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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
     * 发布任务（教师/管理员权限）- 使用增强版Service方法
     * @param id 任务ID
     * @return 发布后任务信息
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Task> publish(@PathVariable Long id) {
        Task task = taskService.publishTask(id);
        return ApiResponse.success("发布成功", task);
    }

    /**
     * 延长截止日期（教师/管理员权限）
     * @param id 任务ID
     * @param body 包含新的截止日期和原因
     * @return 更新后的任务信息
     */
    @PostMapping("/{id}/extend-deadline")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Task> extendDeadline(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String newDueDateStr = body.get("newDueDate").toString();
        String reason = body.get("reason") != null ? body.get("reason").toString() : null;
        LocalDateTime newDueDate = LocalDateTime.parse(newDueDateStr);
        Task task = taskService.extendDeadline(id, newDueDate, reason);
        return ApiResponse.success("截止日期已延长", task);
    }

    /**
     * 复制任务（教师/管理员权限）
     * @param id 源任务ID
     * @param body 包含新标题
     * @return 新任务信息
     */
    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Task> copy(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newTitle = body.get("newTitle");
        Task newTask = taskService.copyTask(id, newTitle);
        return ApiResponse.success("复制成功", newTask);
    }

    /**
     * 获取待批阅的任务列表（教师权限）
     * @param auth 认证信息
     * @return 待批阅任务列表
     */
    @GetMapping("/pending-grading")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<Task>> pendingGrading(Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        List<Task> tasks = taskService.getPendingGradingTasks(teacherId);
        return ApiResponse.success(tasks);
    }

    /**
     * 获取任务完成率统计（增强版）
     * @param id 任务ID
     * @return 完成率统计数据
     */
    @GetMapping("/{id}/completion-stats")
    public ApiResponse<Map<String, Object>> completionStats(@PathVariable Long id) {
        Map<String, Object> stats = taskService.getTaskCompletionStats(id);
        return ApiResponse.success(stats);
    }
}