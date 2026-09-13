package com.aicourse.util;

import com.aicourse.common.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 客户端上下文解析器（Web 层专用）。
 * <p>把 {@link HttpServletRequest} 一次性解析为不可变 {@link ClientInfo}，供 Controller 下传 Service，
 * 使 Service / Component 签名不再泄漏 Servlet API（手册 §46.1-12 修复）。</p>
 * <p>复用既有 {@link IpUtil}（多层代理真实 IP）与 {@link UserAgentUtil}（浏览器 / 操作系统 / 设备解析），
 * 解析口径与原 Service 组件内联调用完全一致，行为保持。Servlet 依赖仅存在于本类与两个底层工具类，
 * 不再向 Service 层扩散。</p>
 */
@Component
public class ClientInfoResolver {

    private final IpUtil ipUtil;
    private final UserAgentUtil userAgentUtil;

    public ClientInfoResolver(IpUtil ipUtil, UserAgentUtil userAgentUtil) {
        this.ipUtil = ipUtil;
        this.userAgentUtil = userAgentUtil;
    }

    /**
     * 从当前请求解析客户端上下文。
     * @param request 当前 HTTP 请求
     * @return 不可变客户端上下文值对象
     */
    public ClientInfo resolve(HttpServletRequest request) {
        return new ClientInfo(
                ipUtil.getClientIp(request),
                request.getHeader("User-Agent"),
                userAgentUtil.getBrowser(request),
                userAgentUtil.getOperatingSystem(request),
                userAgentUtil.getDeviceName(request));
    }
}
