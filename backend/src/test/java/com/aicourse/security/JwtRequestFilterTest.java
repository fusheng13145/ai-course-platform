package com.aicourse.security;

import com.aicourse.entity.User;
import com.aicourse.service.UserService;
import com.aicourse.service.UserSessionService;
import com.aicourse.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JwtRequestFilter 单元测试：覆盖 Header Bearer / Cookie / 无效令牌 / 会话已失效 /
 * 解析异常 / 异步派发声明 / 提取令牌回退分支。
 *
 * <p>另覆盖「令牌角色快照」回归（2026-10-16 线上事故）：authorities 必须以用户表
 * 现行角色为准。管理端修改角色后，已签发令牌在有效期内仍携带旧角色 claim，
 * 若过滤器按 claim 放行，将出现「/auth/permissions 与菜单按 DB 新角色渲染、
 * REST 鉴权按令牌旧角色拒绝」的分裂态（UI 是管理台、API 全 403），降权场景
 * 更是让被降级的管理员在令牌过期前仍持有原角色接口权限。</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtRequestFilterTest {

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private UserSessionService userSessionService;
    @Mock
    private UserService userService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    private JwtRequestFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User dbUser(Long id, String role, boolean active) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        user.setRole(role);
        user.setIsActive(active);
        return user;
    }

    /** 断言当前认证主体持有且仅持有给定角色（ROLE_ 前缀由过滤器添加） */
    private void assertAuthorities(String... roles) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> actual = new java.util.HashSet<>();
        for (GrantedAuthority a : auth.getAuthorities()) {
            actual.add(a.getAuthority());
        }
        assertEquals(Set.of(roles), actual);
    }

    @Test
    void shouldNotFilterAsyncDispatch_returnsFalse() {
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        assertEquals(false, filter.shouldNotFilterAsyncDispatch());
    }

    @Test
    void doFilter_validBearerHeader_setsAuthentication() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(userSessionService.isSessionActive("tok")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("tok")).thenReturn(1L);
        when(jwtUtils.getUsernameFromToken("tok")).thenReturn("alice");
        when(userService.getById(1L)).thenReturn(dbUser(1L, "STUDENT", true));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(1L, auth.getPrincipal());
        assertEquals("alice", auth.getCredentials());
        assertAuthorities("ROLE_STUDENT");
    }

    @Test
    void doFilter_nullToken_noAuthentication() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_cookieToken_setsAuthentication() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("other", "1"),
                new Cookie(JwtUtils.ACCESS_TOKEN_COOKIE, "ck")});
        when(jwtUtils.validateToken("ck")).thenReturn(true);
        when(userSessionService.isSessionActive("ck")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("ck")).thenReturn(2L);
        when(jwtUtils.getUsernameFromToken("ck")).thenReturn("bob");
        when(userService.getById(2L)).thenReturn(dbUser(2L, "ADMIN", true));

        filter.doFilterInternal(request, response, chain);

        assertEquals(2L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertAuthorities("ROLE_ADMIN");
    }

    @Test
    void doFilter_invalidToken_noAuthentication() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer bad");
        when(jwtUtils.extractTokenFromHeader("Bearer bad")).thenReturn("bad");
        when(jwtUtils.validateToken("bad")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_sessionInactive_doesNotAuthenticate() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(userSessionService.isSessionActive("tok")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_parseException_isSwallowed() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenThrow(new RuntimeException("boom"));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ==================== 令牌角色快照回归（2026-10-16） ====================

    @Test
    void doFilter_rolePromotedAfterLogin_usesCurrentDbRole() throws Exception {
        // 事故回归：令牌 claim=TEACHER（登录快照），DB 已提为 ADMIN。
        // 若按 claim 构造 authorities，/admin/** 全 403 而前端管理台正常渲染。
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(userSessionService.isSessionActive("tok")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("tok")).thenReturn(2L);
        when(jwtUtils.getUsernameFromToken("tok")).thenReturn("teacher");
        when(userService.getById(2L)).thenReturn(dbUser(2L, "ADMIN", true));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")),
                "DB 已提权为 ADMIN，authorities 必须含 ROLE_ADMIN");
        assertFalse(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_TEACHER")),
                "不得保留令牌里的旧角色 TEACHER");
    }

    @Test
    void doFilter_roleDemotedAfterLogin_usesCurrentDbRole() throws Exception {
        // 反向（安全缺口）：令牌 claim=ADMIN，DB 已降为 STUDENT。
        // 若按 claim 放行，被降级的管理员在令牌过期前仍持有 ADMIN 接口权限。
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(userSessionService.isSessionActive("tok")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("tok")).thenReturn(3L);
        when(jwtUtils.getUsernameFromToken("tok")).thenReturn("exadmin");
        when(userService.getById(3L)).thenReturn(dbUser(3L, "STUDENT", true));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_STUDENT")));
        assertFalse(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")),
                "DB 已降权，不得保留令牌里的旧角色 ADMIN");
    }

    @Test
    void doFilter_userDeleted_doesNotAuthenticate() throws Exception {
        // 用户被删除：令牌签名仍有效，但不得建立认证上下文
        // （与 /auth/refresh 的「用户不存在或已禁用」口径一致）。
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(userSessionService.isSessionActive("tok")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("tok")).thenReturn(99L);
        when(jwtUtils.getUsernameFromToken("tok")).thenReturn("ghost");
        when(userService.getById(99L)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_userDisabled_doesNotAuthenticate() throws Exception {
        // 用户被禁用（is_active=false）：同样不建立认证上下文，
        // 请求落入 anyRequest().authenticated() 统一 401。
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(userSessionService.isSessionActive("tok")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("tok")).thenReturn(4L);
        when(jwtUtils.getUsernameFromToken("tok")).thenReturn("disabled");
        when(userService.getById(4L)).thenReturn(dbUser(4L, "STUDENT", false));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_userLookupException_isSwallowed() throws Exception {
        // 用户表读取异常：与令牌解析异常同口径——记录并放行（无认证上下文 → 401），
        // 不因依赖故障把请求变成 500。
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(userSessionService.isSessionActive("tok")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("tok")).thenReturn(5L);
        when(jwtUtils.getUsernameFromToken("tok")).thenReturn("alice");
        when(userService.getById(5L)).thenThrow(new RuntimeException("db down"));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // ==================== 管理员作用域（V12 / B-12-P-08 WP1） ====================

    @Test
    void doFilter_schoolAdmin_addsSchoolScopeAuthority() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(userSessionService.isSessionActive("tok")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("tok")).thenReturn(1L);
        when(jwtUtils.getUsernameFromToken("tok")).thenReturn("admin");
        User admin = dbUser(1L, "ADMIN", true);
        admin.setAdminScope("SCHOOL");
        when(userService.getById(1L)).thenReturn(admin);

        filter.doFilterInternal(request, response, chain);

        // scope authority 每请求从 DB 现行用户解析，令牌不携带 scope、无需重签
        assertAuthorities("ROLE_ADMIN", "SCOPE_ADMIN_SCHOOL");
    }

    @Test
    void doFilter_collegeAdmin_addsCollegeScopeAuthority() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(userSessionService.isSessionActive("tok")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("tok")).thenReturn(1L);
        when(jwtUtils.getUsernameFromToken("tok")).thenReturn("admin");
        User admin = dbUser(1L, "ADMIN", true);
        admin.setAdminScope("COLLEGE");
        when(userService.getById(1L)).thenReturn(admin);

        filter.doFilterInternal(request, response, chain);

        assertAuthorities("ROLE_ADMIN", "SCOPE_ADMIN_COLLEGE");
    }

    @Test
    void doFilter_nonAdminWithScope_getsNoScopeAuthority() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(userSessionService.isSessionActive("tok")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("tok")).thenReturn(6L);
        when(jwtUtils.getUsernameFromToken("tok")).thenReturn("teacher");
        User teacher = dbUser(6L, "TEACHER", true);
        teacher.setAdminScope("COLLEGE");
        when(userService.getById(6L)).thenReturn(teacher);

        filter.doFilterInternal(request, response, chain);

        // 作用域只属于 ADMIN：教师即便误写 admin_scope 也不得获得管理员 authority
        assertAuthorities("ROLE_TEACHER");
    }
}
