package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.NotBlank;

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

    @NotBlank(message = "用户名不能为空")
    private String username;

    @JsonIgnore
    @NotBlank(message = "密码不能为空")
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

    /* ---- 组织归属（V12 / B-12-P-08 WP1）----
       过渡期与 class_id 并存：V12 只加可空列，V16 才收紧并清理旧列。 */

    /** 学校ID（V12，过渡期可空） */
    private Long schoolId;

    /** 学院ID（V12，过渡期可空） */
    private Long collegeId;

    /** 专业ID（V12，过渡期可空） */
    private Long majorId;

    /** 行政班ID（V12 目标列，过渡期保留 class_id） */
    private Long adminClassId;

    /** 管理员作用域 SCHOOL/COLLEGE，仅 ADMIN 使用（V12） */
    private String adminScope;

    private String department;

    private String title;

    private String bio;

    private String teacherId;

    private String notificationSettings;

    @JsonIgnore
    private Integer failedLoginAttempts;

    @JsonIgnore
    private LocalDateTime lockedUntil;

    /* ---- 封禁留痕（V11，角色矩阵 ③ 违规处理）----
       封禁复用既有 is_active=0（不另造第二套封禁语义）；这三列只作留痕，
       并以 banned_at IS NOT NULL 区分"经内容审核封禁"与"其它原因停用"。 */

    /** 封禁原因（经内容审核封禁时写入） */
    @TableField("ban_reason")
    private String banReason;

    /** 封禁时间 */
    @TableField("banned_at")
    private LocalDateTime bannedAt;

    /** 执行封禁的管理员ID */
    @TableField("banned_by")
    private Long bannedBy;

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