package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 操作日志实体 — 记录关键数据操作审计追踪
 */
@TableName("operation_logs")
@Data
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String target;
    private String targetId;
    private String ip;
    private String userAgent;
    private String method;
    private String params;
    private Long durationMs;
    private String status;
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}
