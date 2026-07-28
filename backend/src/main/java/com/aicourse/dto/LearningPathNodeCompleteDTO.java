package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 学习路径节点完成请求DTO
 * 用于记录节点完成时的得分和耗时
 */
@Schema(description = "节点完成请求")
@Data
public class LearningPathNodeCompleteDTO {

    @NotNull(message = "得分不能为空")
    @DecimalMin(value = "0.0", inclusive = true, message = "得分不能为负数")
    @Schema(description = "得分", example = "95.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal score;

    @NotNull(message = "实际用时不能为空")
    @Min(value = 0, message = "用时不能为负数")
    @Schema(description = "实际用时（分钟）", example = "45", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer actualTime;

}
