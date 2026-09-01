package com.aicourse.vo;

import lombok.Data;

import java.util.Map;

/**
 * 错题统计出参。
 * 字段名与历史 Map 键一致（total / mastered / unmastered / masteryRate /
 * sourceTypeStats / reasonDistribution / practiceDistribution）。
 * sourceTypeStats / reasonDistribution 为动态键嵌套 Map（键=来源/错因，值=数量），保留不强行结构化；
 * practiceDistribution 原为固定两键（neverOrOnce/multipleTimes），同样保留 Map 形态（同 VoteResultVO.counts 模式）。
 * 原实现无条件 put 此七键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class WrongQuestionStatsVO {

    private long total;
    private long mastered;
    private long unmastered;
    private double masteryRate;
    private Map<String, Long> sourceTypeStats;
    private Map<String, Long> reasonDistribution;
    private Map<String, Long> practiceDistribution;

    public WrongQuestionStatsVO() {
    }

    public WrongQuestionStatsVO(long total, long mastered, long unmastered, double masteryRate,
                                Map<String, Long> sourceTypeStats, Map<String, Long> reasonDistribution,
                                Map<String, Long> practiceDistribution) {
        this.total = total;
        this.mastered = mastered;
        this.unmastered = unmastered;
        this.masteryRate = masteryRate;
        this.sourceTypeStats = sourceTypeStats;
        this.reasonDistribution = reasonDistribution;
        this.practiceDistribution = practiceDistribution;
    }
}
