package com.aicourse.common;

import com.aicourse.util.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 认证 Cookie 统一签发与清除工具。
 *
 * <p>所有令牌（访问 JWT / 刷新令牌）均通过 HttpOnly Cookie 下发，前端 JavaScript 无法直接读取，
 * 从根本上消除此前令牌存于 localStorage 被 XSS 窃取的攻击面。Cookie 默认非 Secure（便于 HTTP 本地联调），
 * 生产环境应通过 {@code COOKIE_SECURE=true} 开启 Secure，且仅 SameSite=Lax 以降低 CSRF 风险。</p>
 */
@Component
public class CookieUtils {

    /** 生产环境必须为 true（Secure Cookie 仅经 HTTPS 传输），开发 HTTP 环境为 false 以便联调。 */
    private final boolean secure;
    private final JwtUtils jwtUtils;

    public CookieUtils(@Value("${app.cookie.secure:false}") boolean secure, JwtUtils jwtUtils) {
        this.secure = secure;
        this.jwtUtils = jwtUtils;
    }

    /**
     * 登录/注册/刷新成功后写入认证 Cookie。
     * @param response 响应对象
     * @param accessToken 访问 JWT（写入 ai_course_token，路径 /，随所有 API 请求携带）
     * @param refreshToken 刷新令牌（写入 ai_course_refresh，路径收窄至刷新端点，降低暴露面）
     * @param rememberMe 是否记住我（仅影响刷新令牌有效期 1/7 天）
     */
    public void addAuthCookies(HttpServletResponse response, String accessToken,
                               String refreshToken, boolean rememberMe) {
        if (accessToken != null) {
            ResponseCookie accessCookie = ResponseCookie.from(JwtUtils.ACCESS_TOKEN_COOKIE, accessToken)
                    .httpOnly(true)
                    .secure(secure)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(Duration.ofSeconds(jwtUtils.getRemainingSeconds(accessToken)))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        }
        if (refreshToken != null) {
            long refreshMaxAgeSeconds = (rememberMe ? 7L : 1L) * 24 * 60 * 60;
            ResponseCookie refreshCookie = ResponseCookie.from(JwtUtils.REFRESH_TOKEN_COOKIE, refreshToken)
                    .httpOnly(true)
                    .secure(secure)
                    .sameSite("Lax")
                    .path("/api/auth/refresh")
                    .maxAge(Duration.ofSeconds(refreshMaxAgeSeconds))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }
    }

    /**
     * 退出登录/刷新失败时清除认证 Cookie（maxAge=0）。
     */
    public void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from(JwtUtils.ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        ResponseCookie refreshCookie = ResponseCookie.from(JwtUtils.REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api/auth/refresh")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    /**
     * 从请求 Cookie 中读取指定名称的值。
     * @param request 请求对象
     * @param name Cookie 名称
     * @return Cookie 值，不存在返回 null
     */
    public String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
