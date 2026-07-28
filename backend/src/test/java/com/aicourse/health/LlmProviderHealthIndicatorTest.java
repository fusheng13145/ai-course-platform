package com.aicourse.health;

import com.aicourse.llm.LLMProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link LlmProviderHealthIndicator} 单元测试。
 */
class LlmProviderHealthIndicatorTest {

    @Test
    void configuredProvider_shouldReportUp() {
        LLMProvider provider = mock(LLMProvider.class);
        when(provider.name()).thenReturn("deepseek");
        when(provider.isConfigured()).thenReturn(true);

        LlmProviderHealthIndicator indicator = new LlmProviderHealthIndicator(provider);
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("deepseek", health.getDetails().get("provider"));
        assertEquals(Boolean.TRUE, health.getDetails().get("configured"));
    }

    @Test
    void unconfiguredProvider_shouldReportDown() {
        LLMProvider provider = mock(LLMProvider.class);
        when(provider.name()).thenReturn("openai");
        when(provider.isConfigured()).thenReturn(false);

        LlmProviderHealthIndicator indicator = new LlmProviderHealthIndicator(provider);
        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("openai", health.getDetails().get("provider"));
        assertEquals(Boolean.FALSE, health.getDetails().get("configured"));
    }
}
