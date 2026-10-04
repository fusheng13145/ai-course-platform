package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 学生最终总评成绩出参（成绩方案维度）。
 * 字段名与历史 Map 键一致（policyId / studentId / components / finalScore），保证前端零改动；
 * {@code warnings} 为 2026-10-04「未评分维度不计入分母 + 缺评提示」新增的可选提示字段。
 * <p>
 * components 原为 {@code List<Map<String,Object>>}，此处一并结构化为
 * {@link GradeComponentDetailVO} 列表，JSON 键保持不变。
 * 原实现无条件 put 全部键，项目全局 Jackson 默认 non_null 会静默删掉 null 值，
 * 故显式声明 ALWAYS 维持出参契约。
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class FinalGradeVO {

    private Long policyId;
    private Long studentId;
    /**
     * 学生姓名，来自 users.full_name（缺失时退回学生ID）。
     * 报表按全班批量填充；{@code /final/{policyId}/student/{studentId}} 单学生端点同样填充。
     */
    private String studentName;
    /** 各成绩构成项明细（顺序与成绩方案配置一致） */
    private List<GradeComponentDetailVO> components;
    /** 加权总评成绩（保留 2 位小数） */
    private BigDecimal finalScore;
    /**
     * 计算过程中的提示（缺评维度剔除、方案权重不合规等）。
     * 空数组表示计算无告警；前端据此在报表顶部显示提示条。
     */
    private List<String> warnings = new ArrayList<>();

    public FinalGradeVO() {
    }

    public FinalGradeVO(Long policyId, Long studentId,
                        List<GradeComponentDetailVO> components, BigDecimal finalScore,
                        List<String> warnings) {
        this.policyId = policyId;
        this.studentId = studentId;
        this.components = components;
        this.finalScore = finalScore;
        this.warnings = warnings;
    }
}
