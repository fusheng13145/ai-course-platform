package com.aicourse.vo;

import lombok.Data;

/**
 * 用户会话统计出参（在线用户数 + 本次清理的过期会话数）。
 * 字段名与历史 Map 键一致（onlineUserCount / cleanedExpiredSessions），保证前端零改动。
 * <p>
 * 两个键在原实现中均无条件 put，故采用默认 INCLUDE.ALWAYS 策略（不加 NON_NULL）。
 */
@Data
public class UserSessionStatsVO {

    /** 当前在线用户数 */
    private Long onlineUserCount;

    /** 本次统计顺带清理掉的过期会话数量 */
    private int cleanedExpiredSessions;

    public UserSessionStatsVO() {
    }

    public UserSessionStatsVO(Long onlineUserCount, int cleanedExpiredSessions) {
        this.onlineUserCount = onlineUserCount;
        this.cleanedExpiredSessions = cleanedExpiredSessions;
    }
}
