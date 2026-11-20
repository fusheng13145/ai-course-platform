package com.aicourse.security;

import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.Resource;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * 资源控制器拆分的共享 ACL。
 *
 * <p>查询 / 上传 / 变更三个控制器共用同一套课程与资源访问判定，避免拆分后
 * 各自复制一份 canAccessCourse / canAccessResource 而产生分叉。</p>
 */
@Component
public class ResourceAccessSupport {

    private final CourseService courseService;
    private final CourseEnrollmentService courseEnrollmentService;
    private final AdminScopeGuard adminScopeGuard;

    public ResourceAccessSupport(CourseService courseService,
                                 CourseEnrollmentService courseEnrollmentService,
                                 AdminScopeGuard adminScopeGuard) {
        this.courseService = courseService;
        this.courseEnrollmentService = courseEnrollmentService;
        this.adminScopeGuard = adminScopeGuard;
    }

    public boolean isAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + RoleConstants.ADMIN)::equals);
    }

    public boolean isTeacher(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + RoleConstants.TEACHER)::equals);
    }

    /**
     * 单个资源访问权限校验（修复 P1-6）：下载 / 详情 / 删除 / 可见性切换复用。
     */
    public boolean canAccessResource(Resource resource, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            return false;
        }
        Long userId = (Long) auth.getPrincipal();
        if (isAdmin(auth)) {
            if (resource.getCourseId() == null) {
                // 全局资源保留既有「登录可读」口径；管理员可读，非管理员仍按下方课程成员规则拒绝。
                return true;
            }
            return canAdminAccessCourse(resource.getCourseId());
        }
        if (isTeacher(auth)) {
            Course course = resource.getCourseId() != null ? courseService.getById(resource.getCourseId()) : null;
            return course != null && userId.equals(course.getTeacherId());
        }
        // 学生：必须是该课程**有效且已审批通过**的成员
        // （修复 2026-09-14：原先只查 is_active，待审批/已驳回的报名记录也能下载资源）
        if (resource.getCourseId() == null) {
            return false;
        }
        return courseEnrollmentService.hasApprovedEnrollment(userId, resource.getCourseId());
    }

    /**
     * 课程级访问权限校验（DR-16 资源列表越权修复）。
     * <p>此前 listByCourse / tree / stats / chapter / search 等列表端点**无任何 ACL**，
     * 任意已登录用户可枚举任意课程的资源（含 {@code /uploads/<uuid>} 路径），再经
     * {@code /uploads/**}（已受登录保护）直链下载，构成跨课程越权读取。</p>
     * 规则与 {@link #canAccessResource} 对齐：管理员按学院作用域（校级全平台）；
     * 教师须是该课程 teacherId；学生须 {@code hasApprovedEnrollment}。返回 false 的调用方应返回 403。
     */
    public boolean canAccessCourse(Long courseId, Authentication auth) {
        if (courseId == null) {
            return false;
        }
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            return false;
        }
        Long userId = (Long) auth.getPrincipal();
        if (isAdmin(auth)) {
            return canAdminAccessCourse(courseId);
        }
        if (isTeacher(auth)) {
            Course course = courseService.getById(courseId);
            return course != null && userId.equals(course.getTeacherId());
        }
        return courseEnrollmentService.hasApprovedEnrollment(userId, courseId);
    }

    /** 管理员课程作用域：院系管理员只能读本学院（含教师学院回退），校级全平台。 */
    private boolean canAdminAccessCourse(Long courseId) {
        if (courseId == null) {
            return false;
        }
        try {
            adminScopeGuard.assertCourseAccess(courseService.getById(courseId));
            return true;
        } catch (AccessDeniedException e) {
            return false;
        }
    }
}
