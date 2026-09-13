package com.aicourse.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 客户端上下文值对象（入参，无后缀值对象按 §19 约定置于 {@code common/}）。
 * <p>由 Web 层（Controller）经 {@code ClientInfoResolver} 从 {@code HttpServletRequest} 解析后下传
 * Service 层，使 {@code AuthService} 及其协作组件的签名不再依赖 Servlet API（手册 §46.1-12 分层修复）。</p>
 * <p>不可变对象；五个字段与原 Service 组件内的解析结果一一对应：
 * {@code ipAddress} ← {@code IpUtil.getClientIp}，{@code userAgent} ← {@code User-Agent} 原始请求头，
 * {@code browser} / {@code operatingSystem} / {@code deviceName} ← {@code UserAgentUtil} 三个解析方法。
 * 解析时机由 Controller 在进入 Service 前一次性完成，字段值与原「用时解析」完全一致，行为保持。</p>
 */
@Getter
@RequiredArgsConstructor
public class ClientInfo {

    /** 客户端真实 IP（多层代理场景已解析） */
    private final String ipAddress;

    /** User-Agent 原始请求头（登录日志/会话审计留存原文） */
    private final String userAgent;

    /** 浏览器（User-Agent 解析结果） */
    private final String browser;

    /** 操作系统（User-Agent 解析结果） */
    private final String operatingSystem;

    /** 设备名（User-Agent 解析结果） */
    private final String deviceName;
}
