package com.aicourse.vo;

import lombok.Data;

/**
 * 每日登录统计出参（GET /admin/login-logs/trends）。
 * 字段名与历史 Map 键一致（date / successCount / failedCount / totalCount）。
 * 原实现无条件 put 此四键（恒含），故默认 INCLUDE.ALWAYS，精确复刻。
 */
@Data
public class DailyLoginTrendVO {

    /** 日期（YYYY-MM-DD） */
    private String date;
    /** 成功登录次数 */
    private long successCount;
    /** 失败登录次数 */
    private long failedCount;
    /** 总登录次数 */
    private long totalCount;

    public DailyLoginTrendVO() {
    }

    public DailyLoginTrendVO(String date, long successCount, long failedCount, long totalCount) {
        this.date = date;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.totalCount = totalCount;
    }
}
