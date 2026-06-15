package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 试卷题目配置DTO
 * 用于定义试卷中每道题目的引用、排序、分值和选项顺序
 */
@Schema(description = "试卷题目配置")
public class ExamQuestionDTO {

    @NotNull(message = "题目ID不能为空")
    @Schema(description = "引用的题库题目ID", example = "101",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long questionId;

    @Min(value = 0, message = "排序序号不能为负数")
    @Schema(description = "在试卷中的排序序号（从0开始）", example = "0")
    private Integer sortOrder = 0;

    @NotNull(message = "该题分值不能为空")
    @DecimalMin(value = "0.1", message = "分值最小为0.1")
    @Schema(description = "该题在试卷中的分值", example = "10",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal score;

    @Schema(description = "打乱后的选项顺序索引列表（null表示不乱序）",
            example = "[2, 0, 3, 1]")
    private List<Integer> optionsOrder;

    // ====================== Getter & Setter ======================

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public List<Integer> getOptionsOrder() {
        return optionsOrder;
    }

    public void setOptionsOrder(List<Integer> optionsOrder) {
        this.optionsOrder = optionsOrder;
    }
}
