package com.aicourse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 题目使用统计出参。
 * 字段名与历史 Map 键一致（questionId / usageCount / correctRate / difficulty / type / score / isActive / createdAt）。
 * 原实现无条件 put 此八键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class QuestionStatsVO {

    private Long questionId;
    private Integer usageCount;
    private BigDecimal correctRate;
    private String difficulty;
    private String type;
    private BigDecimal score;
    private Boolean isActive;
    private LocalDateTime createdAt;

    public QuestionStatsVO() {
    }

    public QuestionStatsVO(Long questionId, Integer usageCount, BigDecimal correctRate,
                           String difficulty, String type, BigDecimal score,
                           Boolean isActive, LocalDateTime createdAt) {
        this.questionId = questionId;
        this.usageCount = usageCount;
        this.correctRate = correctRate;
        this.difficulty = difficulty;
        this.type = type;
        this.score = score;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }
}
