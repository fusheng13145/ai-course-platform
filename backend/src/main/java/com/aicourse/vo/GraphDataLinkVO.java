package com.aicourse.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 知识图谱可视化「图边」子结构出参。
 * 字段名与历史 Map 键一致（source / target / value / relationType）。
 * 原实现无条件 put 此四键（恒含），故默认 INCLUDE.ALWAYS（不标 NON_NULL），精确复刻。
 */
@Data
public class GraphDataLinkVO {

    private Long source;
    private Long target;
    private BigDecimal value;
    private String relationType;

    public GraphDataLinkVO() {
    }

    public GraphDataLinkVO(Long source, Long target, BigDecimal value, String relationType) {
        this.source = source;
        this.target = target;
        this.value = value;
        this.relationType = relationType;
    }
}
