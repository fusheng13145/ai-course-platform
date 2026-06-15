package com.aicourse.service;

import com.aicourse.entity.VideoLearningRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 视频学习记录服务接口
 * 提供视频观看进度更新、进度查询、观看历史、完播统计等功能
 */
public interface VideoLearningRecordService extends IService<VideoLearningRecord> {

    /**
     * 更新视频学习进度（记录播放位置、完播检测、时长统计）
     * @param userId 用户ID
     * @param resourceId 视频资源ID
     * @param position 当前播放位置（秒）
     * @param totalDuration 视频总时长（秒）
     * @param playbackSpeed 播放速度（如1.0、1.5、2.0）
     * @return 更新后的学习记录
     */
    VideoLearningRecord updateProgress(Long userId, Long resourceId, Integer position,
                                        Integer totalDuration, Double playbackSpeed);

    /**
     * 获取用户对某视频的学习进度详情
     * @param userId 用户ID
     * @param resourceId 视频资源ID
     * @return 进度信息Map（含lastPosition、completeCount、totalDuration、progressPercent等）
     */
    Map<String, Object> getProgress(Long userId, Long resourceId);

    /**
     * 获取用户的观看历史记录（按日期聚合）
     * @param userId 用户ID
     * @param days 最近N天的数据
     * @return 按日期分组的观看记录列表
     */
    List<Map<String, Object>> getWatchHistory(Long userId, int days);

    /**
     * 获取用户视频学习完播统计
     * @param userId 用户ID
     * @param courseId 课程ID（可选，用于筛选特定课程的视频）
     * @return 完播统计数据（总视频数、已完播数、完播率等）
     */
    Map<String, Object> getCompletionStats(Long userId, Long courseId);
}
