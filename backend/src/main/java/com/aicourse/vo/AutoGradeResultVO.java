package com.aicourse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 自动阅卷结果出参（POST /exam-sessions/{id}/auto-grade）。
 * 字段名与历史 Map 键一致（sessionId / totalScore / fullScore / percentage /
 * correctCount / wrongCount / pendingReviewCount / questionScores / status）。
 * questionScores 为动态键嵌套 Map（键=题目ID，值=该题得分 BigDecimal），保留 Map 形态（同 VoteResultVO.counts 模式）。
 * status 恒为 GRADED（原实现写死 ExamSessionStatus.GRADED）。
 * 原实现无条件 put 此九键（恒含），故默认 INCLUDE.ALWAYS，精确复刻。
 */
@Data
public class AutoGradeResultVO {

    private Long sessionId;
    private BigDecimal totalScore;
    private BigDecimal fullScore;
    private BigDecimal percentage;
    private int correctCount;
    private int wrongCount;
    private int pendingReviewCount;
    private Map<Long, BigDecimal> questionScores;
    private String status;

    public AutoGradeResultVO() {
    }

    public AutoGradeResultVO(Long sessionId, BigDecimal totalScore, BigDecimal fullScore,
                             BigDecimal percentage, int correctCount, int wrongCount,
                             int pendingReviewCount, Map<Long, BigDecimal> questionScores,
                             String status) {
        this.sessionId = sessionId;
        this.totalScore = totalScore;
        this.fullScore = fullScore;
        this.percentage = percentage;
        this.correctCount = correctCount;
        this.wrongCount = wrongCount;
        this.pendingReviewCount = pendingReviewCount;
        this.questionScores = questionScores;
        this.status = status;
    }
}
