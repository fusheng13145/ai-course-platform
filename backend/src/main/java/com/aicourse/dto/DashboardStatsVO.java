package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 仪表盘统计数据VO
 * 用于返回给前端管理后台仪表盘的核心统计指标
 */
@Schema(description = "仪表盘统计数据响应")
public class DashboardStatsVO {

    @Schema(description = "总用户数", example = "12580")
    private Long totalUsers;

    @Schema(description = "总课程数", example = "356")
    private Long totalCourses;

    @Schema(description = "今日活跃用户数", example = "892")
    private Long activeUsersToday;

    @Schema(description = "整体课程完成率（小数形式，如0.72表示72%）", example = "0.72")
    private Double completionRate;

    @Schema(description = "周环比增长率（小数形式，正数为增长）", example = "0.058")
    private Double growthRate;

    @Schema(description = "待处理任务/审批数量", example = "23")
    private Long pendingTasks;

    @Schema(description = "近7天登录用户数", example = "4560")
    private Long recentLogins;

    // ====================== Getter & Setter ======================

    public Long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(Long totalUsers) { this.totalUsers = totalUsers; }
    public Long getTotalCourses() { return totalCourses; }
    public void setTotalCourses(Long totalCourses) { this.totalCourses = totalCourses; }
    public Long getActiveUsersToday() { return activeUsersToday; }
    public void setActiveUsersToday(Long activeUsersToday) { this.activeUsersToday = activeUsersToday; }
    public Double getCompletionRate() { return completionRate; }
    public void setCompletionRate(Double completionRate) { this.completionRate = completionRate; }
    public Double getGrowthRate() { return growthRate; }
    public void setGrowthRate(Double growthRate) { this.growthRate = growthRate; }
    public Long getPendingTasks() { return pendingTasks; }
    public void setPendingTasks(Long pendingTasks) { this.pendingTasks = pendingTasks; }
    public Long getRecentLogins() { return recentLogins; }
    public void setRecentLogins(Long recentLogins) { this.recentLogins = recentLogins; }
}
