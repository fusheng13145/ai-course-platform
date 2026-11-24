package com.aicourse.controller;

import com.aicourse.application.course.TaskApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.ActivityTargetDTO;
import com.aicourse.dto.TaskCopyDTO;
import com.aicourse.dto.TaskCreateDTO;
import com.aicourse.dto.TaskSaveDTO;
import com.aicourse.vo.TaskCompletionStatsVO;
import com.aicourse.vo.TaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 作业任务接口控制器。
 * 提供作业任务的增删改查、发布、截止日期管理、批改等 RESTful API。
 *
 * <p>3E-04A：可见性过滤、目标教学班维护、8 处「取任务 + 课程管理权」守卫、
 * 状态/归属的服务端写入与截止日期宽松解析已全部下移到 {@link TaskApplicationService}；
 * 本类只保留路由、{@code @PreAuthorize} 与响应封装。</p>
 */
@RestController
@RequestMapping("/tasks")
@Tag(name = "任务管理", description = "提供作业任务的创建、查询、发布、截止日期延长、复制、批改等功能")
public class TaskController {

    private final TaskApplicationService taskApplicationService;

    public TaskController(TaskApplicationService taskApplicationService) {
        this.taskApplicationService = taskApplicationService;
    }

    /**
     * 分页查询任务（支持课程、教学班筛选）
     */
    @GetMapping
    @Operation(summary = "分页查询任务列表", description = "支持按课程ID、教学班ID筛选的分页查询")
    public ApiResponse<PagedResponse<TaskVO>> listByParams(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "课程ID（可选）") @RequestParam(required = false) Long courseId,
            @Parameter(description = "教学班ID（可选，按教学班可见范围过滤）")
            @RequestParam(required = false) Long teachingClassId,
            Authentication auth) {
        return ApiResponse.success(taskApplicationService.listByParams(page, size, courseId, teachingClassId, auth));
    }

    /**
     * 根据课程ID分页查询任务
     */
    @GetMapping("/course/{courseId}")
    @Operation(summary = "按课程查询任务", description = "返回指定课程下的任务列表（按教学班可见范围过滤）")
    public ApiResponse<PagedResponse<TaskVO>> listByCourse(
            @Parameter(description = "课程ID") @PathVariable Long courseId,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "教学班ID（可选，按教学班可见范围过滤）")
            @RequestParam(required = false) Long teachingClassId,
            Authentication auth) {
        return ApiResponse.success(taskApplicationService.listByCourse(courseId, page, size, teachingClassId, auth));
    }

    /**
     * 根据ID查询任务详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取任务详情", description = "根据任务ID返回完整任务信息，包含要求、附件等")
    public ApiResponse<TaskVO> getById(@Parameter(description = "任务ID") @PathVariable Long id,
                                       Authentication auth) {
        return ApiResponse.success(taskApplicationService.detail(id, auth));
    }

    /**
     * 查询任务目标教学班
     */
    @GetMapping("/{id}/targets")
    @Operation(summary = "查询任务目标教学班", description = "返回任务可见范围与目标教学班ID")
    public ApiResponse<Map<String, Object>> getTargets(@Parameter(description = "任务ID") @PathVariable Long id,
                                                       Authentication auth) {
        return ApiResponse.success(taskApplicationService.targets(id, auth));
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
        return ApiResponse.success("设置成功", taskApplicationService.updateTargets(id, targets, auth));
    }

    /**
     * 新增任务（教师/管理员权限）
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "创建新任务", description = "教师或管理员创建新的作业/测验/项目任务")
    public ApiResponse<TaskVO> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "任务创建信息"
    ) @Valid @RequestBody TaskSaveDTO dto,
                                    Authentication auth) {
        // creatorId/status/isPublished 一律服务端写入——否则创建即可自标「已发布」，
        // 绕过 /publish 的状态机与通知；创建人取登录身份（原实现依赖客户端传入，UI 建任务会丢归属）。
        return ApiResponse.success("创建成功", taskApplicationService.create(dto, auth));
    }

    /**
     * 修改任务（教师/管理员权限）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "更新任务信息", description = "教师或管理员可修改任务内容、截止日期等")
    public ApiResponse<TaskVO> update(@Parameter(description = "任务ID") @PathVariable Long id,
                                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                            description = "待更新的任务数据"
                                    ) @Valid @RequestBody TaskSaveDTO dto,
                                    Authentication auth) {
        return ApiResponse.success("更新成功", taskApplicationService.update(id, dto, auth));
    }

    /**
     * 删除任务（教师/管理员权限）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "删除任务", description = "教师或管理员可删除任务")
    public ApiResponse<Void> delete(@Parameter(description = "任务ID") @PathVariable Long id,
                                    Authentication auth) {
        taskApplicationService.delete(id, auth);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 发布任务（教师权限；管理员治理只读，不代办发布）
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "发布任务", description = "教师将草稿状态的任务发布给学生可见；管理员调用返回 403")
    public ApiResponse<TaskVO> publish(@Parameter(description = "任务ID") @PathVariable Long id,
                                       Authentication auth) {
        return ApiResponse.success("发布成功", taskApplicationService.publish(id, auth));
    }

    /**
     * 延长截止日期（教师/管理员权限）
     */
    @PostMapping("/{id}/extend-deadline")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "延长截止日期", description = "延长任务的截止时间并记录原因")
    public ApiResponse<TaskVO> extendDeadline(
            @Parameter(description = "任务ID") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "包含新截止日期(newDueDate)和原因(reason)的任务创建DTO"
            ) @Valid @RequestBody TaskCreateDTO dto,
            Authentication auth) {
        // B-13 ⑨：newDueDate 与全局反序列化同口径（LenientDateTimeParser），解析失败仍是 400
        return ApiResponse.success("截止日期已延长",
                taskApplicationService.extendDeadline(id, dto.getNewDueDate(), dto.getReason(), auth));
    }

    /**
     * 复制任务（教师/管理员权限）
     */
    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "复制任务", description = "基于已有任务快速创建副本，可自定义新标题")
    public ApiResponse<TaskVO> copy(
            @Parameter(description = "源任务ID") @PathVariable Long id,
            @Valid @RequestBody TaskCopyDTO body,
            Authentication auth) {
        return ApiResponse.success("复制成功", taskApplicationService.copy(id, body.getNewTitle(), auth));
    }

    /**
     * 获取待批阅的任务列表（教师权限）
     */
    @GetMapping("/pending-grading")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取待批阅任务列表", description = "返回当前教师有待批阅提交的任务列表")
    public ApiResponse<List<TaskVO>> pendingGrading(Authentication auth) {
        return ApiResponse.success(taskApplicationService.pendingGrading(auth));
    }

    /**
     * 获取任务完成率统计（增强版）
     */
    @GetMapping("/{id}/completion-stats")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取任务完成率统计", description = "返回指定任务的提交完成情况统计")
    public ApiResponse<TaskCompletionStatsVO> completionStats(@Parameter(description = "任务ID") @PathVariable Long id,
                                                             Authentication auth) {
        return ApiResponse.success(taskApplicationService.completionStats(id, auth));
    }
}
