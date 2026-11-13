package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 课程讨论话题实体
 * 对应数据表：discussion_topics
 * 对标超星：课程讨论区 / 答疑区，进阶支持置顶、关闭、章节关联
 */
@Data
@TableName("discussion_topics")
public class DiscussionTopic {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID（可空表示全站公共讨论） */
    private Long courseId;

    /** 所属章节ID（可空表示整课讨论） */
    private Long chapterId;

    /** 话题标题 */
    private String title;

    /** 话题内容 */
    private String content;

    /**
     * 类型：NORMAL / QA
     */
    private String type;

    /**
     * 状态：OPEN / CLOSED
     */
    private String status;

    /** 发起者ID */
    private Long creatorId;

    /** 发起者角色：TEACHER / STUDENT / ADMIN */
    private String creatorRole;

    /** 是否置顶 */
    @TableField("is_pinned")
    private Boolean isPinned;

    /** 是否精华（对标超星精华帖，教师/管理员可标记） */
    @TableField("is_essence")
    private Boolean isEssence;

    /** 回复数 */
    private Integer replyCount;

    /** 最后回复时间 */
    private LocalDateTime lastReplyAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 话题归属教学班（WP7，V16，可空）。
     * 存量话题保持 NULL（课程级）；新话题可指定教学班做班级内讨论。
     */
    private Long teachingClassId;

    // ===================== 类型常量 =====================
    public static final String TYPE_NORMAL = "NORMAL";
    public static final String TYPE_QA = "QA";

    // ===================== 状态常量 =====================
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";
}
