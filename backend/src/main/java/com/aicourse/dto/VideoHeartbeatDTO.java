package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 视频观看心跳上报请求DTO
 * 用于学生端上报视频观看进度（累计时长、播放位置等），达阈值自动判定完成
 */
@Schema(description = "视频观看心跳上报请求")
@Data
public class VideoHeartbeatDTO {

    @Schema(description = "视频资源ID", example = "101")
    private Long resourceId;

    @Schema(description = "当前播放位置（秒）", example = "120")
    private Integer position;

    @Schema(description = "累计已观看时长（秒）", example = "300")
    private Integer watchedDuration;

    @Schema(description = "视频总时长（秒）", example = "600")
    private Integer totalDuration;

    @Schema(description = "播放倍速", example = "1.5")
    private Double playbackSpeed;

}
