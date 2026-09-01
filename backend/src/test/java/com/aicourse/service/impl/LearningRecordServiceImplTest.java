package com.aicourse.service.impl;

import com.aicourse.entity.LearningRecord;
import com.aicourse.mapper.LearningRecordMapper;
import com.aicourse.vo.DailyLearningStatVO;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningRecordServiceImplTest {

    @Mock
    private LearningRecordMapper learningRecordMapper;

    private LearningRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), LearningRecord.class);
        service = new LearningRecordServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", learningRecordMapper);
    }

    private LearningRecord rec(Long userId, Long courseId, Integer duration,
                               BigDecimal progress, LocalDateTime createdAt) {
        LearningRecord r = new LearningRecord();
        r.setId(1L);
        r.setUserId(userId);
        r.setCourseId(courseId);
        r.setDuration(duration);
        r.setProgress(progress);
        r.setCreatedAt(createdAt);
        return r;
    }

    // ===== getLearningStats =====
    @Test
    @DisplayName("getLearningStats 空记录")
    void stats_empty() {
        when(learningRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        Map<String, Object> s = service.getLearningStats(1L);
        assertEquals(0, s.get("totalDuration"));
        assertEquals(0, s.get("learningDays"));
        assertEquals(0, s.get("completedCourses"));
        assertEquals(0, s.get("currentCourses"));
        assertEquals(0, s.get("todayDuration"));
    }

    @Test
    @DisplayName("getLearningStats 统计聚合")
    void stats_aggregate() {
        LocalDate today = LocalDate.now();
        LearningRecord r1 = rec(1L, 10L, 30, new BigDecimal("1.0"), today.atStartOfDay());
        LearningRecord r2 = rec(1L, 11L, 15, new BigDecimal("0.5"), today.minusDays(1).atStartOfDay());
        LearningRecord r3 = rec(1L, 10L, null, null, null);
        when(learningRecordMapper.selectList(any())).thenReturn(List.of(r1, r2, r3));
        Map<String, Object> s = service.getLearningStats(1L);
        assertEquals(45, s.get("totalDuration"));
        assertEquals(2, s.get("learningDays"));
        assertEquals(1, s.get("completedCourses"));
        assertEquals(2, s.get("currentCourses"));
        assertEquals(30, s.get("todayDuration"));
    }

    // ===== getLearningTrend =====
    @Test
    @DisplayName("getLearningTrend 默认 7 天")
    void trend_defaultDays() {
        when(learningRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        List<DailyLearningStatVO> t = service.getLearningTrend(1L, 0);
        assertEquals(7, t.size());
        for (DailyLearningStatVO d : t) {
            assertNotNull(d.getDate());
            assertNotNull(d.getDuration());
            assertNotNull(d.getCount());
        }
    }

    @Test
    @DisplayName("getLearningTrend 聚合窗口内记录")
    void trend_window() {
        LocalDate today = LocalDate.now();
        LearningRecord r = rec(1L, 10L, 20, null, today.atStartOfDay());
        when(learningRecordMapper.selectList(any())).thenReturn(List.of(r));
        List<DailyLearningStatVO> t = service.getLearningTrend(1L, 3);
        assertEquals(3, t.size());
        DailyLearningStatVO todayEntry = t.get(t.size() - 1);
        assertEquals(today.toString(), todayEntry.getDate());
        assertEquals(20, todayEntry.getDuration());
        assertEquals(1, todayEntry.getCount());
    }

    // ===== getMonthlyLearningData =====
    @Test
    @DisplayName("getMonthlyLearningData 整月填充")
    void monthly() {
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();
        LocalDate d = LocalDate.of(year, month, 1);
        LearningRecord r = rec(1L, 10L, 12, null, d.atStartOfDay());
        when(learningRecordMapper.selectList(any())).thenReturn(List.of(r));
        List<DailyLearningStatVO> m = service.getMonthlyLearningData(1L, year, month);
        int daysInMonth = YearMonth.of(year, month).lengthOfMonth();
        assertEquals(daysInMonth, m.size());
        DailyLearningStatVO first = m.get(0);
        assertEquals(d.atStartOfDay().toLocalDate().toString(), first.getDate());
        assertEquals(12, first.getDuration());
        assertEquals(1, first.getCount());
    }
}
