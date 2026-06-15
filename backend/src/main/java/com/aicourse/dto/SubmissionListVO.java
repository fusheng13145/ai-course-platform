package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提交列表项VO（教师视角）
 * 用于返回给教师的作业提交列表数据，包含学生信息、评分状态等摘要信息
 */
@Schema(description = "提交列表项（教师视角）")
public class SubmissionListVO {

    @Schema(description = "提交记录ID", example = "1001")
    private Long submissionId;

    @Schema(description = "提交学生ID", example = "2001")
    private Long studentId;

    @Schema(description = "学生姓名", example = "张三")
    private String studentName;

    @Schema(description = "学生头像URL", example = "https://example.com/avatar/zhangsan.jpg")
    private String studentAvatar;

    @Schema(description = "任务名称", example = "第一次编程作业")
    private String taskName;

    @Schema(description = "得分（未批改时为null）", example = "85.5")
    private BigDecimal score;

    @Schema(description = "提交状态", example = "GRADED",
            allowableValues = {"SUBMITTED", "GRADED", "RETURNED", "LATE"})
    private String status;

    @Schema(description = "是否逾期提交", example = "false")
    private Boolean isLate = false;

    @Schema(description = "逾期扣分数", example = "10.0")
    private BigDecimal latePenalty = BigDecimal.ZERO;

    @Schema(description = "提交时间", example = "2024-12-30T20:15:00")
    private LocalDateTime submittedAt;

    @Schema(description = "批改时间（未批改时为null）", example = "2024-12-31T09:30:00")
    private LocalDateTime gradedAt;

    @Schema(description = "AI评语摘要", example = "代码结构清晰，逻辑正确，但缺少异常处理...")
    private String aiFeedback;

    @Schema(description = "查重相似度(0-100)", example = "12.5")
    private BigDecimal plagiarismScore;

    // ====================== Getter & Setter ======================

    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getStudentAvatar() { return studentAvatar; }
    public void setStudentAvatar(String studentAvatar) { this.studentAvatar = studentAvatar; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getIsLate() { return isLate; }
    public void setIsLate(Boolean isLate) { this.isLate = isLate; }
    public BigDecimal getLatePenalty() { return latePenalty; }
    public void setLatePenalty(BigDecimal latePenalty) { this.latePenalty = latePenalty; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getGradedAt() { return gradedAt; }
    public void setGradedAt(LocalDateTime gradedAt) { this.gradedAt = gradedAt; }
    public String getAiFeedback() { return aiFeedback; }
    public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }
    public BigDecimal getPlagiarismScore() { return plagiarismScore; }
    public void setPlagiarismScore(BigDecimal plagiarismScore) { this.plagiarismScore = plagiarismScore; }
}
