package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 课堂投票/问卷实体
 * 对应数据表：votes
 * 选项以 JSON 数组存储：[{"id":"a","text":"正确"},{"id":"b","text":"错误"}]
 */
@Data
@TableName("votes")
public class Vote {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID */
    private Long courseId;

    /** 所属章节ID（可空） */
    private Long chapterId;

    /** 投票标题 */
    private String title;

    /** 投票描述 */
    private String description;

    /**
     * 投票模式：SINGLE_CHOICE / MULTIPLE_CHOICE / TEXT
     */
    private String voteMode;

    /**
     * 选项 JSON：[{"id":"a","text":"..."}]
     */
    private String options;

    /**
     * 状态：PENDING / ONGOING / CLOSED
     */
    private String status;

    /** 发起教师ID */
    private Long creatorId;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime closedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ===================== 模式常量 =====================
    public static final String MODE_SINGLE = "SINGLE_CHOICE";
    public static final String MODE_MULTIPLE = "MULTIPLE_CHOICE";
    public static final String MODE_TEXT = "TEXT";

    // ===================== 状态常量 =====================
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ONGOING = "ONGOING";
    public static final String STATUS_CLOSED = "CLOSED";
}
