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
 * 学习记录实体
 * 记录用户在课程、章节、资源上的学习行为、时长与进度数据
 */
@TableName("learning_records")
@Data
public class LearningRecord {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 关联资源ID
     */
    private Long resourceId;

    /**
     * 关联章节ID
     */
    private Long chapterId;

    /**
     * 关联课程ID
     */
    private Long courseId;

    /**
     * 行为类型（如：观看视频、阅读文档、完成练习等）
     */
    private String actionType;

    /**
     * 学习时长（单位：秒/分钟，根据业务定义）
     */
    private Integer duration;

    /**
     * 学习进度（百分比，如0.8代表80%）
     */
    private BigDecimal progress;

    /**
     * 扩展元数据（JSON格式存储，如播放位置、笔记内容等）
     */
    private String metadata;

    /**
     * 记录创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

}