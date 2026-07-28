package com.aicourse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link DoubleSubmitCsrfFilter} 单元测试：验证双提交 Cookie CSRF 的核心不变式。
 *
 * <ul>
 *   <li>安全方法放行且首访下发 XSRF-TOKEN Cookie；</li>
 *   <li>写请求缺失/不匹配令牌 → 403 熔断，不进入后续链；</li>
 *   <li>写请求令牌匹配 → 放行；</li>
 *   <li>引导端点（登录等）豁免；</li>
 *   <li>开关关闭时全量放行。</li>
 * </ul>
 */
class DoubleSubmitCsrfFilterTest {

    private DoubleSubmitCsrfFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new DoubleSubmitCsrfFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "secure", false);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @Test
    void getRequest_passesThrough_andIssuesTokenCookie() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/api/courses");

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        // 首访无 Cookie → 应下发 XSRF-TOKEN
        String setCookie = response.getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains(DoubleSubmitCsrfFilter.CSRF_COOKIE));
        assertTrue(setCookie.contains("HttpOnly") == false, "XSRF Cookie 必须可被 JS 读取（非 HttpOnly）");
    }

    @Test
    void postWithoutToken_isForbidden() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/submissions");

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void postWithMatchingToken_passes() throws Exception {
        String token = "test-csrf-token-123";
        request.setMethod("POST");
        request.setRequestURI("/api/submissions");
        request.setCookies(new Cookie(DoubleSubmitCsrfFilter.CSRF_COOKIE, token));
        request.addHeader(DoubleSubmitCsrfFilter.CSRF_HEADER, token);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void postWithMismatchedToken_isForbidden() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/submissions");
        request.setCookies(new Cookie(DoubleSubmitCsrfFilter.CSRF_COOKIE, "cookie-token"));
        request.addHeader(DoubleSubmitCsrfFilter.CSRF_HEADER, "header-token-different");

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void exemptBootstrapEndpoint_login_passesWithoutToken() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/auth/login");

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void disabled_passesEverything() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", false);
        request.setMethod("POST");
        request.setRequestURI("/api/submissions");

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }
}
