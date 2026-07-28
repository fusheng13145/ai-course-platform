package com.aicourse.entity;

import lombok.Data;

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
@Data
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

}