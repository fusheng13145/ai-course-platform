package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.TeacherAccessGuard;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教师端作业管理接口（2026-11-18 从 TeacherController 拆出）。
 *
 * <p>创建/列表/详情/更新/删除、提交列表、批改与发布共 8 个端点；路径与权限不变。</p>
 */
@RestController
@RequestMapping("/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "教师端作业管理接口", description = "作业/提交/批改/发布")
public class TeacherAssignmentController {

    private final TaskService taskService;
    private final SubmissionService submissionService;
    private final CourseAccessControl courseAccessControl;
    private final TeacherAccessGuard teacherAccessGuard;

    public TeacherAssignmentController(TaskService taskService,
                                       SubmissionService submissionService,
                                       CourseAccessControl courseAccessControl,
                                       TeacherAccessGuard teacherAccessGuard) {
        this.taskService = taskService;
        this.submissionService = submissionService;
        this.courseAccessControl = courseAccessControl;
        this.teacherAccessGuard = teacherAccessGuard;
    }

    /** 创建作业（若指定课程则需归属校验） */
    @PostMapping("/assignments")
    @Operation(summary = "创建作业")
    public ApiResponse<Task> createAssignment(@Valid @RequestBody Task task, Authentication auth) {
        if (task.getCourseId() != null) {
            teacherAccessGuard.assertCourseOwner(task.getCourseId(), auth);
        }
        if (task.getIsPublished() == null) {
            task.setIsPublished(false);
        }
        if (task.getStatus() == null) {
            task.setStatus("DRAFT");
        }
        taskService.save(task);
        return ApiResponse.success("创建成功", task);
    }

    /** 分页查询作业列表（可按课程筛选；教师视角收窄到本人可读课程，管理员全量） */
    @GetMapping("/assignments")
    @Operation(summary = "分页查询作业列表")
    public ApiResponse<PagedResponse<Task>> getAssignments(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        LambdaQueryWrapper<Task> qw = new LambdaQueryWrapper<Task>()
                .eq(courseId != null, Task::getCourseId, courseId);
        List<Long> readableCourseIds = courseAccessControl.readableCourseIds(auth);
        if (readableCourseIds != null) {
            if (readableCourseIds.isEmpty()) {
                return ApiResponse.success(PagedResponse.empty());
            }
            qw.in(Task::getCourseId, readableCourseIds);
        }
        Page<Task> taskPage = taskService.page(Page.of(page, size), qw);
        return ApiResponse.success(PagedResponse.of(taskPage));
    }

    /** 获取作业详情（需作业归属校验） */
    @GetMapping("/assignments/{assignmentId}")
    @Operation(summary = "获取作业详情")
    public ApiResponse<Task> getAssignmentDetail(@PathVariable Long assignmentId, Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        Task task = taskService.getById(assignmentId);
        if (task == null) {
            throw new BusinessException("作业不存在");
        }
        return ApiResponse.success(task);
    }

    /** 更新作业（需作业归属校验） */
    @PutMapping("/assignments/{assignmentId}")
    @Operation(summary = "更新作业")
    public ApiResponse<Task> updateAssignment(@PathVariable Long assignmentId,
                                              @Valid @RequestBody Task task,
                                              Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        Task updated = taskService.updateTaskById(assignmentId, task);
        return ApiResponse.success("更新成功", updated);
    }

    /** 删除作业（需作业归属校验） */
    @DeleteMapping("/assignments/{assignmentId}")
    @Operation(summary = "删除作业")
    public ApiResponse<Void> deleteAssignment(@PathVariable Long assignmentId, Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        taskService.removeById(assignmentId);
        return ApiResponse.success("删除成功", null);
    }

    /** 分页获取某作业的提交列表（需作业归属校验） */
    @GetMapping("/assignments/{assignmentId}/submissions")
    @Operation(summary = "获取作业提交列表")
    public ApiResponse<PagedResponse<Submission>> getSubmissions(
            @PathVariable Long assignmentId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        Page<Submission> submissionPage = submissionService.page(Page.of(page, size),
                new LambdaQueryWrapper<Submission>().eq(Submission::getTaskId, assignmentId));
        return ApiResponse.success(PagedResponse.of(submissionPage));
    }

    /** 教师批改某条作业提交（需作业归属校验） */
    @PutMapping("/assignments/{assignmentId}/submissions/{submissionId}")
    @Operation(summary = "批改作业")
    public ApiResponse<Submission> gradeSubmission(
            @PathVariable Long assignmentId,
            @PathVariable Long submissionId,
            @Valid @RequestBody Submission grade,
            Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        Submission submission = submissionService.gradeSubmissionByTeacher(
                assignmentId, submissionId, grade, (Long) auth.getPrincipal());
        return ApiResponse.success("批改完成", submission);
    }

    /** 发布作业（需作业归属校验） */
    @PutMapping("/assignments/{assignmentId}/publish")
    @Operation(summary = "发布作业")
    public ApiResponse<Task> publishAssignment(@PathVariable Long assignmentId, Authentication auth) {
        teacherAccessGuard.assertTaskOwner(assignmentId, auth);
        Task task = taskService.publishTask(assignmentId);
        return ApiResponse.success("发布成功", task);
    }
}
