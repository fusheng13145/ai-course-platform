package com.aicourse.vo;

import com.aicourse.entity.UserSession;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录会话出参（15 字段）。
 *
 * <p>`sessionToken` / `refreshToken` 在实体上已有 {@code @JsonIgnore}，
 * 本 VO 干脆不声明这两列——出参里连字段名都不出现，比依赖注解更稳。
 */
@Data
public class UserSessionVO {

    private Long id;
    private Long userId;
    private String deviceName;
    private String browser;
    private String operatingSystem;
    private String ipAddress;
    private String location;
    private String userAgent;
    private Boolean isActive;
    private Boolean isCurrent;
    private LocalDateTime loginTime;
    private LocalDateTime lastActivity;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserSessionVO from(UserSession session) {
        if (session == null) {
            return null;
        }
        UserSessionVO vo = new UserSessionVO();
        vo.setId(session.getId());
        vo.setUserId(session.getUserId());
        vo.setDeviceName(session.getDeviceName());
        vo.setBrowser(session.getBrowser());
        vo.setOperatingSystem(session.getOperatingSystem());
        vo.setIpAddress(session.getIpAddress());
        vo.setLocation(session.getLocation());
        vo.setUserAgent(session.getUserAgent());
        vo.setIsActive(session.getIsActive());
        vo.setIsCurrent(session.getIsCurrent());
        vo.setLoginTime(session.getLoginTime());
        vo.setLastActivity(session.getLastActivity());
        vo.setExpiresAt(session.getExpiresAt());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setUpdatedAt(session.getUpdatedAt());
        return vo;
    }

    public static List<UserSessionVO> from(List<UserSession> sessions) {
        return sessions == null ? List.of() : sessions.stream().map(UserSessionVO::from).toList();
    }
}


