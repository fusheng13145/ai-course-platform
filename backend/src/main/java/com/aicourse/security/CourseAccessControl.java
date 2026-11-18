package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.CourseEnrollment;
import com.aicourse.entity.Task;
import com.aicourse.entity.TeachingClass;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private final TeachingClassMapper teachingClassMapper;
    private final AdminScopeGuard adminScopeGuard;

    public CourseAccessControl(CourseService courseService,
                               CourseEnrollmentService enrollmentService,
                               TeachingClassMapper teachingClassMapper,
                               AdminScopeGuard adminScopeGuard) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.teachingClassMapper = teachingClassMapper;
        this.adminScopeGuard = adminScopeGuard;
    }

    public void assertCanManageCourse(Long courseId, Authentication auth) {
        // 角色判定先于课程查询：学生无论课程是否存在都只得到 403，不泄露课程存在性。
        if (isStudent(auth)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "学生无权管理课程内容");
        }
        Course course = requireCourse(courseId);
        if (isAdmin(auth)) {
            adminScopeGuard.assertCourseAccess(course);
            return;
        }
        if (!principalId(auth).equals(course.getTeacherId())
                && !isCourseSectionLead(course.getId(), principalId(auth))) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权操作该课程（需为课程负责人或本课程教学班主讲）");
        }
    }

    public void assertCanReadCourse(Long courseId, Authentication auth) {
        Course course = requireCourse(courseId);
        if (isAdmin(auth)) {
            adminScopeGuard.assertCourseAccess(course);
            return;
        }
        Long userId = principalId(auth);
        if (isStudent(auth)) {
            if (!enrollmentService.hasApprovedEnrollment(userId, course.getId())) {
                throw new BusinessException(ApiCode.FORBIDDEN, "尚未加入该课程，无权查看课程内容");
            }
            return;
        }
        if (!userId.equals(course.getTeacherId())
                && !isCourseSectionLead(course.getId(), userId)) {
            throw new BusinessException(ApiCode.FORBIDDEN, "无权查看该课程内容（需为课程负责人或本课程教学班主讲）");
        }
    }

    public void assertCanReadTask(Task task, Authentication auth) {
        if (task == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "任务不存在");
        }
        if (isAdmin(auth)) {
            adminScopeGuard.assertCourseAccess(requireCourse(task.getCourseId()));
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
            Long adminCollegeId = adminScopeGuard.currentCollegeFilter();
            if (adminCollegeId == null) {
                return null;
            }
            List<Long> ids = new ArrayList<>(courseService.list(new LambdaQueryWrapper<Course>()
                            .eq(Course::getCollegeId, adminCollegeId))
                    .stream()
                    .map(Course::getId)
                    .filter(Objects::nonNull)
                    .toList());
            List<Long> collegeMembers = adminScopeGuard.currentCollegeUserIds();
            if (collegeMembers != null && !collegeMembers.isEmpty()) {
                courseService.list(new LambdaQueryWrapper<Course>()
                                .isNull(Course::getCollegeId)
                                .in(Course::getTeacherId, collegeMembers))
                        .stream()
                        .map(Course::getId)
                        .filter(Objects::nonNull)
                        .forEach(ids::add);
            }
            return ids.stream().distinct().toList();
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
            List<Long> ids = new ArrayList<>(courseService.list(new LambdaQueryWrapper<Course>()
                            .eq(Course::getTeacherId, userId))
                    .stream()
                    .map(Course::getId)
                    .filter(Objects::nonNull)
                    .toList());
            teachingClassMapper.selectList(new LambdaQueryWrapper<TeachingClass>()
                            .eq(TeachingClass::getLeadTeacherId, userId))
                    .stream()
                    .map(TeachingClass::getCourseId)
                    .filter(Objects::nonNull)
                    .forEach(ids::add);
            return ids.stream().distinct().toList();
        }
        return List.of();
    }

    public boolean isStudent(Authentication auth) {
        return hasRole(auth, RoleConstants.STUDENT);
    }

    /** 教师是否是这门课任一教学班的主讲（V28 多教学班模型）。 */
    private boolean isCourseSectionLead(Long courseId, Long userId) {
        if (courseId == null || userId == null) {
            return false;
        }
        return teachingClassMapper.selectCount(new LambdaQueryWrapper<TeachingClass>()
                .eq(TeachingClass::getCourseId, courseId)
                .eq(TeachingClass::getLeadTeacherId, userId)) > 0;
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
