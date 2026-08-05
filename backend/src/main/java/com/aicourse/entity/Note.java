package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户笔记与收藏实体
 * 学生/教师在课程、章节、知识点处记录的笔记，可标记为收藏。
 * 与现有课程体系（courses / course_chapters / knowledge_points）通过外键式字段关联，
 * 不做物理外键约束，降低与自愈合建表逻辑的耦合。
 */
@TableName("notes")
@Data
public class Note {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（笔记所有者）
     */
    private Long userId;

    /**
     * 关联课程ID（可空）
     */
    private Long courseId;

    /**
     * 关联章节ID（可空）
     */
    private Long chapterId;

    /**
     * 关联知识点ID（可空）
     */
    private Long knowledgePointId;

    /**
     * 笔记标题（可空）
     */
    private String title;

    /**
     * 笔记正文（Markdown 文本）
     */
    private String content;

    /**
     * 是否收藏：0-否，1-是
     */
    @TableField("is_favorite")
    private Integer isFavorite;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
