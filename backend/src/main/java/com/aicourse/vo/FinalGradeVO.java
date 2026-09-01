package com.aicourse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学生最终总评成绩出参（成绩方案维度）。
 * 字段名与历史 Map 键一致（policyId / studentId / components / finalScore），保证前端零改动。
 * <p>
 * components 原为 {@code List<Map<String,Object>>}，此处一并结构化为
 * {@link GradeComponentDetailVO} 列表，JSON 键保持不变。
 * 全部键在原实现中均无条件 put，故采用默认 INCLUDE.ALWAYS 策略（不加 NON_NULL）。
 */
@Data
public class FinalGradeVO {

    private Long policyId;
    private Long studentId;
    /** 各成绩构成项明细（顺序与成绩方案配置一致） */
    private List<GradeComponentDetailVO> components;
    /** 加权总评成绩（保留 2 位小数） */
    private BigDecimal finalScore;

    public FinalGradeVO() {
    }

    public FinalGradeVO(Long policyId, Long studentId,
                        List<GradeComponentDetailVO> components, BigDecimal finalScore) {
        this.policyId = policyId;
        this.studentId = studentId;
        this.components = components;
        this.finalScore = finalScore;
    }
}
