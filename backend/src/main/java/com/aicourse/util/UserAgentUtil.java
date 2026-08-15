package com.aicourse.util;

import eu.bitwalker.useragentutils.UserAgent;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * User-Agent 解析工具类
 * 用于获取客户端浏览器、操作系统、设备类型信息
 */
@Component
public class UserAgentUtil {

    private static final Logger log = LoggerFactory.getLogger(UserAgentUtil.class);

    /**
     * 解析 User-Agent 字符串
     * @param request 请求对象
     * @return UserAgent 解析实例
     */
    private UserAgent parseUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        return UserAgent.parseUserAgentString(userAgent);
    }

    /**
     * 获取客户端浏览器名称
     * @param request 请求对象
     * @return 浏览器名称，未知则返回 Unknown
     */
    public String getBrowser(HttpServletRequest request) {
        UserAgent ua = parseUserAgent(request);
        return ua != null && ua.getBrowser() != null ? ua.getBrowser().getName() : "Unknown";
    }

    /**
     * 获取客户端操作系统名称
     * @param request 请求对象
     * @return 操作系统名称，未知则返回 Unknown
     */
    public String getOperatingSystem(HttpServletRequest request) {
        UserAgent ua = parseUserAgent(request);
        return ua != null && ua.getOperatingSystem() != null ? ua.getOperatingSystem().getName() : "Unknown";
    }

    /**
     * 获取客户端设备类型
     * @param request 请求对象
     * @return 设备类型名称，未知则返回 Unknown
     */
    public String getDeviceName(HttpServletRequest request) {
        UserAgent ua = parseUserAgent(request);
        if (ua == null || ua.getOperatingSystem() == null) {
            return "Unknown";
        }
        return ua.getOperatingSystem().getDeviceType().getName();
    }
}