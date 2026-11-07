package com.aicourse.service.impl;

import com.aicourse.entity.AiCallLog;
import com.aicourse.mapper.AiCallLogMapper;
import com.aicourse.vo.AiUsageByCourseVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiCallLogServiceImpl 单元测试（纯 Mockito）。
 *
 * <p>两类断言重点：① 落库是"失败安全"的旁路——mapper 抛异常也不得外抛；
 * ② 聚合口径——含"未绑定课程"行的总量守恒、均值按次数加权（直接对分组 AVG 再平均是错的）。</p>
 */
@ExtendWith(MockitoExtension.class)
class AiCallLogServiceImplTest {

    @Mock
    private AiCallLogMapper aiCallLogMapper;
    @Mock
    private AiQuotaService aiQuotaService;

    private AiCallLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiCallLogServiceImpl(aiCallLogMapper, aiQuotaService);
    }

    // ===================== record =====================

    @Test
    @DisplayName("record 写入全部字段，completion token 由输出文本按 1.5 字符/token 折算")
    void record_persistsAllFields() {
        when(aiQuotaService.estimateTokens(any(String[].class))).thenReturn(3L);
        when(aiCallLogMapper.insert(any(AiCallLog.class))).thenReturn(1);

        service.record(7L, 4007L, "QA_ASK", "deepseek", 120L, "abcd", 88L, true, null);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogMapper).insert(captor.capture());
        AiCallLog row = captor.getValue();
        assertEquals(7L, row.getUserId());
        assertEquals(4007L, row.getCourseId());
        assertEquals("QA_ASK", row.getScene());
        assertEquals("deepseek", row.getProvider());
        assertEquals(120, row.getPromptTokens());
        assertEquals(3, row.getCompletionTokens());
        assertEquals(88, row.getDurationMs());
        assertEquals(1, row.getSuccess());
        assertNull(row.getErrorMsg());
    }

    @Test
    @DisplayName("record 失败原因超长截断到 255（列宽），且负数 token 归零")
    void record_truncatesAndClamps() {
        when(aiQuotaService.estimateTokens(any(String[].class))).thenReturn(0L);
        when(aiCallLogMapper.insert(any(AiCallLog.class))).thenReturn(1);

        service.record(1L, null, "ESSAY_CORRECT", null, -5L, null, -9L, false, "x".repeat(400));

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(aiCallLogMapper).insert(captor.capture());
        AiCallLog row = captor.getValue();
        assertEquals(0, row.getPromptTokens());
        assertEquals(0, row.getDurationMs());
        assertEquals(0, row.getSuccess());
        assertEquals(255, row.getErrorMsg().length());
    }

    @Test
    @DisplayName("落库失败安全：mapper 抛异常必须被吞掉，绝不外抛拖垮 AI 主流程")
    void record_mapperFailure_isSwallowed() {
        when(aiQuotaService.estimateTokens(any(String[].class))).thenReturn(0L);
        when(aiCallLogMapper.insert(any(AiCallLog.class))).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> service.record(1L, 2L, "QA_ASK", null, 1L, null, 1L, true, null));
    }

    // ===================== statistics =====================

    @Test
    @DisplayName("statistics 聚合总览：未绑定课程单列、总量守恒、均值按次数加权、成功率按行分摊")
    void statistics_aggregates() {
        when(aiCallLogMapper.selectUsageByCourse(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        row(4007L, "计算机网络", 3L, 300L, 100.0, 3L),
                        row(null, null, 1L, 50L, 200.0, 0L)));

        Map<String, Object> stats = service.statistics(30);

        assertEquals(30, stats.get("rangeDays"));
        assertEquals(4L, stats.get("totalCalls"));
        assertEquals(350L, stats.get("totalTokens"));
        assertEquals(1L, stats.get("distinctCourses"));
        assertEquals(1L, stats.get("unboundCalls"));
        // 加权均值 = (100*3 + 200*1) / 4 = 125（若对分组 AVG 直接平均会得到 150 —— 那是错的）
        assertEquals(125.0, stats.get("avgDurationMs"));
        assertEquals(0.75, stats.get("successRate"));

        @SuppressWarnings("unchecked")
        List<AiUsageByCourseVO> courses = (List<AiUsageByCourseVO>) stats.get("courses");
        assertEquals("未绑定课程", courses.get(1).getCourseName());
        assertEquals(1.0, courses.get(0).getSuccessRate());
        assertEquals(0.0, courses.get(1).getSuccessRate());
        assertNotNull(stats.get("collectionNote"));
        assertTrue(String.valueOf(stats.get("collectionNote")).contains("2026-10-13"));
    }

    @Test
    @DisplayName("statistics 天数为 0 或超大时收敛到 1 / 365（越界不放大查询窗口）")
    void statistics_clampsDays() {
        when(aiCallLogMapper.selectUsageByCourse(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertEquals(1, service.statistics(0).get("rangeDays"));
        assertEquals(365, service.statistics(9999).get("rangeDays"));
        // 无调用时成功率不编造：返回 null 而不是 0 或 1
        assertNull(service.statistics(7).get("successRate"));
        assertEquals(0L, service.statistics(7).get("totalCalls"));
    }

    // ===================== statisticsByDay / statisticsByScene（2026-11-07 P2 批次） =====================

    @Test
    @DisplayName("statisticsByDay 聚合：按日行透传且成功率按行分摊；院系口径走 ForCollege 变体")
    void statisticsByDay_aggregates() {
        com.aicourse.vo.AiUsageByDayVO d1 = new com.aicourse.vo.AiUsageByDayVO();
        d1.setDay("2026-11-06");
        d1.setCalls(3L);
        d1.setTokens(300L);
        d1.setAvgDurationMs(100.0);
        d1.setSuccessCalls(2L);
        when(aiCallLogMapper.selectUsageByDay(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(d1));

        Map<String, Object> stats = service.statisticsByDay(30);
        assertEquals(30, stats.get("rangeDays"));
        assertEquals(d1, ((List<?>) stats.get("days")).get(0));
        assertEquals(0.67, d1.getSuccessRate()); // round2 口径（与 statistics 既有断言一致）

        // 院系口径：必须走内连接变体，且带 scope 标记
        when(aiCallLogMapper.selectUsageByDayForCollege(any(LocalDateTime.class),
                any(LocalDateTime.class), eq(10L))).thenReturn(List.of());
        Map<String, Object> college = service.statisticsByDay(30, 10L);
        assertEquals("college", college.get("scope"));
        assertEquals(10L, college.get("collegeId"));
        verify(aiCallLogMapper).selectUsageByDayForCollege(any(LocalDateTime.class),
                any(LocalDateTime.class), eq(10L));
    }

    @Test
    @DisplayName("statisticsByScene 聚合：场景行透传；空窗不出行也不编造成功率")
    void statisticsByScene_aggregates() {
        com.aicourse.vo.AiUsageBySceneVO s1 = new com.aicourse.vo.AiUsageBySceneVO();
        s1.setScene("QA_ASK");
        s1.setCalls(5L);
        s1.setTokens(500L);
        s1.setAvgDurationMs(220.0);
        s1.setSuccessCalls(5L);
        when(aiCallLogMapper.selectUsageByScene(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(s1));

        Map<String, Object> stats = service.statisticsByScene(7);
        assertEquals(7, stats.get("rangeDays"));
        assertEquals(1, ((List<?>) stats.get("scenes")).size());
        assertEquals(1.0, s1.getSuccessRate());

        when(aiCallLogMapper.selectUsageByScene(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        Map<String, Object> empty = service.statisticsByScene(7);
        assertTrue(((List<?>) empty.get("scenes")).isEmpty());
    }

    private AiUsageByCourseVO row(Long courseId, String name, Long calls, Long tokens,
                                  Double avgDuration, Long successCalls) {
        AiUsageByCourseVO vo = new AiUsageByCourseVO();
        vo.setCourseId(courseId);
        vo.setCourseName(name);
        vo.setCalls(calls);
        vo.setTokens(tokens);
        vo.setAvgDurationMs(avgDuration);
        vo.setSuccessCalls(successCalls);
        return vo;
    }
}
