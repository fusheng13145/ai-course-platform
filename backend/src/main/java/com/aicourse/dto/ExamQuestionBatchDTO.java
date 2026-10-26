package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * 批量替换试卷题目请求 DTO（与 ExamQuestion 实体对齐）。
 *
 * <p>语义为<b>批量替换</b>：先清空该试卷原有关联行，再按清单写入。
 * points 缺省时回落题库题目分值（题库分值亦缺省则为 5，与自动组卷兜底口径一致）；
 * orderIndex 缺省时按清单顺序补齐。</p>
 */
@Schema(description = "批量替换试卷题目请求")
@lombok.Data
public class ExamQuestionBatchDTO {

    @Valid
    @NotNull(message = "题目清单不能为空")
    @Schema(description = "试卷题目清单")
    private List<ExamQuestionItem> questions;

    /**
     * 试卷题目项：对应 exam_questions 关联行。
     */
    @Schema(description = "试卷题目项")
    @lombok.Data
    public static class ExamQuestionItem {

        @NotNull(message = "题目ID不能为空")
        @Schema(description = "题库题目ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private Long questionId;

        @Schema(description = "题目分值（缺省回落题库题目分值）")
        private BigDecimal points;

        @Schema(description = "排序序号（缺省按清单顺序）")
        private Integer orderIndex;
    }
}
