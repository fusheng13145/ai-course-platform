package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/**
 * 创建提醒请求DTO
 * 用于接收前端创建定时提醒时的请求数据，支持多种提醒类型和触发条件
 */
@Schema(description = "创建提醒请求")
public class ReminderCreateDTO {

    @NotBlank(message = "提醒标题不能为空")
    @Size(max = 100, message = "标题不能超过100个字符")
    @Schema(description = "提醒标题", example = "作业截止提醒",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank(message = "提醒内容不能为空")
    @Size(max = 2000, message = "提醒内容不能超过2000个字符")
    @Schema(description = "提醒内容/消息", example = "您的《第一次编程作业》将于明天截止，请尽快提交！",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Pattern(regexp = "^(BEFORE_DEADLINE|DAILY_STUDY|NEW_CONTENT)$", message = "提醒类型不合法")
    @Schema(description = "提醒类型", example = "BEFORE_DEADLINE",
            allowableValues = {"BEFORE_DEADLINE", "DAILY_STUDY", "NEW_CONTENT"})
    private String remindType = "BEFORE_DEADLINE";

    @Pattern(regexp = "^(TASK|EXAM|COURSE)$", message = "关联业务类型不合法")
    @Schema(description = "关联的业务实体类型", example = "TASK",
            allowableValues = {"TASK", "EXAM", "COURSE"})
    private String relatedType;

    @Schema(description = "关联的业务实体ID", example = "101")
    private Long relatedId;

    @Min(value = 1, message = "提前提醒时间至少为1")
    @Schema(description = "提前多少分钟/天触发提醒\n" +
            "- BEFORE_DEADLINE: 分钟\n" +
            "- DAILY_STUDY: 天\n" +
            "- NEW_CONTENT: 不适用", example = "60")
    private Integer remindBefore;

    // ====================== Getter & Setter ======================

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRemindType() {
        return remindType;
    }

    public void setRemindType(String remindType) {
        this.remindType = remindType;
    }

    public String getRelatedType() {
        return relatedType;
    }

    public void setRelatedType(String relatedType) {
        this.relatedType = relatedType;
    }

    public Long getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(Long relatedId) {
        this.relatedId = relatedId;
    }

    public Integer getRemindBefore() {
        return remindBefore;
    }

    public void setRemindBefore(Integer remindBefore) {
        this.remindBefore = remindBefore;
    }
}
