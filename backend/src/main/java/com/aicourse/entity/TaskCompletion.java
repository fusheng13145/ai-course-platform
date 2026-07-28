package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务点完成记录实体
 * 对应数据表：task_completions
 * <p>
 * 统一记录学生各类任务点（视频/文档/阅读/测验/作业）的完成状态与过程数据。
 * 作业类（ASSIGNMENT）完成态由 submissions 派生，其余类型以本表为准，
 * 以便学习中心统一展示「X/Y 完成」与闯关解锁判定。
 */
@Data
@TableName("task_completions")
public class TaskCompletion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生ID */
    private Long studentId;

    /** 关联任务ID（tasks.id） */
    private Long taskId;

    /** 关联课程ID */
    private Long courseId;

    /** 关联章节ID */
    private Long chapterId;

    /** 任务类型（与 tasks.task_type 对齐：VIDEO/DOC/READING/QUIZ/ASSIGNMENT） */
    private String taskType;

    /** 完成方式：VIDEO / DOC / READING / QUIZ / ASSIGNMENT */
    private String completionType;

    /** 视频已观看时长（秒，累计） */
    private Integer watchedDuration;

    /** 视频最近一次播放位置（秒） */
    private Integer lastPosition;

    /** 视频总时长（秒） */
    private Integer totalDuration;

    /** 测验得分（百分制或原始分，依业务而定） */
    private BigDecimal score;

    /** 是否已完成 */
    private Boolean completed;

    /** 完成时间 */
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
