package com.aicourse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 试卷统计出参（题型分布、分值分布）。
 * 字段名与历史 Map 键一致（paperInfo / questionCount / totalPoints / typeDistribution / scoreDistribution）。
 * paperInfo 原为内联 Map（id/title/status），一并收敛为 {@link ExamPaperStatsPaperVO}；
 * typeDistribution / scoreDistribution 为动态键嵌套 Map（键=题型/分数段，值=数量），保留不强行结构化（同 VoteResultVO.counts 模式）。
 * 原实现无条件 put 此五键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class ExamPaperStatsVO {

    private ExamPaperStatsPaperVO paperInfo;
    private Integer questionCount;
    private BigDecimal totalPoints;
    private Map<String, Integer> typeDistribution;
    private Map<String, Integer> scoreDistribution;

    public ExamPaperStatsVO() {
    }

    public ExamPaperStatsVO(ExamPaperStatsPaperVO paperInfo, Integer questionCount, BigDecimal totalPoints,
                            Map<String, Integer> typeDistribution, Map<String, Integer> scoreDistribution) {
        this.paperInfo = paperInfo;
        this.questionCount = questionCount;
        this.totalPoints = totalPoints;
        this.typeDistribution = typeDistribution;
        this.scoreDistribution = scoreDistribution;
    }
}
