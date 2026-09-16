package com.aicourse.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link TrustedClientIpResolver} 单元测试。
 * <p>覆盖 2026-09-16 上线前全检修复的「可逐请求换头绕过限流」缺陷：安全决策用的客户端 IP
 * 必须取自可信来源，绝不能被客户端可控的请求头污染。</p>
 * <p>注意：本类只验证<b>取值策略</b>本身；「边缘代理须以覆写语义设置代理头」这一前提
 * 由部署侧的 {@code frontend/nginx.conf} 保证。</p>
 */
class TrustedClientIpResolverTest {

    private static final String REMOTE_ADDR = "203.0.113.7";

    private TrustedClientIpResolver resolver;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        resolver = new TrustedClientIpResolver();
        request = new MockHttpServletRequest();
        request.setRemoteAddr(REMOTE_ADDR);
        // 单元测试无 Spring 上下文，@Value 字段需显式注入
        ReflectionTestUtils.setField(resolver, "trustProxyHeader", false);
    }

    private void enableTrustProxyHeader() {
        ReflectionTestUtils.setField(resolver, "trustProxyHeader", true);
    }

    @Test
    @DisplayName("默认不信任代理头：伪造 X-Real-IP 不得影响取值")
    void notTrusted_shouldIgnoreForgedRealIpHeader() {
        request.addHeader("X-Real-IP", "1.2.3.4");

        assertEquals(REMOTE_ADDR, resolver.resolve(request),
                "trust-proxy-header=false 时应以 TCP 对端地址为准，伪造 X-Real-IP 必须被忽略");
    }

    @Test
    @DisplayName("默认不信任代理头：无任何代理头时取 TCP 对端地址")
    void notTrusted_withoutHeaders_shouldUseRemoteAddr() {
        assertEquals(REMOTE_ADDR, resolver.resolve(request));
    }

    @Test
    @DisplayName("信任代理头：取反代覆写的 X-Real-IP")
    void trusted_shouldPreferRealIpHeader() {
        enableTrustProxyHeader();
        request.addHeader("X-Real-IP", "198.51.100.24");

        assertEquals("198.51.100.24", resolver.resolve(request));
    }

    @Test
    @DisplayName("信任代理头：X-Real-IP 缺失时回退 TCP 对端地址")
    void trusted_missingRealIp_shouldFallBackToRemoteAddr() {
        enableTrustProxyHeader();

        assertEquals(REMOTE_ADDR, resolver.resolve(request));
    }

    @Test
    @DisplayName("信任代理头：空白 X-Real-IP 应回退 TCP 对端地址")
    void trusted_blankRealIp_shouldFallBackToRemoteAddr() {
        enableTrustProxyHeader();
        request.addHeader("X-Real-IP", "   ");

        assertEquals(REMOTE_ADDR, resolver.resolve(request));
    }

    @Test
    @DisplayName("信任代理头：unknown 占位值应回退 TCP 对端地址")
    void trusted_unknownRealIp_shouldFallBackToRemoteAddr() {
        enableTrustProxyHeader();
        request.addHeader("X-Real-IP", "UNKNOWN");

        assertEquals(REMOTE_ADDR, resolver.resolve(request));
    }

    @Test
    @DisplayName("信任代理头：超长（>45，超 IPv6 上限）异常输入应回退 TCP 对端地址")
    void trusted_overlongRealIp_shouldFallBackToRemoteAddr() {
        enableTrustProxyHeader();
        request.addHeader("X-Real-IP", "0".repeat(46));

        assertEquals(REMOTE_ADDR, resolver.resolve(request));
    }

    @Test
    @DisplayName("信任代理头：首尾空格应被裁剪后返回")
    void trusted_realIpWithPadding_shouldBeTrimmed() {
        enableTrustProxyHeader();
        request.addHeader("X-Real-IP", "  198.51.100.99 ");

        assertEquals("198.51.100.99", resolver.resolve(request));
    }

    @Test
    @DisplayName("信任代理头：恰恰 45 字符（IPv6 上限）仍应被采用")
    void trusted_maxLengthRealIp_shouldBeAccepted() {
        enableTrustProxyHeader();
        // 45 字符是 IPv6 文本表示的合法上限，边界值应放行
        String maxLengthIp = "0".repeat(45);
        request.addHeader("X-Real-IP", maxLengthIp);

        assertEquals(maxLengthIp, resolver.resolve(request));
    }
}
