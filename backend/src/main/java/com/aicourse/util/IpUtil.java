package com.aicourse.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * IP 工具类，用于获取客户端真实IP地址
 * 适配反向代理、负载均衡等多层代理场景
 */
@Component
public class IpUtil {

    /**
     * 获取客户端真实IP
     * @param request 请求对象
     * @return 客户端IP地址
     */
    public String getClientIp(HttpServletRequest request) {
        String ip = getHeaderIp(request, "X-Forwarded-For");
        if (isValidIp(ip)) {
            ip = getHeaderIp(request, "Proxy-Client-IP");
        }
        if (isValidIp(ip)) {
            ip = getHeaderIp(request, "WL-Proxy-Client-IP");
        }
        // 无代理时，直接获取原始请求地址
        if (isValidIp(ip)) {
            ip = request.getRemoteAddr();
        }

        // 多级代理场景，截取第一个有效IP
        if (StringUtils.hasText(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 从请求头中获取IP
     * @param request 请求对象
     * @param headerName 请求头名称
     * @return 对应头信息的值
     */
    private String getHeaderIp(HttpServletRequest request, String headerName) {
        return request.getHeader(headerName);
    }

    /**
     * 校验IP是否有效
     * @param ip IP字符串
     * @return true=有效，false=无效
     */
    private boolean isValidIp(String ip) {
        return !StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip);
    }
}