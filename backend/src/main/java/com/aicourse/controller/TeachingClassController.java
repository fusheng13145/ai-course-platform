package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.dto.TeachingClassCreateDTO;
import com.aicourse.dto.TeachingClassUpdateDTO;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.service.TeachingClassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教学班接口控制器（V14 / B-12-P-08 WP4）。
 *
 * <p>写操作只对课程负责人开放（服务层二次校验）；读取名单/成绩仅对该教学班主讲开放。</p>
 */
@RestController
@RequestMapping("/teaching-classes")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "教学班", description = "课程下的教学班、主讲归属与名单")
public class TeachingClassController {

    private final TeachingClassService teachingClassService;
    private final AdminScopeGuard adminScopeGuard;

    public TeachingClassController(TeachingClassService teachingClassService, AdminScopeGuard adminScopeGuard) {
        this.teachingClassService = teachingClassService;
        this.adminScopeGuard = adminScopeGuard;
    }

    @GetMapping("/course/{courseId}")
    @Operation(summary = "课程下的教学班列表")
    public ApiResponse<List<TeachingClass>> listByCourse(@PathVariable Long courseId) {
        return ApiResponse.success(teachingClassService.listByCourse(courseId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "教学班详情")
    public ApiResponse<TeachingClass> detail(@PathVariable Long id) {
        return ApiResponse.success(teachingClassService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "创建教学班", description = "仅课程负责人；课程未通过审批时创建为 DRAFT")
    public ApiResponse<TeachingClass> create(@Valid @RequestBody TeachingClassCreateDTO body) {
        return ApiResponse.success("教学班创建成功", teachingClassService.create(
                adminScopeGuard.currentUserId(), body.getCourseId(), body.getName(),
                body.getCode(), body.getLeadTeacherId(), body.getCapacity()));
    }

    @PostMapping("/{id}/lead-teacher")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "指定主讲", description = "仅课程负责人；只能指派本院教师")
    public ApiResponse<TeachingClass> assignLeadTeacher(@PathVariable Long id,
                                                        @RequestParam Long teacherId) {
        return ApiResponse.success("主讲已更新",
                teachingClassService.assignLeadTeacher(id, teacherId, adminScopeGuard.currentUserId()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "修改教学班基础信息", description = "字段为 null 表示不修改；ACTIVE 要求课程已通过审批")
    public ApiResponse<TeachingClass> update(@PathVariable Long id,
                                             @Valid @RequestBody TeachingClassUpdateDTO body) {
        return ApiResponse.success("教学班已更新", teachingClassService.updateBasic(
                id, adminScopeGuard.currentUserId(), body.getName(), body.getCode(),
                body.getCapacity(), body.getStatus()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "删除教学班", description = "有学生名单时拒绝删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        teachingClassService.delete(id, adminScopeGuard.currentUserId());
        return ApiResponse.success("教学班已删除", null);
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "教学班名单", description = "仅本教学班主讲可读；管理员治理只读放行")
    public ApiResponse<List<CourseEnrollment>> members(@PathVariable Long id) {
        return ApiResponse.success(teachingClassService.listMembers(id, adminScopeGuard.currentUserId()));
    }
}
