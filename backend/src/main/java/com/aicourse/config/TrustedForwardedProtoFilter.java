package com.aicourse.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 受信反代协议头过滤器（P1-2 前向头治理 / 根因收窄）。
 *
 * <p><b>背景</b>：原先 {@code server.forward-headers-strategy: framework} 会以 X-Forwarded-For 最左端
 * 覆写 {@code getRemoteAddr()}，污染审计/限流 IP 并耦合 HTTPS 检测——这是「伪造 X-Forwarded-For 绕过限流」
 * 缺陷的土壤。现改为 {@code forward-headers-strategy: none}，并由本过滤器<b>仅</b>修正协议/安全标记。</p>
 *
 * <p><b>职责边界（安全约束）</b>：
 * <ul>
 *   <li>仅读取受信边缘代理（nginx）回传的 {@code X-Forwarded-Proto} / {@code X-Forwarded-Ssl}，
 *       把 {@code isSecure()} 与 {@code getScheme()} 修正为反映客户端经 HTTPS 访问，
 *       使 Spring Security 的 {@code requiresSecure()} 与 Cookie {@code Secure} 标记正确生效。</li>
 *   <li><b>绝不</b>覆写 {@code getRemoteAddr()} / {@code getRemoteHost()}：客户端真实 IP 由独立的
 *       {@code TrustedClientIpResolver} 经 {@code X-Real-IP} 在受信代理下取值，与本题解耦，避免限流键被伪造。</li>
 *   <li><b>不</b>消费或剥离 {@code X-Forwarded-For}：其解析/信任完全交给 {@code TrustedClientIpResolver}，
 *       本过滤器不碰它，杜绝重复职责或误删导致节流键漂移。</li>
 *   <li>排序在 Spring Security 之前（{@link Ordered#HIGHEST_PRECEDENCE}），确保安全决策看到修正后的 secure。</li>
 * </ul>
 *
 * <p><b>前提</b>：本过滤器仅在「应用只经受信 nginx 暴露」的部署姿态下有意义（见 docker-compose 已收紧为
 * 127.0.0.1:8080）。若需接受不可信来源直连，必须配合 {@code TRUST_PROXY_HEADER=false} 关闭 IP 维度信任。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrustedForwardedProtoFilter implements Filter {

    private static final String HEADER_FORWARDED_PROTO = "X-Forwarded-Proto";
    private static final String HEADER_FORWARDED_SSL = "X-Forwarded-Ssl";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            chain.doFilter(new ProtoAwareRequest(httpRequest), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * 仅重写协议/安全标记的 {@link HttpServletRequestWrapper}。
     * {@code getRemoteAddr()} / {@code getRemoteHost()} / {@code getHeader("X-Forwarded-For")} 全部保持原始语义。
     */
    private static final class ProtoAwareRequest extends HttpServletRequestWrapper {

        private final boolean secure;

        ProtoAwareRequest(HttpServletRequest request) {
            super(request);
            this.secure = resolveSecure(request);
        }

        private static boolean resolveSecure(HttpServletRequest request) {
            String proto = request.getHeader(HEADER_FORWARDED_PROTO);
            if (proto != null && !proto.isBlank()) {
                return "https".equalsIgnoreCase(proto.trim());
            }
            String ssl = request.getHeader(HEADER_FORWARDED_SSL);
            if (ssl != null && !ssl.isBlank()) {
                return "on".equalsIgnoreCase(ssl.trim()) || "true".equalsIgnoreCase(ssl.trim());
            }
            // 无受信反代头时回退到原始判定（直连 TLS 场景不应被误判为非安全）
            return request.isSecure();
        }

        @Override
        public boolean isSecure() {
            return secure;
        }

        @Override
        public String getScheme() {
            return secure ? "https" : "http";
        }
    }
}
