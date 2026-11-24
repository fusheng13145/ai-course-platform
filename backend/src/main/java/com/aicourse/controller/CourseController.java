package com.aicourse.controller;

import com.aicourse.application.course.CourseApplicationService;
import com.aicourse.common.ApiResponse;
import com.aicourse.common.PagedResponse;
import com.aicourse.dto.CourseRejectDTO;
import com.aicourse.dto.CourseSaveDTO;
import com.aicourse.vo.CourseStatsVO;
import com.aicourse.vo.CourseVOs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程管理接口控制器
 * 提供课程的增删改查、审批、选课管理等 RESTful API。
 *
 * <p>3E-04B：5 处 {@code assertCourseOwner} + 1 处 {@code assertCollegeScope}、
 * 学院范围过滤、4 处 wrapper、3 处分页转 VO，以及「管理员代建必须显式 teacherId」
 * 「课程库稳定信息由服务层置空」两条口径，全部下移到 {@link CourseApplicationService}。
 * 本类只剩路由、{@code @PreAuthorize} 与响应封装。</p>
 */
@RestController
@RequestMapping("/courses")
@Tag(name = "课程管理", description = "提供课程的新增、查询、修改、删除、审批、选课统计等功能")
public class CourseController {

    private final CourseApplicationService courseApplicationService;

    public CourseController(CourseApplicationService courseApplicationService) {
        this.courseApplicationService = courseApplicationService;
    }

    /**
     * 分页查询课程列表（支持状态、关键词筛选）
     */
    @GetMapping
    @Operation(summary = "分页查询课程列表", description = "支持按关键词搜索和状态筛选的分页查询")
    public ApiResponse<PagedResponse<CourseVOs.CourseVO>> list(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "搜索关键词（匹配课程名称）") @RequestParam(required = false) String keyword,
            @Parameter(description = "课程状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "审批状态筛选（PENDING 即审批队列）") @RequestParam(required = false) String approvalStatus,
            @Parameter(description = "学院ID筛选（院系管理员只能查本院）") @RequestParam(required = false) Long collegeId,
            @Parameter(description = "课程库定义ID筛选") @RequestParam(required = false) Long definitionId,
            @Parameter(description = "学期ID筛选") @RequestParam(required = false) Long semesterId) {
        return ApiResponse.success(courseApplicationService.list(
                page, size, keyword, status, approvalStatus, collegeId, definitionId, semesterId));
    }

    /**
     * 根据ID查询课程详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取课程详情", description = "根据课程ID返回完整课程信息")
    public ApiResponse<CourseVOs.CourseVO> getById(@Parameter(description = "课程ID") @PathVariable Long id) {
        return ApiResponse.success(courseApplicationService.detail(id));
    }

    /**
     * 新增开课任务（教师/管理员；V28 三层模型）。
     * 教师：授课教师固定为本人；管理员：必须显式指定 teacherId，可代建。
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "创建开课任务",
            description = "引用课程库定义，指定学期、授课教师与计划教学班数；提交后进入审批")
    public ApiResponse<CourseVOs.CourseVO> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "开课任务信息；至少包含 definitionId、semesterId、teacherId（管理员）"
    ) @Valid @RequestBody CourseSaveDTO dto, Authentication auth) {
        return ApiResponse.success("创建成功", courseApplicationService.create(dto, auth));
    }

    /**
     * 修改课程信息（课程创建者权限；WP9 管理员只读矩阵——管理员 403）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "更新课程信息", description = "课程创建者可修改课程信息")
    public ApiResponse<CourseVOs.CourseVO> update(@Parameter(description = "课程ID") @PathVariable Long id,
                                      @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                              description = "待更新的课程数据"
                                      ) @Valid @RequestBody CourseSaveDTO dto,
                                      Authentication auth) {
        return ApiResponse.success("更新成功", courseApplicationService.update(id, dto, auth));
    }

    /**
     * 结课归档（课程负责人或管理员；结课后教学班关闭，选课入口关闭）
     */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "结课归档", description = "写入结课时间，关闭课程及其教学班")
    public ApiResponse<CourseVOs.CourseVO> complete(@Parameter(description = "课程ID") @PathVariable Long id,
                                                    Authentication auth) {
        return ApiResponse.success("已结课", courseApplicationService.complete(id, auth));
    }

    /**
     * 删除课程（教师/管理员权限，教师仅能删除自己创建的课程）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "删除课程", description = "教师或管理员可删除课程（软删除），教师仅能删除自己创建的课程")
    public ApiResponse<Void> delete(@Parameter(description = "课程ID") @PathVariable Long id, Authentication auth) {
        courseApplicationService.delete(id, auth);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 课程审批通过（仅管理员）
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "审批通过课程", description = "管理员审批通过课程申请，将状态设为ACTIVE")
    public ApiResponse<CourseVOs.CourseVO> approve(@Parameter(description = "课程ID") @PathVariable Long id,
                                                   Authentication auth) {
        return ApiResponse.success("审批成功", courseApplicationService.approve(id, auth));
    }

    /**
     * 切换课程启用/禁用状态（教师/管理员权限，教师仅能操作自己创建的课程）
     */
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    @Operation(summary = "切换课程状态", description = "在ACTIVE和INACTIVE之间切换课程状态，教师仅能操作自己创建的课程")
    public ApiResponse<CourseVOs.CourseVO> toggle(@Parameter(description = "课程ID") @PathVariable Long id,
                                                  Authentication auth) {
        return ApiResponse.success("状态切换成功", courseApplicationService.toggle(id, auth));
    }

    /**
     * 课程审批驳回（仅管理员）
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "驳回课程审批", description = "管理员驳回课程申请，可填写驳回原因")
    public ApiResponse<CourseVOs.CourseVO> reject(@Parameter(description = "课程ID") @PathVariable Long id,
                                      @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                              description = "驳回原因（可选）"
                                      ) @Valid @RequestBody(required = false) CourseRejectDTO body,
                                      Authentication auth) {
        String reason = body != null ? body.getReason() : null;
        return ApiResponse.success("已拒绝", courseApplicationService.reject(id, reason, auth));
    }

    /**
     * 重新提交被驳回的课程（教师仅限本人课程，院系管理员限本院）
     */
    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasRole('TEACHER')")  // WP9 管理员只读矩阵：教学写面不含 ADMIN
    @Operation(summary = "重新提交课程", description = "仅驳回状态可重提，历史审批流水保留")
    public ApiResponse<CourseVOs.CourseVO> resubmit(@Parameter(description = "课程ID") @PathVariable Long id,
                                                    Authentication auth) {
        return ApiResponse.success("已重新提交", courseApplicationService.resubmit(id, auth));
    }

    /**
     * 查询课程审批流水（教师仅限本人课程，院系管理员限本院）
     */
    @GetMapping("/{id}/approval-records")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取课程审批流水", description = "按时间正序返回提交/通过/驳回/重提记录")
    public ApiResponse<List<CourseVOs.ApprovalRecordVO>> approvalRecords(
            @Parameter(description = "课程ID") @PathVariable Long id, Authentication auth) {
        return ApiResponse.success(courseApplicationService.approvalRecords(id, auth));
    }

    /**
     * 查询当前用户已报名的课程（仅返回审批通过且有效的记录，V14 / WP5）
     */
    @GetMapping("/enrolled")
    @Operation(summary = "获取我的已报名课程", description = "返回当前登录用户的课程选课记录列表")
    public ApiResponse<PagedResponse<CourseVOs.EnrollmentVO>> enrolledCourses(
            Authentication auth,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(courseApplicationService.enrolledCourses(auth, page, size));
    }

    /**
     * 查询当前教师创建的课程
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "获取我创建的课程", description = "返回当前教师用户创建的所有课程列表")
    public ApiResponse<PagedResponse<CourseVOs.CourseVO>> myCourses(
            Authentication auth,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(courseApplicationService.myCourses(auth, page, size));
    }

    /**
     * 查询课程下所有报名学生（教师/管理员权限，教师仅能查看自己课程的学生名单）
     */
    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取课程学生列表", description = "返回指定课程的所有选课学生记录，教师仅能查看自己课程的学生名单")
    public ApiResponse<PagedResponse<CourseVOs.EnrollmentVO>> students(
            @Parameter(description = "课程ID") @PathVariable Long id,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            Authentication auth) {
        return ApiResponse.success(courseApplicationService.students(id, page, size, auth));
    }

    /**
     * 获取课程统计数据（仅课程教师或管理员可见）
     */
    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "获取课程统计数据", description = "返回课程的学生数、待审核数、状态等统计信息")
    public ApiResponse<CourseStatsVO> getStats(@Parameter(description = "课程ID") @PathVariable Long id,
                                               Authentication auth) {
        return ApiResponse.success(courseApplicationService.stats(id, auth));
    }
}
