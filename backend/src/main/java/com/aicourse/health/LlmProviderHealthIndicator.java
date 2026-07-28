package com.aicourse.health;

import com.aicourse.llm.LLMProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 大模型供应商健康指示器。
 *
 * <p>作为 readiness 探针的一部分：当当前启用的 LLM 供应商未配置 API Key 时，
 * 返回 DOWN，使编排系统（Kubernetes / Docker Swarm）知道 AI 对话/生成能力尚未就绪，
 * 避免流量进入后全部触发“未配置”降级文案。</p>
 *
 * <p>注意：本指示器仅做静态配置检查，不做实际模型调用，避免健康探测产生 token 费用与延迟。</p>
 */
@Component
public class LlmProviderHealthIndicator implements HealthIndicator {

    private final LLMProvider llmProvider;

    public LlmProviderHealthIndicator(LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    @Override
    public Health health() {
        String name = llmProvider.name();
        if (llmProvider.isConfigured()) {
            return Health.up()
                    .withDetail("provider", name)
                    .withDetail("configured", true)
                    .build();
        }
        return Health.down()
                .withDetail("provider", name)
                .withDetail("configured", false)
                .withDetail("reason", "API Key 未配置，AI 能力不可用")
                .build();
    }
}
