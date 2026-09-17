package com.aicourse.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TrustedForwardedProtoFilter 单元测试（无 Spring 上下文，纯逻辑验证）。
 *
 * <p>证明：受信反代回传 {@code X-Forwarded-Proto: https} 时，包装后 {@code isSecure()} 为 true、
 * {@code getScheme()} 为 https；而 {@code getRemoteAddr()} 仍保持原始 TCP 对等体（不被覆写），
 * 且 {@code X-Forwarded-For} 不被本过滤器消费/剥离（其信任由 {@code TrustedClientIpResolver} 负责）。</p>
 */
class TrustedForwardedProtoFilterTest {

    private final TrustedForwardedProtoFilter filter = new TrustedForwardedProtoFilter();

    /** 把原始请求交给过滤器，捕获下游看到（被包装）的请求。 */
    private HttpServletRequest wrap(MockHttpServletRequest raw) throws Exception {
        final HttpServletRequest[] captured = new HttpServletRequest[1];
        FilterChain chain = (ServletRequest request, ServletResponse response) ->
                captured[0] = (HttpServletRequest) request;
        filter.doFilter(raw, new MockHttpServletResponse(), chain);
        return captured[0];
    }

    @Test
    void secure_true_when_xForwardedProtoHttps() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setRemoteAddr("192.0.2.5"); // 模拟 TCP 对等体（反代内网地址）
        raw.addHeader("X-Forwarded-Proto", "https");
        raw.addHeader("X-Forwarded-For", "203.0.113.7"); // 客户端真实 IP（由 TrustedClientIpResolver 信任）

        HttpServletRequest wrapped = wrap(raw);
        assertTrue(wrapped.isSecure(), "X-Forwarded-Proto=https 应判定为安全通道");
        assertEquals("https", wrapped.getScheme());
        // 关键安全断言：客户端 IP 维度不被覆写，仍是 TCP 对等体
        assertEquals("192.0.2.5", wrapped.getRemoteAddr(), "getRemoteAddr 必须仍是 TCP 对等体，不可被覆写");
        assertEquals("203.0.113.7", wrapped.getHeader("X-Forwarded-For"),
                "X-Forwarded-For 不应被本过滤器剥离");
    }

    @Test
    void secure_false_when_xForwardedProtoHttp() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setRemoteAddr("192.0.2.5");
        raw.addHeader("X-Forwarded-Proto", "http");

        HttpServletRequest wrapped = wrap(raw);
        assertFalse(wrapped.isSecure());
        assertEquals("http", wrapped.getScheme());
        assertEquals("192.0.2.5", wrapped.getRemoteAddr());
    }

    @Test
    void secure_true_when_xForwardedSslOn() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setRemoteAddr("192.0.2.5");
        raw.addHeader("X-Forwarded-Ssl", "on");

        HttpServletRequest wrapped = wrap(raw);
        assertTrue(wrapped.isSecure());
        assertEquals("https", wrapped.getScheme());
    }

    @Test
    void secure_delegates_when_no_header() throws Exception {
        // 直连 TLS 场景：无反代头时应回退到原始判定，不可被误判为非安全
        MockHttpServletRequest raw = new MockHttpServletRequest();
        raw.setRemoteAddr("192.0.2.5");
        raw.setSecure(true);

        HttpServletRequest wrapped = wrap(raw);
        assertTrue(wrapped.isSecure());
        assertEquals("192.0.2.5", wrapped.getRemoteAddr());
    }
}
