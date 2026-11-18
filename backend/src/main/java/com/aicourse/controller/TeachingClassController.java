package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.dto.TeachingClassCreateDTO;
import com.aicourse.dto.TeachingClassUpdateDTO;
import com.aicourse.entity.AdminClass;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.TeachingClassAdminClass;
import com.aicourse.security.AdminScopeGuard;
import com.aicourse.security.TeachingClassAccessControl;
import com.aicourse.service.RosterService;
import com.aicourse.service.TeachingClassService;
import com.aicourse.vo.RosterSyncResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 教学班接口控制器（V14 / B-12-P-08 WP4；WP5 增加名单导入与重同步）。
 *
 * <p>写操作对课程负责人、本班主讲或管理员开放（服务层二次校验）；读取名单/成绩仅对该教学班主讲开放。</p>
 */
@RestController
@RequestMapping("/teaching-classes")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
@Tag(name = "教学班", description = "课程下的教学班、主讲归属与名单")
public class TeachingClassController {

    private final TeachingClassService teachingClassService;
    private final RosterService rosterService;
    private final AdminScopeGuard adminScopeGuard;
    private final TeachingClassAccessControl teachingClassAccessControl;

    public TeachingClassController(TeachingClassService teachingClassService,
                                   RosterService rosterService,
                                   AdminScopeGuard adminScopeGuard,
                                   TeachingClassAccessControl teachingClassAccessControl) {
        this.teachingClassService = teachingClassService;
        this.rosterService = rosterService;
        this.adminScopeGuard = adminScopeGuard;
        this.teachingClassAccessControl = teachingClassAccessControl;
    }

    @GetMapping("/course/{courseId}")
    @Operation(summary = "课程下的教学班列表")
    public ApiResponse<List<TeachingClass>> listByCourse(@PathVariable Long courseId, Authentication auth) {
        Course course = teachingClassAccessControl.assertCanViewCourseClasses(courseId, auth);
        List<TeachingClass> classes = teachingClassService.listByCourse(courseId);
        // 非课程负责人（例如仅是本课程某教学班主讲）只返回本人教学班，避免全课程枚举。
        if (!isAdmin(auth) && !Objects.equals(course.getTeacherId(), (Long) auth.getPrincipal())) {
            Long userId = (Long) auth.getPrincipal();
            classes = classes.stream()
                    .filter(c -> Objects.equals(c.getLeadTeacherId(), userId))
                    .toList();
        }
        return ApiResponse.success(classes);
    }

    @GetMapping("/{id}")
    @Operation(summary = "教学班详情")
    public ApiResponse<TeachingClass> detail(@PathVariable Long id, Authentication auth) {
        teachingClassAccessControl.assertReadable(id, auth);
        return ApiResponse.success(teachingClassService.getById(id));
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities() != null
                && auth.getAuthorities().stream()
                .anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "创建教学班", description = "课程负责人或管理员；课程未通过审批时创建为 DRAFT")
    public ApiResponse<TeachingClass> create(@Valid @RequestBody TeachingClassCreateDTO body) {
        return ApiResponse.success("教学班创建成功", teachingClassService.create(
                adminScopeGuard.currentUserId(), body.getCourseId(), body.getName(),
                body.getCode(), body.getLeadTeacherId(), body.getCapacity()));
    }

    @PostMapping("/course/{courseId}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "发布课程教学班", description = "课程负责人或管理员；将 DRAFT 教学班置为 ACTIVE")
    public ApiResponse<List<TeachingClass>> publishCourse(@PathVariable Long courseId) {
        return ApiResponse.success("教学班已发布",
                teachingClassService.publishCourseClasses(courseId, adminScopeGuard.currentUserId()));
    }

    @PostMapping("/{id}/lead-teacher")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "指定主讲", description = "课程负责人或管理员；只能指派本院教师")
    public ApiResponse<TeachingClass> assignLeadTeacher(@PathVariable Long id,
                                                        @RequestParam Long teacherId) {
        return ApiResponse.success("主讲已更新",
                teachingClassService.assignLeadTeacher(id, teacherId, adminScopeGuard.currentUserId()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Operation(summary = "修改教学班基础信息", description = "字段为 null 表示不修改；ACTIVE 要求课程已通过审批")
    public ApiResponse<TeachingClass> update(@PathVariable Long id,
                                             @Valid @RequestBody TeachingClassUpdateDTO body) {
        return ApiResponse.success("教学班已更新", teachingClassService.updateBasic(
                id, adminScopeGuard.currentUserId(), body.getName(), body.getCode(),
                body.getCapacity(), body.getStatus()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
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

    @GetMapping("/{id}/members/detail")
    @Operation(summary = "教学班名单（含学生信息）", description = "课程负责人、本班主讲或管理员可读")
    public ApiResponse<List<com.aicourse.vo.TeachingClassMemberVO>> memberDetails(@PathVariable Long id) {
        return ApiResponse.success(
                teachingClassService.listMemberDetails(id, adminScopeGuard.currentUserId()));
    }

    // ==================== 行政班名单（WP5） ====================

    @GetMapping("/{id}/admin-classes")
    @Operation(summary = "教学班的行政班来源", description = "课程负责人/本班主讲/管理员可读")
    public ApiResponse<List<TeachingClassAdminClass>> listAdminClasses(@PathVariable Long id) {
        return ApiResponse.success(rosterService.listSources(id, adminScopeGuard.currentUserId()));
    }

    @GetMapping("/{id}/roster/candidates")
    @Operation(summary = "可导入的行政班", description = "课程所属学院的启用行政班；课程负责人/本班主讲/管理员可读")
    public ApiResponse<List<AdminClass>> rosterCandidates(@PathVariable Long id) {
        return ApiResponse.success(
                rosterService.listCandidates(id, adminScopeGuard.currentUserId()));
    }

    @PostMapping("/{id}/roster/import")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "按行政班导入名单", description = "生成 ADMIN_CLASS_IMPORT；重复导入幂等，不覆盖既有有效选课")
    public ApiResponse<RosterSyncResultVO> importRoster(@PathVariable Long id, @RequestParam Long adminClassId) {
        return ApiResponse.success("名单导入完成",
                rosterService.importFromAdminClass(id, adminClassId, adminScopeGuard.currentUserId()));
    }

    @PostMapping("/{id}/roster/resync")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "重同步名单", description = "返回 added/removed/skipped；不删除手工与自助记录")
    public ApiResponse<RosterSyncResultVO> resyncRoster(
            @PathVariable Long id,
            @RequestParam(required = false) Long adminClassId) {
        return ApiResponse.success("名单已重同步",
                rosterService.resync(id, adminClassId, adminScopeGuard.currentUserId()));
    }
}
