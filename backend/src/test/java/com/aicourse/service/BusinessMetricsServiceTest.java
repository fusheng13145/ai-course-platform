package com.aicourse.service;

import com.aicourse.mapper.MetricsMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * BusinessMetricsService 单元测试：验证 @Scheduled 聚合能正确将 Mapper 计数刷新到 Micrometer Gauge，
 * 以及单条查询异常时不会中断其余指标采集（fail-soft）。
 */
class BusinessMetricsServiceTest {

    @Test
    void collect_updatesGaugesFromMapper() {
        MetricsMapper mapper = org.mockito.Mockito.mock(MetricsMapper.class);
        when(mapper.countActiveCourses()).thenReturn(7L);
        when(mapper.countApprovedEnrollments()).thenReturn(120L);
        when(mapper.countActiveStudents()).thenReturn(300L);
        when(mapper.countTeachers()).thenReturn(12L);
        when(mapper.countSubmissions()).thenReturn(540L);
        when(mapper.countPendingSubmissions()).thenReturn(18L);
        when(mapper.countExams()).thenReturn(9L);
        when(mapper.countQuestions()).thenReturn(250L);
        when(mapper.countWrongQuestions()).thenReturn(80L);
        when(mapper.countLearningRecords()).thenReturn(1200L);
        when(mapper.countDiscussionPosts()).thenReturn(45L);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessMetricsService svc = new BusinessMetricsService(registry, mapper);
        svc.init();

        assertEquals(7.0, registry.find("aicourse_courses").gauge().value(), 0.001);
        assertEquals(120.0, registry.find("aicourse_enrollments").gauge().value(), 0.001);
        assertEquals(300.0, registry.find("aicourse_active_students").gauge().value(), 0.001);
        assertEquals(18.0, registry.find("aicourse_pending_submissions").gauge().value(), 0.001);
        assertEquals(45.0, registry.find("aicourse_discussions").gauge().value(), 0.001);
    }

    @Test
    void collect_handlesMapperExceptionGracefully() {
        MetricsMapper mapper = org.mockito.Mockito.mock(MetricsMapper.class);
        when(mapper.countActiveCourses()).thenThrow(new RuntimeException("db down"));
        when(mapper.countApprovedEnrollments()).thenReturn(5L);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessMetricsService svc = new BusinessMetricsService(registry, mapper);
        svc.collect(); // 不应抛出异常

        // 失败的指标保持 0，其余指标正常刷新
        assertEquals(0.0, registry.find("aicourse_courses").gauge().value(), 0.001);
        assertEquals(5.0, registry.find("aicourse_enrollments").gauge().value(), 0.001);
    }
}
