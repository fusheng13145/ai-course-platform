package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 消息通知实体
 * 存储系统向用户推送的各类通知消息，包含消息内容、状态、跳转地址及关联业务数据
 */
@TableName("notifications")
@Data
public class Notification {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收通知的用户ID
     */
    private Long userId;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 消息分类
     */
    private String category;

    /**
     * 消息类型
     */
    private String type;

    /**
     * 是否已读
     */
    @TableField("is_read")
    private Boolean isRead;

    /**
     * 消息优先级
     */
    private String priority;

    /**
     * 点击跳转地址
     */
    private String actionUrl;

    /**
     * 扩展业务数据（JSON格式存储）
     */
    private String data;

    /**
     * 关联业务ID
     */
    private Long relatedId;

    /**
     * 关联业务类型
     */
    private String relatedType;

    /**
     * 消息创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 消息读取时间
     */
    private LocalDateTime readAt;

}