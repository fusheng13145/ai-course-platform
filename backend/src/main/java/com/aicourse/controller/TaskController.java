package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.common.ActivityVisibility;
import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.ActivityTargetDTO;
import com.aicourse.entity.Task;
import com.aicourse.dto.TaskCreateDTO;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.ActivityVisibilityGuard;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.service.ActivityTargetService;
import com.aicourse.service.SubmissionService;
import jakarta.validation.Valid;
import com.aicourse.service.TaskService;
import com.aicourse.util.LenientDateTimeParser;
import com.aicourse.vo.TaskCompletionStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作业任务接口控制器
 * 提供作业任务的增删改查、发布、截止日期管理、批改等RESTful API
 */
@RestController
@RequestMapping("/tasks")
@Tag(name = "任务管理", description = "提供作业任务的创建、查询、发布、截止日期延长、复制、批改等功能")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final TaskService taskService;
    @SuppressWarnings("unused")
    private final SubmissionService submissionService;
    private final CourseAccessControl courseAccessControl;
    private final ActivityVisibilityGuard activityVisibilityGuard;
    private final ActivityTargetService activityTargetService;

    public TaskController(TaskService taskService,
                          SubmissionService submissionService,
                          CourseAccessControl courseAccessControl,
                          ActivityVisibilityGuard activityVisibilityGuard,
                          ActivityTargetService activityTargetService) {
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.courseAccessControl = courseAccessControl;
        this.activityVisibilityGuard = activityVisibilityGuard;
        this.activityTargetService = activityTargetService;
    }

    /**
     * 分页查询任务（支持课程、教学班筛选）
     */
    @GetMapping
    @Operation(summary = "分页查询任务列表", description = "支持按课程ID、教学班ID筛选的分页查询")
    public ApiResponse<PagedResponse<Task>> listByParams(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "课程ID（可选）") @RequestParam(required = false) Long courseId,
            @Parameter(description = "教学班ID（可选，按教学班可见范围过滤）")
            @RequestParam(required = false) Long teachingClassId,
            Authentication auth) {
        if (courseId != null) {
            courseAccessControl.assertCanReadCourse(courseId, auth);
        }
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<Task>()
                .eq(courseId != null, Task::getCourseId, courseId)
                .eq(courseAccessControl.isStudent(auth), Task::getStatus, "PUBLISHED");
        activityVisibilityGuard.applyTaskScope(queryWrapper, auth, teachingClassId);
        Page<Task> taskPage = taskService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(taskPage));
    }

    /**
     * 根据课程ID分页查询任务
     */
    @GetMapping("/course/{courseId}")
    @Operation(summary = "按课程查询任务", description = "返回指定课程下的任务列表（按教学班可见范围过滤）")
    public ApiResponse<PagedResponse<Task>> listByCourse(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "教学班ID（可选，按教学班可见范围过滤）")
            @RequestParam(required = false) Long teachingClassId,
            Authentication auth) {
        courseAccessControl.assertCanReadCourse(courseId, auth);
        LambdaQueryWrapper<Task> queryWrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getCourseId, courseId)
                .eq(courseAccessControl.isStudent(auth), Task::getStatus, "PUBLISHED");
        activityVisibilityGuard.applyTaskScope(queryWrapper, auth, teachingClassId);
        Page<Task> taskPage = taskService.page(Page.of(page, size), queryWrapper);
        return ApiResponse.success(PagedResponse.of(taskPage));
    }

    /**
     * 根据ID查询任务详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取任务详情", description = "根据任务ID返回完整任务信息，包含要求、附件等")
    public ApiResponse<Task> getById(@Parameter(description = "任务ID") @PathVariable Long id,
                                     Authentication auth) {
        Task task = taskService.getById(id);
        activityVisibilityGuard.assertVisibleTask(task, auth);
        return ApiResponse.success(task);
    }

    /**
     * 查询任务目标教学班
     */
    @GetMapping("/{id}/targets")
    @Operation(summary = "查询任务目标教学班", description = "返回任务可见范围与目标教学班ID")
    public ApiResponse<Map<String, Object>> getTargets(@Parameter(description = "任务ID") @PathVariable Long id,
                                                       Authentication auth) {
        Task task = requireTask(id);
        activityVisibilityGuard.assertVisibleTask(task, auth);
        return ApiResponse.success(buildTargetsPayload(
                ActivityVisibility.normalize(task.getVisibility()),
                activityTargetService.taskTargetClassIds(id)));
    }

    /**
     * 设置任务目标教学班（教师权限；管理员调用返回 403）
     */
    @PutMapping("/{id}/targets")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "设置任务目标教学班", description = "维护 visibility 与目标教学班；管理员调用返回 403")
    public ApiResponse<Map<String, Object>> updateTargets(
            @Parameter(description = "任务ID") @PathVariable Long id,
            @Valid @RequestBody ActivityTargetDTO targets,
            Authentication auth) {
        activityVisibilityGuard.assertNotAdmin(auth);
        Task existing = requireTask(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        String visibility = activityTargetService.normalizeAndValidate(
                id, existing.getCourseId(), targets.getVisibility(), targets.getTeachingClassIds(), false);
        Task patch = new Task();
        patch.setId(id);
        patch.setVisibility(visibility);
        taskService.updateById(patch);
        activityTargetService.replaceTaskTargets(id, visibility, targets.getTeachingClassIds());
        return ApiResponse.success("设置成功", buildTargetsPayload(visibility,
                activityTargetService.taskTargetClassIds(id)));
    }

    private static Map<String, Object> buildTargetsPayload(String visibility, List<Long> teachingClassIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("visibility", visibility);
        payload.put("teachingClassIds", teachingClassIds);
        return payload;
    }

    /**
     * 新增任务（教师/管理员权限）
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "创建新任务", description = "教师或管理员创建新的作业/测验/项目任务")
    public ApiResponse<Task> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "任务创建信息"
    ) @Valid @RequestBody Task task,
                                    Authentication auth) {
        courseAccessControl.assertCanManageCourse(task.getCourseId(), auth);
        String requestedVisibility = ActivityVisibility.normalizeOrNull(task.getVisibility());
        if (ActivityVisibility.CLASSES.equals(requestedVisibility)
                || (task.getTeachingClassIds() != null && !task.getTeachingClassIds().isEmpty())) {
            activityVisibilityGuard.assertNotAdmin(auth);
        }
        String visibility = activityTargetService.normalizeAndValidate(
                null, task.getCourseId(), task.getVisibility(), task.getTeachingClassIds(), false);
        task.setVisibility(visibility);
        taskService.save(task);
        activityTargetService.replaceTaskTargets(task.getId(), visibility, task.getTeachingClassIds());
        return ApiResponse.success("创建成功", task);
    }

    /**
     * 修改任务（教师/管理员权限）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "更新任务信息", description = "教师或管理员可修改任务内容、截止日期等")
    public ApiResponse<Task> update(@Parameter(description = "任务ID") @PathVariable Long id,
                                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                            description = "待更新的任务数据"
                                    ) @Valid @RequestBody Task task,
                                    Authentication auth) {
        Task existing = requireTask(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        boolean targetsTouched = task.getVisibility() != null || task.getTeachingClassIds() != null;
        String normalizedVisibility = null;
        if (targetsTouched) {
            // 管理员不得修改目标教学班（WP6 验收第 4 条）
            activityVisibilityGuard.assertNotAdmin(auth);
            String effectiveVisibility = task.getVisibility() != null
                    ? task.getVisibility() : existing.getVisibility();
            normalizedVisibility = activityTargetService.normalizeAndValidate(
                    id, existing.getCourseId(), effectiveVisibility, task.getTeachingClassIds(), false);
            task.setVisibility(normalizedVisibility);
        }
        task.setId(id);
        task.setCourseId(existing.getCourseId());
        taskService.updateById(task);
        if (targetsTouched) {
            activityTargetService.replaceTaskTargets(id, normalizedVisibility, task.getTeachingClassIds());
        }
        Task updatedTask = taskService.getById(id);
        return ApiResponse.success("更新成功", updatedTask);
    }

    /**
     * 删除任务（教师/管理员权限）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "删除任务", description = "教师或管理员可删除任务")
    public ApiResponse<Void> delete(@Parameter(description = "任务ID") @PathVariable Long id,
                                    Authentication auth) {
        Task existing = requireTask(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        taskService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 发布任务（教师权限；管理员治理只读，不代办发布）
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "发布任务", description = "教师将草稿状态的任务发布给学生可见；管理员调用返回 403")
    public ApiResponse<Task> publish(@Parameter(description = "任务ID") @PathVariable Long id,
                                     Authentication auth) {
        // WP6：管理员只读治理，不代教师发布
        activityVisibilityGuard.assertNotAdmin(auth);
        Task existing = requireTask(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        Task task = taskService.publishTask(id);
        return ApiResponse.success("发布成功", task);
    }

    /**
     * 延长截止日期（教师/管理员权限）
     */
    @PostMapping("/{id}/extend-deadline")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "延长截止日期", description = "延长任务的截止时间并记录原因")
    public ApiResponse<Task> extendDeadline(
            @Parameter(description = "任务ID") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "包含新截止日期(newDueDate)和原因(reason)的任务创建DTO"
            ) @Valid @RequestBody TaskCreateDTO dto,
            Authentication auth) {
        String newDueDateStr = dto.getNewDueDate();
        String reason = dto.getReason();
        if (newDueDateStr == null || newDueDateStr.isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "新的截止日期不能为空");
        }
        // 修复（2026-10-30，B-13 ⑨）：原本手写 LocalDateTime.parse 只认 ISO-8601，
        // 导致同一控制器内 dueDate 接受 "2026-10-30 23:59:59"、而 newDueDate 对同样的
        // 字符串必 400。改为走 LenientDateTimeParser，与全局反序列化口径完全一致
        // （2026-11-01 项目主裁定：方向为「放宽」，ISO-8601 为契约推荐格式）。
        // 解析失败仍是 400（不是 500），文案统一由该工具给出。
        LocalDateTime newDueDate;
        try {
            newDueDate = LenientDateTimeParser.parse(newDueDateStr);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiCode.BAD_REQUEST,
                    "截止日期格式不正确；" + LenientDateTimeParser.FORMAT_HINT);
        }
        Task existing = requireTask(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        Task task = taskService.extendDeadline(id, newDueDate, reason);
        return ApiResponse.success("截止日期已延长", task);
    }

    /**
     * 复制任务（教师/管理员权限）
     */
    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "复制任务", description = "基于已有任务快速创建副本，可自定义新标题")
    public ApiResponse<Task> copy(
            @Parameter(description = "源任务ID") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "包含新标题(newTitle)的JSON对象"
            ) @Valid @RequestBody Map<String, String> body,
            Authentication auth) {
        Task existing = requireTask(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        String newTitle = body.get("newTitle");
        Task newTask = taskService.copyTask(id, newTitle);
        return ApiResponse.success("复制成功", newTask);
    }

    /**
     * 获取待批阅的任务列表（教师权限）
     */
    @GetMapping("/pending-grading")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取待批阅任务列表", description = "返回当前教师有待批阅提交的任务列表")
    public ApiResponse<List<Task>> pendingGrading(Authentication auth) {
        Long teacherId = (Long) auth.getPrincipal();
        List<Task> tasks = taskService.getPendingGradingTasks(teacherId);
        return ApiResponse.success(tasks);
    }

    /**
     * 获取任务完成率统计（增强版）
     */
    @GetMapping("/{id}/completion-stats")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取任务完成率统计", description = "返回指定任务的提交完成情况统计")
    public ApiResponse<TaskCompletionStatsVO> completionStats(@Parameter(description = "任务ID") @PathVariable Long id,
                                                             Authentication auth) {
        Task existing = requireTask(id);
        courseAccessControl.assertCanManageCourse(existing.getCourseId(), auth);
        TaskCompletionStatsVO stats = taskService.getTaskCompletionStats(id);
        return ApiResponse.success(stats);
    }

    private Task requireTask(Long id) {
        Task task = taskService.getById(id);
        if (task == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }
}
