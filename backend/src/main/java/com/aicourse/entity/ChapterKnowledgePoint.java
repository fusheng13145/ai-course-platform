package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 章节-知识点关联实体
 * 对应数据表：chapter_knowledge_points
 */
@TableName("chapter_knowledge_points")
@Data
public class ChapterKnowledgePoint {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 章节ID
     */
    private Long chapterId;

    /**
     * 知识点ID
     */
    private Long knowledgePointId;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}