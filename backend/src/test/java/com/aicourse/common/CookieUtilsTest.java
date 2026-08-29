package com.aicourse.common;

import com.aicourse.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CookieUtils 单元测试：覆盖 addAuthCookies（访问/刷新两套、accessToken 为 null、计算有效期异常）/
 * clearAuthCookies / readCookie（命中 / 无 cookies / 未命中）。
 */
@ExtendWith(MockitoExtension.class)
class CookieUtilsTest {

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private HttpServletResponse response;
    @Mock
    private HttpServletRequest request;

    @Test
    void addAuthCookies_both() {
        when(jwtUtils.getRemainingSeconds("access")).thenReturn(100L);
        CookieUtils utils = new CookieUtils(true, jwtUtils);

        utils.addAuthCookies(response, "access", "refresh", true);

        verify(response, atLeastOnce()).addHeader(
                org.mockito.ArgumentMatchers.eq(org.springframework.http.HttpHeaders.SET_COOKIE),
                org.mockito.ArgumentMatchers.contains(JwtUtils.ACCESS_TOKEN_COOKIE));
        verify(response, atLeastOnce()).addHeader(
                org.mockito.ArgumentMatchers.eq(org.springframework.http.HttpHeaders.SET_COOKIE),
                org.mockito.ArgumentMatchers.contains(JwtUtils.REFRESH_TOKEN_COOKIE));
    }

    @Test
    void addAuthCookies_accessTokenNull_onlyRefresh() {
        CookieUtils utils = new CookieUtils(false, jwtUtils);
        utils.addAuthCookies(response, null, "refresh", false);
        verify(response, org.mockito.Mockito.never()).addHeader(
                org.mockito.ArgumentMatchers.eq(org.springframework.http.HttpHeaders.SET_COOKIE),
                org.mockito.ArgumentMatchers.contains(JwtUtils.ACCESS_TOKEN_COOKIE));
    }

    @Test
    void addAuthCookies_remainingSecondsThrows_fallsBackToZero() {
        when(jwtUtils.getRemainingSeconds("access")).thenThrow(new RuntimeException("expired"));
        CookieUtils utils = new CookieUtils(false, jwtUtils);
        // 不应抛异常
        utils.addAuthCookies(response, "access", "refresh", false);
        verify(response, atLeastOnce()).addHeader(
                org.mockito.ArgumentMatchers.eq(org.springframework.http.HttpHeaders.SET_COOKIE),
                org.mockito.ArgumentMatchers.contains(JwtUtils.ACCESS_TOKEN_COOKIE));
    }

    @Test
    void clearAuthCookies() {
        CookieUtils utils = new CookieUtils(true, jwtUtils);
        utils.clearAuthCookies(response);
        verify(response, atLeastOnce()).addHeader(
                org.mockito.ArgumentMatchers.eq(org.springframework.http.HttpHeaders.SET_COOKIE),
                org.mockito.ArgumentMatchers.contains(JwtUtils.ACCESS_TOKEN_COOKIE));
        verify(response, atLeastOnce()).addHeader(
                org.mockito.ArgumentMatchers.eq(org.springframework.http.HttpHeaders.SET_COOKIE),
                org.mockito.ArgumentMatchers.contains(JwtUtils.REFRESH_TOKEN_COOKIE));
    }

    @Test
    void readCookie_hit() {
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("other", "1"), new Cookie(JwtUtils.ACCESS_TOKEN_COOKIE, "tok")});
        CookieUtils utils = new CookieUtils(true, jwtUtils);
        assertEquals("tok", utils.readCookie(request, JwtUtils.ACCESS_TOKEN_COOKIE));
    }

    @Test
    void readCookie_noCookies() {
        when(request.getCookies()).thenReturn(null);
        CookieUtils utils = new CookieUtils(true, jwtUtils);
        assertNull(utils.readCookie(request, "x"));
    }

    @Test
    void readCookie_notFound() {
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("a", "1")});
        CookieUtils utils = new CookieUtils(true, jwtUtils);
        assertNull(utils.readCookie(request, "missing"));
    }
}
