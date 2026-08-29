package com.aicourse.service.impl;

import com.aicourse.entity.Resource;
import com.aicourse.entity.VideoLearningRecord;
import com.aicourse.mapper.VideoLearningRecordMapper;
import com.aicourse.service.ResourceService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * VideoLearningRecordServiceImpl 单元测试（B 主战场 P1，缺口 804）。
 * extends ServiceImpl<VideoLearningRecordMapper, VideoLearningRecord>：手动构造 + 注入 baseMapper(mock)；
 * getOne 走双参 selectOne(Wrapper, boolean) 重载，故 selectOne 桩须 anyBoolean() 锚定；
 * VideoLearningRecord lambda wrapper 需注册 TableInfo；resourceService 为 mock（resolveCourseId 直接调 getById）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoLearningRecordServiceImplTest {

    @Mock private ResourceService resourceService;
    @Mock private VideoLearningRecordMapper videoLearningRecordMapper;

    private VideoLearningRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), VideoLearningRecord.class);
        service = new VideoLearningRecordServiceImpl(resourceService);
        ReflectionTestUtils.setField(service, "baseMapper", videoLearningRecordMapper);

        when(videoLearningRecordMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        when(videoLearningRecordMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(videoLearningRecordMapper.insert(any(VideoLearningRecord.class))).thenReturn(1);
        when(videoLearningRecordMapper.updateById(any(VideoLearningRecord.class))).thenReturn(1);
        when(resourceService.getById(anyLong())).thenReturn(null);
    }

    private VideoLearningRecord existing(int lastPos, int watchDur, int completeCount) {
        VideoLearningRecord r = new VideoLearningRecord();
        r.setId(1L); r.setUserId(11L); r.setResourceId(22L);
        r.setLastPosition(lastPos); r.setWatchDuration(watchDur);
        r.setCompleteCount(completeCount); r.setTotalDuration(100);
        r.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        r.setUpdatedAt(LocalDateTime.now().minusMinutes(5));
        r.setLastWatchedAt(LocalDateTime.now().minusMinutes(5));
        return r;
    }

    // ===================== updateProgress =====================

    @Test
    @DisplayName("updateProgress 首次观看创建记录并解析课程")
    void progress_firstWatch() {
        Resource res = new Resource(); res.setCourseId(5L);
        when(resourceService.getById(22L)).thenReturn(res);
        VideoLearningRecord r = service.updateProgress(11L, 22L, 10, 100, 1.0);
        assertEquals(5L, r.getCourseId());
        assertEquals(10, r.getLastPosition());
        assertEquals(0, r.getWatchDuration());
        verify(videoLearningRecordMapper).insert(any(VideoLearningRecord.class));
    }

    @Test
    @DisplayName("updateProgress 已有记录按真实时长累加有效观看")
    void progress_existingIncrement() {
        when(videoLearningRecordMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(existing(0, 0, 0));
        VideoLearningRecord r = service.updateProgress(11L, 22L, 10, 100, 1.0);
        assertEquals(10, r.getWatchDuration()); // 位置前进 10 未超真实经过 -> 全部计入
        assertEquals(BigDecimal.valueOf(10).setScale(2), r.getProgress());
        verify(videoLearningRecordMapper).updateById(any(VideoLearningRecord.class));
    }

    @Test
    @DisplayName("updateProgress 防拖拽：快进不计入观看时长")
    void progress_seekCheat() {
        VideoLearningRecord rec = existing(0, 0, 0);
        rec.setUpdatedAt(LocalDateTime.now().minusSeconds(2)); // 真实仅过 2 秒
        when(videoLearningRecordMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(rec);
        VideoLearningRecord r = service.updateProgress(11L, 22L, 95, 100, 1.0);
        // 位置跳到 95，但有效增量被限制为 min(95, 2+3)=5
        assertEquals(5, r.getWatchDuration());
        assertEquals(0, r.getCompleteCount()); // 未达 90% 有效观看
    }

    @Test
    @DisplayName("updateProgress 跨过 90% 阈值计一次完播")
    void progress_completeCross() {
        VideoLearningRecord rec = existing(85, 85, 0);
        when(videoLearningRecordMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(rec);
        VideoLearningRecord r = service.updateProgress(11L, 22L, 95, 100, 1.0);
        assertEquals(1, r.getCompleteCount());
    }

    @Test
    @DisplayName("updateProgress 位置为空则按 0 处理")
    void progress_nullPosition() {
        VideoLearningRecord r = service.updateProgress(11L, 22L, null, 100, 1.0);
        assertEquals(0, r.getLastPosition());
        assertEquals(0, r.getWatchDuration());
    }

    @Test
    @DisplayName("updateProgress 资源缺失则课程为空")
    void progress_resourceMissing() {
        when(resourceService.getById(22L)).thenReturn(null);
        VideoLearningRecord r = service.updateProgress(11L, 22L, 10, 100, 1.0);
        assertNull(r.getCourseId());
    }

    // ===================== getProgress =====================

    @Test
    @DisplayName("getProgress 无记录返回默认")
    void getProgress_noRecord() {
        Map<String, Object> r = service.getProgress(11L, 22L);
        assertEquals(false, r.get("hasRecord"));
        assertEquals(0, r.get("lastPosition"));
        assertEquals(BigDecimal.ZERO, r.get("progressPercent"));
    }

    @Test
    @DisplayName("getProgress 有记录返回详情与完播态")
    void getProgress_withRecord() {
        VideoLearningRecord rec = existing(50, 50, 2);
        when(videoLearningRecordMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(rec);
        Map<String, Object> r = service.getProgress(11L, 22L);
        assertEquals(true, r.get("hasRecord"));
        assertEquals(50, r.get("lastPosition"));
        assertEquals(true, r.get("isCompleted"));
    }

    // ===================== getWatchHistory =====================

    @Test
    @DisplayName("getWatchHistory 无记录返回空")
    void history_empty() {
        when(videoLearningRecordMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertTrue(service.getWatchHistory(11L, 7).isEmpty());
    }

    @Test
    @DisplayName("getWatchHistory 按日期聚合")
    void history_aggregate() {
        VideoLearningRecord a = existing(10, 10, 0);
        a.setLastWatchedAt(LocalDateTime.now().minusDays(1));
        VideoLearningRecord b = existing(20, 20, 1);
        b.setLastWatchedAt(LocalDateTime.now().minusDays(1));
        when(videoLearningRecordMapper.selectList(any())).thenReturn(List.of(a, b));
        List<Map<String, Object>> r = service.getWatchHistory(11L, 7);
        assertEquals(1, r.size()); // 同一天聚合为一条
        @SuppressWarnings("unchecked")
        Map<String, Object> day = r.get(0);
        assertEquals(2, day.get("videoCount"));
        assertEquals(30, day.get("totalWatchDuration"));
        assertEquals(1L, day.get("completedCount"));
    }

    // ===================== getCompletionStats =====================

    @Test
    @DisplayName("getCompletionStats 无记录统计为 0")
    void stats_empty() {
        when(videoLearningRecordMapper.selectList(any())).thenReturn(new ArrayList<>());
        Map<String, Object> r = service.getCompletionStats(11L, null);
        assertEquals(0, r.get("totalVideos"));
        assertEquals(0L, r.get("completedVideos"));
        assertEquals(BigDecimal.ZERO.setScale(2), r.get("completionRate"));
    }

    @Test
    @DisplayName("getCompletionStats 含记录统计完播率与时长")
    void stats_withRecords() {
        VideoLearningRecord a = existing(10, 10, 1); // 已完播
        VideoLearningRecord b = existing(20, 20, 0); // 未完播
        when(videoLearningRecordMapper.selectList(any())).thenReturn(List.of(a, b));
        Map<String, Object> r = service.getCompletionStats(11L, null);
        assertEquals(2, r.get("totalVideos"));
        assertEquals(1L, r.get("completedVideos"));
        assertEquals(30, r.get("totalWatchDuration"));
        assertTrue(((BigDecimal) r.get("completionRate")).compareTo(BigDecimal.ZERO) > 0);
    }
}
