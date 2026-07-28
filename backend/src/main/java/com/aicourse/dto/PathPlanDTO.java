package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 学习路径规划请求DTO
 * 用于规划从起点到目标知识点的最优学习路径
 */
@Schema(description = "学习路径规划请求")
@Data
public class PathPlanDTO {

    @NotNull(message = "起点知识点ID不能为空")
    @Schema(description = "起点知识点ID", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long startPointId;

    @NotNull(message = "目标知识点ID不能为空")
    @Schema(description = "目标知识点ID", example = "5",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetPointId;

}
