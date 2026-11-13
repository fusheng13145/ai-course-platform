package com.aicourse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 试卷统计出参（题型分布、分值分布、作答成绩聚合）。
 * 字段名与历史 Map 键一致（paperInfo / questionCount / totalPoints / typeDistribution / scoreDistribution）。
 * paperInfo 原为内联 Map（id/title/status），一并收敛为 {@link ExamPaperStatsPaperVO}；
 * typeDistribution / scoreDistribution 为动态键嵌套 Map（键=题型/分数段，值=数量），保留不强行结构化（同 VoteResultVO.counts 模式）。
 * 原实现无条件 put 此五键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 *
 * <p>成绩聚合口径：只统计 {@code exam_sessions.status=GRADED} 且 {@code percentage} 非空的会话；
 * {@code averageScore}/{@code passRate} 均按百分制；无样本时为 {@code null}（前端显示「—」，不画 0）。</p>
 */
@Data
public class ExamPaperStatsVO {

    private ExamPaperStatsPaperVO paperInfo;
    private Integer questionCount;
    private BigDecimal totalPoints;
    private Map<String, Integer> typeDistribution;
    private Map<String, Integer> scoreDistribution;
    /** 已阅卷会话数（成绩聚合分母） */
    private Integer submittedCount;
    /** 平均分（百分制，1 位小数）；无样本为 null */
    private BigDecimal averageScore;
    /** 及格率（百分制，1 位小数）；无样本为 null */
    private BigDecimal passRate;
    /** 及格线（百分制固定 60） */
    private Integer passLine;

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
