package com.aicourse.vo;

import lombok.Data;

/**
 * 题库总览统计出参。
 * <p>口径：{@code totalCount} 为题库全部题目（含 courseId 为空的历史题）；
 * {@code todayCount} 按 {@code created_at} 当日 00:00 起算，不做课程过滤。</p>
 */
@Data
public class QuestionBankStatisticsVO {

    /** 题库总题数（含无课程归属的历史题） */
    private Long totalCount;
    /** 今日新增题数（本机时区当日 00:00 起） */
    private Long todayCount;

    public QuestionBankStatisticsVO() {
    }

    public QuestionBankStatisticsVO(Long totalCount, Long todayCount) {
        this.totalCount = totalCount;
        this.todayCount = todayCount;
    }
}
