package com.aicourse.entity;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 视频观看记录实体类
 * 对应数据表：video_watch_sessions
 */
@TableName("video_watch_sessions")
@Data
public class VideoWatchSession {

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
     * 资源ID
     */
    private Long resourceId;

    /**
     * 开始播放位置（单位：秒）
     */
    private Integer startPosition;

    /**
     * 结束播放位置（单位：秒）
     */
    private Integer endPosition;

    /**
     * 本次观看时长（单位：秒）
     */
    private Integer duration;

    /**
     * 开始观看时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束观看时间
     */
    private LocalDateTime endedAt;

}