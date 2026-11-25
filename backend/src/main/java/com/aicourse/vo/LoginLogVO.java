package com.aicourse.vo;

import com.aicourse.entity.LoginLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志出参。
 *
 * <p>字段与 {@link LoginLog} 实体一一对应、零删减，仅为切断「HTTP 出参 = 表结构」的耦合，
 * 不承担脱敏职责（该表本身就是审计数据，调用方已限定为管理员或其本人）。
 */
@Data
public class LoginLogVO {

    private Long id;
    private Long userId;
    private String username;
    private String loginStatus;
    private String ipAddress;
    private String location;
    private String deviceName;
    private String browser;
    private String operatingSystem;
    private String userAgent;
    private String failureReason;
    private LocalDateTime createdAt;

    /** 实体 → 出参。 */
    public static LoginLogVO from(LoginLog log) {
        if (log == null) {
            return null;
        }
        LoginLogVO vo = new LoginLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setLoginStatus(log.getLoginStatus());
        vo.setIpAddress(log.getIpAddress());
        vo.setLocation(log.getLocation());
        vo.setDeviceName(log.getDeviceName());
        vo.setBrowser(log.getBrowser());
        vo.setOperatingSystem(log.getOperatingSystem());
        vo.setUserAgent(log.getUserAgent());
        vo.setFailureReason(log.getFailureReason());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }
}
