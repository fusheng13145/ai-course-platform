package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 人工评分请求DTO
 * 用于教师对考试会话中的主观题进行人工打分和评语
 */
@Schema(description = "人工评分请求")
@Data
public class ExamManualGradeDTO {

    @NotNull(message = "题目ID不能为空")
    @Schema(description = "题目ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long questionId;

    @NotNull(message = "得分不能为空")
    @DecimalMin(value = "0.0", inclusive = true, message = "得分不能为负数")
    @Schema(description = "得分", example = "8.5", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal score;

    @Schema(description = "教师评语", example = "思路清晰，论证充分")
    private String comment;

}
