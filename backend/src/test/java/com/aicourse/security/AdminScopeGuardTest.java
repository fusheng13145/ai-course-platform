package com.aicourse.security;

import com.aicourse.entity.Course;
import com.aicourse.entity.User;
import com.aicourse.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * AdminScopeGuard 单元测试（V12 / B-12-P-08 WP1）。
 *
 * <p>作用域 authority 由 JwtRequestFilter 每请求写入 SecurityContext；院系 ID 从 users 表现行数据读取。</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminScopeGuardTest {

    @Mock
    private UserService userService;

    private AdminScopeGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AdminScopeGuard(userService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Long userId, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId, null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    @DisplayName("校级管理员：不过滤学院，且可执行校级操作")
    void schoolAdmin() {
        authenticate(1L, "ROLE_ADMIN", "SCOPE_ADMIN_SCHOOL");

        assertTrue(guard.isSchoolAdmin());
        assertFalse(guard.isCollegeAdmin());
        assertNull(guard.currentCollegeFilter(), "校级管理员返回 null 表示不过滤");
        guard.assertSchoolOnly();
    }

    @Test
    @DisplayName("存量 ADMIN 无作用域 authority 视为校级（保持改造前行为）")
    void legacyAdminWithoutScopeIsSchoolLevel() {
        authenticate(1L, "ROLE_ADMIN");

        assertTrue(guard.isSchoolAdmin());
        assertNull(guard.currentCollegeFilter());
    }

    @Test
    @DisplayName("院系管理员：过滤本学院")
    void collegeAdmin() {
        authenticate(2L, "ROLE_ADMIN", "SCOPE_ADMIN_COLLEGE");
        User user = new User();
        user.setId(2L);
        user.setCollegeId(10L);
        when(userService.getById(2L)).thenReturn(user);

        assertTrue(guard.isCollegeAdmin());
        assertFalse(guard.isSchoolAdmin());
        assertEquals(10L, guard.currentCollegeFilter());
        guard.assertCollegeAccess(10L);
    }

    @Test
    @DisplayName("院系管理员访问其他学院被拒绝")
    void collegeAdminCannotAccessOtherCollege() {
        authenticate(2L, "ROLE_ADMIN", "SCOPE_ADMIN_COLLEGE");
        User user = new User();
        user.setId(2L);
        user.setCollegeId(10L);
        when(userService.getById(2L)).thenReturn(user);

        assertThrows(AccessDeniedException.class, () -> guard.assertCollegeAccess(11L));
        assertThrows(AccessDeniedException.class, () -> guard.assertCollegeAccess(null));
    }

    @Test
    @DisplayName("院系管理员不得执行仅校级可用的操作")
    void collegeAdminCannotRunSchoolOnlyAction() {
        authenticate(2L, "ROLE_ADMIN", "SCOPE_ADMIN_COLLEGE");

        assertThrows(AccessDeniedException.class, guard::assertSchoolOnly);
    }

    @Test
    @DisplayName("院系管理员未绑定学院时拒绝访问")
    void collegeAdminWithoutCollegeIsDenied() {
        authenticate(2L, "ROLE_ADMIN", "SCOPE_ADMIN_COLLEGE");

        assertThrows(AccessDeniedException.class, guard::currentCollegeFilter);
    }

    @Test
    @DisplayName("未认证时取当前用户ID被拒绝")
    void unauthenticatedIsDenied() {
        assertThrows(AccessDeniedException.class, guard::currentUserId);
    }

    @Test
    @DisplayName("非 ADMIN 即便带作用域 authority 也不被视为校级管理员")
    void nonAdminIsNotSchoolAdmin() {
        authenticate(3L, "ROLE_TEACHER", "SCOPE_ADMIN_SCHOOL");

        assertFalse(guard.isSchoolAdmin());
        assertThrows(AccessDeniedException.class, guard::assertSchoolOnly);
    }

    @Test
    @DisplayName("院系管理员只能访问本院用户，且不能管理 ADMIN 账号")
    void collegeAdminUserBoundary() {
        authenticate(2L, "ROLE_ADMIN", "SCOPE_ADMIN_COLLEGE");
        when(userService.getById(2L)).thenReturn(collegeAdmin(2L));

        User sameCollege = new User();
        sameCollege.setId(20L);
        sameCollege.setCollegeId(10L);
        sameCollege.setRole("TEACHER");
        guard.assertUserAccess(sameCollege);
        guard.assertUserManageable(sameCollege);

        User otherCollege = new User();
        otherCollege.setId(21L);
        otherCollege.setCollegeId(11L);
        otherCollege.setRole("TEACHER");
        assertThrows(AccessDeniedException.class, () -> guard.assertUserAccess(otherCollege));

        User adminTarget = new User();
        adminTarget.setId(22L);
        adminTarget.setCollegeId(10L);
        adminTarget.setRole("ADMIN");
        assertThrows(AccessDeniedException.class, () -> guard.assertUserManageable(adminTarget));
    }

    @Test
    @DisplayName("校级管理员不受用户/课程学院边界限制")
    void schoolAdminBypassesObjectBoundary() {
        authenticate(1L, "ROLE_ADMIN", "SCOPE_ADMIN_SCHOOL");
        User otherCollege = new User();
        otherCollege.setId(21L);
        otherCollege.setCollegeId(11L);
        otherCollege.setRole("ADMIN");
        guard.assertUserAccess(otherCollege);
        guard.assertUserManageable(otherCollege);

        Course course = new Course();
        course.setId(100L);
        course.setTeacherId(99L);
        guard.assertCourseAccess(course);
    }

    @Test
    @DisplayName("院系管理员只能访问本院教师的课程")
    void collegeAdminCourseBoundary() {
        authenticate(2L, "ROLE_ADMIN", "SCOPE_ADMIN_COLLEGE");
        when(userService.getById(2L)).thenReturn(collegeAdmin(2L));

        Course own = new Course();
        own.setId(100L);
        own.setTeacherId(10L);
        User ownTeacher = new User();
        ownTeacher.setId(10L);
        ownTeacher.setCollegeId(10L);
        when(userService.getById(10L)).thenReturn(ownTeacher);
        guard.assertCourseAccess(own);

        Course other = new Course();
        other.setId(101L);
        other.setTeacherId(11L);
        User otherTeacher = new User();
        otherTeacher.setId(11L);
        otherTeacher.setCollegeId(11L);
        when(userService.getById(11L)).thenReturn(otherTeacher);
        assertThrows(AccessDeniedException.class, () -> guard.assertCourseAccess(other));

        Course orphan = new Course();
        orphan.setId(102L);
        orphan.setTeacherId(12L);
        assertThrows(AccessDeniedException.class, () -> guard.assertCourseAccess(orphan));
    }

    private User collegeAdmin(Long id) {
        User user = new User();
        user.setId(id);
        user.setCollegeId(10L);
        return user;
    }
}
