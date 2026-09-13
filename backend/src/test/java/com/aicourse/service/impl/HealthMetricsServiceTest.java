package com.aicourse.service.impl;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthMetricsServiceTest {

    @Test
    void registersAllComponentsAndMapsStatus() {
        HealthIndicator database = mock(HealthIndicator.class);
        when(database.health()).thenReturn(Health.up().build());

        HealthIndicator redis = mock(HealthIndicator.class);
        when(redis.health()).thenReturn(Health.down().build());

        HealthIndicator llm = mock(HealthIndicator.class);
        when(llm.health()).thenReturn(Health.up().build());

        HealthIndicator python = mock(HealthIndicator.class);
        when(python.health()).thenReturn(Health.unknown().build());

        var registry = new SimpleMeterRegistry();
        var service = new HealthMetricsService(registry, Map.of(
                "databaseHealthIndicator", database,
                "redisHealthIndicator", redis,
                "llmProviderHealthIndicator", llm,
                "pythonServicesHealthIndicator", python
        ));

        service.refresh();

        assertEquals(1.0, gauge(registry, "database"), 0.001);
        assertEquals(0.0, gauge(registry, "redis"), 0.001);
        assertEquals(1.0, gauge(registry, "llmProvider"), 0.001);
        assertEquals(0.5, gauge(registry, "pythonServices"), 0.001);
    }

    @Test
    void missingIndicatorYieldsUnknown() {
        var registry = new SimpleMeterRegistry();
        var service = new HealthMetricsService(registry, Map.of());

        service.refresh();

        for (String component : List.of("database", "redis", "llmProvider", "pythonServices")) {
            assertEquals(0.5, gauge(registry, component), 0.001);
        }
    }

    private double gauge(SimpleMeterRegistry registry, String component) {
        return registry.find("aicourse_health_status")
                .tag("component", component)
                .gauge()
                .value();
    }
}
