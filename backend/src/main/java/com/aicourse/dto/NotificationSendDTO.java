package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发送通知请求DTO
 * 用于接收管理员/教师发送系统通知时的请求数据
 */
@Schema(description = "发送通知请求")
@Data
public class NotificationSendDTO {

    @NotBlank(message = "通知标题不能为空")
    @Size(max = 100, message = "标题不能超过100个字符")
    @Schema(description = "通知标题", example = "期中考试安排通知",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank(message = "通知内容不能为空")
    @Size(max = 5000, message = "通知内容不能超过5000个字符")
    @Schema(description = "通知正文内容（支持Markdown）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "目标用户ID列表（空列表或null表示全员发送）", example = "[1001, 1002, 1003]")
    private List<Long> targetUserIds;

    @Pattern(regexp = "^(SYSTEM|TASK|EXAM|GRADE|REMINDER|ANNOUNCEMENT)$",
            message = "通知分类不合法")
    @Schema(description = "通知分类", example = "ANNOUNCEMENT",
            allowableValues = {"SYSTEM", "TASK", "EXAM", "GRADE", "REMINDER", "ANNOUNCEMENT"})
    private String category = "SYSTEM";

    @Pattern(regexp = "^(HIGH|MEDIUM|LOW)$", message = "优先级不合法")
    @Schema(description = "通知优先级", example = "HIGH",
            allowableValues = {"HIGH", "MEDIUM", "LOW"})
    private String priority = "MEDIUM";

    @Size(max = 500, message = "跳转链接不能超过500个字符")
    @Schema(description = "点击通知后的跳转链接", example = "/courses/1/exams/5")
    private String actionUrl;

    @Schema(description = "通知类型", example = "SYSTEM",
            allowableValues = {"SYSTEM", "TASK", "EXAM", "GRADE", "REMINDER", "ANNOUNCEMENT"})
    private String type;

    @Schema(description = "附加数据（JSON格式字符串）", example = "{\"redirectId\": 123}")
    private String data;

    @Schema(description = "定时发送时间（null或省略表示立即发送）", example = "2024-12-20T09:00:00")
    private LocalDateTime scheduledAt;

    @Schema(description = "过期时间（过期后通知不再展示）", example = "2025-01-20T23:59:59")
    private LocalDateTime expireAt;

}
