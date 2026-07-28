package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 知识点实体
 * 存储课程下的知识点信息，支持层级父子结构、难度、重要度与排序配置
 */
@TableName("knowledge_points")
@Data
public class KnowledgePoint {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识点名称
     */
    private String name;

    /**
     * 知识点描述
     */
    private String description;

    /**
     * 所属课程ID
     */
    private Long courseId;

    /**
     * 父级知识点ID（顶级节点为0/NULL）
     */
    private Long parentId;

    /**
     * 难度等级
     */
    private String difficultyLevel;

    /**
     * 重要程度（数值越大优先级越高）
     */
    private Integer importance;

    /**
     * 预计学习时长（单位：分钟）
     */
    private Integer estimatedTime;

    /**
     * 知识点类型
     */
    private String pointType;

    /**
     * 同层级排序序号
     */
    private Integer orderIndex;

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