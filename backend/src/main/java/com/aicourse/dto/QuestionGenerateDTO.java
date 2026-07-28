package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AI生成题目请求DTO
 * 用于接收AI智能生成题目的请求参数
 */
@Schema(description = "AI生成题目请求")
@Data
public class QuestionGenerateDTO {

    @NotNull(message = "课程ID不能为空")
    @Schema(description = "所属课程ID", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @NotBlank(message = "题目类型不能为空")
    @Schema(description = "题目类型", example = "SINGLE_CHOICE",
            allowableValues = {"SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER", "FILL_BLANK"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "难度等级", example = "EASY",
            allowableValues = {"EASY", "MEDIUM", "HARD"})
    private String difficulty;

    @Schema(description = "生成题目数量", example = "5")
    private Integer count;

    @Schema(description = "题目内容/题干提示", example = "请生成关于多线程的题目")
    private String content;

    @Schema(description = "知识点名称（逗号分隔）", example = "多线程,并发编程")
    private String knowledgePointNames;

}
