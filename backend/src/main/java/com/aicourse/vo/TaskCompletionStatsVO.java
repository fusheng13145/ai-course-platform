package com.aicourse.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务完成率统计出参。
 * 字段名与历史 Map 键一致（taskId / taskTitle / totalSubmissions / pendingReview /
 * gradedCount / returnedCount / lateCount / averageScore / completionRate / dueDate / status），
 * 保证前端零改动。
 * <p>
 * 全部键在原实现中均无条件 put，故采用默认 INCLUDE.ALWAYS 策略（不加 NON_NULL）。
 */
@Data
public class TaskCompletionStatsVO {

    private Long taskId;
    private String taskTitle;
    private long totalSubmissions;
    /** 待批阅数量（SUBMITTED / RESUBMITTED 状态提交数） */
    private long pendingReview;
    private long gradedCount;
    private long returnedCount;
    private long lateCount;
    private double averageScore;
    /** 完成率百分比，分母为课程选课人数 */
    private double completionRate;
    private LocalDateTime dueDate;
    private String status;

    public TaskCompletionStatsVO() {
    }

    public TaskCompletionStatsVO(Long taskId, String taskTitle, long totalSubmissions,
                                 long pendingReview, long gradedCount, long returnedCount,
                                 long lateCount, double averageScore, double completionRate,
                                 LocalDateTime dueDate, String status) {
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.totalSubmissions = totalSubmissions;
        this.pendingReview = pendingReview;
        this.gradedCount = gradedCount;
        this.returnedCount = returnedCount;
        this.lateCount = lateCount;
        this.averageScore = averageScore;
        this.completionRate = completionRate;
        this.dueDate = dueDate;
        this.status = status;
    }
}
