package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 最终成绩计算明细项出参（单个成绩构成项）。
 * 历史 Map 键为 key / name / weight / score / contribution，{@code graded} 为
 * 「未评分维度剔除」语义新增的标记（2026-10-04），旧前端忽略即可，不影响既有渲染。
 * <p>
 * 全部键在原实现中均无条件 put；项目全局 Jackson 默认是 non_null，会丢掉
 * {@code score=null}（未评分）这一关键信号，故此处显式声明 ALWAYS。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class GradeComponentDetailVO {

    /** 构成项标识（对应 GradeItem.componentKey） */
    private String key;
    /** 构成项名称 */
    private String name;
    /** 权重（百分数，如 30 表示 30%） */
    private double weight;
    /** 该项得分；{@code null} 表示尚未评分（不再以 0 分冒充未评分） */
    private BigDecimal score;
    /** 加权后的贡献分（相对已评分维度权重归一后的实际得分贡献） */
    private BigDecimal contribution;
    /** 该维度是否已计入总评（false = 未评分或权重被降级为 0，前端据此显示提示） */
    private boolean graded;

    public GradeComponentDetailVO() {
    }

    public GradeComponentDetailVO(String key, String name, double weight,
                                  BigDecimal score, BigDecimal contribution, boolean graded) {
        this.key = key;
        this.name = name;
        this.weight = weight;
        this.score = score;
        this.contribution = contribution;
        this.graded = graded;
    }
}
