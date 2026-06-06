package com.aicourse.util;

import eu.bitwalker.useragentutils.UserAgent;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class UserAgentUtil {
    public String getBrowser(HttpServletRequest request) {
        UserAgent ua = UserAgent.parseUserAgentString(request.getHeader("User-Agent"));
        return ua.getBrowser() != null ? ua.getBrowser().getName() : "Unknown";
    }

    public String getOperatingSystem(HttpServletRequest request) {
        UserAgent ua = UserAgent.parseUserAgentString(request.getHeader("User-Agent"));
        return ua.getOperatingSystem() != null ? ua.getOperatingSystem().getName() : "Unknown";
    }

    public String getDeviceName(HttpServletRequest request) {
        UserAgent ua = UserAgent.parseUserAgentString(request.getHeader("User-Agent"));
        return ua.getOperatingSystem() != null ? ua.getOperatingSystem().getDeviceType().getName() : "Unknown";
    }
}
