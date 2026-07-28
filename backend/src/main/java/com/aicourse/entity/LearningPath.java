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
 * 学习路线实体
 * 记录学生针对单门课程制定的个性化学习规划、阶段进度、任务及完成情况
 */
@TableName("learning_paths")
@Data
public class LearningPath {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属学生ID
     */
    private Long studentId;

    /**
     * 关联课程ID
     */
    private Long courseId;

    /**
     * 学习路线名称
     */
    private String pathName;

    /**
     * 路线简介描述
     */
    private String description;

    /**
     * 阶段集合（JSON格式存储）
     */
    private String stages;

    /**
     * 整体规划详情
     */
    private String planDetail;

    /**
     * 每日学习任务（JSON格式存储）
     */
    private String dailyTasks;

    /**
     * 整体学习目标
     */
    private String learningGoal;

    /**
     * 当前所处阶段序号
     */
    private Integer currentStage;

    /**
     * 总阶段数量
     */
    private Integer totalStages;

    /**
     * 整体完成率
     */
    private BigDecimal completionRate;

    /**
     * 路线状态（进行中/已完成/已暂停）
     */
    private String status;

    /**
     * 预计完成时间
     */
    private LocalDateTime estimatedCompletionDate;

    /**
     * 实际完成时间
     */
    private LocalDateTime actualCompletionDate;

    /**
     * 记录创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 记录更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}