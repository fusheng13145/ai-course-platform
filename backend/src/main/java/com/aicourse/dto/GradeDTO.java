package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 作业评分请求 DTO（白名单）
 * 评分接口仅允许教师/管理员写入 score 与 feedback，杜绝越权修改其他字段。
 */
@Schema(description = "作业评分请求")
@lombok.Data
public class GradeDTO {

    @NotNull(message = "分数不能为空")
    @DecimalMin(value = "0.0", inclusive = true, message = "分数不能为负")
    @DecimalMax(value = "1000.0", inclusive = true, message = "分数超出合理范围")
    @Schema(description = "评分（0~任务满分）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal score;

    @Schema(description = "教师评语")
    @Size(max = 5000, message = "评语过长")
    private String feedback;
}
