package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 登录日志实体
 * 记录用户登录行为、客户端信息、登录状态及异常原因
 */
@TableName("login_logs")
@Data
public class LoginLog {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 登录账号
     */
    private String username;

    /**
     * 登录状态（成功/失败）
     */
    private String loginStatus;

    /**
     * 登录IP地址
     */
    private String ipAddress;

    /**
     * IP归属地
     */
    private String location;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 浏览器名称
     */
    private String browser;

    /**
     * 操作系统
     */
    private String operatingSystem;

    /**
     * 客户端UA标识
     */
    private String userAgent;

    /**
     * 登录失败原因
     */
    private String failureReason;

    /**
     * 日志创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}