package com.aicourse.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 最终成绩计算明细项出参（单个成绩构成项）。
 * 字段名与历史 Map 键一致（key / name / weight / score / contribution），保证前端零改动。
 * <p>
 * 全部键在原实现中均无条件 put，故采用默认 INCLUDE.ALWAYS 策略（不加 NON_NULL）。
 */
@Data
public class GradeComponentDetailVO {

    /** 构成项标识（对应 GradeItem.componentKey） */
    private String key;
    /** 构成项名称 */
    private String name;
    /** 权重（百分数，如 30 表示 30%） */
    private double weight;
    /** 该项得分 */
    private BigDecimal score;
    /** 加权后的贡献分 */
    private BigDecimal contribution;

    public GradeComponentDetailVO() {
    }

    public GradeComponentDetailVO(String key, String name, double weight,
                                  BigDecimal score, BigDecimal contribution) {
        this.key = key;
        this.name = name;
        this.weight = weight;
        this.score = score;
        this.contribution = contribution;
    }
}
