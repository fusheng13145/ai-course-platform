package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户实体
 * 表名：users
 */
@TableName("users")
@Data
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    private String username;

    @JsonIgnore
    private String password;

    private String fullName;

    private String role;

    /** 布尔is前缀，建议显式映射 */
    @TableField("is_active")
    private Boolean isActive;

    /** 布尔is前缀，建议显式映射 */
    @TableField("is_verified")
    private Boolean isVerified;

    private String avatarUrl;

    private String phone;

    private String studentId;

    private String major;

    private String grade;

    private Long classId;

    private String department;

    private String title;

    private String bio;

    private String teacherId;

    private String notificationSettings;

    @JsonIgnore
    private Integer failedLoginAttempts;

    @JsonIgnore
    private LocalDateTime lockedUntil;

    @JsonIgnore
    private String registerIp;

    private LocalDateTime lastLogin;

    /** 自动填充创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 自动填充更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 非数据库字段 */
    @TableField(exist = false)
    private String className;

}