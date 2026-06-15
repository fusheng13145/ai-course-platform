package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建任务请求DTO（增强版）
 * 用于接收教师创建作业/任务/测验/项目/练习时的完整请求数据
 */
@Schema(description = "创建任务请求（增强版）")
public class TaskCreateEnhancedDTO {

    @NotNull(message = "所属课程ID不能为空")
    @Schema(description = "所属课程ID", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long courseId;

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 100, message = "任务标题不能超过100个字符")
    @Schema(description = "任务标题", example = "第一次编程作业：实现学生管理系统",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Size(max = 5000, message = "任务描述不能超过5000个字符")
    @Schema(description = "任务详细描述（支持Markdown富文本）", example = "## 作业要求\n\n1. 使用Java实现...\n2. 数据库使用MySQL...")
    private String description;

    @Pattern(regexp = "^(ASSIGNMENT|QUIZ|PROJECT|PRACTICE)$", message = "任务类型不合法")
    @Schema(description = "任务类型", example = "ASSIGNMENT",
            allowableValues = {"ASSIGNMENT", "QUIZ", "PROJECT", "PRACTICE"})
    private String type = "ASSIGNMENT";

    @Size(max = 10000, message = "内容长度不能超过10000个字符")
    @Schema(description = "任务内容（富文本/HTML/Markdown）", example = "<p>请完成以下编程题目...</p>")
    private String content;

    @Schema(description = "截止时间", example = "2024-12-31T23:59:59")
    private LocalDateTime dueDate;

    @DecimalMin(value = "0.1", message = "满分分值最小为0.1")
    @DecimalMax(value = "10000", message = "满分分值最大为10000")
    @Schema(description = "满分分值", example = "100")
    private BigDecimal maxScore = BigDecimal.valueOf(100);

    @Schema(description = "是否允许逾期提交", example = "true")
    private Boolean allowLateSubmission = false;

    @Min(value = 0, message = "每日扣分不能为负数")
    @Max(value = 50, message = "每日扣分不能超过50")
    @Schema(description = "逾期每天扣分数（仅allowLateSubmission=true时生效）", example = "10")
    private Integer latePenaltyPerDay = 0;

    @Min(value = 0, message = "最大尝试次数不能为负数")
    @Schema(description = "最大提交次数（0表示无限次）", example = "3")
    private Integer maxAttempts = 0;

    @Schema(description = "是否打乱选项顺序（测验类型有效）", example = "false")
    private Boolean shuffleOptions = false;

    @Schema(description = "附件URL列表", example = "[\"https://example.com/requirement.pdf\", \"https://example.com/template.zip\"]")
    private List<String> attachments;

    // ====================== Getter & Setter ======================

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal maxScore) { this.maxScore = maxScore; }
    public Boolean getAllowLateSubmission() { return allowLateSubmission; }
    public void setAllowLateSubmission(Boolean allowLateSubmission) { this.allowLateSubmission = allowLateSubmission; }
    public Integer getLatePenaltyPerDay() { return latePenaltyPerDay; }
    public void setLatePenaltyPerDay(Integer latePenaltyPerDay) { this.latePenaltyPerDay = latePenaltyPerDay; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public Boolean getShuffleOptions() { return shuffleOptions; }
    public void setShuffleOptions(Boolean shuffleOptions) { this.shuffleOptions = shuffleOptions; }
    public List<String> getAttachments() { return attachments; }
    public void setAttachments(List<String> attachments) { this.attachments = attachments; }
}
