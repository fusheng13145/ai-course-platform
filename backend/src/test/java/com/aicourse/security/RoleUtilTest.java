package com.aicourse.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * RoleUtil 单元测试：覆盖 isTeacherOrAdmin / isAdmin / roleOf 及各种角色组合与 null 分支。
 */
@ExtendWith(MockitoExtension.class)
class RoleUtilTest {

    @Mock
    private Authentication auth;

    private void withRoles(String... roles) {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        for (String r : roles) authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
        lenient().doReturn(authorities).when(auth).getAuthorities();
    }

    @Test
    void isTeacherOrAdmin_true() {
        withRoles("TEACHER");
        assertTrue(RoleUtil.isTeacherOrAdmin(auth));
        withRoles("ADMIN");
        assertTrue(RoleUtil.isTeacherOrAdmin(auth));
    }

    @Test
    void isTeacherOrAdmin_falseForStudent() {
        withRoles("STUDENT");
        assertFalse(RoleUtil.isTeacherOrAdmin(auth));
    }

    @Test
    void isAdmin_trueAndFalse() {
        withRoles("ADMIN");
        assertTrue(RoleUtil.isAdmin(auth));
        withRoles("TEACHER");
        assertFalse(RoleUtil.isAdmin(auth));
    }

    @Test
    void roleOf_firstRole() {
        withRoles("ADMIN", "TEACHER");
        assertEquals("ADMIN", RoleUtil.roleOf(auth));
    }

    @Test
    void nullAuth_returnsFalseOrEmpty() {
        when(auth.getAuthorities()).thenReturn(null);
        assertFalse(RoleUtil.isTeacherOrAdmin(auth));
        assertFalse(RoleUtil.isAdmin(auth));
        assertEquals("", RoleUtil.roleOf(auth));
    }

    @Test
    void noAuthObject_nullSafe() {
        assertFalse(RoleUtil.isTeacherOrAdmin(null));
        assertFalse(RoleUtil.isAdmin(null));
        assertEquals("", RoleUtil.roleOf(null));
    }
}
