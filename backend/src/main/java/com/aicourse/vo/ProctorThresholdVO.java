package com.aicourse.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 监考阈值配置 VO。
 * <p>
 * 由 {@code ProctorController.getThresholds}（GET /api/proctor/thresholds）产出：
 * 高/中置信度阈值与低/中/高权重。字段名与历史 Map 返回保持一致，
 * 前端消费无需改动（2026-08-30 由 Map 裸返回收敛为结构化出参）。
 * </p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProctorThresholdVO {

    /** 高置信度阈值 */
    private Integer high;

    /** 中置信度阈值 */
    private Integer medium;

    /** 低权重 */
    private Integer weightLow;

    /** 中权重 */
    private Integer weightMedium;

    /** 高权重 */
    private Integer weightHigh;

    public ProctorThresholdVO() {
    }

    public ProctorThresholdVO(Integer high, Integer medium, Integer weightLow,
                              Integer weightMedium, Integer weightHigh) {
        this.high = high;
        this.medium = medium;
        this.weightLow = weightLow;
        this.weightMedium = weightMedium;
        this.weightHigh = weightHigh;
    }
}
