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
 * 视频学习进度记录实体类
 * 对应数据表：video_learning_records
 */
@TableName("video_learning_records")
@Data
public class VideoLearningRecord {

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
     * 视频资源ID
     */
    private Long resourceId;

    /**
     * 课程ID（由 resourceId 关联 resources 解析，用于按课程统计视频完播，修复 P0-6）
     */
    private Long courseId;

    /**
     * 累计观看时长（单位：秒）
     */
    private Integer watchDuration;

    /**
     * 视频总时长（单位：秒）
     */
    private Integer totalDuration;

    /**
     * 学习进度
     */
    private BigDecimal progress;

    /**
     * 上次播放位置（单位：秒）
     */
    private Integer lastPosition;

    /**
     * 完整观看次数
     */
    private Integer completeCount;

    /**
     * 最近观看时间
     */
    private LocalDateTime lastWatchedAt;

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