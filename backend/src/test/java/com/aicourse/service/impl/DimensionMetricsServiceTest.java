package com.aicourse.service.impl;

import com.aicourse.mapper.MetricsMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 维度指标采集服务单元测试：验证课程/班级维度 Gauge 正确注册与取值、以及 null 行容错。
 */
@ExtendWith(MockitoExtension.class)
class DimensionMetricsServiceTest {

    @Mock
    private MetricsMapper metricsMapper;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private DimensionMetricsService service;

    @BeforeEach
    void setUp() {
        when(metricsMapper.courseSubmissions())
                .thenReturn(List.of(Map.of("course", "CS101", "course_name", "计算机网络", "val", 5L)));
        when(metricsMapper.classroomStudents())
                .thenReturn(List.of(Map.of("class", "C1", "class_name", "计科2301", "val", 46L)));
        service = new DimensionMetricsService(registry, metricsMapper);
    }

    @Test
    void collect_registersCourseAndClassGaugesWithValues() {
        service.collect();

        Gauge courseGauge = registry.find("aicourse_course_submissions").tag("course", "CS101").gauge();
        assertNotNull(courseGauge, "课程提交维度 Gauge 应已注册");
        assertEquals(5.0, courseGauge.value());

        Gauge classGauge = registry.find("aicourse_classroom_students").tag("class", "C1").gauge();
        assertNotNull(classGauge, "班级人数维度 Gauge 应已注册");
        assertEquals(46.0, classGauge.value());
    }

    @Test
    void collect_handlesNullRowsWithoutThrowing() {
        when(metricsMapper.courseEnrollments()).thenReturn(null);
        assertDoesNotThrow(() -> service.collect());
    }
}
