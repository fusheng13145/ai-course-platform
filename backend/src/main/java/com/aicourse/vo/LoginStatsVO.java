package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户登录统计 VO。
 * <p>
 * 由 {@code LoginLogController.getLoginStats}（GET /api/login-logs/user/{userId}/stats）产出：
 * 登录总次数、成功/失败次数、成功率、最后登录时间/IP/地点与不同 IP 数量。
 * 字段名与历史 Map 返回保持一致，前端消费无需改动（2026-08-30 由 Map 裸返回收敛为结构化出参）。
 * 条件性字段（lastLoginTime/lastLoginIp/lastLoginLocation）为可空，经 {@code @JsonInclude(NON_NULL)}
 * 序列化后与原 Map「缺失键即不出现」的行为一致。
 * </p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginStatsVO {

    /** 登录总次数 */
    private Long totalCount;

    /** 成功次数 */
    private Long successCount;

    /** 失败次数 */
    private Long failedCount;

    /** 成功率（百分比，保留两位小数） */
    private Double successRate;

    /** 最后登录时间（无成功登录时为 null） */
    private LocalDateTime lastLoginTime;

    /** 最后登录 IP（无成功登录时为 null） */
    private String lastLoginIp;

    /** 最后登录地点（无成功登录时为 null） */
    private String lastLoginLocation;

    /** 不同 IP 登录数量 */
    private Long uniqueIpCount;

    public LoginStatsVO() {
    }

    public LoginStatsVO(Long totalCount, Long successCount, Long failedCount, Double successRate,
                        LocalDateTime lastLoginTime, String lastLoginIp, String lastLoginLocation, Long uniqueIpCount) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.successRate = successRate;
        this.lastLoginTime = lastLoginTime;
        this.lastLoginIp = lastLoginIp;
        this.lastLoginLocation = lastLoginLocation;
        this.uniqueIpCount = uniqueIpCount;
    }
}
