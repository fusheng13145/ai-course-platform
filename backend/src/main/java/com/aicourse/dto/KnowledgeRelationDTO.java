package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建知识点关联关系请求DTO
 * 用于接收教师/管理员创建知识点之间关联关系时的请求数据
 */
@Schema(description = "创建知识点关联关系请求")
@Data
public class KnowledgeRelationDTO {

    @NotNull(message = "源知识点ID不能为空")
    @Schema(description = "源知识点ID（关系起点）", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceId;

    @NotNull(message = "目标知识点ID不能为空")
    @Schema(description = "目标知识点ID（关系终点）", example = "2",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetId;

    @NotBlank(message = "关联类型不能为空")
    @Size(max = 50, message = "关联类型不能超过50个字符")
    @Schema(description = "关联类型", example = "PREREQUISITE",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String relationType;

    @Schema(description = "关系强度（0.0-1.0之间）", example = "0.85")
    private BigDecimal strength;

    @Size(max = 500, message = "关系描述不能超过500个字符")
    @Schema(description = "关联关系描述", example = "学习A是理解B的前置条件")
    private String description;

}
