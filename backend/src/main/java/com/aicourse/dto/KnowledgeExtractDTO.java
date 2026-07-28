package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * AI知识图谱抽取请求DTO
 * 用于教师/管理员调用AI从课程内容中自动抽取知识点和关联关系
 */
@Schema(description = "AI知识图谱抽取请求")
@Data
public class KnowledgeExtractDTO {

    @NotNull(message = "课程ID不能为空")
    @Schema(description = "所属课程ID", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @Schema(description = "待抽取的课程文本内容", example = "第一章介绍Java概述，包含语言发展史、JDK安装...")
    private String content;

}
