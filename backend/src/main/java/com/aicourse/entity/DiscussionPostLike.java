package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 讨论回复点赞记录实体（修复 P1-5：点赞去重，每人每帖仅一条）
 * 对应数据表：discussion_post_likes
 */
@TableName("discussion_post_likes")
@Data
public class DiscussionPostLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 回复ID，关联 discussion_posts(id) */
    private Long postId;

    /** 点赞用户ID，关联 users(id) */
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}
