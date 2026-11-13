package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Task;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 课程内容的对象级授权：课程归属、选课关系与任务读权限。
 * 控制器在进入写操作或读取任务正文前调用，避免只做角色校验造成跨教师/跨课程越权。
 */
@Component
public class CourseAccessControl {

    private final CourseService courseService;
    private final CourseEnrollmentService enrollmentService;

    public CourseAccessControl(CourseService courseService,
                               CourseEnrollmentService enrollmentService) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
    }

    public void assertCanManageCourse(Long courseId, Authentication auth) {
        if (isAdmin(auth)) {
            return;
        }
        if (isStudent(auth)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "学生无权管理课程内容");
        }
        Course course = requireCourse(courseId);
        if (!principalId(auth).equals(course.getTeacherId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权操作其他教师的课程");
        }
    }

    public void assertCanReadCourse(Long courseId, Authentication auth) {
        if (isAdmin(auth)) {
            return;
        }
        Course course = requireCourse(courseId);
        Long userId = principalId(auth);
        if (isStudent(auth)) {
            if (!enrollmentService.hasApprovedEnrollment(userId, course.getId())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "尚未加入该课程，无权查看课程内容");
            }
            return;
        }
        if (!userId.equals(course.getTeacherId())) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权查看其他教师课程的内容");
        }
    }

    public void assertCanReadTask(Task task, Authentication auth) {
        if (task == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "任务不存在");
        }
        if (isAdmin(auth)) {
            return;
        }
        if (isStudent(auth)) {
            boolean published = "PUBLISHED".equals(task.getStatus())
                    || Boolean.TRUE.equals(task.getIsPublished());
            if (!published) {
                throw new BusinessException(ApiCode.FORBIDDEN, "任务尚未发布，无权查看");
            }
            assertCanReadCourse(task.getCourseId(), auth);
            return;
        }
        assertCanManageCourse(task.getCourseId(), auth);
    }

    public List<Long> readableCourseIds(Authentication auth) {
        if (isAdmin(auth)) {
            return null;
        }
        Long userId = principalId(auth);
        if (isStudent(auth)) {
            return enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>()
                            .eq(CourseEnrollment::getStudentId, userId)
                            .eq(CourseEnrollment::getApprovalStatus, "APPROVED")
                            .eq(CourseEnrollment::getIsActive, true))
                    .stream()
                    .map(CourseEnrollment::getCourseId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        if (hasRole(auth, RoleConstants.TEACHER)) {
            return courseService.list(new LambdaQueryWrapper<Course>()
                            .eq(Course::getTeacherId, userId))
                    .stream()
                    .map(Course::getId)
                    .filter(Objects::nonNull)
                    .toList();
        }
        return List.of();
    }

    public boolean isStudent(Authentication auth) {
        return hasRole(auth, RoleConstants.STUDENT);
    }

    private Course requireCourse(Long courseId) {
        if (courseId == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "课程ID不能为空");
        }
        Course course = courseService.getById(courseId);
        if (course == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "课程不存在");
        }
        return course;
    }

    private boolean isAdmin(Authentication auth) {
        return hasRole(auth, RoleConstants.ADMIN);
    }

    private boolean hasRole(Authentication auth, String role) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + role)::equals);
    }

    private Long principalId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }
}
