package com.aicourse.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 课程章节实体类
 * 对应数据表：course_chapters
 */
@TableName("course_chapters")
@Data
public class CourseChapter {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 父章节ID（0表示一级章节）
     */
    private Long parentId;

    /**
     * 章节标题
     */
    private String title;

    /**
     * 章节内容
     */
    private String content;

    /**
     * 排序序号
     */
    private Integer orderIndex;

    /**
     * 时长（单位：分钟）
     */
    private Integer duration;

    /**
     * 是否发布
     */
    @TableField("is_published")
    private Boolean isPublished;

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

    public void setIsPublished(Boolean published) {
        isPublished = published;
    }

}