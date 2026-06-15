package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 任务创建请求体DTO
 * 用于接收前端创建作业/任务时的请求数据
 */
@Schema(description = "任务创建请求体")
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

    // ====================== Getter & Setter ======================

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public java.math.BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(java.math.BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public String getSubmissionFormat() {
        return submissionFormat;
    }

    public void setSubmissionFormat(String submissionFormat) {
        this.submissionFormat = submissionFormat;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }

    public String getAttachmentUrls() {
        return attachmentUrls;
    }

    public void setAttachmentUrls(String attachmentUrls) {
        this.attachmentUrls = attachmentUrls;
    }
}
