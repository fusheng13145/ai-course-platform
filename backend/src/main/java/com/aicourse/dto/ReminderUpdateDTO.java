package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 更新提醒请求DTO
 * 字段命名与前端编辑表单对齐（remindAt→scheduledAt，targetUserId→userId，content→message）。
 * 所有字段均可选，仅更新调用方实际提供的字段（局部更新）。
 */
@Schema(description = "更新提醒请求")
@Data
public class ReminderUpdateDTO {

    @Size(max = 100, message = "标题不能超过100个字符")
    @Schema(description = "提醒标题", example = "作业截止提醒")
    private String title;

    @Size(max = 2000, message = "提醒内容不能超过2000个字符")
    @Schema(description = "提醒内容（映射到 message）", example = "请尽快提交作业")
    private String content;

    @Schema(description = "新的计划发送时间（ISO-8601），映射到 scheduledAt", example = "2025-01-15T10:00:00")
    private String remindAt;

    @Schema(description = "接收用户ID（映射到 userId）", example = "1001")
    private Long targetUserId;
}
