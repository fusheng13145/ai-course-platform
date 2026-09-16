package com.aicourse.util;

import com.aicourse.common.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 客户端上下文解析器（Web 层专用）。
 * <p>把 {@link HttpServletRequest} 一次性解析为不可变 {@link ClientInfo}，供 Controller 下传 Service，
 * 使 Service / Component 签名不再泄漏 Servlet API（手册 §46.1-12 修复）。</p>
 * <p>其中 {@code ipAddress}（用于登录失败节流键、限流键等安全决策）<b>不再</b>复用 {@link IpUtil}
 * （其取 {@code X-Forwarded-For} 最左段、可被客户端伪造，曾导致限流被逐请求换头绕过），
 * 改由 {@link TrustedClientIpResolver} 解析——该解析器专用于安全决策，从可信来源（受信反代覆写的
 * X-Real-IP，或直连时的 TCP 对端地址）取 IP，杜绝伪造头污染计数键。{@link UserAgentUtil} 仍负责
 * 浏览器 / 操作系统 / 设备解析。Servlet 依赖仅存在于本类与两个底层工具类，不再向 Service 层扩散。</p>
 */
@Component
public class ClientInfoResolver {

    private final TrustedClientIpResolver trustedClientIpResolver;
    private final UserAgentUtil userAgentUtil;

    /**
     * 注入可信 IP 解析器与 UA 解析器。
     * <p>构造器唯一，由 Spring 自动装配。原先基于 {@link IpUtil} 的重载已删除：它只为兼容既有测试
     * 而存在，且会让调用方误以为可以注入任意（含可伪造的）IP 来源，属生产 API 污染。测试应直接构造
     * {@link TrustedClientIpResolver} 并显式设定其 {@code trustProxyHeader} 开关。</p>
     */
    public ClientInfoResolver(TrustedClientIpResolver trustedClientIpResolver, UserAgentUtil userAgentUtil) {
        this.trustedClientIpResolver = trustedClientIpResolver;
        this.userAgentUtil = userAgentUtil;
    }

    /**
     * 从当前请求解析客户端上下文。
     * @param request 当前 HTTP 请求
     * @return 不可变客户端上下文值对象
     */
    public ClientInfo resolve(HttpServletRequest request) {
        return new ClientInfo(
                trustedClientIpResolver.resolve(request),
                request.getHeader("User-Agent"),
                userAgentUtil.getBrowser(request),
                userAgentUtil.getOperatingSystem(request),
                userAgentUtil.getDeviceName(request));
    }
}
