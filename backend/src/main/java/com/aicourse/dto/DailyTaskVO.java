package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 每日任务VO
 * 用于描述学习路径中每一天的具体学习任务项及其完成状态
 */
@Schema(description = "每日任务")
public class DailyTaskVO {

    @Schema(description = "任务ID", example = "6001")
    private Long taskId;

    @Schema(description = "任务标题", example = "学习Java基础语法 - 变量与数据类型")
    private String title;

    @Pattern(regexp = "^(LEARNING|PRACTICE|EXAM|REVIEW)$", message = "节点类型不合法")
    @Schema(description = "任务/节点类型", example = "LEARNING",
            allowableValues = {"LEARNING", "PRACTICE", "EXAM", "REVIEW"})
    private String nodeType;

    @Schema(description = "计划学习时长（分钟）", example = "45")
    private Integer plannedMinutes;

    @Schema(description = "实际学习时长（分钟，未完成时为null）", example = "52")
    private Integer actualMinutes;

    @Schema(description = "任务状态", example = "COMPLETED",
            allowableValues = {"NOT_STARTED", "IN_PROGRESS", "COMPLETED", "OVERDUE", "SKIPPED"})
    private String status = "NOT_STARTED";

    @Schema(description = "得分（练习/测试类任务有效，其他为null）", example = "90.0")
    private BigDecimal score;

    @Schema(description = "完成时间（未完成时为null）", example = "2024-12-01T14:30:00")
    private LocalDateTime completedAt;

    // ====================== Getter & Setter ======================

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public Integer getPlannedMinutes() {
        return plannedMinutes;
    }

    public void setPlannedMinutes(Integer plannedMinutes) {
        this.plannedMinutes = plannedMinutes;
    }

    public Integer getActualMinutes() {
        return actualMinutes;
    }

    public void setActualMinutes(Integer actualMinutes) {
        this.actualMinutes = actualMinutes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
