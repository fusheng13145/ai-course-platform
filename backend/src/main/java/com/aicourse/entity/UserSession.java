package com.aicourse.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户会话实体类
 * 对应数据表：user_sessions
 */
@TableName("user_sessions")
@Data
public class UserSession {

    /**
     * 会话ID，主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID，关联users表
     */
    private Long userId;

    /**
     * 访问令牌（JWT）
     * 敏感凭证，禁止经任何 API 序列化返回（防止令牌泄漏后被冒用）
     */
    @JsonIgnore
    private String sessionToken;

    /**
     * 刷新令牌（库内为 SHA-256 哈希值）
     * 敏感凭证，禁止经任何 API 序列化返回
     */
    @JsonIgnore
    private String refreshToken;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 浏览器信息
     */
    private String browser;

    /**
     * 操作系统信息
     */
    private String operatingSystem;

    /**
     * 登录IP地址
     */
    private String ipAddress;

    /**
     * 登录地理位置
     */
    private String location;

    /**
     * 完整User-Agent字符串
     */
    private String userAgent;

    /**
     * 会话是否有效：0-失效，1-有效
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 是否为当前会话：0-否，1-是
     */
    @TableField("is_current")
    private Boolean isCurrent;

    /**
     * 登录时间
     */
    private LocalDateTime loginTime;

    /**
     * 最后活动时间
     */
    private LocalDateTime lastActivity;

    /**
     * 会话过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}