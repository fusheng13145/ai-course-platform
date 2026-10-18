package com.aicourse.security;

import com.aicourse.entity.User;
import com.aicourse.service.UserService;
import com.aicourse.service.UserSessionService;
import com.aicourse.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JwtRequestFilter 从 HttpOnly Cookie 解析令牌的单元测试。
 * 验证：Cookie 中的 ai_course_token 可被识别并建立认证；既无头也无 Cookie 时不建立认证。
 */
@ExtendWith(MockitoExtension.class)
class JwtRequestFilterCookieTest {

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private UserSessionService userSessionService;
    @Mock
    private UserService userService;

    private JwtRequestFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtRequestFilter(jwtUtils, userSessionService, userService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @Test
    void cookieToken_establishesAuthentication() throws Exception {
        request.setCookies(new Cookie(JwtUtils.ACCESS_TOKEN_COOKIE, "jwt"));

        when(jwtUtils.extractTokenFromHeader(null)).thenReturn(null);
        when(jwtUtils.validateToken("jwt")).thenReturn(true);
        when(userSessionService.isSessionActive("jwt")).thenReturn(true);
        when(jwtUtils.getUserIdFromToken("jwt")).thenReturn(1L);
        when(jwtUtils.getUsernameFromToken("jwt")).thenReturn("alice");
        User dbUser = new User();
        dbUser.setId(1L);
        dbUser.setUsername("alice");
        dbUser.setRole("STUDENT");
        dbUser.setIsActive(true);
        when(userService.getById(1L)).thenReturn(dbUser);

        filter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Cookie 中的令牌应建立认证上下文");
        assertEquals(1L, auth.getPrincipal());
    }

    @Test
    void noTokenAndNoCookie_noAuthentication() throws Exception {
        when(jwtUtils.extractTokenFromHeader(null)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "既无 Authorization 头也无 Cookie 时不应建立认证");
    }
}
