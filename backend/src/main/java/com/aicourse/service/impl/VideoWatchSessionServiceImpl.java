package com.aicourse.service.impl;

import com.aicourse.entity.VideoWatchSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.VideoWatchSessionMapper;
import com.aicourse.service.VideoLearningRecordService;
import com.aicourse.service.VideoWatchSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VideoWatchSessionServiceImpl extends ServiceImpl<VideoWatchSessionMapper, VideoWatchSession> implements VideoWatchSessionService {

    private final VideoWatchSessionMapper videoWatchSessionMapper;
    private final VideoLearningRecordService videoLearningRecordService;

    public VideoWatchSessionServiceImpl(VideoWatchSessionMapper videoWatchSessionMapper,
                                         VideoLearningRecordService videoLearningRecordService) {
        this.videoWatchSessionMapper = videoWatchSessionMapper;
        this.videoLearningRecordService = videoLearningRecordService;
    }

    @Override
    public VideoWatchSession startSession(Long userId, Long resourceId) {
        VideoWatchSession session = new VideoWatchSession();
        session.setUserId(userId);
        session.setResourceId(resourceId);
        session.setStartPosition(0);
        session.setEndPosition(0);
        session.setDuration(0);
        session.setStartedAt(LocalDateTime.now());
        this.save(session);
        return session;
    }

    @Override
    public VideoWatchSession updateProgress(Long sessionId, int currentTime, int totalDuration) {
        VideoWatchSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("观看会话不存在");
        }
        if (session.getEndedAt() != null) {
            throw new BusinessException("该会话已结束，无法更新进度");
        }
        session.setEndPosition(currentTime);
        session.setDuration(currentTime - (session.getStartPosition() != null ? session.getStartPosition() : 0));
        this.updateById(session);
        return session;
    }

    @Override
    public VideoWatchSession endSession(Long sessionId) {
        VideoWatchSession session = this.getById(sessionId);
        if (session == null) {
            throw new BusinessException("观看会话不存在");
        }
        if (session.getEndedAt() != null) {
            throw new BusinessException("该会话已结束");
        }
        session.setEndedAt(LocalDateTime.now());
        this.updateById(session);
        return session;
    }

    @Override
    public List<VideoWatchSession> getActiveSessions(Long userId) {
        return this.list(new LambdaQueryWrapper<VideoWatchSession>()
                .eq(VideoWatchSession::getUserId, userId)
                .isNull(VideoWatchSession::getEndedAt)
                .orderByDesc(VideoWatchSession::getStartedAt));
    }

    @Override
    public List<Map<String, Object>> getWatchHistory(Long userId, Long courseId) {
        List<Map<String, Object>> history = new ArrayList<>();
        LambdaQueryWrapper<VideoWatchSession> wrapper = new LambdaQueryWrapper<VideoWatchSession>()
                .eq(VideoWatchSession::getUserId, userId)
                .orderByDesc(VideoWatchSession::getStartedAt);

        List<VideoWatchSession> sessions = this.list(wrapper);
        for (VideoWatchSession session : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("sessionId", session.getId());
            item.put("resourceId", session.getResourceId());
            item.put("userId", session.getUserId());
            item.put("startPosition", session.getStartPosition());
            item.put("endPosition", session.getEndPosition());
            item.put("duration", session.getDuration());
            item.put("startedAt", session.getStartedAt());
            item.put("endedAt", session.getEndedAt());
            item.put("isActive", session.getEndedAt() == null);
            history.add(item);
        }
        return history;
    }
}