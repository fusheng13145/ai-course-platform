package com.aicourse.security;

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
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JwtRequestFilter 单元测试：覆盖 Header Bearer / Cookie / 无效令牌 / 会话已失效 /
 * 解析异常 / 异步派发声明 / 提取令牌回退分支。
 */
@ExtendWith(MockitoExtension.class)
class JwtRequestFilterTest {

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private UserSessionService userSessionService;
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

    @Test
    void shouldNotFilterAsyncDispatch_returnsFalse() {
        filter = new JwtRequestFilter(jwtUtils, userSessionService);
        assertEquals(false, filter.shouldNotFilterAsyncDispatch());
    }

    @Test
    void doFilter_validBearerHeader_setsAuthentication() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(userSessionService.isSessionActive("tok")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("tok")).thenReturn(1L);
        when(jwtUtils.getUsernameFromToken("tok")).thenReturn("alice");
        when(jwtUtils.getRoleFromToken("tok")).thenReturn("STUDENT");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(1L, auth.getPrincipal());
        assertEquals("alice", auth.getCredentials());
    }

    @Test
    void doFilter_nullToken_noAuthentication() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_cookieToken_setsAuthentication() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("other", "1"),
                new Cookie(JwtUtils.ACCESS_TOKEN_COOKIE, "ck")});
        when(jwtUtils.validateToken("ck")).thenReturn(true);
        when(userSessionService.isSessionActive("ck")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("ck")).thenReturn(2L);
        when(jwtUtils.getUsernameFromToken("ck")).thenReturn("bob");
        when(jwtUtils.getRoleFromToken("ck")).thenReturn("ADMIN");

        filter.doFilterInternal(request, response, chain);

        assertEquals(2L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void doFilter_invalidToken_noAuthentication() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService);
        when(request.getHeader("Authorization")).thenReturn("Bearer bad");
        when(jwtUtils.extractTokenFromHeader("Bearer bad")).thenReturn("bad");
        when(jwtUtils.validateToken("bad")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_sessionInactive_doesNotAuthenticate() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(userSessionService.isSessionActive("tok")).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_parseException_isSwallowed() throws Exception {
        filter = new JwtRequestFilter(jwtUtils, userSessionService);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok");
        when(jwtUtils.extractTokenFromHeader("Bearer tok")).thenReturn("tok");
        when(jwtUtils.validateToken("tok")).thenThrow(new RuntimeException("boom"));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
