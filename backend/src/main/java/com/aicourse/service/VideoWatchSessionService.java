package com.aicourse.service;

import com.aicourse.entity.VideoWatchSession;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;


/**
 * 视频观看会话接口：相关业务逻辑入口，提供增删改查与编排能力。
 */
public interface VideoWatchSessionService extends IService<VideoWatchSession> {

    /**
     * 开始一个新的观看会话
     * @param userId 用户ID
     * @param resourceId 视频资源ID
     * @return 创建的会话
     */
    VideoWatchSession startSession(Long userId, Long resourceId);

    /**
     * 更新观看进度
     * @param sessionId 会话ID
     * @param currentTime 当前播放位置（秒）
     * @param totalDuration 视频总时长（秒）
     * @return 更新后的会话
     */
    VideoWatchSession updateProgress(Long sessionId, int currentTime, int totalDuration);

    /**
     * 结束观看会话
     * @param sessionId 会话ID
     * @return 更新后的会话
     */
    VideoWatchSession endSession(Long sessionId);

    /**
     * 获取用户的活跃会话列表
     * @param userId 用户ID
     * @return 活跃会话列表
     */
    List<VideoWatchSession> getActiveSessions(Long userId);

    /**
     * 获取用户的观看历史
     * @param userId 用户ID
     * @param courseId 课程ID（可选）
     * @return 观看历史列表
     */
    List<Map<String, Object>> getWatchHistory(Long userId, Long courseId);
}