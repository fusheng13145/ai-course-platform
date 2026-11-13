package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 公告实体（B-12／P-08 WP8，V16）。
 * <p>
 * 三种投放目标互斥（发布时必须且只能指定一个）：课程（教师）、教学班（教师/主讲）、
 * 行政班（班主任）。发布后按受众<b>扇出</b>到 {@code notifications}（category=ANNOUNCEMENT），
 * 用户收件箱继续走既有通知通道；本表是公告的事实源，历史站内信不迁移、不伪造成公告
 * （拆包方案 WP8「不做」条款）。
 */
@TableName("announcements")
@Data
public class Announcement {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程公告目标（与 teachingClassId / adminClassId 互斥） */
    private Long courseId;

    /** 教学班公告目标（与 courseId / adminClassId 互斥） */
    private Long teachingClassId;

    /** 行政班公告目标（与 courseId / teachingClassId 互斥，班主任发布） */
    private Long adminClassId;

    /** 公告标题 */
    private String title;

    /** 公告正文 */
    private String content;

    /** 发布人ID */
    private Long publisherId;

    /** 发布人角色（TEACHER / ADVISOR / ADMIN） */
    private String publisherRole;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ===================== 常量 =====================
    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_ADVISOR = "ADVISOR";
    public static final String ROLE_ADMIN = "ADMIN";
}
