package com.aicourse.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 双提交 Cookie（Double-Submit Cookie）CSRF 防护过滤器——为「令牌走 Cookie 自动携带」补上纵深防御。
 *
 * <p><b>为什么需要它——把「SameSite=Lax 足够」这一乐观假设变成纵深保证：</b>
 * 本项目认证令牌存于 HttpOnly Cookie，浏览器会对同站请求自动携带，天然暴露于 CSRF。
 * 现有防线仅依赖 {@code SameSite=Lax}；虽然 Lax 能拦截绝大多数跨站 POST，但它是<b>单点</b>假设
 * （依赖浏览器实现、且对某些顶层导航型请求存在边界情况）。本过滤器叠加第二道正交防线：</p>
 *
 * <ol>
 *   <li>向浏览器下发一个<b>可被 JS 读取</b>的随机令牌 Cookie（{@code XSRF-TOKEN}）；</li>
 *   <li>对写请求（POST/PUT/PATCH/DELETE）强制校验请求头 {@code X-XSRF-TOKEN} 是否与该 Cookie 一致。</li>
 * </ol>
 *
 * <p>攻击者站点因<b>同源策略</b>读不到受害者的 {@code XSRF-TOKEN} Cookie，也就无法伪造匹配的请求头；
 * 而合法前端（axios，同源 {@code /api}）会自动读取该 Cookie 并回填到请求头。二者结合，
 * 即便 SameSite 失效，跨站伪造请求仍会因缺少匹配令牌被拒。</p>
 *
 * <p>豁免：安全方法（GET/HEAD/OPTIONS/TRACE）与登录态尚未建立的引导端点（登录/注册/刷新/登出/验证码）。
 * 开关 {@code app.security.csrf.enabled}（默认 true）；本地联调与切片测试可关闭。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DoubleSubmitCsrfFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DoubleSubmitCsrfFilter.class);

    /** JS 可读的 CSRF 令牌 Cookie 名（与 axios 默认 xsrfCookieName 对齐）。 */
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    /** 前端回填的请求头名（与 axios 默认 xsrfHeaderName 对齐）。 */
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 引导型端点：此时尚无会话/令牌，需豁免以免死锁登录流程。以 context-path=/api 为前缀。 */
    private static final Set<String> EXEMPT_PREFIXES = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/verify-code",
            "/api/common/verify-code"
    );

    @Value("${app.security.csrf.enabled:true}")
    private boolean enabled;

    /** 令牌 Cookie 是否 Secure（与认证 Cookie 保持一致，生产 HTTPS 环境应为 true）。 */
    @Value("${app.cookie.secure:true}")
    private boolean secure;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        // 确保浏览器持有一个可读的 CSRF 令牌 Cookie；缺失则新签发并写回。
        String cookieToken = readCookie(request, CSRF_COOKIE);
        if (cookieToken == null || cookieToken.isEmpty()) {
            cookieToken = generateToken();
            writeCsrfCookie(response, cookieToken);
        }

        // 仅对会改变服务端状态的写请求做校验；安全方法与引导端点放行。
        if (requiresCsrfCheck(request)) {
            String headerToken = request.getHeader(CSRF_HEADER);
            if (headerToken == null || !constantTimeEquals(headerToken, cookieToken)) {
                log.warn("[Csrf] CSRF 令牌校验失败（缺失或不匹配），拒绝写请求: method={}, uri={}",
                        request.getMethod(), request.getRequestURI());
                writeForbidden(response);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean requiresCsrfCheck(HttpServletRequest request) {
        String method = request.getMethod();
        boolean stateChanging = "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
        if (!stateChanging) {
            return false;
        }
        String path = request.getRequestURI();
        for (String prefix : EXEMPT_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private void writeCsrfCookie(HttpServletResponse response, String token) {
        // HttpOnly 必须为 false：前端 JS（axios）需读取该 Cookie 才能回填请求头，这是双提交模式的前提。
        ResponseCookie cookie = ResponseCookie.from(CSRF_COOKIE, token)
                .httpOnly(false)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofHours(12))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String readCookie(HttpServletRequest request, String name) {
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

    /** 定长比较，避免基于时序的令牌猜测。 */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(ab, bb);
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "code", HttpStatus.FORBIDDEN.value(),
                "message", "CSRF 校验失败：缺少或不匹配的 " + CSRF_HEADER + " 令牌",
                "data", ""
        ));
        response.getWriter().write(body);
    }
}
