package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务创建请求体DTO
 * 用于接收前端创建作业/任务时的请求数据
 */
@Schema(description = "任务创建请求体")
@Data
public class TaskCreateDTO {

    @Schema(description = "任务标题", example = "第一次编程作业", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "任务描述", example = "完成第1-3章的课后练习题")
    private String description;

    @Schema(description = "任务类型", example = "HOMEWORK", allowableValues = {"HOMEWORK", "PROGRAMMING", "QUIZ", "PROJECT"})
    private String taskType;

    @Schema(description = "所属课程ID", example = "100")
    private Long courseId;

    @Schema(description = "截止时间（ISO格式）", example = "2024-12-31T23:59:59")
    private String dueDate;

    @Schema(description = "满分分值", example = "100")
    private java.math.BigDecimal maxScore;

    @Schema(description = "提交格式要求", example = "PDF", allowableValues = {"PDF", "WORD", "CODE", "LINK", "OTHER"})
    private String submissionFormat;

    @Schema(description = "作业要求说明", example = "独立完成，禁止抄袭")
    private String requirements;

    @Schema(description = "附件URL列表（JSON数组格式）", example = "[\"https://example.com/file1.pdf\"]")
    private String attachmentUrls;

    @Schema(description = "新的截止日期（ISO格式，延长截止日期用）", example = "2025-01-15T23:59:59")
    private String newDueDate;

    @Schema(description = "延长截止日期的原因", example = "因假期延长截止时间")
    private String reason;

}
