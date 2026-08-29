package com.aicourse.service.impl;

import com.aicourse.entity.VideoWatchSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.VideoWatchSessionMapper;
import com.aicourse.service.VideoLearningRecordService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoWatchSessionServiceImplTest {

    @Mock
    private VideoWatchSessionMapper videoWatchSessionMapper; // baseMapper
    @Mock
    private VideoLearningRecordService videoLearningRecordService;

    private VideoWatchSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), VideoWatchSession.class);
        service = new VideoWatchSessionServiceImpl(videoWatchSessionMapper, videoLearningRecordService);
        ReflectionTestUtils.setField(service, "baseMapper", videoWatchSessionMapper);
    }

    private VideoWatchSession session(Long id, Long userId, Integer start, Integer end,
                                      Integer dur, LocalDateTime startedAt, LocalDateTime endedAt) {
        VideoWatchSession s = new VideoWatchSession();
        s.setId(id);
        s.setUserId(userId);
        s.setResourceId(99L);
        s.setStartPosition(start);
        s.setEndPosition(end);
        s.setDuration(dur);
        s.setStartedAt(startedAt);
        s.setEndedAt(endedAt);
        return s;
    }

    // ===== startSession =====
    @Test
    @DisplayName("startSession 正常")
    void start_ok() {
        when(videoWatchSessionMapper.insert(any(VideoWatchSession.class))).thenReturn(1);
        VideoWatchSession r = service.startSession(1L, 99L);
        assertEquals(1L, r.getUserId());
        assertEquals(99L, r.getResourceId());
        assertEquals(0, r.getStartPosition());
        assertEquals(0, r.getEndPosition());
        assertEquals(0, r.getDuration());
        assertNotNull(r.getStartedAt());
        verify(videoWatchSessionMapper).insert(any(VideoWatchSession.class));
    }

    // ===== updateProgress =====
    @Test
    @DisplayName("updateProgress 正常计算时长（防拖拽截断）")
    void update_ok() {
        // startedAt 设为 5 分钟前，使真实经过时长远大于 rawDuration，确保 duration=rawDuration 确定
        VideoWatchSession s = session(1L, 1L, 0, 0, 0, LocalDateTime.now().minusMinutes(5), null);
        when(videoWatchSessionMapper.selectById(1L)).thenReturn(s);
        when(videoWatchSessionMapper.updateById(any(VideoWatchSession.class))).thenReturn(1);
        VideoWatchSession r = service.updateProgress(1L, 10, 100);
        assertEquals(10, r.getEndPosition());
        assertEquals(10, r.getDuration());
        verify(videoWatchSessionMapper).updateById(any(VideoWatchSession.class));
    }

    @Test
    @DisplayName("updateProgress 会话不存在")
    void update_notFound() {
        when(videoWatchSessionMapper.selectById(2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.updateProgress(2L, 10, 100));
    }

    @Test
    @DisplayName("updateProgress 已结束")
    void update_ended() {
        VideoWatchSession s = session(1L, 1L, 0, 10, 5, LocalDateTime.now(), LocalDateTime.now());
        when(videoWatchSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.updateProgress(1L, 10, 100));
    }

    // ===== endSession =====
    @Test
    @DisplayName("endSession 正常")
    void end_ok() {
        VideoWatchSession s = session(1L, 1L, 0, 10, 5, LocalDateTime.now(), null);
        when(videoWatchSessionMapper.selectById(1L)).thenReturn(s);
        when(videoWatchSessionMapper.updateById(any(VideoWatchSession.class))).thenReturn(1);
        VideoWatchSession r = service.endSession(1L);
        assertNotNull(r.getEndedAt());
        verify(videoWatchSessionMapper).updateById(any(VideoWatchSession.class));
    }

    @Test
    @DisplayName("endSession 不存在")
    void end_notFound() {
        when(videoWatchSessionMapper.selectById(2L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.endSession(2L));
    }

    @Test
    @DisplayName("endSession 已结束")
    void end_already() {
        VideoWatchSession s = session(1L, 1L, 0, 10, 5, LocalDateTime.now(), LocalDateTime.now());
        when(videoWatchSessionMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.endSession(1L));
    }

    // ===== getActiveSessions =====
    @Test
    @DisplayName("getActiveSessions 返回进行中会话")
    void active_ok() {
        when(videoWatchSessionMapper.selectList(any())).thenReturn(
                List.of(session(1L, 1L, 0, 5, 5, LocalDateTime.now(), null)));
        List<VideoWatchSession> r = service.getActiveSessions(1L);
        assertEquals(1, r.size());
    }

    // ===== getWatchHistory =====
    @Test
    @DisplayName("getWatchHistory 聚合")
    void history_ok() {
        when(videoWatchSessionMapper.selectList(any())).thenReturn(List.of(
                session(1L, 1L, 0, 5, 5, LocalDateTime.now(), null),
                session(2L, 1L, 0, 8, 8, LocalDateTime.now(), LocalDateTime.now())));
        List<Map<String, Object>> r = service.getWatchHistory(1L, 99L);
        assertEquals(2, r.size());
        assertEquals(99L, r.get(0).get("resourceId"));
        assertEquals(true, r.get(0).get("isActive"));   // endedAt=null -> 进行中
        assertEquals(false, r.get(1).get("isActive"));  // endedAt 已设置 -> 已结束
    }
}
