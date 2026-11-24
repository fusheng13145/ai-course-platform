package com.aicourse.controller;

import com.aicourse.application.teacher.TeacherAssignmentApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.GradeDTO;
import com.aicourse.dto.TaskSaveDTO;
import com.aicourse.vo.SubmissionVO;
import com.aicourse.vo.TaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师端作业管理接口（2026-11-18 从 TeacherController 拆出）。
 *
 * <p>创建/列表/详情/更新/删除、提交列表、批改与发布共 8 个端点；路径与权限不变。</p>
 *
 * <p>3E-05E：8 个端点的编排、可见范围收窄与 2 处 wrapper 已下移到
 * {@link TeacherAssignmentApplicationService}。</p>
 */
@RestController
@RequestMapping("/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "教师端作业管理接口", description = "作业/提交/批改/发布")
public class TeacherAssignmentController {

    private final TeacherAssignmentApplicationService teacherAssignmentApplicationService;

    public TeacherAssignmentController(TeacherAssignmentApplicationService teacherAssignmentApplicationService) {
        this.teacherAssignmentApplicationService = teacherAssignmentApplicationService;
    }

    /** 创建作业（若指定课程则需归属校验） */
    @PostMapping("/assignments")
    @Operation(summary = "创建作业")
    public ApiResponse<TaskVO> createAssignment(@Valid @RequestBody TaskSaveDTO dto, Authentication auth) {
        return ApiResponse.success("创建成功", teacherAssignmentApplicationService.create(dto, auth));
    }

    /** 分页查询作业列表（可按课程筛选；教师视角收窄到本人可读课程，管理员全量） */
    @GetMapping("/assignments")
    @Operation(summary = "分页查询作业列表")
    public ApiResponse<PagedResponse<TaskVO>> getAssignments(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long courseId,
            Authentication auth) {
        return ApiResponse.success(
                teacherAssignmentApplicationService.listAssignments(page, size, courseId, auth));
    }

    /** 获取作业详情（需作业归属校验） */
    @GetMapping("/assignments/{assignmentId}")
    @Operation(summary = "获取作业详情")
    public ApiResponse<TaskVO> getAssignmentDetail(@PathVariable Long assignmentId, Authentication auth) {
        return ApiResponse.success(teacherAssignmentApplicationService.detail(assignmentId, auth));
    }

    /** 更新作业（需作业归属校验） */
    @PutMapping("/assignments/{assignmentId}")
    @Operation(summary = "更新作业")
    public ApiResponse<TaskVO> updateAssignment(@PathVariable Long assignmentId,
                                              @Valid @RequestBody TaskSaveDTO dto,
                                              Authentication auth) {
        return ApiResponse.success("更新成功", teacherAssignmentApplicationService.update(assignmentId, dto, auth));
    }

    /** 删除作业（需作业归属校验） */
    @DeleteMapping("/assignments/{assignmentId}")
    @Operation(summary = "删除作业")
    public ApiResponse<Void> deleteAssignment(@PathVariable Long assignmentId, Authentication auth) {
        teacherAssignmentApplicationService.delete(assignmentId, auth);
        return ApiResponse.success("删除成功", null);
    }

    /** 分页获取某作业的提交列表（需作业归属校验） */
    @GetMapping("/assignments/{assignmentId}/submissions")
    @Operation(summary = "获取作业提交列表")
    public ApiResponse<PagedResponse<SubmissionVO>> getSubmissions(
            @PathVariable Long assignmentId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            Authentication auth) {
        return ApiResponse.success(
                teacherAssignmentApplicationService.submissions(assignmentId, page, size, auth));
    }

    /** 教师批改某条作业提交（需作业归属校验） */
    @PutMapping("/assignments/{assignmentId}/submissions/{submissionId}")
    @Operation(summary = "批改作业")
    public ApiResponse<SubmissionVO> gradeSubmission(
            @PathVariable Long assignmentId,
            @PathVariable Long submissionId,
            @Valid @RequestBody GradeDTO grade,
            Authentication auth) {
        return ApiResponse.success("批改完成",
                teacherAssignmentApplicationService.grade(assignmentId, submissionId, grade, auth));
    }

    /** 发布作业（需作业归属校验） */
    @PutMapping("/assignments/{assignmentId}/publish")
    @Operation(summary = "发布作业")
    public ApiResponse<TaskVO> publishAssignment(@PathVariable Long assignmentId, Authentication auth) {
        return ApiResponse.success("发布成功",
                teacherAssignmentApplicationService.publish(assignmentId, auth));
    }
}
