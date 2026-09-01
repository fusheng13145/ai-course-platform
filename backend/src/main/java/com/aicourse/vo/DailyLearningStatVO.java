package com.aicourse.vo;

import lombok.Data;

/**
 * 单日学习统计出参（学习趋势 / 月度日历共用）。
 * 字段名与历史 Map 键一致（date / duration / count）。
 * 原实现无条件 put 此三键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 *
 * 覆盖 4 个端点（两两共用同一 Service 方法）：
 * - GET /student/progress/statistics、GET /learning-records/trend  → getLearningTrend
 * - GET /student/progress/calendar、GET /learning-records/calendar → getMonthlyLearningData
 */
@Data
public class DailyLearningStatVO {

    /** 日期（YYYY-MM-DD） */
    private String date;
    /** 学习时长（分钟） */
    private int duration;
    /** 学习次数 */
    private int count;

    public DailyLearningStatVO() {
    }

    public DailyLearningStatVO(String date, int duration, int count) {
        this.date = date;
        this.duration = duration;
        this.count = count;
    }
}
