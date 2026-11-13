package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 作业任务实体类
 * 对应数据表：tasks
 */
@TableName("tasks")
@Data
public class Task {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 作业标题
     */
    private String title;

    /**
     * 作业描述
     */
    private String description;

    /**
     * 作业类型
     */
    private String taskType;

    /**
     * 所属课程ID
     */
    private Long courseId;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 关联章节ID（章节任务点绑定，PRD 4.3）
     */
    @TableField("chapter_id")
    private Long chapterId;

    /**
     * 截止时间
     */
    private LocalDateTime dueDate;

    /**
     * 满分分值
     */
    private BigDecimal maxScore;

    /**
     * 逾期每天扣分比例（百分比，如 10 表示 10%/天；null 表示使用默认 10%）
     * 替代原先硬编码的 10%/天，支持按课程/教师配置（对标超星可配逾期策略）
     */
    private BigDecimal latePenaltyPerDay;

    /**
     * 最大提交次数（0 或 null 表示不限；>0 时超过即禁止再提交，对标超星提交次数上限）
     */
    private Integer maxSubmissions;

    /**
     * 状态
     */
    private String status;

    /**
     * 可见范围（V15 / WP6）：COURSE-课程全部教学班（默认），CLASSES-仅目标教学班
     */
    private String visibility;

    /**
     * 目标教学班ID（非本表字段）：表单提交/详情回显用，落库到 task_teaching_classes
     */
    @TableField(exist = false)
    private java.util.List<Long> teachingClassIds;

    /**
     * 是否发布
     */
    @TableField("is_published")
    private Boolean isPublished;

    /**
     * 提交格式
     */
    private String submissionFormat;

    /**
     * 作业要求
     */
    private String requirements;

    /**
     * 附件地址
     */
    private String attachmentUrl;

    /**
     * 开始时间
     */
    private LocalDateTime startDate;

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