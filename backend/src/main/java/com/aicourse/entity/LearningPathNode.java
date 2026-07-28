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
 * 学习路线节点实体
 * 记录学习路线下每个阶段/任务的学习进度、耗时与完成情况
 */
@TableName("learning_path_nodes")
@Data
public class LearningPathNode {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属学习路线ID
     */
    private Long learningPathId;

    /**
     * 关联知识点ID
     */
    private Long knowledgePointId;

    /**
     * 关联真实任务点ID（修复 P1-4：学习路径节点挂载课程真实任务点，而非仅知识点）
     */
    private Long taskId;

    /**
     * 关联章节ID（任务点所属章节）
     */
    private Long chapterId;

    /**
     * 节点标题
     */
    private String title;

    /**
     * 节点描述/学习要求
     */
    private String description;

    /**
     * 节点类型（学习/练习/测试等）
     */
    private String nodeType;

    /**
     * 节点状态（未开始/进行中/已完成）
     */
    private String status;

    /**
     * 节点排序序号
     */
    private Integer orderIndex;

    /**
     * 预计学习时长（单位：分钟）
     */
    private Integer estimatedTime;

    /**
     * 实际学习时长（单位：分钟）
     */
    private Integer actualTime;

    /**
     * 节点测试/练习得分
     */
    private BigDecimal score;

    /**
     * 节点完成时间
     */
    private LocalDateTime completedAt;

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