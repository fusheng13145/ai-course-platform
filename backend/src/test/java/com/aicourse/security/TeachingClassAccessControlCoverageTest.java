package com.aicourse.security;

import com.aicourse.common.RoleConstants;
import com.aicourse.entity.Course;
import com.aicourse.entity.TeachingClass;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.TeachingClassMapper;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TeachingClassAccessControl 覆盖率补测（I-04 第七批）。
 *
 * <p>覆盖空 ID/不存在、课程负责人与教学班主讲判定、管理员学院作用域、
 * 名单读写权限、教学班可读性、课程教学班列表、主讲学院校验。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeachingClassAccessControlCoverageTest {

    @Mock private CourseService courseService;
    @Mock private UserService userService;
    @Mock private TeachingClassMapper teachingClassMapper;
    @Mock private AdminScopeGuard adminScopeGuard;

    private TeachingClassAccessControl control;

    @BeforeEach
    void setUp() {
        control = new TeachingClassAccessControl(courseService, userService,
                teachingClassMapper, adminScopeGuard);
        lenient().when(adminScopeGuard.isCollegeAdmin()).thenReturn(false);
    }

    private Course course(Long id, Long teacherId, Long collegeId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        c.setCollegeId(collegeId);
        return c;
    }

    private TeachingClass tc(Long id, Long courseId, Long leadId) {
        TeachingClass t = new TeachingClass();
        t.setId(id);
        t.setCourseId(courseId);
        t.setLeadTeacherId(leadId);
        return t;
    }

    private User user(Long id, String role, Long collegeId) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setCollegeId(collegeId);
        return u;
    }

    private Authentication adminAuth() {
        Authentication auth = mock(Authentication.class);
        lenient().doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + RoleConstants.ADMIN)))
                .when(auth).getAuthorities();
        lenient().when(auth.getPrincipal()).thenReturn(99L);
        return auth;
    }

    private Authentication teacherAuth(Long principalId) {
        Authentication auth = mock(Authentication.class);
        lenient().doReturn(List.of()).when(auth).getAuthorities();
        lenient().when(auth.getPrincipal()).thenReturn(principalId);
        return auth;
    }

    @Test
    @DisplayName("requireCourse：null/不存在抛 404，存在返回")
    void requireCourse_boundaries() {
        assertThrows(BusinessException.class, () -> control.requireCourse(null));
        when(courseService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> control.requireCourse(1L));

        Course c = course(2L, 10L, 20L);
        when(courseService.getById(2L)).thenReturn(c);
        assertSame(c, control.requireCourse(2L));
    }

    @Test
    @DisplayName("assertCourseOwner：管理员走学院作用域，非负责人 403，负责人放行")
    void courseOwner_adminAndTeacher() {
        Course c = course(1L, 10L, 20L);
        when(courseService.getById(1L)).thenReturn(c);

        when(userService.getById(99L)).thenReturn(user(99L, RoleConstants.ADMIN, 20L));
        assertSame(c, control.assertCourseOwner(1L, 99L));
        verify(adminScopeGuard).assertCourseAccess(c);

        when(userService.getById(11L)).thenReturn(user(11L, RoleConstants.TEACHER, 20L));
        assertThrows(BusinessException.class, () -> control.assertCourseOwner(1L, 11L));

        assertSame(c, control.assertCourseOwner(1L, 10L));
    }

    @Test
    @DisplayName("requireTeachingClass：null/不存在抛 404，存在返回")
    void requireTeachingClass_boundaries() {
        assertThrows(BusinessException.class, () -> control.requireTeachingClass(null));
        when(teachingClassMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> control.requireTeachingClass(1L));

        TeachingClass t = tc(2L, 3L, 4L);
        when(teachingClassMapper.selectById(2L)).thenReturn(t);
        assertSame(t, control.requireTeachingClass(2L));
    }

    @Test
    @DisplayName("assertTeachingClassLead：管理员(校级/院级)放行，非主讲 403，主讲放行")
    void teachingClassLead_adminAndLead() {
        TeachingClass t = tc(1L, 2L, 3L);
        when(teachingClassMapper.selectById(1L)).thenReturn(t);
        when(courseService.getById(2L)).thenReturn(course(2L, 3L, 20L));

        when(userService.getById(99L)).thenReturn(user(99L, RoleConstants.ADMIN, 20L));
        assertSame(t, control.assertTeachingClassLead(1L, 99L));

        when(adminScopeGuard.isCollegeAdmin()).thenReturn(true);
        assertSame(t, control.assertTeachingClassLead(1L, 99L));
        verify(adminScopeGuard).assertCourseAccess(any(Course.class));

        assertSame(t, control.assertTeachingClassLead(1L, 3L));
        assertThrows(BusinessException.class, () -> control.assertTeachingClassLead(1L, 8L));
    }

    @Test
    @DisplayName("assertRosterManage：本班主讲/课程负责人放行，其他人 403")
    void rosterManage() {
        TeachingClass t = tc(1L, 2L, 3L);
        when(teachingClassMapper.selectById(1L)).thenReturn(t);
        when(courseService.getById(2L)).thenReturn(course(2L, 10L, 20L));

        assertSame(t, control.assertRosterManage(1L, 3L));
        assertSame(t, control.assertRosterManage(1L, 10L));
        assertThrows(BusinessException.class, () -> control.assertRosterManage(1L, 11L));
    }

    @Test
    @DisplayName("assertRosterRead：管理员/本班主讲/课程负责人放行，其他人 403")
    void rosterRead() {
        TeachingClass t = tc(1L, 2L, 3L);
        when(teachingClassMapper.selectById(1L)).thenReturn(t);
        when(courseService.getById(2L)).thenReturn(course(2L, 10L, 20L));

        when(userService.getById(99L)).thenReturn(user(99L, RoleConstants.ADMIN, 20L));
        assertSame(t, control.assertRosterRead(1L, 99L));

        assertSame(t, control.assertRosterRead(1L, 3L));
        assertSame(t, control.assertRosterRead(1L, 10L));
        assertThrows(BusinessException.class, () -> control.assertRosterRead(1L, 11L));
    }

    @Test
    @DisplayName("assertReadable：管理员/主讲/负责人放行，其他人 403")
    void readable() {
        TeachingClass t = tc(1L, 2L, 3L);
        when(teachingClassMapper.selectById(1L)).thenReturn(t);
        when(courseService.getById(2L)).thenReturn(course(2L, 10L, 20L));

        assertSame(t, control.assertReadable(1L, adminAuth()));
        assertSame(t, control.assertReadable(1L, teacherAuth(3L)));
        assertSame(t, control.assertReadable(1L, teacherAuth(10L)));
        assertThrows(BusinessException.class, () -> control.assertReadable(1L, teacherAuth(11L)));
    }

    @Test
    @DisplayName("assertCanViewCourseClasses：管理员/负责人/任一班主讲放行，其余 403")
    void canViewCourseClasses() {
        Course c = course(2L, 10L, 20L);
        when(courseService.getById(2L)).thenReturn(c);

        assertSame(c, control.assertCanViewCourseClasses(2L, adminAuth()));
        assertSame(c, control.assertCanViewCourseClasses(2L, teacherAuth(10L)));

        when(teachingClassMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertSame(c, control.assertCanViewCourseClasses(2L, teacherAuth(3L)));

        when(teachingClassMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        assertThrows(BusinessException.class, () -> control.assertCanViewCourseClasses(2L, teacherAuth(4L)));
        when(teachingClassMapper.selectCount(any(Wrapper.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> control.assertCanViewCourseClasses(2L, teacherAuth(4L)));
    }

    @Test
    @DisplayName("assertLeadTeacherInCourseCollege：教师不存在/非教师/学院不匹配 400/403，匹配放行")
    void leadTeacherCollege() {
        Course c = course(2L, null, null);
        when(userService.getById(5L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> control.assertLeadTeacherInCourseCollege(c, 5L));

        when(userService.getById(6L)).thenReturn(user(6L, RoleConstants.STUDENT, 20L));
        assertThrows(BusinessException.class, () -> control.assertLeadTeacherInCourseCollege(c, 6L));

        when(userService.getById(7L)).thenReturn(user(7L, RoleConstants.TEACHER, 20L));
        // 课程学院为空且负责人不存在 -> courseCollegeId 仍为 null
        assertThrows(BusinessException.class, () -> control.assertLeadTeacherInCourseCollege(c, 7L));

        // 课程学院为空，回退负责人学院
        Course ownerCourse = course(3L, 8L, null);
        User owner = user(8L, RoleConstants.TEACHER, 20L);
        User lead = user(9L, RoleConstants.TEACHER, 20L);
        when(userService.getById(8L)).thenReturn(owner);
        when(userService.getById(9L)).thenReturn(lead);
        assertSame(lead, control.assertLeadTeacherInCourseCollege(ownerCourse, 9L));

        // 学院不匹配
        when(userService.getById(12L)).thenReturn(user(12L, RoleConstants.TEACHER, 21L));
        assertThrows(BusinessException.class, () -> control.assertLeadTeacherInCourseCollege(
                course(4L, 8L, 20L), 12L));
    }

    @Test
    @DisplayName("assertReadable：auth 没有权限集合时按普通用户处理")
    void readable_nullAuthorities() {
        TeachingClass t = tc(1L, 2L, 3L);
        when(teachingClassMapper.selectById(1L)).thenReturn(t);
        when(courseService.getById(2L)).thenReturn(course(2L, 10L, 20L));

        Authentication auth = mock(Authentication.class);
        when(auth.getAuthorities()).thenReturn(null);
        when(auth.getPrincipal()).thenReturn(3L);

        assertSame(t, control.assertReadable(1L, auth));
    }
}
