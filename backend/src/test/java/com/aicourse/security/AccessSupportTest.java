package com.aicourse.security;

import com.aicourse.common.ApiCode;
import com.aicourse.common.RoleConstants;
import com.aicourse.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 角色/身份读取单一事实源的契约测试（阶段 3 · DUP-16/18/19 收敛后的行为基线）。
 *
 * <p>这些断言同时是"收敛没有降级"的证据：收敛前散落的 20 余处写法里，
 * 有的漏了 {@code auth == null} 判定（匿名请求会 NPE → 500），
 * 有的把 principal 直接强转 {@code Long}（非 Long 时抛 ClassCastException → 500）。
 * 收敛到本类后这些边界行为被固定为 fail-closed（不抛 NPE、不误判为有权限）。</p>
 */
class AccessSupportTest {

    private static Authentication authOf(String... authorities) {
        Authentication auth = mock(Authentication.class);
        List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        when(auth.getAuthorities()).thenReturn((java.util.Collection) granted);
        return auth;
    }

    private static Authentication authWithPrincipal(Object principal, String... authorities) {
        Authentication auth = authOf(authorities);
        when(auth.getPrincipal()).thenReturn(principal);
        return auth;
    }

    @Test
    @DisplayName("roleOf 剥离 ROLE_ 前缀；无角色/无授权时返回空串而不是 null")
    void roleOf_stripsPrefixAndNeverReturnsNull() {
        assertEquals(RoleConstants.ADMIN, AccessSupport.roleOf(authOf("ROLE_ADMIN")));
        assertEquals("", AccessSupport.roleOf(authOf()));
        assertEquals("", AccessSupport.roleOf(null));
    }

    @Test
    @DisplayName("roleOf 只认 ROLE_ 前缀，作用域授权项不会被当成角色")
    void roleOf_ignoresNonRoleAuthorities() {
        assertEquals("", AccessSupport.roleOf(authOf("SCOPE_ADMIN_COLLEGE")));
    }

    @Test
    @DisplayName("isAdmin/hasRole 是精确匹配：ROLE_ADMINX 不算管理员")
    void hasRole_isExactMatch() {
        assertTrue(AccessSupport.isAdmin(authOf("ROLE_ADMIN")));
        assertFalse(AccessSupport.isAdmin(authOf("ROLE_ADMINX")));
        assertFalse(AccessSupport.isAdmin(authOf("ROLE_TEACHER")));
        assertFalse(AccessSupport.hasRole(authOf("ROLE_TEACHER"), RoleConstants.ADMIN));
        assertFalse(AccessSupport.isAdmin(null), "匿名请求必须被判为非管理员（fail-closed）");
        assertFalse(AccessSupport.hasRole(authOf("ROLE_TEACHER"), null));
        assertFalse(AccessSupport.hasRole(authOf("ROLE_TEACHER"), ""));
    }

    @Test
    @DisplayName("hasAuthority 匹配授权项原文（作用域判定用）")
    void hasAuthority_matchesRawAuthority() {
        assertTrue(AccessSupport.hasAuthority(authOf("SCOPE_ADMIN_COLLEGE"), "SCOPE_ADMIN_COLLEGE"));
        assertFalse(AccessSupport.hasAuthority(authOf("ROLE_ADMIN"), "SCOPE_ADMIN_COLLEGE"));
        assertFalse(AccessSupport.hasAuthority(null, "SCOPE_ADMIN_COLLEGE"));
    }

    @Test
    @DisplayName("principalId 取 Long 身份；非 Long 返回 null 而不是抛 ClassCastException")
    void principalId_isTypeSafe() {
        assertEquals(42L, AccessSupport.principalId(authWithPrincipal(42L)));
        assertNull(AccessSupport.principalId(authWithPrincipal("42")));
        assertNull(AccessSupport.principalId(null));
    }

    @Test
    @DisplayName("requirePrincipalId 取不到身份时抛 401（原来会抛 ClassCastException → 500）")
    void requirePrincipalId_failsClosedWith401() {
        assertEquals(42L, AccessSupport.requirePrincipalId(authWithPrincipal(42L)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> AccessSupport.requirePrincipalId(authWithPrincipal("anonymous")));
        assertEquals(ApiCode.UNAUTHORIZED, ex.getCode());

        assertEquals(ApiCode.UNAUTHORIZED,
                assertThrows(BusinessException.class, () -> AccessSupport.requirePrincipalId(null)).getCode());
    }

    @Test
    @DisplayName("RoleUtil 是可转发门面：与 AccessSupport 结论一致（含教师或管理员判定）")
    void roleUtilDelegates() {
        assertTrue(RoleUtil.isAdmin(authOf("ROLE_ADMIN")));
        assertFalse(RoleUtil.isAdmin(null));
        assertEquals(RoleConstants.TEACHER, RoleUtil.roleOf(authOf("ROLE_TEACHER")));
        assertTrue(RoleUtil.isTeacherOrAdmin(authOf("ROLE_TEACHER")));
        assertFalse(RoleUtil.isTeacherOrAdmin(authOf("ROLE_STUDENT")));
    }
}
