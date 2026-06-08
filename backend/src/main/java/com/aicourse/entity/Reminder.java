package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 提醒通知实体
 * 记录课程、任务相关的待办提醒，包含提醒类型、触发时间、发送状态等信息
 */
@TableName("reminders")
public class Reminder {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 提醒类型
     */
    private String reminderType;

    /**
     * 提醒状态（待发送、已发送、已取消）
     */
    private String status;

    /**
     * 接收提醒用户ID
     */
    private Long userId;

    /**
     * 关联任务ID
     */
    private Long taskId;

    /**
     * 关联课程ID
     */
    private Long courseId;

    /**
     * 提醒标题
     */
    private String title;

    /**
     * 提醒内容
     */
    private String message;

    /**
     * 截止时间
     */
    private LocalDateTime dueDate;

    /**
     * 提前触发天数
     */
    private Integer triggerDay;

    /**
     * 计划发送时间
     */
    private LocalDateTime scheduledAt;

    /**
     * 实际发送时间
     */
    private LocalDateTime sentAt;

    /**
     * 记录创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReminderType() {
        return reminderType;
    }

    public void setReminderType(String reminderType) {
        this.reminderType = reminderType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public Integer getTriggerDay() {
        return triggerDay;
    }

    public void setTriggerDay(Integer triggerDay) {
        this.triggerDay = triggerDay;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}