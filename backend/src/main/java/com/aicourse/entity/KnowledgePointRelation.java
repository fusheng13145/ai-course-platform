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
 * 知识点关联关系实体
 * 用于记录知识点之间的关联、依赖、前置学习等关系，支持关联强度与类型区分
 */
@TableName("knowledge_point_relations")
@Data
public class KnowledgePointRelation {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 源知识点ID
     */
    private Long sourceId;

    /**
     * 目标知识点ID
     */
    private Long targetId;

    /**
     * 关联类型（依赖/前置/关联等）
     */
    private String relationType;

    /**
     * 关联强度（数值越大关联度越高）
     */
    private BigDecimal strength;

    /**
     * 关联关系描述
     */
    private String description;

    /**
     * 记录创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}