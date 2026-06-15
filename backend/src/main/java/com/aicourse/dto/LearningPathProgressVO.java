package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 学习路径进度响应VO
 * 用于返回给前端的学习路径整体进度、今日任务和连续学习统计等信息
 */
@Schema(description = "学习路径进度响应")
public class LearningPathProgressVO {

    @Schema(description = "学习路径ID", example = "501")
    private Long pathId;

    @Schema(description = "路径标题", example = "Java程序设计 - 30天速成路线")
    private String title;

    @Schema(description = "路径状态", example = "IN_PROGRESS",
            allowableValues = {"NOT_STARTED", "IN_PROGRESS", "COMPLETED", "PAUSED", "EXPIRED"})
    private String status;

    @Schema(description = "总体完成进度(0-100)", example = "45.5")
    private Double overallProgress;

    @Schema(description = "今日任务列表")
    private List<DailyTaskVO> todayTasks;

    @Schema(description = "已完成节点数", example = "12")
    private Integer completedNodes;

    @Schema(description = "总节点数", example = "28")
    private Integer totalNodes;

    @Schema(description = "预计剩余天数", example = "18")
    private Integer estimatedRemainingDays;

    @Schema(description = "连续学习天数", example = "7")
    private Integer streakDays;

    // ====================== Getter & Setter ======================

    public Long getPathId() { return pathId; }
    public void setPathId(Long pathId) { this.pathId = pathId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getOverallProgress() { return overallProgress; }
    public void setOverallProgress(Double overallProgress) { this.overallProgress = overallProgress; }
    public List<DailyTaskVO> getTodayTasks() { return todayTasks; }
    public void setTodayTasks(List<DailyTaskVO> todayTasks) { this.todayTasks = todayTasks; }
    public Integer getCompletedNodes() { return completedNodes; }
    public void setCompletedNodes(Integer completedNodes) { this.completedNodes = completedNodes; }
    public Integer getTotalNodes() { return totalNodes; }
    public void setTotalNodes(Integer totalNodes) { this.totalNodes = totalNodes; }
    public Integer getEstimatedRemainingDays() { return estimatedRemainingDays; }
    public void setEstimatedRemainingDays(Integer estimatedRemainingDays) { this.estimatedRemainingDays = estimatedRemainingDays; }
    public Integer getStreakDays() { return streakDays; }
    public void setStreakDays(Integer streakDays) { this.streakDays = streakDays; }
}
