package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 讨论回复实体（含楼中楼 parentId）
 * 对应数据表：discussion_posts
 */
@Data
@TableName("discussion_posts")
public class DiscussionPost {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属话题ID */
    private Long topicId;

    /** 父回复ID（楼中楼，顶层回复为 NULL） */
    private Long parentId;

    /** 作者ID */
    private Long authorId;

    /** 作者角色：TEACHER / STUDENT / ADMIN */
    private String authorRole;

    /** 回复内容 */
    private String content;

    /** 点赞数 */
    private Integer likeCount;

    /** 楼层号（按话题内回复顺序递增，顶层与楼中楼统一编号） */
    private Integer floor;

    /** 被@提及的用户ID列表（逗号分隔的字符串存储） */
    private String mentions;

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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 前端传入的@提及用户ID（不入库，仅用于创建时发通知/落库到 mentions 列） */
    @TableField(exist = false)
    private java.util.List<Long> mentionUserIds;
}
