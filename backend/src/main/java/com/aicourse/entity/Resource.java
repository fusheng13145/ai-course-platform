package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 课程资源实体
 * 存储课程配套文件、资料、视频等资源信息，包含文件地址、归属及可见权限
 */
@TableName("resources")
@Data
public class Resource {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 资源访问链接
     */
    private String url;

    /**
     * 所属课程ID
     */
    private Long courseId;

    /**
     * 所属章节ID
     */
    private Long chapterId;

    /**
     * 上传人用户ID
     */
    private Long uploaderId;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 资源描述
     */
    private String description;

    /**
     * 是否全员可见 0-否 1-是
     */
    @TableField("visible_to_all")
    private Boolean visibleToAll;

    /* ---- 内容审核（V11，角色矩阵 ③）---- */

    /** 审核状态取值：正常（常量与 ModerationServiceImpl、V11 迁移的口径一致）。 */
    public static final String MODERATION_NORMAL = "NORMAL";
    /** 审核状态取值：已隐藏（读路径过滤，可恢复）。 */
    public static final String MODERATION_HIDDEN = "HIDDEN";

    /** 审核状态：NORMAL-正常；HIDDEN-已隐藏（读路径过滤，可恢复） */
    @TableField("moderation_status")
    private String moderationStatus;

    /** 审核原因（管理员填写） */
    @TableField("moderation_reason")
    private String moderationReason;

    /** 最近一次审核时间 */
    @TableField("moderated_at")
    private LocalDateTime moderatedAt;

    /** 最近一次审核管理员ID */
    @TableField("moderated_by")
    private Long moderatedBy;

    // 注意（2026-09-14 修复）：此处曾手写 `public Boolean isVisibleToAll()`，
    // 与 Lombok @Data 为包装类型字段生成的 `getVisibleToAll()` 形成**重载歧义**
    // （JavaBeans 规范中 `is` 前缀仅适用于基本类型 boolean）。MyBatis 反射解析属性
    // 时会抛出 ReflectionException: Illegal overloaded getter method with ambiguous type，
    // 导致所有 resources 写操作（上传/可见性切换/删除）恒返回 500。
    // 统一由 Lombok 生成 `getVisibleToAll()`，请勿再手写 `isXxx()` 形式的包装类型 getter。
}