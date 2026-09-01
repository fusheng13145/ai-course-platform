package com.aicourse.vo;

import lombok.Data;

import java.util.Map;

/**
 * 全局登录统计出参。
 * 字段名与历史 Map 键一致（totalCount / successCount / failedCount / successRate /
 * todayCount / weekCount / statusDistribution），保证前端零改动。
 * <p>
 * statusDistribution 为按登录状态分组的动态键映射（且仅统计数量大于 0 的状态），
 * 属合法的动态键结构，故保留 {@code Map<String, Long>} 而不强行结构化，
 * 与 VoteResultVO.counts 处理策略一致。
 * <p>
 * 其余键均无条件 put，故采用默认 INCLUDE.ALWAYS 策略（不加 NON_NULL）。
 */
@Data
public class LoginGlobalStatsVO {

    private long totalCount;
    private long successCount;
    private long failedCount;
    /** 成功率百分比（保留 2 位小数） */
    private double successRate;
    private long todayCount;
    private long weekCount;
    /** 各登录状态分布（SUCCESS / FAILED / LOCKED / EXPIRED），仅含数量大于 0 的状态 */
    private Map<String, Long> statusDistribution;

    public LoginGlobalStatsVO() {
    }

    public LoginGlobalStatsVO(long totalCount, long successCount, long failedCount,
                              double successRate, long todayCount, long weekCount,
                              Map<String, Long> statusDistribution) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.successRate = successRate;
        this.todayCount = todayCount;
        this.weekCount = weekCount;
        this.statusDistribution = statusDistribution;
    }
}
